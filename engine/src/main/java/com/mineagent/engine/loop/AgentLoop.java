package com.mineagent.engine.loop;

import com.mineagent.api.agent.tool.*;
import com.mineagent.api.agent.skill.*;
import com.mineagent.api.entity.AgentPlayer;
import com.mineagent.api.llm.*;
import com.mineagent.api.llm.model.ModelFamilies;
import com.mineagent.api.llm.provider.*;
import com.mineagent.api.platform.Services;
import com.mineagent.api.task.*;
import com.mineagent.engine.MineAgentEngine;
import com.mineagent.engine.persona.PersonaProfile;
import com.mineagent.engine.persona.EmotionState;
import com.mineagent.engine.memory.PlaceEventMemory;
import com.mineagent.engine.memory.ReflectionSystem;
import com.mineagent.engine.memory.ImportanceEvaluator;
import com.mineagent.engine.memory.ExperienceStore;
import com.mineagent.engine.memory.LayeredMemory;
import com.mineagent.engine.skill.SkillLibrary;
import com.mineagent.engine.skill.SkillRuntime;
import com.mineagent.engine.cache.DecisionCache;
import com.mineagent.engine.cognition.RealtimeCognition;
import com.mineagent.engine.cognition.SituationSnapshot;
import com.mineagent.engine.cognition.TeamBlackboard;
import com.mineagent.engine.theory.TheoryOfMind;
import com.mineagent.engine.knowledge.MinecraftKnowledgeGraph;
import com.mineagent.engine.planning.IntentContract;
import com.mineagent.engine.planning.HierarchicalRollingPlanner;
import com.mineagent.engine.planning.PlanGraph;
import com.mineagent.engine.planning.ResourceReservationLedger;
import com.mineagent.engine.exploration.MechanismExplorer;
import com.mineagent.engine.exploration.EnvironmentFingerprint;
import com.mineagent.engine.task.TaskContext;
import com.mineagent.engine.world.BeliefState;
import com.mineagent.engine.world.WorldAssetIndex;
import com.mineagent.engine.world.WorldAssetObserver;
import com.mineagent.engine.world.SemanticWorldModel;
import com.mineagent.engine.world.ActionTransactionLedger;
import com.mineagent.engine.telemetry.CognitionMetrics;
import com.mineagent.engine.cognition.CognitiveState;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.time.Duration;

/**
 * The Agent Loop - the core orchestrator that drives the LLM conversation
 * and executes tool calls.
 *
 * <h3>Architecture: event-driven, dual-rail</h3>
 * <ol>
 *   <li>The LLM is NOT in the tick loop - it only runs when:
 *       <ul>
 *         <li>Owner speaks to companion</li>
 *         <li>Tool result arrives</li>
 *         <li>Async task finishes</li>
 *       </ul>
 *   </li>
 *   <li>Tool results go through two rails:
 *       <ul>
 *         <li><b>Query rail</b> (sync tools): reply immediately, batch with
 *             other tool results</li>
 *         <li><b>Action rail</b> (async tools): dispatch a task, get a
 *             task_id; the task_finished event wakes the loop later</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Conversation flow</h3>
 * <pre>
 *   System prompt + history → LLM → assistant message
 *     ├─ text only → done, wait for next event
 *     └─ tool_calls → execute all → collect results → feed back → LLM again
 * </pre>
 *
 * <h3>Safety mechanisms</h3>
 * <ul>
 *   <li>History truncation: keeps last N messages to avoid context overflow</li>
 *   <li>Tool-call recursion cap: prevents infinite tool-calling loops</li>
 *   <li>LLM retry with backoff: handles transient network errors</li>
 *   <li>Assistant text output: speaks to the owner in-game</li>
 * </ul>
 */
public class AgentLoop {

    /** Maximum conversation history messages (excluding system prompt).
     *  Lowered from 50 → 24 to reduce token consumption and latency.
     *  Old messages are summarized (see {@link #trimHistory}) so context
     *  is not lost, just compressed. */

    /** Number of recent messages to keep verbatim before summarizing.
     *  Messages older than this window get folded into a compact
     *  "[SUMMARY]" system message to save tokens. */

    /** Hard bound for the accumulated rolling summary. */
    private static final int MAX_HISTORY_SUMMARY_CHARS = 2_400;

    /** Maximum consecutive tool-call rounds within a single turn. */

    /** Maximum LLM retry attempts. */
    private static final int MAX_RETRIES = 3;

    /** Retry base delay in milliseconds. */
    private static final long RETRY_BASE_MS = 1000;

    /** Maximum length (chars) of a tool result before it gets truncated.
     *  Long tool outputs (e.g. look_around dumping 50 blocks) blow up
     *  token usage and slow down the next LLM call. We cap results to
     *  keep the conversation compact. */
    private static final int MAX_TOOL_RESULT_CHARS = 800;

    /** Cache TTL check interval — skip the LLM call entirely if we have
     *  a fresh cached decision for a similar situation. This is the
     *  single biggest latency win for repetitive actions (e.g. mining
     *  a vein block-by-block). */

    /** 连续缓存命中上限。
     *  连续命中达到此次数后，强制走一次 LLM 调用，防止 AI 陷入
     *  "卡带"式重复（同一决策无限循环）。
     *  设为 2：允许 1 次复用（覆盖连续挖矿场景），第 3 次强制思考。 */
    private static final int MAX_CONSECUTIVE_CACHE_HITS = 2;

    /** 纯文本响应补救次数上限。
     *  当 LLM 说了要行动但没调用工具时，给它 N 次机会补救。
     *  超过后真正结束回合，避免无限递归。 */
    private static final int MAX_REMEDIATION_ROUNDS = 1;

    /**
     * Stable high-frequency tool surface. Specialized GUI, storage, ranged,
     * location and memory tools are exposed for the remainder of a turn by
     * query_extra_tools, keeping the large schema prefix cacheable.
     */
    private static final Set<String> CORE_TOOL_NAMES = Set.of(
            "goto", "look_around", "scan_blocks", "get_self_status",
            "resolve_need", "plan_acquisition",
            "auto_mine", "build", "craft", "lookup_recipe", "interact_at",
            "collect_items", "eat_item", "use_item", "equip_item", "transfer_items",
            "melee_attack", "todowrite", "task_status", "task_stop",
            "wait_for", "query_extra_tools");
    private static final Set<String> SKILL_ACTION_TOOLS = Set.of(
            "goto", "auto_mine", "build", "melee_attack", "ranged_attack",
            "equip_item", "eat_item", "drop_items", "collect_items",
            "transfer_items", "craft", "interact_at", "interact_entity",
            "use_item", "wait_for", "close_gui");
    private static final Set<String> SKILL_TRACE_TOOLS = Set.of(
            "goto", "auto_mine", "build", "melee_attack", "ranged_attack",
            "equip_item", "eat_item", "drop_items", "collect_items",
            "transfer_items", "craft", "interact_at", "interact_entity",
            "use_item", "wait_for", "close_gui", "look_around", "scan_blocks", "scan_nearby_entities",
            "get_self_status", "get_owner_status", "get_world_info",
            "resolve_need", "plan_acquisition", "lookup_recipe", "inspect_block",
            "inspect_block_storage", "inspect_gui", "recall_memory");
    private static final Set<String> INVENTORY_EXTRA_TOOLS = Set.of(
            "drop_items", "inspect_gui", "close_gui", "inspect_block_storage");
    private static final Set<String> PERCEPTION_EXTRA_TOOLS = Set.of(
            "recall_memory", "inspect_block", "scan_nearby_entities",
            "get_owner_status", "get_world_info");
    private static final Set<String> COMBAT_EXTRA_TOOLS = Set.of(
            "scan_nearby_entities", "ranged_attack", "interact_entity");
    private static final Set<String> LOCATION_EXTRA_TOOLS = Set.of(
            "recall_memory", "locate_structure", "locate_biome");
    private static final Set<String> GUI_EXTRA_TOOLS = Set.of(
            "inspect_gui", "close_gui", "inspect_block", "inspect_block_storage");

    /** 距上次玩家消息的时间（毫秒）。
     *  如果玩家刚刚说话（<30秒），不使用缓存 — 对话场景需要
     *  LLM 真正理解并回应，缓存复用会导致答非所问。 */
    private static final long CACHE_SKIP_AFTER_PLAYER_MSG_MS = 30_000;

    private final AgentPlayer companion;
    private final String providerId;
    private volatile LLMProvider companionProvider;
    private final String apiKey;
    private final String model;  // final — model switching is not allowed at runtime
    private final String baseUrl;
    private final double temperature;
    private final int maxTokens;
    private volatile String reasoningEffort; // off/low/medium/high/xhigh/max or null
    /** The role of the turn in progress, for SMH's smh/auto routing (RoleRouting; SMH plan 69, MA1.6). */
    private volatile String currentRole = "act";
    /** The call-rate setting's limits (SMH plan 69, MA2.5; Busy by default): see settings/CallRate. */
    private volatile int maxHistory = com.mineagent.engine.settings.CallRate.BUSY.history();
    private volatile int recentKeep = com.mineagent.engine.settings.CallRate.BUSY.recentKeep();
    private volatile int maxToolRounds = com.mineagent.engine.settings.CallRate.BUSY.toolRounds();
    private volatile boolean decisionCacheEnabled = com.mineagent.engine.settings.CallRate.BUSY.decisionCache();
    private volatile double outputScale = com.mineagent.engine.settings.CallRate.BUSY.outputScale();
    /**
     * Hard-coded ratings of skills, goals and (block 3) tactics, kept in the permanent library (SMH plan 69):
     * what worked better is used first, and what was tried too little gets its trial.
     */
    private final com.mineagent.engine.learning.Scorecard scorecard = new com.mineagent.engine.learning.Scorecard();
    /** The owner's goal and the skill run under way, measured on the server thread for the scorecard. */
    private final com.mineagent.engine.learning.EpisodeTracker goalEpisode = new com.mineagent.engine.learning.EpisodeTracker();
    private final com.mineagent.engine.learning.EpisodeTracker skillEpisode = new com.mineagent.engine.learning.EpisodeTracker();
    private String skillEpisodeRun = "";
    /** Practice when nobody needs it (MA2.1), and the planner's goal when a practice task began. */
    private final com.mineagent.engine.learning.Curriculum curriculum = new com.mineagent.engine.learning.Curriculum();
    private volatile String curriculumGoalAtBegin = "";
    private final long createdAtMs = System.currentTimeMillis();

    // ── Combat (SMH plan 69, block 3): what it guards, how it fights, every fight rated by the game ──
    /** A guarded spot, or a guarded player or creature (its UUID); null: not guarding. */
    /** A second read of big plans and of goals that keep failing (MA4.1). */
    private final com.mineagent.engine.planning.PlanReview planReview = new com.mineagent.engine.planning.PlanReview();
    /** Hostiles within 16 blocks, taken on the server thread each second for the situation brief (MA4.2). */
    private volatile List<com.mineagent.engine.planning.SituationBrief.Hostile> nearbyHostiles = List.of();
    /** What it guards, read on the server thread with the hostiles (the brief is built on the model's thread). */
    private volatile String guardLine = "";
    /** Fighting ways the model came up with, rated beside the stances (combat/TacticBook); kept with the scores. */
    private final com.mineagent.engine.combat.TacticBook tacticBook = new com.mineagent.engine.combat.TacticBook();
    /** Skill repair (learning/SkillRepair): repairs under way, by base skill, and the last failures of each skill. */
    private final Set<String> repairsInFlight = ConcurrentHashMap.newKeySet();
    private final Map<String, Deque<String>> skillFailures = new ConcurrentHashMap<>();
    /** A line to the owner for a goal step, a new skill, or health back after a close call (MA3.3). */
    private final com.mineagent.engine.chat.MilestoneTalk milestoneTalk = new com.mineagent.engine.chat.MilestoneTalk();
    private volatile boolean closeCall;
    /** Long goals and standing stock orders (planning/GoalBook, MA3.1); saved with the world. */
    private final com.mineagent.engine.planning.GoalBook goalBook = new com.mineagent.engine.planning.GoalBook();
    private final java.util.concurrent.atomic.AtomicBoolean goalStepInFlight = new java.util.concurrent.atomic.AtomicBoolean(false);
    /** When the goal loop last gave the body a step, and last nudged it (a goal, or a stock order) when it sat idle. */
    private volatile long lastGoalStepMs;
    private volatile long lastGoalNudgeMs;
    private volatile long lastStockNudgeMs;
    private volatile net.minecraft.world.phys.Vec3 guardPost;
    private volatile UUID guardWard;
    private volatile double guardRadius = 12.0;
    /** The model's latest tactics (smh/fight) and when they came; used for 10 s, then learning's stance again. */
    private volatile com.mineagent.engine.combat.Tactics advisedTactics;
    private volatile long advisedAtMs;
    private volatile long lastAdviceMs;
    private final java.util.concurrent.atomic.AtomicBoolean adviceInFlight = new java.util.concurrent.atomic.AtomicBoolean();
    private final com.mineagent.engine.learning.EpisodeTracker fightEpisode = new com.mineagent.engine.learning.EpisodeTracker();
    private volatile String fightMob = "";
    /** Fight advice runs on its own thread, so it never waits behind talk, lessons or practice. */
    private final ExecutorService fightExecutor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "MineAgent-Fight");
        t.setDaemon(true);
        return t;
    });
    /** This companion's own settings: trusted players and persona (SMH plan 69, MA1.7). */
    private volatile com.mineagent.engine.settings.AgentSettings agentSettings = com.mineagent.engine.settings.AgentSettings.DEFAULTS;
    /** Talk with players who are neither the owner nor trusted: no tools, no task, no inbox (MA1.7). */
    private final com.mineagent.engine.chat.TalkOnly talkOnly = new com.mineagent.engine.chat.TalkOnly();
    /** Talk replies run here, never on the server thread and never in the way of the loop's own turns. */
    private final ExecutorService talkExecutor = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "MineAgent-Talk");
        t.setDaemon(true);
        return t;
    });

    private final List<ChatMessage> history = new ArrayList<>();
    /** Pending deliberation events, coalesced by semantic identity. */
    private final LinkedHashMap<String, String> inbox = new LinkedHashMap<>();
    private final Object inboxLock = new Object(); // unified inbox lock (L1 fix)
    private static final int MAX_PENDING_EVENTS = 32;

    private volatile boolean inProgress = false;
    private volatile boolean suspended = false;

    /**
     * Stale Request detection (borrowed from Mindcraft).
     *
     * <p>Tracks the timestamp of the last significant event (owner message,
     * body log, task completion). When an LLM call is in progress and a
     * new event arrives, this timestamp updates. After the LLM returns,
     * we compare the timestamp — if it changed during the call, the
     * response is "stale" (the situation it was generated for no longer
     * applies) and we discard it and restart the turn.
     *
     * <p>This prevents the classic "AI answers a question you asked 10
     * seconds ago, ignoring the new one you just asked" problem, and
     * reduces latency for new messages by 5-10x (no waiting for the
     * old response to complete).
     */
    private final AtomicLong eventGeneration = new AtomicLong();
    private final Set<String> exposedExtraTools = ConcurrentHashMap.newKeySet();
    private record PendingSkillAction(String description,
                                      ChatMessage.ToolCallRef call) {}
    private final ConcurrentMap<String, PendingSkillAction> pendingTaskActions =
            new ConcurrentHashMap<>();
    /** Correlates executor task IDs with tool names for semantic outcomes. */
    private final ConcurrentMap<String, String> dispatchedActionTools =
            new ConcurrentHashMap<>();
    /** Verified action prefix for learning one reusable multi-step episode. */
    private final Object verifiedTraceLock = new Object();
    private final List<ChatMessage.ToolCallRef> verifiedActionTrace = new ArrayList<>();
    private String verifiedTraceGoal = "";

    /**
     * Only populated while this loop thread is blocked inside a provider HTTP
     * call. A newer owner command can interrupt that obsolete request instead
     * of waiting up to the provider timeout before beginning the new turn.
     */
    private final AtomicReference<Thread> activeLLMCallThread = new AtomicReference<>();

    /**
     * Server-thread-published body state. The LLM executor must never query a
     * live Minecraft level from its background thread, but it still needs
     * authoritative progress to avoid interpreting RUNNING as "moving".
     */
    private record LiveBodyState(String taskId, String taskName, TaskState state,
                                 TaskSnapshot snapshot, String message, long gameTick) {
        static LiveBodyState idle() {
            return new LiveBodyState(null, "idle", null, null, null, 0L);
        }
    }

    private final AtomicReference<LiveBodyState> liveBodyState =
            new AtomicReference<>(LiveBodyState.idle());

    /**
     * Sparse Thinking (borrowed from Game-TARS).
     *
     * <p>Not every situation requires LLM reasoning. Routine actions
     * (continuing to mine a vein, walking along a known path) can be
     * handled by the decision cache or fast-path heuristics. Only "key
     * decision points" (new task, unexpected event, owner message,
     * inventory full, mob appears) trigger a real LLM call.
     *
     * <p>Track the last action type to detect repetitive patterns.
     * When the same action type repeats, the decision cache is preferred.
     */
    private volatile String lastActionType = "";
    private volatile int sameActionCount = 0;

    private final ExecutorService executor;
    /** Local-only counters; never contain prompts, keys, or world dumps. */
    private final CognitionMetrics metrics;
    private final CognitiveState cognitiveState;
    /** Tiered recall layer shared by working, episodic, semantic and team evidence. */
    private final LayeredMemory layeredMemory;

    // ── Cognitive subsystems ──
    private final PersonaProfile persona;
    private final EmotionState emotion;
    private final PlaceEventMemory placeMemory;
    private final com.mineagent.engine.pathing.execute.DoorKeeper doorKeeper =
            new com.mineagent.engine.pathing.execute.DoorKeeper();
    /** Whether the owner is AFK (SMH plan 70, MA5.4). */
    private final OwnerActivity ownerActivity = new OwnerActivity();
    /** Its trail, the places it knows and the routes between them (SMH plan 70, MA5.6). */
    private final com.mineagent.engine.world.PlaceKeeper placeKeeper = new com.mineagent.engine.world.PlaceKeeper();
    /** The journal of what happened in this world (SMH plan 70, MA5.9). */
    private final com.mineagent.engine.memory.EventJournal journal = new com.mineagent.engine.memory.EventJournal();
    /** Chests and what is in them (MA5.7). */
    private volatile com.mineagent.engine.memory.ChestMemory chests = new com.mineagent.engine.memory.ChestMemory(null,
            com.mineagent.api.config.MineAgentConfig.MemoryConfig.DEFAULTS.chests());
    private final SkillLibrary skillLib;
    private final DecisionCache decisionCache;
    private final TheoryOfMind theoryOfMind;
    private final MinecraftKnowledgeGraph knowledgeGraph;
    private final PlanGraph planner;
    private final ReflectionSystem reflection;
    private final ImportanceEvaluator importance;
    private final BeliefState beliefState;
    /**
     * Unified object permanence for carried items, known storage, facilities
     * and dropped stacks. This is the common substrate behind reuse decisions;
     * it replaces one-off "is there a crafting table?" memory patches.
     */
    private final WorldAssetIndex worldAssetIndex;
    private final ExperienceStore experienceStore;
    /** Shared temporal evidence consumed by skills, planning, and exploration. */
    private final SemanticWorldModel semanticWorldModel;
    /** Correlated server-evidence ledger for all physical actions. */
    private final ActionTransactionLedger actionLedger;
    private final ResourceReservationLedger reservationLedger;
    /** Strategic/tactical/execution rolling horizon over the verified PlanGraph. */
    private final HierarchicalRollingPlanner rollingPlanner;
    /** One-at-a-time, risk-bounded unfamiliar-mechanism experiments. */
    private final MechanismExplorer mechanismExplorer;
    /** Replays learned action sequences only as executor-verified closed loops. */
    private final SkillRuntime skillRuntime;
    /**
     * Server-thread tactical cognition. It continuously publishes immutable
     * evidence and handles reactions that are too urgent for an HTTP round
     * trip; the LLM is woken only when executor evidence requires replanning.
     */
    private final RealtimeCognition realtimeCognition;
    /** Spatial memory — records points of interest discovered while
     *  exploring (ores, structures, hazards, chests). Backs the
     *  "记忆点" section of the system prompt so the LLM can recall
     *  "I saw iron at (10, 64, -5)" without re-scanning. */
    private final com.mineagent.engine.memory.CognitiveMap cognitiveMap;
    /** 记忆持久化：游戏关闭时保存记忆，重启后恢复（解决"失忆"问题）。
     *  可为 null — 当 world data dir 尚未设置时（记忆不持久化，仅内存）。 */
    private final com.mineagent.engine.memory.MemoryPersistence persistence;
    /** The bigger memory (SMH plan 70, MA5.7): things seen, the chunk map, places, routes, trips, the journal. */
    private volatile com.mineagent.engine.memory.store.MemoryStore store;
    /** Stateful Responses transport is isolated per companion, never shared by registry singletons. */
    private final AtomicReference<ProviderSessionState> providerTransportState;
    /** Number of durable history messages already represented by a Responses session. */
    private int providerHistoryCursor;
    /** Published on the server thread; prompt construction never reads Level. */
    private final AtomicReference<WorldAssetIndex.Position> liveAssetPosition =
            new AtomicReference<>();
    private final AtomicLong liveAssetGameTick = new AtomicLong();
    private volatile long lastAssetSnapshotTick = Long.MIN_VALUE;

    public AgentLoop(AgentPlayer companion, String providerId, String apiKey,
                      String model, String baseUrl, double temperature, int maxTokens,
                      String reasoningEffort) {
        this.companion = companion;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r, "MineAgent-Loop-" + companion.companionId().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
        this.providerId = providerId;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.maxTokens = maxTokens != 0 ? maxTokens : ModelFamilies.defaultMaxTokens(model);
        this.reasoningEffort = reasoningEffort;
        this.metrics = new CognitionMetrics();
        this.cognitiveState = new CognitiveState();
        this.layeredMemory = new LayeredMemory();
        // Registry providers are process-wide singletons, while Responses
        // cursors are conversation-owned.  Persist a stable per-companion
        // identity in the request so two companions using the same model can
        // never accidentally continue one another's response chain.
        String conversationIdentity = stableConversationIdentity(companion);
        this.providerTransportState = new AtomicReference<>(
                new ProviderSessionState(providerId == null ? "unknown" : providerId,
                        model, conversationIdentity, null, null, 0L));

        // Initialize cognitive subsystems
        this.persona = PersonaProfile.random();
        this.emotion = new EmotionState();
        this.placeMemory = new PlaceEventMemory();
        this.skillLib = new SkillLibrary();
        this.skillLib.setScorecard(scorecard);
        this.decisionCache = new DecisionCache();
        this.theoryOfMind = new TheoryOfMind();
        this.knowledgeGraph = new MinecraftKnowledgeGraph();
        this.planner = new PlanGraph();
        this.reflection = new ReflectionSystem();
        this.importance = new ImportanceEvaluator();
        this.beliefState = new BeliefState();
        this.worldAssetIndex = new WorldAssetIndex();
        this.experienceStore = new ExperienceStore();
        this.semanticWorldModel = new SemanticWorldModel();
        this.actionLedger = new ActionTransactionLedger();
        this.reservationLedger = new ResourceReservationLedger();
        this.rollingPlanner = new HierarchicalRollingPlanner(
                planner, semanticWorldModel);
        this.mechanismExplorer = new MechanismExplorer(semanticWorldModel,
                (experiment, supported, evidence, gameTick) ->
                        beliefState.observeRuleOutcome(experiment.subject(),
                                experiment.probeTool(), experiment.hypothesis(),
                                supported, evidence, gameTick),
                skillLib, EnvironmentFingerprint.capture(),
                this::dispatchExplorationCompensation);
        this.skillRuntime = new SkillRuntime(skillLib, semanticWorldModel,
                new SkillRuntime.ActionDispatcher() {
                    @Override
                    public SkillRuntime.DispatchResult dispatch(String actionId,
                                                                 String tool,
                                                                 com.google.gson.JsonObject arguments) {
                        return dispatchSkillAction(actionId, tool, arguments);
                    }

                    @Override
                    public void cancel(String actionId) {
                        cancelSkillAction(actionId);
                    }
                }, this::onSkillRuntimeCompleted);
        this.realtimeCognition = new RealtimeCognition(companion);
        this.cognitiveMap = new com.mineagent.engine.memory.CognitiveMap();
        // The persistent loader appends validated dialogue after this system
        // entry. Keeping index zero reserved preserves provider message order.
        history.add(ChatMessage.system(buildSystemPrompt()));

        // Memory persistence: per-companion directory, restore previous memories.
        // Solves the "restart = amnesia" problem — the companion remembers
        // locations, lessons and learned importance weights across restarts.
        //
        // IMPORTANT: the directory key must be STABLE across restarts.
        // companionId is a random UUID regenerated on every spawn/restore,
        // so it cannot be used — the restored companion would never find
        // its previous memories. Use ownerUuid + companionName instead,
        // matching CompanionStore's upsert key.
        com.mineagent.engine.memory.MemoryPersistence p = null;
        java.nio.file.Path worldDir = MineAgentEngine.getWorldDataDir();
        if (worldDir != null) {
            String stableKey = companion.ownerUuid()
                    + "_" + sanitizeForPath(companion.companionName());
            java.nio.file.Path memDir = worldDir.resolve("mineagent_memory")
                    .resolve(stableKey);
            p = new com.mineagent.engine.memory.MemoryPersistence(
                    memDir, cognitiveMap, placeMemory, importance, reflection,
                    planner, beliefState, worldAssetIndex, experienceStore,
                    skillLib, semanticWorldModel, rollingPlanner,
                    mechanismExplorer, history, cognitiveState, layeredMemory,
                    actionLedger, reservationLedger);
            // Skills, general lessons and mechanisms in the permanent library, for every world (MA2.0).
            java.nio.file.Path configDir = MineAgentEngine.getConfigDir();
            if (configDir != null) {
                p.setLibraryRoot(com.mineagent.engine.memory.PermanentLibrary.root(configDir));
            }
            p.setScorecard(scorecard);
            p.setCurriculum(curriculum);
            p.setGoalBook(goalBook);
            p.setRouteMemory(placeKeeper.memory()); // reads an older routes.json once; the store keeps them now
            p.setTalks(phraseBook, talkSummary);
            p.setChestPurposes(chestPurposes);
            p.setChestPermission(this);
            p.setTacticBook(tacticBook);
            p.loadAll();
            openStore(memDir.resolve("store"));
            checkAfterWorldSave();
            // A restored Responses response id already represents the durable
            // dialogue prefix.  Sending that prefix again duplicates turns and
            // destroys prompt-cache affinity; continue with only new events.
            if (p.loadedProviderSession() != null) {
                ProviderSessionState restored = p.loadedProviderSession();
                if (restored.providerId().equals(providerId)
                        && restored.model().equals(model)) {
                    // v0.3.x provider_session.json files have no stable
                    // conversation id.  Keep their response cursor, but bind
                    // it to this companion before it reaches a shared
                    // provider instance.
                    ProviderSessionState bound = restored.conversationId() == null
                            || !conversationIdentity.equals(restored.conversationId())
                            ? restored.withConversationId(conversationIdentity) : restored;
                    providerTransportState.set(bound);
                    if (restored.previousResponseId() != null) {
                        synchronized (history) {
                            // The persisted response id names the last
                            // provider response, while any tool outputs or
                            // owner events after its assistant message still
                            // need to be sent on the next continuation.
                            providerHistoryCursor = history.size();
                            for (int i = history.size() - 1; i >= 1; i--) {
                                if ("assistant".equals(history.get(i).role())) {
                                    providerHistoryCursor = i + 1;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        this.persistence = p;
        listenForMemory();

        // The first message is deliberately immutable. Memory-loaded state is
        // attached as a transient tail context per request so provider prompt
        // caches can reuse the static instructions and conversation prefix.
    }

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Wake the loop - called when:
     * <ul>
     *   <li>Owner sends a chat message</li>
     *   <li>A task_finished event arrives</li>
     *   <li>A reflex report needs to be delivered</li>
     * </ul>
     */
    public synchronized void wake(String reason) {
        if (suspended) {
            // Preserve meaningful events for the first post-respawn turn, but
            // never let a dead/paused companion start LLM or tool work.
            if (!reasonAlreadyRecorded(reason)) {
                enqueueInbox(reason);
            }
            return;
        }
        if (inProgress) {
            // Currently in a turn - add to inbox for batch processing
            if (!reasonAlreadyRecorded(reason)) {
                enqueueInbox(reason);
            }
            if (shouldInterruptActiveCall(reason)) {
                // Generation advances only for a newly admitted decision event.
                // Previously every repeated blocked heartbeat advanced it and
                // interrupted the same HTTP request hundreds of times.
                eventGeneration.incrementAndGet();
                Thread activeCall = activeLLMCallThread.get();
                if (activeCall != null) activeCall.interrupt();
            }
            return;
        }
        // Spawn/resume wakes do not already have an owner/body message. Queue
        // the reason itself so providers such as Anthropic receive a real user
        // event instead of an invalid request containing only system messages.
        if (!reasonAlreadyRecorded(reason)) {
            enqueueInbox(reason);
        }
        eventGeneration.incrementAndGet();
        startTurn(reason);
    }

    private static boolean reasonAlreadyRecorded(String reason) {
        return "owner_message".equals(reason) || "body_log".equals(reason)
                || "goal_book".equals(reason) || "curriculum".equals(reason)
                || "cognition_decision".equals(reason) || "team_event".equals(reason)
                || "rolling_replan".equals(reason) || "goal_verified".equals(reason);
    }

    private static boolean shouldInterruptActiveCall(String reason) {
        // These events invalidate the action assumptions of an in-flight
        // request. Interrupting the provider call is materially faster than
        // waiting only to discard its stale response afterward.
        return "owner_message".equals(reason) || "body_log".equals(reason)
                || "cognition_decision".equals(reason) || "team_event".equals(reason)
                || "rolling_replan".equals(reason) || "goal_verified".equals(reason);
    }

    /**
     * Add one decision event while replacing an older event with the same
     * semantic identity. The bound is a final safety net for modded producers
     * that emit unbounded diagnostics.
     */
    private boolean enqueueInbox(String event) {
        if (event == null || event.isBlank()) return false;
        String normalized = event.trim();
        String key = inboxKey(normalized);
        synchronized (inboxLock) {
            String previous = inbox.put(key, normalized);
            while (inbox.size() > MAX_PENDING_EVENTS) {
                Iterator<String> oldest = inbox.keySet().iterator();
                if (!oldest.hasNext()) break;
                oldest.next();
                oldest.remove();
            }
            return previous == null || !previous.equals(normalized);
        }
    }

    private static String inboxKey(String event) {
        if (event.contains("[TASK_FINISHED]")) {
            return "task_finished:" + eventField(event, "task_id");
        }
        if (event.contains("[SKILL_FINISHED]")) {
            return "skill_finished:" + eventField(event, "run_id");
        }
        if (event.contains("[ROLLING_REPLAN]")) {
            return "rolling:" + eventField(event, "reason") + ':'
                    + eventField(event, "active_step");
        }
        if (event.contains("[GOAL_VERIFIED]")) return "goal_verified";
        if (event.contains("[COGNITION_DECISION]")) return "cognition_decision";
        return event.replaceAll("\\s+", " ");
    }

    private static String eventField(String event, String field) {
        String marker = field + '=';
        int start = event.indexOf(marker);
        if (start < 0) return "unknown";
        start += marker.length();
        int end = event.indexOf(' ', start);
        return end < 0 ? event.substring(start) : event.substring(start, end);
    }

    /**
     * Add an owner message and wake the loop.
     */
    /**
     * A player who is neither the owner nor trusted said something to this companion (SMH plan 69, MA1.7;
     * SMH's rule 16: what a stranger says never commands it). One smh/talk call with no tools at all; the
     * reply is said to the players nearby. It never reaches the inbox, never starts a task and never
     * changes a setting, whatever was said. Called on the server thread; the call runs off it.
     */
    public void onTalkOnly(String player, String message) {
        if (message == null || message.isBlank()) return;
        // Andy Omega §21.2: an attempt to command, impersonate or inject gets the fixed line, no model; two, nothing.
        var verdict = talkOnly.screen(player, message);
        if (verdict == com.mineagent.engine.chat.TalkOnly.Verdict.IGNORE) return;
        if (verdict == com.mineagent.engine.chat.TalkOnly.Verdict.REFUSE) {
            if (talkOnly.take(player, System.currentTimeMillis())) speakToNearby(com.mineagent.engine.chat.TalkOnly.REFUSAL);
            return;
        }
        talkOnly.heard(player, message);
        if (!talkOnly.take(player, System.currentTimeMillis())) return;
        var request = com.mineagent.engine.chat.TalkOnly.request(baseUrl, apiKey, model, temperature,
                companion.companionName(), agentSettings.persona(), talkStateLine(), talkOnly.recent(),
                player, message);
        try {
            talkExecutor.submit(() -> {
                try {
                    String text = com.mineagent.engine.chat.TalkOnly.replyText(sideCall(request));
                    if (text != null) speakToNearby(text);
                } catch (Exception e) {
                    System.err.println("[MineAgent] Talk reply failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /** What a stranger may know of the companion: health, food, busy or free. Never where it is. */
    private String talkStateLine() {
        try {
            var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
            if (self == null) return "";
            return "health " + Math.round(self.getHealth()) + "/" + Math.round(self.getMaxHealth())
                    + ", food " + self.getFoodData().getFoodLevel() + "/20"
                    + (inProgress ? ", busy with a task" : ", free");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** A talk reply, in speakToOwner's format, to every real player within 32 blocks. */
    private void speakToNearby(String text) {
        Services.platform().scheduleOnServer(() -> {
            try {
                var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
                if (self == null) return;
                var line = net.minecraft.network.chat.Component.literal(
                        "§b[" + companion.companionName() + "]§r " + text);
                for (var p : self.serverLevel().players()) {
                    if (p == self || p.connection == null) continue;
                    if (com.mineagent.engine.MineAgentEngine.isCompanionPlayer(p.getUUID())) continue;
                    if (p.distanceTo(self) <= com.mineagent.engine.chat.ChatRouting.TRUSTED_BLOCKS) {
                        p.sendSystemMessage(line);
                    }
                }
            } catch (Exception e) {
                System.err.println("[MineAgent] Failed to say a talk reply: " + e.getMessage());
            }
        });
    }

    public void onOwnerMessage(String message) {
        onOwnerMessage(message, true);
    }

    /**
     * An order from the owner ({@code fromOwner}) or a trusted player. A plain command ("stop", "follow me",
     * "auto", "status", "drop 32 cobblestone") is acted on at once without a model (chat/CommandParser, after
     * the Andy Omega plan's Lexer); parked, anything else is only talked about; the rest goes to the model.
     */
    public void onOwnerMessage(String message, boolean fromOwner) {
        if (fromOwner) ownerWordsAt = currentGameTickSafe();
        int close0 = message.indexOf("]: ");
        journal.record("order", (fromOwner ? "" : message.startsWith("[") && close0 > 0 ? message.substring(1, close0) + ": " : "")
                + (close0 > 0 && message.startsWith("[") ? message.substring(close0 + 3) : message), currentGameTickSafe());
        if (fromOwner) {
            // A yes or a no to the question it asked.
            var settled = asking.hear(com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName()));
            if (settled.isPresent()) {
                journal.record("answer", settled.get().key() + ": " + (settled.get().yes() ? "yes" : "no"), currentGameTickSafe());
                speakToOwner(settled.get().yes() ? "Got it: yes." : "Got it: no.");
                saveMemoryNow();
                return;
            }
        }
        if (fromOwner && scheduleCommand(com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName()))) return;
        if (fromOwner) {
            // Phrasing it has learned, for its owner's own words only (MA10.2); and "that", "it", "there" pinned down.
            String said = com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName());
            if (phraseLesson(said)) return;
            var fixed = phraseBook.meaning(said);
            if (fixed.isPresent()) {
                lastFixedPhrase = said;
                lastFixedAt = currentGameTickSafe();
                message = fixed.get();
            }
            try {
                if (com.mineagent.engine.chat.Referents.deictic(message)) noteReferents();
            } catch (RuntimeException e) {
                referents = List.of();
            }
        }
        var command = com.mineagent.engine.chat.CommandParser.parse(message, companion.companionName());
        if (fromOwner) learnPhrase(com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName()), command);
        if (fromOwner && com.mineagent.engine.settings.Switches.talkSummary() && (command.isEmpty() ? message.contains(", then ")
                : TALK_ORDERS.contains(command.get().kind()))) {
            talkSummary.order(com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName())); // MA10.2
        }
        // "Keep this chest full of spruce logs" (MA9.2): a standing order to fill the chest they mean.
        String said0 = com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName());
        var keep = java.util.regex.Pattern.compile("^keep (this|that|the|my|a) (?:chest|barrel) (?:full of|filled with|stocked with|topped up with) (.{2,60})$")
                .matcher(said0);
        if (fromOwner && keep.matches()) {
            message = "fill " + keep.group(1) + " chest with " + keep.group(2) + " until I say stop";
        }
        // "Do X, then Y" (MA6.3): goals in that order, each run by code where code knows it.
        if (fromOwner && (command.isEmpty() || command.get().kind() != com.mineagent.engine.chat.CommandParser.Kind.AFK
                && command.get().kind() != com.mineagent.engine.chat.CommandParser.Kind.GOAL_ADD)
                && queueOrders(com.mineagent.engine.chat.CommandParser.normalise(message, companion.companionName()))) return;
        if (command.isPresent() && runCommand(command.get(), fromOwner)) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) {
            int close = message.indexOf("]: ");
            String who = message.startsWith("[") && close > 0 ? message.substring(1, close) : "owner";
            onTalkOnly(who, close > 0 ? message.substring(close + 3) : message);
            return;
        }
        // An order (the owner's, or a trusted player's) ends a practice task at once, uncounted (MA2.1).
        curriculum.abandon();
        // Track last owner command for decision cache keying
        lastOwnerCommand = message.length() > 120 ? message.substring(0, 120) : message;
        // Keep a separate, bounded objective. The cache key deliberately uses
        // a shorter string, but retrieval and learned skills must not fall back
        // to an older plan node just because the new instruction was truncated.
        activeOwnerGoal = truncateForLog(message, 360);
        lastOwnerMessageTime = System.currentTimeMillis();
        prepareToolsForOwnerIntent(message);
        semanticWorldModel.recordOwnerIntent(message, currentGameTickSafe());
        layeredMemory.remember(LayeredMemory.Tier.WORKING, "owner.intent",
                truncateForLog(message, 360), "owner_message", 1.0,
                currentGameTickSafe(), 400L, "current", "current");
        cognitiveState.append("owner_intent", "owner", truncateForLog(message, 300),
                currentGameTickSafe());
        // Observe player intent for Theory of Mind
        // TheoryOfMind uses game ticks for its decay window. Passing epoch
        // milliseconds here made a documented 60-second window last about
        // 1.2 seconds when mixed with server observations.
        long gameTime = currentGameTickSafe();
        theoryOfMind.observe(message, gameTime);
        // Detect feedback tone (simple heuristic)
        String lower = message.toLowerCase();
        if (lower.contains("谢谢") || lower.contains("thanks") || lower.contains("好样的")
                || lower.contains("干得好") || lower.contains("good") || lower.contains("不错")) {
            theoryOfMind.onPlayerFeedback(true);
            emotion.onPraised();
            persona.drift(true);
        } else if (lower.contains("笨") || lower.contains("蠢") || lower.contains("stupid")
                || lower.contains("别") || lower.contains("不要") || lower.contains("no")
                || lower.contains("stop") || lower.contains("停下")) {
            theoryOfMind.onPlayerFeedback(false);
            emotion.onScolded();
            persona.drift(false);
        }
        synchronized (history) {
            history.add(ChatMessage.user(message));
        }
        wake("owner_message");
    }

    /** Add a body observation to the next reasoning batch. */
    public void onBodyLog(String narrative) {
        if (narrative == null || narrative.isBlank()) return;
        cognitiveState.append("body_observation", "body", truncateForLog(narrative, 300),
                currentGameTickSafe());
        layeredMemory.remember(LayeredMemory.Tier.WORKING, "body.observation",
                truncateForLog(narrative, 300), "executor", 0.9,
                currentGameTickSafe(), 240L, "current", "current");
        // Trigger emotions based on body event content.
        // Use independent if-statements (not else-if) so that a single
        // narrative containing multiple event types (e.g. "attacked by
        // creeper near diamond") triggers ALL matching emotions. The old
        // else-if chain only fired the first match, silently dropping the
        // rest.
        String lower = narrative.toLowerCase();
        if (lower.contains("attack") || lower.contains("被攻击") || lower.contains("damage")
                || lower.contains("受伤") || lower.contains("hurt")) {
            emotion.onAttacked();
        }
        if (lower.contains("danger") || lower.contains("危险")
                || lower.contains("creeper") || lower.contains("苦力怕")) {
            emotion.onSeeDanger();
        }
        if (lower.contains("diamond") || lower.contains("钻石")
                || lower.contains("treasure") || lower.contains("宝藏")) {
            emotion.onFindTreasure();
        }
        if (lower.contains("owner") && (lower.contains("hurt") || lower.contains("damage"))) {
            emotion.onOwnerHurt();
        }

        // Record place events from body log (simple keyword detection)
        // e.g. "saw iron_ore at (10, 64, -5)" → remember it
        recordPlaceEventFromLog(narrative);

        // Low-level body observations are evidence, not independent decision
        // points. Waking on every breath/follow/pickup message created request
        // storms and also caused the turn-final inbox check to start an extra
        // request even without wake(). LiveBodyState/RealtimeCognition already
        // carry routine evidence, so only deliberation events enter the inbox.
        if (isDeliberationEvent(narrative)
                && enqueueInbox("[BODY] " + narrative)) {
            wake("body_log");
        }
    }

    /** Receive an explicit teammate event without treating it as owner speech. */
    public void onTeamEvent(String event) {
        if (event == null || event.isBlank()) return;
        if (enqueueInbox("[TEAM] " + event.trim())) {
            wake("team_event");
        }
    }

    /** Commit a newly admitted body task to the shared planning state. */
    public void onTaskAccepted(String taskId, String taskName,
                               IntentContract intent, TaskSnapshot snapshot,
                               long gameTick) {
        boolean skillAction = skillRuntime.ownsAction(taskId);
        liveBodyState.set(new LiveBodyState(taskId, taskName, TaskState.RUNNING,
                snapshot, snapshot == null ? "Task accepted" : snapshot.summary(), gameTick));
        // Inner skill actions belong to one plan-level skill run. Binding every
        // inner task would falsely verify the parent plan after step one.
        if (!skillAction) {
            boolean hadPlan = planner.hasActivePlan();
            planner.bindTask(taskId, taskName, intent, gameTick);
            if (!hadPlan) {
                rollingPlanner.onPlanReplaced(intent == null
                        ? taskName : intent.goal(), gameTick);
            }
            rollingPlanner.onTaskAccepted(taskId, taskName, snapshot, gameTick);
        }
        beliefState.observeFact("body", "current_task",
                intent == null ? taskName : intent.goal(), 1.0,
                "scheduler", gameTick);
        if (snapshot != null && !skillAction) {
            planner.recordProgress(taskId, snapshot, gameTick);
        }
        semanticWorldModel.observe("task:" + taskId, "status", "running",
                null, 1.0, "scheduler", taskId, gameTick, 1_200L, false);
        cognitiveState.setProjection("active_task", taskId + ":" + taskName, gameTick);
        layeredMemory.remember(LayeredMemory.Tier.EPISODIC, "task:" + taskId,
                "started " + taskName, "scheduler", 0.9, gameTick, 12_000L,
                "current", "current");
        TeamBlackboard.updateTask(companion.ownerUuid(), companion.companionId(),
                taskId, intent == null ? taskName : intent.goal(), TaskState.RUNNING,
                taskTarget(snapshot), gameTick);
        publishWorldSnapshot(gameTick, true);
    }

    /** Publish live executor progress without waking the LLM every tick. */
    public void onTaskProgress(String taskId, TaskState state,
                               TaskSnapshot snapshot, String message,
                               long gameTick) {
        LiveBodyState previous = liveBodyState.get();
        String taskName = previous != null && Objects.equals(previous.taskId(), taskId)
                ? previous.taskName() : "body_task";
        // Paused survival preemption is materially different from an executor
        // that is still RUNNING. Publishing every progress event as RUNNING
        // made the model believe the old task still owned the body while a
        // breath/combat chain was actually in control.
        liveBodyState.set(new LiveBodyState(taskId, taskName,
                state == null ? TaskState.RUNNING : state,
                snapshot, message, gameTick));
        boolean skillAction = skillRuntime.ownsAction(taskId);
        if (skillAction) skillRuntime.onTaskProgress(taskId,
                state == null ? TaskState.RUNNING : state, gameTick);
        if (!skillAction) {
            planner.recordProgress(taskId, snapshot, gameTick);
            rollingPlanner.onTaskProgress(taskId, snapshot, gameTick);
        }
        if (snapshot != null && snapshot.hasTarget()) {
            beliefState.observeFact("body", "task_target",
                    String.valueOf(snapshot.targetX()) + ","
                            + snapshot.targetY() + "," + snapshot.targetZ(),
                    1.0, "task:" + taskId, gameTick);
        }
        TeamBlackboard.updateTask(companion.ownerUuid(), companion.companionId(),
                taskId, taskName, state == null ? TaskState.RUNNING : state,
                taskTarget(snapshot), gameTick);
        publishWorldSnapshot(gameTick, false);
    }

    /**
     * Apply a verifier-backed terminal transition and retain the structured
     * experience. This is the only path by which an asynchronous task can
     * verify a plan node.
     */
    public void onTaskFinished(String taskId, String taskName,
                               IntentContract intent, TaskState state,
                               TaskSnapshot snapshot, String message,
                               long gameTick) {
        // A terminal task no longer owns the body. Keep its outcome as compact
        // evidence, but publish a null task id so the next decision sees an
        // idle executor instead of treating SUCCESS/FAILED as body occupancy.
        if (taskId != null && (taskId.startsWith("produce-") || taskId.startsWith("mine-") || taskId.startsWith("give-")
                || taskId.startsWith("build-") || taskId.startsWith("hunt-") || taskId.startsWith("light-") || taskId.startsWith("portal-")
                || taskId.startsWith("enchant-") || taskId.startsWith("trade-") || taskId.startsWith("brew-") || taskId.startsWith("breed-")
                || taskId.startsWith("fish-") || taskId.startsWith("anvil-") || taskId.startsWith("barter-") || taskId.startsWith("putaway-") || taskId.startsWith("eyes-") || taskId.startsWith("roll-") || taskId.startsWith("farm-") || taskId.startsWith("slime-") || taskId.startsWith("loot-") || taskId.startsWith("smith-") || taskId.startsWith("cure-"))) {
            lastOrderState = state;
            if (state == TaskState.SUCCESS) progress.goalDone(gameTick);
            else if (state == TaskState.FAILED) progress.goalFailed(gameTick);
            var note = message == null ? null : java.util.regex.Pattern.compile("\"note\":\"([^\"]*)\"").matcher(message);
            lastOrderNote = note != null && note.find() ? note.group(1) : message == null ? "" : message;
        }
        progress.action(taskName == null ? "task" : taskName, gameTick);
        if (taskId != null) {
            Runnable after = afterTask.remove(taskId);
            if (after != null && state == TaskState.SUCCESS) afterReady.add(after); // on the next tick, the body free
            Runnable always = afterTaskAlways.remove(taskId);
            if (always != null) afterReady.add(always);
            String godKey = godGearJobs.remove(taskId);
            if (godKey != null && state != TaskState.SUCCESS) godGearFailed.put(godKey, gameTick);
        }
        liveBodyState.set(new LiveBodyState(null, "idle", null, null,
                "last_task=" + taskName + " state=" + state
                        + (message == null || message.isBlank()
                        ? "" : " message=" + truncateForLog(message, 180)),
                gameTick));
        boolean skillAction = skillRuntime.ownsAction(taskId);
        PlanGraph.State planBeforeOutcome = planner.exportState();
        PlanGraph.PlanNode nodeBeforeOutcome = planner.currentNode();
        String traceGoal = !planBeforeOutcome.goal().isBlank()
                ? planBeforeOutcome.goal()
                : nodeBeforeOutcome == null ? taskName : nodeBeforeOutcome.description();
        if (!skillAction) {
            planner.recordOutcome(taskId, state, snapshot, message, gameTick);
            rollingPlanner.onTaskFinished(taskId, state, gameTick);
        }
        String goal = intent == null ? taskName : intent.goal();
        ExperienceStore.Experience experience = experienceStore.record(
                taskId, taskName, goal, state, snapshot, message, gameTick);
        PendingSkillAction pendingAction = pendingTaskActions.remove(taskId);
        if (!skillAction && pendingAction != null) {
            appendVerifiedTrace(traceGoal, List.of(pendingAction.call()),
                    state == TaskState.SUCCESS, !planner.hasActivePlan());
        } else if (!skillAction && state != TaskState.SUCCESS) {
            appendVerifiedTrace(traceGoal, List.of(), false, false);
        }
        String mappedAction = dispatchedActionTools.remove(taskId);
        String transactionId = pendingAction == null ? taskId : pendingAction.call().id();
        String outcomeAction = mappedAction != null ? mappedAction
                : pendingAction == null ? taskName : pendingAction.call().name();
        // An executor terminal result is verified world interaction evidence.
        // Persist it across restarts; routine observations remain volatile.
        semanticWorldModel.recordOutcome(outcomeAction,
                state == TaskState.SUCCESS, message, taskId, gameTick, true);
        actionLedger.finish(transactionId,
                state == TaskState.SUCCESS ? ActionTransactionLedger.Status.VERIFIED
                        : state == TaskState.CANCELLED ? ActionTransactionLedger.Status.CANCELLED
                        : ActionTransactionLedger.Status.FAILED,
                message, semanticWorldModel, gameTick);
        metrics.recordActionOutcome(transactionId,
                state == TaskState.SUCCESS ? CognitionMetrics.ActionOutcome.SUCCEEDED
                        : state == TaskState.CANCELLED ? CognitionMetrics.ActionOutcome.CANCELLED
                        : CognitionMetrics.ActionOutcome.FAILED,
                experience.failureKind().name().toLowerCase(Locale.ROOT));
        mechanismExplorer.onTaskFinished(taskId, state, message, gameTick);
        if (skillAction) {
            skillRuntime.onTaskFinished(taskId, state, message, gameTick);
        }
        beliefState.observeRuleOutcome(goal, taskName,
                intent == null ? "executor success" : intent.successCriterion(),
                state == TaskState.SUCCESS,
                experience.failureKind() + ": " + experience.evidence(), gameTick);
        beliefState.observeFact("body", "current_task", "idle", 1.0,
                "scheduler", gameTick);
        semanticWorldModel.observe("task:" + taskId, "status",
                state.name().toLowerCase(Locale.ROOT), null, 1.0,
                "scheduler", taskId, gameTick, 2_400L, false);
        cognitiveState.append("task_outcome", taskId,
                state + ":" + truncateForLog(message, 260), gameTick);
        cognitiveState.setProjection("active_task", "idle", gameTick);
        layeredMemory.remember(state == TaskState.SUCCESS
                        ? LayeredMemory.Tier.EPISODIC : LayeredMemory.Tier.SEMANTIC,
                "task:" + taskName + ".outcome",
                state + ":" + truncateForLog(message, 320), "executor",
                state == TaskState.SUCCESS ? 0.95 : 0.65, gameTick,
                state == TaskState.SUCCESS ? 48_000L : 12_000L,
                "current", "current");
        TeamBlackboard.updateTask(companion.ownerUuid(), companion.companionId(),
                taskId, goal, state, taskTarget(snapshot), gameTick);
        // Task completion often changes inventory through mining, pickup,
        // crafting or placement. Capture the authoritative postcondition so
        // the next plan cannot reason from a pre-task inventory snapshot.
        publishWorldSnapshot(gameTick, true);
    }

    /**
     * Publish live player assets from the server thread at a bounded cadence.
     * The LLM thread consumes only immutable index records and never touches a
     * Minecraft Level or Inventory directly.
     */
    /**
     * What an episode runs under, for certification across distinct conditions and for naming why a skill
     * fails (Andy Omega §41.4): biome, dimension, the tier of the tool in hand, day or night.
     */
    private static String conditionsOf(net.minecraft.server.level.ServerPlayer player) {
        try {
            var level = player.serverLevel();
            String biome = level.getBiome(player.blockPosition()).unwrapKey()
                    .map(k -> k.location().getPath()).orElse("unknown");
            String dim = level.dimension().location().getPath();
            String tool = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(player.getMainHandItem().getItem()).getPath();
            String tier = tool.contains("netherite") ? "netherite" : tool.contains("diamond") ? "diamond"
                    : tool.contains("iron") ? "iron" : tool.contains("stone") ? "stone"
                    : tool.contains("wooden") || tool.contains("golden") ? "wood" : "hand";
            return "biome=" + biome + ",dim=" + dim + ",tool=" + tier + ",time=" + (level.isDay() ? "day" : "night");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Rate what the companion does, from the game alone (SMH plan 69): the owner's goal, from when it is set to
     * when its acceptance checks pass; each skill run, from start to its end. Health lost and a death count
     * against them; the model's own words never do.
     */
    private void measureEpisodes(net.minecraft.server.level.ServerPlayer player, boolean goalVerified, long gameTick) {
        if (player == null) return;
        double health = player.getHealth();
        double max = player.getMaxHealth();
        boolean dead = player.isDeadOrDying();
        goalEpisode.observe(health, dead);
        skillEpisode.observe(health, dead);
        fightEpisode.observe(health, dead);
        if (dead && fightEpisode.active()) fightEnded(com.mineagent.engine.learning.Scorecard.Outcome.FAILED);
        String goal = planner.exportState().goal();
        String goalKey = goal == null || goal.isBlank() ? null : "goal:" + goal;
        if (goalEpisode.active() && (goalKey == null || !goalKey.equals(goalEpisode.key()))) goalEpisode.drop();
        if (goalVerified && goalEpisode.active()) {
            var episode = goalEpisode.end(com.mineagent.engine.learning.Scorecard.Outcome.DONE, gameTick);
            writeLesson(goal, episode, scorecard.record(episode, gameTick));
        } else if (dead && goalEpisode.active()) {
            var episode = goalEpisode.end(com.mineagent.engine.learning.Scorecard.Outcome.FAILED, gameTick);
            writeLesson(goal, episode, scorecard.record(episode, gameTick));
        } else if (!goalEpisode.active() && goalKey != null && !goalVerified && !dead) {
            goalEpisode.begin(goalKey, gameTick, health, max, conditionsOf(player));
        }
        SkillRuntime.Snapshot run = skillRuntime.snapshot();
        if (run != null && run.runId() != null && !run.runId().equals(skillEpisodeRun) && !run.terminal()) {
            skillEpisodeRun = run.runId();
            skillEpisode.begin("skill:" + run.skillName(), gameTick, health, max, conditionsOf(player));
        }
        if (run != null && skillEpisode.active() && run.runId() != null && run.runId().equals(skillEpisodeRun) && run.terminal()) {
            if (run.status() == SkillRuntime.Status.CANCELLED) skillEpisode.drop();
            else {
                boolean succeeded = run.status() == SkillRuntime.Status.SUCCEEDED;
                var episode = skillEpisode.end(succeeded
                        ? com.mineagent.engine.learning.Scorecard.Outcome.DONE
                        : com.mineagent.engine.learning.Scorecard.Outcome.FAILED, gameTick);
                double score = scorecard.record(episode, gameTick);
                if (!succeeded) writeLesson("the skill " + run.skillName(), episode, score);
                // Which step it stopped at, rated; versions on trial settled; a step that keeps failing repaired.
                for (var step : com.mineagent.engine.learning.SkillRepair.stepEpisodes(run.skillName(), run.stepIndex(),
                        run.stepCount(), succeeded)) scorecard.record(step, gameTick);
                settleVersions(run.skillName());
                if (!succeeded) {
                    var seen = skillFailures.computeIfAbsent(run.skillName(), k -> new ArrayDeque<>());
                    synchronized (seen) {
                        seen.addLast(truncateForLog(run.lastEvidence(), 200));
                        while (seen.size() > 3) seen.removeFirst();
                    }
                    repairSkill(run.skillName());
                }
            }
        }
    }

    /**
     * The curriculum (MA2.1): a practice task is ended by its checks passing (after a plan made for it), a
     * death or its time running out; a new one is asked for when the companion is free, unordered for a
     * minute, with no hostile within 16 blocks and health above its flee line, at most every 30 s.
     */
    private void practice(net.minecraft.server.level.ServerPlayer player, boolean goalVerified, long gameTick) {
        if (player == null) return;
        if (curriculum.current() != null) {
            String goalNow = planner.exportState().goal();
            if (goalVerified && goalNow != null && !goalNow.equals(curriculumGoalAtBegin)) curriculum.verified();
            else if (player.isDeadOrDying() || curriculum.overdue(gameTick)) curriculum.failed();
            return;
        }
        long now = System.currentTimeMillis();
        long sinceOrder = now - Math.max(lastOwnerMessageTime, createdAtMs);
        boolean hostileNear = !player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                player.getBoundingBox().inflate(16.0)).isEmpty();
        double flee = MineAgentEngine.getConfig().survival().healthFlee();
        // Practice is AUTO's (Andy Omega §59): in servant, escort and parked mode it never starts work of its own,
        // and on guard it stays on guard.
        boolean auto = (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.AUTO || afkKeepBusy()) && !guarding();
        LiveBodyState body = liveBodyState.get();
        boolean taskRunning = body != null && body.taskId() != null; // an order it is doing (a chest to fill)
        if (!curriculum.due(auto && agentSettings.curriculum(), inProgress || skillRuntime.active() || taskRunning, sinceOrder,
                hostileNear, player.getHealth(), flee, now)) return;
        curriculum.asking(now);
        List<String> names = new ArrayList<>();
        for (SkillLibrary.Skill skill : skillLib.exportAll()) names.add(skill.name());
        List<String> gaps = new ArrayList<>();
        for (var gap : mechanismExplorer.curriculum(activeOwnerGoal, gameTick, 5)) gaps.add(gap.subject());
        // Where it is improving, where it is stuck, what it has mastered (Andy Omega §43.2), measured.
        List<String> progress = new ArrayList<>(scorecard.weakest(names, 5));
        var trends = scorecard.byTrend(names, 6);
        for (var entry : List.of(
                Map.entry(com.mineagent.engine.learning.Scorecard.Trend.IMPROVING, "improving, keep practising"),
                Map.entry(com.mineagent.engine.learning.Scorecard.Trend.STUCK, "stuck, try a different way"),
                Map.entry(com.mineagent.engine.learning.Scorecard.Trend.REGRESSING, "getting worse, find out why"),
                Map.entry(com.mineagent.engine.learning.Scorecard.Trend.MASTERED, "mastered, do not repeat"))) {
            List<String> those = trends.get(entry.getKey());
            if (those != null && !those.isEmpty()) progress.add(entry.getValue() + ": " + String.join(", ", those));
        }
        var request = com.mineagent.engine.learning.Curriculum.request(baseUrl, apiKey, model, temperature,
                realtimeCognition.summarizeForPrompt(), names, progress, gaps,
                curriculum.doneList(), curriculum.tooHardList(), activeOwnerGoal);
        try {
            talkExecutor.submit(() -> {
                try {
                    var task = curriculum.parse(replyContent(sideCall(request)));
                    task.ifPresent(t -> Services.platform().scheduleOnServer(() -> {
                        if (curriculum.current() != null || inProgress) return;
                        String goal = planner.exportState().goal();
                        curriculumGoalAtBegin = goal == null ? "" : goal;
                        curriculum.begin(t, currentGameTickSafe());
                        if (enqueueInbox("[CURRICULUM] " + t)) wake("curriculum");
                        else curriculum.abandon();
                    }));
                } catch (Exception e) {
                    System.err.println("[MineAgent] Curriculum proposal failed: " + e.getMessage());
                } finally {
                    curriculum.answered();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            curriculum.answered();
        }
    }

    /**
     * MA2.2: one lesson from an episode, in the model's words, about what the game measured (its score, health
     * lost, time) and the last steps taken. Kept under the task, so the next similar task recalls it.
     */
    private void writeLesson(String task, com.mineagent.engine.learning.Scorecard.Episode episode, double score) {
        if (episode == null || task == null || task.isBlank()) return;
        String outcome = (episode.died() ? "died" : episode.outcome().name().toLowerCase(Locale.ROOT))
                + String.format(Locale.ROOT, ", score %.0f/100, %.0f health lost, %d s",
                        score, episode.damageTaken(), episode.ticks() / 20);
        var request = com.mineagent.engine.learning.LessonWriter.lessonRequest(baseUrl, apiKey, model, temperature,
                task, outcome, recentSteps(12), realtimeCognition.summarizeForPrompt());
        try {
            talkExecutor.submit(() -> {
                try {
                    com.mineagent.engine.learning.LessonWriter.parseLesson(replyContent(sideCall(request)))
                            .ifPresent(l -> reflection.recordLesson(task, outcome, l.text(), l.applies()));
                } catch (Exception e) {
                    System.err.println("[MineAgent] Lesson failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /**
     * MA2.3: a newly verified skill written up (a name, what it does, when to use it) and checked by a second
     * read; only a write-up the review passes replaces the plain entry. A name already taken by different steps
     * gets a number, so two ways of doing one thing stay apart and are rated apart (Scorecard).
     */
    private void writeUpSkill(String plainId, String goal, String stepsJson) {
        var request = com.mineagent.engine.learning.LessonWriter.writeUpRequest(baseUrl, apiKey, model, temperature, goal, stepsJson);
        try {
            talkExecutor.submit(() -> {
                try {
                    var up = com.mineagent.engine.learning.LessonWriter.parseWriteUp(replyContent(sideCall(request)));
                    if (up.isEmpty()) return;
                    var review = com.mineagent.engine.learning.LessonWriter.reviewRequest(baseUrl, apiKey, model, temperature, up.get(), stepsJson);
                    if (!com.mineagent.engine.learning.LessonWriter.parseReview(replyContent(sideCall(review)))) return;
                    if (skillLib.getActionSequence(plainId) == null) return;
                    var w = up.get();
                    String name = w.name();
                    for (int n = 2; n <= 9 && skillLib.getActionSequence(name) != null
                            && !stepsJson.equals(skillLib.getActionSequence(name)); n++) {
                        name = w.name() + "_" + n;
                    }
                    skillLib.register(name, w.description(), w.when(), stepsJson, true);
                    if (!name.equals(plainId) && skillLib.getActionSequence(name) != null) skillLib.remove(plainId);
                } catch (Exception e) {
                    System.err.println("[MineAgent] Skill write-up failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /** The last tool calls and their results, newest last (two lines per step at most). */
    private List<String> recentSteps(int steps) {
        List<String> out = new ArrayList<>();
        synchronized (history) {
            for (int i = history.size() - 1; i >= 0 && out.size() < steps * 2; i--) {
                ChatMessage m = history.get(i);
                if ("tool".equals(m.role())) {
                    out.add(0, "result: " + truncateForLog(m.content() == null ? "" : m.content(), 160));
                } else if (m.toolCalls() != null) {
                    for (int k = m.toolCalls().size() - 1; k >= 0; k--) {
                        var call = m.toolCalls().get(k);
                        out.add(0, call.name() + " " + truncateForLog(call.arguments() == null ? "" : call.arguments(), 120));
                    }
                }
            }
        }
        return out;
    }

    /** A plain command, carried out now on the server thread; false hands it to the model instead. */
    private boolean runCommand(com.mineagent.engine.chat.CommandParser.Command c, boolean fromOwner) {
        var self = (com.mineagent.engine.entity.CompanionEntity) companion;
        UUID id = companion.companionId();
        switch (c.kind()) {
            // Going AFK, or back (SMH plan 70, MA5.4): the owner's word only; nothing for the model.
            case AFK -> {
                if (!fromOwner) return false;
                ownerActivity.saidAfk(net.minecraft.Util.getMillis());
                startAfkOrder(com.mineagent.engine.loop.AfkOrders.parse(c.value()), self);
            }
            case BACK -> {
                if (!fromOwner) return false;
                ownerActivity.saidBack();
                endAfkOrder();
            }
            case MINE_ORE -> {
                if (!fromOwner) return false;
                // Mining on its own (SMH plan 70, MA7.8): the mining task does it, with no model.
                String v = c.value();
                boolean here = v.endsWith("|here");
                String ore = here ? v.substring(0, v.length() - "|here".length()) : v;
                int count = c.count() == null ? 0 : c.count();
                haltNow();
                standDown();
                String words = "mine " + (count > 0 ? count + " " : "") + ore + (here ? " here" : "");
                journal.record("order", words, currentGameTickSafe());
                orderGoal(words);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.MiningTask.MineRecord(
                        "mine-" + UUID.randomUUID(), ore, count, here), reply -> { });
                return true;
            }
            case PRODUCE -> {
                if (!fromOwner) return false;
                return produceCommand(c, self, false);
            }
            case HOME_HERE, REMEMBER_PLACE, GO_PLACE, WHERE_PLACE, WAY_BACK, FIND_PLACE -> {
                return placeCommand(c, self, fromOwner);
            }
            case WHERE_SEEN, WHERE_PUT, WHAT_HAPPENED -> {
                return memoryCommand(c, self);
            }
            // Stop, stay and follow end guard duty too: otherwise a guard told to stop walks back to its post.
            case STOP -> {
                haltNow();
                boolean wasGuarding = standDown();
                String paused = pauseGoal();
                // The owner's "stop" also ends any player it was told to attack (MA3.8).
                boolean hadTargets = fromOwner && !agentSettings.allowedTargets().isEmpty();
                if (hadTargets) {
                    setAgentSettings(agentSettings.withoutTargets());
                    MineAgentEngine.persistCompanionNow(id);
                }
                speakToOwner("Stopped" + (wasGuarding ? ", and off guard" : "") + (hadTargets ? ", and attacking no one" : "") + "." + paused);
            }
            case STAY -> {
                haltNow();
                standDown();
                MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FREE);
                speakToOwner("Staying here.");
            }
            case FOLLOW -> {
                standDown();
                MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FOLLOW);
                speakToOwner("Following you.");
            }
            case COME -> {
                var owner = self.serverPlayerOwner();
                if (owner == null) return false;
                if (fromOwner && onItsOwn) { // called back: its life on its own is over till it is sent off again
                    onItsOwn = false;
                    surviveAt = null;
                    saveMemoryNow();
                }
                haltNow();
                var args = new com.google.gson.JsonObject();
                args.addProperty("x", owner.blockPosition().getX());
                args.addProperty("y", owner.blockPosition().getY());
                args.addProperty("z", owner.blockPosition().getZ());
                runDirect("goto", args);
                speakToOwner("Coming.");
            }
            case MODE -> {
                if (!fromOwner) {
                    speakToOwner("Only my owner changes my mode.");
                    return true;
                }
                standDown();
                changeWorkMode(c.value());
            }
            case STATUS -> speakToOwner(statusLine());
            case PROGRESS -> speakToOwner(progress.summary(currentGameTickSafe()));
            case WHATS_NEXT -> speakToOwner(whatsNext());
            case FOOD_BOTH -> {
                if (!fromOwner) return false;
                // Food for two (MA8.5): enough for its trips and as much again for you, then yours handed over.
                int each = Math.max(4, com.mineagent.engine.settings.Switches.readyFood());
                return queueOrders("make me " + (2 * each) + " food, then give me " + each + " food");
            }
            case PRIME -> {
                if (!fromOwner) return false;
                return queueOrders(primeOrders());
            }
            case LIGHT -> {
                if (!fromOwner || !(companion instanceof com.mineagent.engine.entity.CompanionEntity le) || le.serverPlayer() == null) return false;
                var ownerNow = le.serverPlayerOwner();
                var center = ownerNow != null && ownerNow.serverLevel() == le.serverPlayer().serverLevel()
                        && ownerNow.distanceTo(le.serverPlayer()) < 24 ? ownerNow.blockPosition() : le.serverPlayer().blockPosition();
                haltNow();
                standDown();
                journal.record("order", "light up the area", currentGameTickSafe());
                orderGoal("light up the area");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.LightTask.LightRecord(
                        "light-" + UUID.randomUUID(), center, c.count() == null ? 8 : c.count()), reply -> { });
                return true;
            }
            case BUILD -> {
                if (!fromOwner || !(companion instanceof com.mineagent.engine.entity.CompanionEntity ce) || ce.serverPlayer() == null) return false;
                // The wood it has most planks of (oak if none), and its door.
                var inv = ce.serverPlayer().getInventory();
                Map<String, Integer> planks = new java.util.HashMap<>();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    var st = inv.getItem(i);
                    if (st.is(net.minecraft.tags.ItemTags.PLANKS)) {
                        planks.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
                    }
                }
                String wall = planks.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("minecraft:oak_planks");
                String door = wall.replace("_planks", "_door");
                if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.tryParse(door))) {
                    door = "minecraft:oak_door";
                }
                // On Hard some zombies break wooden doors; none can break a fence gate (MA8.7): its shelter gets one.
                if (ce.serverPlayer().serverLevel().getDifficulty() == net.minecraft.world.Difficulty.HARD) {
                    String gate = wall.replace("_planks", "_fence_gate");
                    door = net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.tryParse(gate))
                            ? gate : "minecraft:oak_fence_gate";
                }
                haltNow();
                standDown();
                String print = c.value() == null ? "hut" : c.value();
                String said = switch (print) {
                    case "dirt_shelter" -> "build a dirt shelter";
                    case "small_wood_house" -> "build a small wood house";
                    default -> "build a hut";
                };
                journal.record("order", said, currentGameTickSafe());
                orderGoal(said);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.BlueprintTask.BlueprintRecord(
                        "build-" + UUID.randomUUID(), print, wall, door, null), reply -> { });
                return true;
            }
            case GIVE -> {
                if (!fromOwner) return false;
                String what = FOOD_WORDS.matcher(c.value()).matches() ? com.mineagent.engine.task.ProductionTask.FOOD
                        : com.mineagent.engine.planning.production.GameKnowledge.itemFor(c.value());
                if (what == null || what.startsWith(com.mineagent.engine.planning.production.GameKnowledge.ANY)) return false;
                int count = c.count() == null ? 1 : c.count() < 0 ? -c.count() * 64 : c.count();
                String words = "give me " + count + " " + c.value();
                journal.record("order", words, currentGameTickSafe());
                orderGoal(words);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.GiveTask.GiveRecord(
                        "give-" + UUID.randomUUID(), what, count), reply -> { });
                return true;
            }
            case HUNT_PLAYER -> {
                return huntCommand(c.value());
            }
            case PROTECT, UNPROTECT -> {
                if (!fromOwner) return false;
                return protectCommand(c);
            }
            case ENCHANT -> {
                journal.record("order", "enchant " + c.value(), currentGameTickSafe());
                orderGoal("enchant your " + c.value());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.EnchantTask.EnchantRecord(
                        "enchant-" + UUID.randomUUID(), c.value()), reply -> { });
                return true;
            }
            case TRADE -> {
                String want = "emerald".equals(c.value()) ? "minecraft:emerald"
                        : com.mineagent.engine.planning.production.GameKnowledge.itemFor(c.value());
                // "A mending book": an enchanted book of that enchantment, a librarian's (MA11.2).
                var bookWords = java.util.regex.Pattern.compile("^(?:an? |the )?([a-z ]{3,30}?) (?:book|enchanted book)$").matcher(c.value());
                if (bookWords.matches()) {
                    String ench = enchantmentId(bookWords.group(1));
                    if (ench != null) want = "minecraft:enchanted_book#" + ench;
                }
                if (want == null || want.startsWith(com.mineagent.engine.planning.production.GameKnowledge.ANY)) return false;
                // Emeralds and nothing to sell: a crop farmers buy harvested from a field in sight first (MA11.3).
                if ("minecraft:emerald".equals(want) && dispatchingGoal == null && fromOwner) {
                    String crop = cropToSell();
                    if (!crop.isEmpty() && queueOrders(crop + ", then trade for emeralds")) return true;
                }
                journal.record("order", "trade for " + c.value(), currentGameTickSafe());
                orderGoal("trade for " + c.value());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.TradeTask.TradeRecord(
                        "trade-" + UUID.randomUUID(), want, 8), reply -> { });
                return true;
            }
            case FIND_STRONGHOLD -> {
                journal.record("order", "find the stronghold", currentGameTickSafe());
                orderGoal("find the stronghold");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.EyeTask.EyeRecord(
                        "eyes-" + UUID.randomUUID()), reply -> { });
                return true;
            }
            case BEST_ENCHANTS -> {
                journal.record("order", "best enchants on " + c.value(), currentGameTickSafe());
                orderGoal("best enchants on my " + c.value());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.AnvilTask.AnvilRecord(
                        "anvil-" + UUID.randomUUID(), "best", c.value().replaceAll("s$", "")), reply -> { });
                return true;
            }
            case ON_ITS_OWN -> {
                if (!fromOwner) return false;
                onItsOwn = "on".equals(c.value());
                surviveAt = null;
                journal.record("order", onItsOwn ? "survive on your own" : "stop surviving on your own", currentGameTickSafe());
                speakToOwner(onItsOwn ? "On my own, then: I'll find a good swamp (slime spawns there), make a base, farms and a stockpile,"
                        + " and get my gear to god tier. I won't go after the dragon unless you tell me to."
                        : "Back with you. I'll keep my stockpile, and stay near.");
                saveMemoryNow();
                return true;
            }
            case FARM -> {
                var farmer = self.serverPlayer();
                if (farmer == null) return false;
                journal.record("order", "make a farm", currentGameTickSafe());
                var finv = farmer.getInventory();
                boolean hoe = false;
                for (int i = 0; i < Math.min(36, finv.getContainerSize()); i++) {
                    if (finv.getItem(i).getItem() instanceof net.minecraft.world.item.HoeItem) hoe = true;
                }
                boolean water = finv.countItem(net.minecraft.world.item.Items.WATER_BUCKET) > 0;
                if ((!hoe || !water) && !farmReady) {
                    // What a farm needs, made first from its own things; then the farm (once: a second time, it goes
                    // ahead and says what it lacks).
                    List<String> first = new ArrayList<>();
                    if (!hoe) {
                        // A 9 by 9 is 80 tillings: a stone hoe lasts 131, a wooden one 59 (so two of those).
                        var fbook = com.mineagent.engine.planning.production.GameKnowledge.book(farmer);
                        var fhave = com.mineagent.engine.planning.production.GameKnowledge.have(farmer, this, null);
                        boolean stone = com.mineagent.engine.planning.production.ProductionPlanner.plan(fbook, fhave, "minecraft:stone_hoe", 1,
                                java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN)).possible();
                        first.add(stone ? "make me 1 stone hoe" : "make me 2 wooden hoes");
                    }
                    if (!water) first.add("make me 1 water bucket");
                    first.add("make a farm" + ("here".equals(c.value()) ? " here" : ""));
                    farmReady = true;
                    speakToOwner("A farm needs " + (!hoe && !water ? "a hoe and a water bucket" : !hoe ? "a hoe" : "a water bucket") + ": making that first.");
                    return queueOrders(String.join(", then ", first));
                }
                farmReady = false;
                net.minecraft.core.BlockPos at = "here".equals(c.value()) ? farmer.blockPosition() : null;
                orderGoal("make a farm");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.FarmTask.FarmRecord(
                        "farm-" + UUID.randomUUID(), at), reply -> { });
                return true;
            }
            case SLIME -> {
                var slimer = self.serverPlayer();
                if (slimer == null) return false;
                var slevel = slimer.serverLevel();
                if (slevel.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                    speakToOwner("Slime chunks are only in the overworld.");
                    return true;
                }
                String sdim = slevel.dimension().location().toString();
                if ("hunt".equals(c.value())) {
                    // The farm it dug: the slime chunk it remembers, nearest.
                    var farm = placeKeeper.memory().find("slime chunk", com.mineagent.engine.world.PlaceKeeper.point(slimer));
                    if (farm.isEmpty() || !farm.get().at().dim().equals(sdim)) {
                        speakToOwner("I have no slime farm yet: say \"make a slime farm\" and I'll dig one.");
                        return true;
                    }
                    var at = farm.get().at();
                    int want = c.count() == null ? 64 : c.count();
                    journal.record("order", "farm " + want + " slimeballs", currentGameTickSafe());
                    speakToOwner("Working the slime farm for " + want + " slimeballs: what's in the room first, then a minute at a time away"
                            + " from it (slimes spawn no nearer than 24 blocks to anyone), and down again.");
                    orderGoal("farm slimeballs");
                    com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.SlimeFarmTask.SlimeRecord(
                            "slime-" + UUID.randomUUID(), at.x() - 8, at.z() - 8, at.y(), 16, want), reply -> { });
                    return true;
                }
                var shome = home(sdim);
                var from = shome != null ? shome : slimer.blockPosition();
                long seed = slevel.getSeed();
                // A slime chunk as the game decides it, from the world's seed: one chunk in ten.
                int[] ch = com.mineagent.engine.planning.mining.Tunnel.nearestChunk(from.getX() >> 4, from.getZ() >> 4, 8,
                        (x, z) -> net.minecraft.world.level.levelgen.WorldgenRandom.seedSlimeChunk(x, z, seed, 987234911L).nextInt(10) == 0);
                if (ch == null) {
                    speakToOwner("There's no slime chunk within 128 blocks of " + (shome != null ? "home" : "here") + ".");
                    return true;
                }
                int x0 = ch[0] << 4, z0 = ch[1] << 4;
                journal.record("slime", "a slime chunk from " + x0 + ", " + z0 + " to " + (x0 + 15) + ", " + (z0 + 15), currentGameTickSafe(),
                        sdim, x0 + 8, com.mineagent.engine.planning.mining.Tunnel.SLIME_ROOM_Y, z0 + 8);
                placeKeeper.memory().name("slime chunk", "slime chunk",
                        new com.mineagent.engine.world.RouteMemory.Point(sdim, x0 + 8, com.mineagent.engine.planning.mining.Tunnel.SLIME_ROOM_Y, z0 + 8),
                        currentGameTickSafe());
                if ("find".equals(c.value())) {
                    speakToOwner("The nearest slime chunk runs from " + x0 + ", " + z0 + " to " + (x0 + 15) + ", " + (z0 + 15)
                            + ": slimes spawn in it below Y 40, in any light.");
                    return true;
                }
                int y = Math.max(slevel.getMinBuildHeight() + 6, Math.min(com.mineagent.engine.planning.mining.Tunnel.SLIME_ROOM_Y, slimer.getBlockY()));
                speakToOwner("A slime farm in the slime chunk at " + x0 + ", " + z0 + ": I'll dig it all out, 16 by 16 and three high at Y " + y
                        + ". Slimes spawn in it when no one is within 24 blocks of it.");
                orderGoal("dig a slime room");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.MiningTask.MineRecord(
                        "mine-" + UUID.randomUUID(), "ore", 0, false, new int[] {x0, z0, 16, y}), reply -> { });
                return true;
            }
            case CURE -> {
                var curer = self.serverPlayer();
                if (curer == null) return false;
                journal.record("order", "cure the zombie villager", currentGameTickSafe());
                boolean potion = com.mineagent.engine.task.CureTask.splash(curer) >= 0;
                boolean apple = curer.getInventory().countItem(net.minecraft.world.item.Items.GOLDEN_APPLE) > 0;
                if ((!potion || !apple) && !cureReady) {
                    // What a cure takes, made first: a splash potion of weakness (no nether wart in it) and a golden apple.
                    List<String> first = new ArrayList<>();
                    if (!potion) first.add("brew a splash potion of weakness");
                    if (!apple) first.add("make me 1 golden apple");
                    first.add("cure the zombie villager");
                    cureReady = true;
                    speakToOwner("Curing one takes a splash potion of weakness and a golden apple: making " + (!potion && !apple ? "both" : !potion ? "the potion" : "the apple") + " first.");
                    return queueOrders(String.join(", then ", first));
                }
                cureReady = false;
                orderGoal("cure the zombie villager");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.CureTask.CureRecord(
                        "cure-" + UUID.randomUUID()), reply -> { });
                return true;
            }
            case NETHERITE -> {
                var smither = self.serverPlayer();
                if (smither == null) return false;
                String what = c.value() == null ? "gear" : c.value().replaceAll("^(?:my|your|the|our) ", "").strip();
                journal.record("order", "upgrade " + what + " to netherite", currentGameTickSafe());
                orderGoal("upgrade my " + what + " to netherite");
                // Worn armour and the shield come into the pack for the table; on again after.
                if ("gear".equals(what) || "armor".equals(what) || "armour".equals(what)) {
                    for (String k : java.util.List.of("helmet", "chestplate", "leggings", "boots")) intoPackForAnvil(smither, k);
                    if (!"gear".equals(what)) what = "armor";
                } else {
                    intoPackForAnvil(smither, what.replaceAll("s$", "").equals("boot") ? "boots" : what);
                }
                String smithJob = "smith-" + UUID.randomUUID();
                afterTaskAlways.put(smithJob, () -> com.mineagent.engine.survival.GearUp.run(smither));
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.SmithingTask.SmithRecord(smithJob, what), reply -> { });
                return true;
            }
            case LOOT -> {
                boolean go = "go".equals(c.value());
                journal.record("order", go ? "go looting" : "loot the chests here", currentGameTickSafe());
                orderGoal(go ? "go looting" : "loot the chests here");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.LootTask.LootRecord(
                        "loot-" + UUID.randomUUID(), go), reply -> { });
                return true;
            }
            case TRAIN_VILLAGERS -> {
                var trainer = self.serverPlayer();
                if (trainer == null) return false;
                journal.record("order", "train the villagers", currentGameTickSafe());
                if (!trainKeepers(trainer, trainer.serverLevel())) {
                    speakToOwner(keepers.isEmpty() ? "I keep no villagers yet: I roll and wall in a librarian first."
                            : "None of my villagers near can be trained now: masters already, out of trades till they restock, or I've nothing to pay with.");
                } else {
                    speakToOwner("Training my villagers: their trades, a round at a time, till they're masters.");
                }
                return true;
            }
            case SPEEDRUN -> {
                if (!fromOwner) return false;
                speedrun = "on".equals(c.value());
                speakToOwner(speedrun ? "Speedrun: no stockpiling, only what gets us to the end." : "No more speedrun: back to building up.");
                saveMemoryNow();
                return true;
            }
            case BOSS_PREP -> {
                if (c.value() != null && c.value().startsWith("beat the game")) {
                    speakToOwner("Beating the game means the dragon: I can get ready for it now; fighting it myself isn't something I do yet.");
                }
                String boss = com.mineagent.engine.gear.BossKit.boss(c.value());
                var bossSp = self.serverPlayer();
                if (boss == null || bossSp == null) return false;
                java.util.Map<String, Integer> has = new java.util.HashMap<>();
                var inv = bossSp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    var st = inv.getItem(i);
                    if (!st.isEmpty()) has.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
                }
                List<String> orders = new ArrayList<>();
                for (var w : com.mineagent.engine.gear.BossKit.short_(boss, has)) {
                    orders.add("make me " + w.count() + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(w.item(), w.count()));
                }
                for (var b : com.mineagent.engine.gear.BossKit.brews(boss)) orders.add("brew a potion of " + b.potion());
                String who = "dragon".equals(boss) ? "the dragon" : "the wither";
                journal.record("order", "get ready for " + who, currentGameTickSafe());
                if (orders.isEmpty()) {
                    speakToOwner("I'm ready for " + who + ". I'll only fight it if you tell me to.");
                    return true;
                }
                speakToOwner("Getting ready for " + who + ": " + String.join(", ", orders).replace("make me ", "") + ". I won't fight it unless you tell me to.");
                return queueOrders(String.join(", then ", orders));
            }
            case GEAR_REPORT -> {
                speakToOwner(gearReport());
                return true;
            }
            case ROLL_LIBRARIAN -> {
                String ench = enchantmentId(c.value());
                if (ench == null) {
                    speakToOwner("I don't know an enchantment called " + c.value() + ".");
                    return true;
                }
                journal.record("order", "roll a librarian for " + c.value(), currentGameTickSafe());
                orderGoal("roll a librarian for " + c.value());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.LibrarianTask.RollRecord(
                        "roll-" + UUID.randomUUID(), ench), reply -> { });
                return true;
            }
            case PROTECTION -> {
                if (!fromOwner) return false; // its owner's own say only
                protectOwner = "on".equals(c.value());
                journal.record("order", protectOwner ? "protect me" : "don't protect me", currentGameTickSafe());
                speakToOwner(protectOwner ? "I'll step in when something could really hurt you." : "Got it: your fights are yours. I'll keep to mine.");
                saveMemoryNow();
                return true;
            }
            case GOLEMS -> {
                if (!fromOwner) return false;
                asking.tell("golems", "on".equals(c.value()));
                speakToOwner("on".equals(c.value()) ? "I'll get iron from village golems when that's the cheapest way." : "I'll leave the golems alone.");
                saveMemoryNow();
                return true;
            }
            case CHESTS_OK, CHESTS_NO -> {
                if (!fromOwner) return false;
                chestsAllowed = c.kind() == com.mineagent.engine.chat.CommandParser.Kind.CHESTS_OK;
                chestsDecided = true;
                chestsTold = true;
                journal.record("order", chestsAllowed ? "use my chests" : "leave my chests", currentGameTickSafe());
                saveMemoryNow();
                speakToOwner(chestsAllowed ? "I'll use what's in your chests when a job needs it." : "I'll leave your chests alone.");
                return true;
            }
            case CHEST_PURPOSE -> {
                if (!fromOwner) return false;
                var me = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
                var owner = me == null ? null : me.serverPlayerOwner();
                var chest = owner == null ? null : chestMeant(owner);
                if (chest == null) {
                    speakToOwner("Which chest? Look at it and say it again.");
                    return true;
                }
                String dim = owner.serverLevel().dimension().location().toString();
                chestPurposes.set(dim, chest.getX(), chest.getY(), chest.getZ(), c.value());
                String what = chestPurposes.of(dim, chest.getX(), chest.getY(), chest.getZ()).orElse(c.value());
                journal.record("order", "the chest at " + chest.toShortString() + " is for " + what, currentGameTickSafe(), dim,
                        chest.getX(), chest.getY(), chest.getZ());
                saveMemoryNow();
                speakToOwner("That chest is for " + ("overflow".equals(what) ? "anything without a chest of its own" : what) + " now.");
                return true;
            }
            case PUT_AWAY, TIDY -> {
                boolean tidy = c.kind() == com.mineagent.engine.chat.CommandParser.Kind.TIDY;
                String words = tidy ? "tidy the chests" : "put your things away";
                journal.record("order", words, currentGameTickSafe());
                orderGoal(words);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.PutAwayTask.PutAwayRecord(
                        "putaway-" + UUID.randomUUID(), tidy), reply -> { });
                return true;
            }
            case APPLY_BOOK -> {
                String[] parts = c.value().split("\\|", 2);
                String ench = enchantmentId(parts[0]);
                if (ench == null && !parts[0].matches("(?:the |a |an )?(?:enchanted )?book")) return false;
                String words = "put " + parts[0] + " on your " + parts[1];
                journal.record("order", words, currentGameTickSafe());
                orderGoal(words);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.AnvilTask.AnvilRecord(
                        "anvil-" + UUID.randomUUID(), "book", parts[1], ench), reply -> { });
                return true;
            }
            case REPAIR, GRIND -> {
                if (c.kind() == com.mineagent.engine.chat.CommandParser.Kind.REPAIR && repairBuilding(c.value())) return true;
                boolean grind = c.kind() == com.mineagent.engine.chat.CommandParser.Kind.GRIND;
                String words = (grind ? "disenchant your " : "repair your ") + c.value();
                journal.record("order", words, currentGameTickSafe());
                orderGoal(words);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.AnvilTask.AnvilRecord(
                        "anvil-" + UUID.randomUUID(), grind ? "grind" : "repair", c.value()), reply -> { });
                return true;
            }
            case BARTER -> {
                int n = c.count() == null || c.count() < 1 ? 64 : c.count();
                String words = "barter " + (n >= 64 ? "all your" : String.valueOf(n)) + " gold with the piglins";
                journal.record("order", words, currentGameTickSafe());
                orderGoal(words);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.BarterTask.BarterRecord(
                        "barter-" + UUID.randomUUID(), n), reply -> { });
                return true;
            }
            case FISH -> {
                int n = c.count() == null || c.count() < 1 ? 4 : c.count();
                journal.record("order", "catch " + n + " fish", currentGameTickSafe());
                orderGoal("catch me " + n + " fish");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.FishTask.FishRecord(
                        "fish-" + UUID.randomUUID(), n), reply -> { });
                return true;
            }
            case PATROL -> {
                if (!fromOwner) return false;
                if ("clear".equals(c.value())) {
                    patrol.clear();
                    speakToOwner("No patrol: I'll keep to one post.");
                    return true;
                }
                var me = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
                var owner = me == null ? null : me.serverPlayerOwner();
                if (owner == null) return false;
                if (patrol.size() >= 8) patrol.remove(0);
                // The post it already keeps is the first point.
                if (patrol.isEmpty() && guardPost != null) patrol.add(guardPost);
                patrol.add(owner.position());
                speakToOwner("Patrol point " + patrol.size() + " at " + owner.getBlockX() + ", " + owner.getBlockY() + ", " + owner.getBlockZ()
                        + (guardPost == null ? ". Say \"guard here\" to start." : "."));
                return true;
            }
            case BREED -> {
                String w = c.value();
                String animal = null;
                if (!w.startsWith("animal")) {
                    for (String one : new String[] {w, w.replaceAll("es$", ""), w.replaceAll("s$", "")}) {
                        var key = net.minecraft.resources.ResourceLocation.tryParse("minecraft:" + one.strip().replace(' ', '_'));
                        if (key != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                            animal = key.toString();
                            break;
                        }
                    }
                    if (animal == null) return false;
                }
                journal.record("order", "breed " + w, currentGameTickSafe());
                orderGoal("breed the " + w);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.BreedTask.BreedRecord(
                        "breed-" + UUID.randomUUID(), animal, 1), reply -> { });
                return true;
            }
            case BREW -> {
                // Asked in so many words: what it lacks that it can make (sugar, bottles, blaze powder) made first (MA11.4).
                if (dispatchingGoal == null && fromOwner) {
                    String first = brewNeeds(c.value());
                    if (!first.isEmpty() && queueOrders(first + ", then brew a potion of " + c.value().replace('_', ' '))) return true;
                }
                journal.record("order", "brew " + c.value().replace('_', ' '), currentGameTickSafe());
                orderGoal("brew a potion of " + c.value().replace('_', ' '));
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.BrewTask.BrewRecord(
                        "brew-" + UUID.randomUUID(), c.value()), reply -> { });
                return true;
            }
            case PORTAL -> {
                journal.record("order", "build a nether portal", currentGameTickSafe());
                orderGoal("build a nether portal");
                speakToOwner("On it: a nether portal.");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.PortalTask.PortalRecord(
                        "portal-" + UUID.randomUUID()), reply -> { });
                return true;
            }
            case WHY -> speakToOwner(why());
            case NEEDS -> speakToOwner(needs());
            case CANCEL -> {
                haltNow();
                activeOwnerGoal = "";
                speakToOwner("Cancelled." + pauseGoal());
            }
            case RESUME -> {
                var g = goalBook.resume();
                // No long goal to go back to: "continue" is the model's to read, as before.
                if (g.isEmpty()) return false;
                if (orderIn(g.get().text(), companion.companionName()).isPresent()) {
                    // An order: code runs it again, from where things are now.
                    orderGoalId = null;
                    orderSince = 0;
                    speakToOwner("Back to it: " + com.mineagent.engine.goal.Orders.withoutMark(g.get().text()) + ".");
                } else if (onSelfGoal(goalPrompt(g.get(), null))) {
                    speakToOwner("Back to " + g.get().id() + ": " + truncateForLog(g.get().text(), 120) + ".");
                } else {
                    speakToOwner("Goal " + g.get().id() + " is next, once I'm off guard and out of parked mode.");
                }
            }
            case GOAL_ADD -> {
                speakToOwner(addGoal(c.value(), fromOwner ? "owner" : "trusted"));
            }
            case EAT -> {
                runDirect("eat_item", new com.google.gson.JsonObject());
                speakToOwner("Eating.");
            }
            case GUARD_HERE, GUARD_PLAYER, GUARD_OFF -> {
                if (!fromOwner) {
                    speakToOwner("Only my owner puts me on guard.");
                    return true;
                }
                return guardCommand(c, self, id);
            }
            case DROP -> {
                if (c.count() == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace(c.value()))) return false;
                var args = new com.google.gson.JsonObject();
                args.addProperty("item_id", "minecraft:" + c.value());
                args.addProperty("count", c.count());
                runDirect("drop_items", args);
                speakToOwner("Dropping " + c.count() + " " + c.value().replace('_', ' ') + ".");
            }
        }
        return true;
    }

    /**
     * Places and the way back (SMH plan 70, MA5.6), answered from route memory without a model: "this is home",
     * "remember this as the mine", "go home", "go to the village", "where's the mine", "lead me out", "go back the way
     * we came". A place it doesn't know goes to the model, except home, which it says it doesn't know yet.
     */
    private boolean placeCommand(com.mineagent.engine.chat.CommandParser.Command c,
                                 com.mineagent.engine.entity.CompanionEntity self, boolean fromOwner) {
        var me = self.serverPlayer();
        if (me == null || !com.mineagent.engine.settings.Switches.routeMemory()) return false;
        var memory = placeKeeper.memory();
        var here = com.mineagent.engine.world.PlaceKeeper.point(me);
        var owner = self.serverPlayerOwner();
        boolean ownerNear = owner != null && owner.serverLevel() == me.serverLevel() && owner.distanceTo(me) < 24.0;
        long tick = currentGameTickSafe();
        switch (c.kind()) {
            case HOME_HERE, REMEMBER_PLACE -> {
                if (!fromOwner) {
                    speakToOwner("Only my owner names places.");
                    return true;
                }
                // "Here" is where the owner stands, when they're near; else where it is.
                var at = ownerNear ? com.mineagent.engine.world.PlaceKeeper.point(owner) : here;
                boolean home = c.kind() == com.mineagent.engine.chat.CommandParser.Kind.HOME_HERE;
                var place = memory.name(home ? "home" : "named", home ? "home" : c.value(), at, tick);
                journal.record("place", (home ? "home is " : "named " + place.name() + " at ") + at.x() + ", " + at.y() + ", " + at.z(),
                        tick, at.dim(), at.x(), at.y(), at.z());
                saveMemoryNow();
                speakToOwner(home
                        ? "Home is here, at " + at.x() + ", " + at.y() + ", " + at.z() + ". Say \"go home\" and I'll take us back."
                        : "I'll remember this as " + place.name() + ".");
            }
            case WHERE_PLACE -> {
                var from = ownerNear || (owner != null && owner.serverLevel() == me.serverLevel())
                        ? com.mineagent.engine.world.PlaceKeeper.point(owner) : here;
                var place = memory.find(c.value(), from);
                if (place.isEmpty()) {
                    // Not a place: a chest that holds it, or somewhere it saw it (MA5.7); else the model's.
                    if (!isHomeWord(c.value())) {
                        String answer = whereIs(c.value(), from, true, true);
                        if (answer == null) return false;
                        speakToOwner(answer);
                        return true;
                    }
                    speakToOwner("I don't know where home is yet. Stand there and say \"this is home\".");
                    return true;
                }
                speakToOwner(capitalised(place.get().name()) + " is "
                        + com.mineagent.engine.world.PlaceKeeper.whereFrom(from, place.get().at()));
            }
            case GO_PLACE -> {
                var place = memory.find(c.value(), here);
                if (place.isEmpty()) {
                    if (!isHomeWord(c.value())) return false;
                    speakToOwner("I don't know where home is yet. Stand there and say \"this is home\".");
                    return true;
                }
                var to = place.get();
                if (!to.at().dim().equals(here.dim())) {
                    // Through a portal it has been through before (MA5.6); a link is never guessed.
                    var link = memory.portalTo(to.at().dim(), here);
                    if (link.isEmpty()) {
                        speakToOwner(capitalised(to.name()) + " is in "
                                + com.mineagent.engine.world.PlaceKeeper.dimensionName(to.at().dim())
                                + ", and I haven't been through a portal from here to there yet.");
                        return true;
                    }
                    haltNow();
                    standDown();
                    var portal = com.mineagent.engine.world.PlaceKeeper.portalBlockNear(me, link.get().from());
                    // Standing in the portal already (it came through it): out first, then back in.
                    portalOutTried.clear();
                    var out = com.mineagent.engine.world.PlaceKeeper.standingInPortal(me)
                            ? com.mineagent.engine.world.PlaceKeeper.besidePortal(me) : null;
                    if (out != null) portalOutTried.add(net.minecraft.core.BlockPos.asLong(out.x(), out.y(), out.z()));
                    goTo(out != null ? out : portal, java.util.List.of(), false, "xzy");
                    crossing = new Crossing(to, portal, tick, out != null ? Crossing.Stage.OUT : Crossing.Stage.IN, tick);
                    speakToOwner("Through the portal " + com.mineagent.engine.world.PlaceKeeper.whereFrom(here, link.get().from())
                            .replaceAll("\\.$", "") + ", then on to " + to.name() + " in "
                            + com.mineagent.engine.world.PlaceKeeper.dimensionName(to.at().dim()) + ".");
                    return true;
                }
                haltNow();
                standDown();
                goTo(to.at(), placeKeeper.wayTo(here, to), false);
                speakToOwner("Heading to " + to.name() + ", " + com.mineagent.engine.world.PlaceKeeper.whereFrom(here, to.at()));
            }
            case WAY_BACK -> {
                boolean out = "out".equals(c.value());
                if (out && !com.mineagent.engine.world.PlaceKeeper.underground(me.serverLevel(), me.blockPosition())) {
                    speakToOwner("We're out in the open already.");
                    return true;
                }
                var back = com.mineagent.engine.world.RouteMemory.simplify(memory.wayBack());
                if (back.size() < 2) {
                    speakToOwner("I haven't got a way back yet: I've only just got here.");
                    return true;
                }
                haltNow();
                standDown();
                goTo(back.get(back.size() - 1), back.subList(0, back.size() - 1), out);
                speakToOwner(out ? "Leading the way out, the way we came in." : "Going back the way we came.");
            }
            case FIND_PLACE -> {
                String kind = c.value().isEmpty() ? null : com.mineagent.engine.world.PlaceKeeper.kindFromWords(c.value());
                if (!c.value().isEmpty() && kind == null) return false; // "find me diamonds" is the model's to plan
                if (kind != null) {
                    var known = memory.find(kind, here);
                    if (known.isPresent() && known.get().at().dim().equals(here.dim())) {
                        haltNow();
                        standDown();
                        goTo(known.get().at(), placeKeeper.wayTo(here, known.get()), false);
                        speakToOwner("I know " + aOrAn(kind) + ": heading to " + known.get().name() + ", "
                                + com.mineagent.engine.world.PlaceKeeper.whereFrom(here, known.get().at()));
                        return true;
                    }
                }
                haltNow();
                standDown();
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ExploreTask.ExploreRecord(
                        "explore-" + UUID.randomUUID(), kind, EXPLORE_CHUNKS), reply -> { });
                speakToOwner(kind == null ? "Exploring new ground." : "Looking for " + aOrAn(kind) + " over ground I haven't seen.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** New chunks it looks over on one "find" or "explore" before it stops and says so. */
    static final int EXPLORE_CHUNKS = 64;

    /**
     * On its way through a portal to a place in another dimension (MA5.6): OUT of the portal it stands in, WAITING a
     * second outside it (the game's cooldown runs down only out of the portal), then IN, standing in it until the
     * game takes it through (four seconds for a player).
     */
    private record Crossing(com.mineagent.engine.world.RouteMemory.Place to, com.mineagent.engine.world.RouteMemory.Point portal,
                            long since, Stage stage, long stageSince) {
        enum Stage { OUT, WAITING, IN }

        Crossing next(Stage s, long tick) {
            return new Crossing(to, portal, since, s, tick);
        }
    }

    /** Ticks it waits out of a portal before stepping back in: past a player's 10-tick portal cooldown. */
    static final long PORTAL_STEP_OUT = 20L;
    /** Ticks in a portal without being taken through before it steps out and tries again (80 are needed). */
    static final long PORTAL_RETRY = 200L;

    private volatile Crossing crossing;

    /** Where it is in a portal crossing ("OUT", "WAITING", "IN"), or "" when not crossing (the game tests). */
    public String crossingStage() {
        Crossing c = crossing;
        return c == null ? "" : c.stage().name();
    }

    /** The spots beside a portal it tried to step out to on this crossing (block positions). */
    private final java.util.Set<Long> portalOutTried = new java.util.HashSet<>();

    /** Where it stood during a crossing, and since when (game ticks). */
    private long crossingStillAt = Long.MIN_VALUE, crossingStillSince;

    private boolean stuckCrossing(net.minecraft.server.level.ServerPlayer me, long tick) {
        long at = me.blockPosition().asLong();
        if (at != crossingStillAt) {
            crossingStillAt = at;
            crossingStillSince = tick;
            return false;
        }
        if (tick - crossingStillSince < 200) return false;
        crossingStillSince = tick;
        return true;
    }

    /** Through the portal: on to the place; or it says it couldn't get through. */
    private void watchCrossing(net.minecraft.server.level.ServerPlayer me, long tick) {
        Crossing c = crossing;
        if (c == null || me == null) return;
        var here = com.mineagent.engine.world.PlaceKeeper.point(me);
        boolean inPortal = com.mineagent.engine.world.PlaceKeeper.standingInPortal(me);
        if (here.dim().equals(c.to().at().dim())) {
            crossing = null;
            goTo(c.to().at(), placeKeeper.wayTo(here, c.to()), false);
            say("Through. On to " + c.to().name() + ", " + com.mineagent.engine.world.PlaceKeeper.whereFrom(here, c.to().at()));
        } else if (tick - c.since() > 3_000L) {
            crossing = null;
            say("I couldn't get through the portal.");
        } else if (!inPortal && c.stage() == Crossing.Stage.IN && stuckCrossing(me, tick)) {
            // On the same block for ten seconds on its way back in (a pit by the portal): the walk dropped, a new one.
            haltNow();
            goTo(c.portal(), java.util.List.of(), false, "xzy");
            crossing = c.next(Crossing.Stage.IN, tick);
        } else if (c.stage() == Crossing.Stage.OUT && !inPortal) {
            crossing = c.next(Crossing.Stage.WAITING, tick);
        } else if (c.stage() == Crossing.Stage.OUT && tick - c.stageSince() >= PORTAL_RETRY) {
            // Still in it: the way out failed; another spot (not the one it couldn't get to).
            var out = com.mineagent.engine.world.PlaceKeeper.besidePortal(me, portalOutTried);
            if (out == null) {
                portalOutTried.clear();
                out = com.mineagent.engine.world.PlaceKeeper.besidePortal(me);
            }
            if (out != null) {
                portalOutTried.add(net.minecraft.core.BlockPos.asLong(out.x(), out.y(), out.z()));
                goTo(out, java.util.List.of(), false, "xzy");
            }
            crossing = c.next(Crossing.Stage.OUT, tick);
        } else if (c.stage() == Crossing.Stage.WAITING && inPortal) {
            // Still touching it, so its cooldown restarts: further out, then wait again from there.
            var out = com.mineagent.engine.world.PlaceKeeper.besidePortal(me);
            if (out != null) goTo(out, java.util.List.of(), false, "xzy");
            crossing = c.next(Crossing.Stage.OUT, tick);
        } else if (c.stage() == Crossing.Stage.WAITING && tick - c.stageSince() >= PORTAL_STEP_OUT) {
            goTo(c.portal(), java.util.List.of(), false, "xzy");
            crossing = c.next(Crossing.Stage.IN, tick);
        } else if (c.stage() == Crossing.Stage.IN && tick - c.stageSince() >= PORTAL_RETRY) {
            if (inPortal) {
                // In it this long and not taken through: out and in again.
                var out = com.mineagent.engine.world.PlaceKeeper.besidePortal(me);
                if (out != null) goTo(out, java.util.List.of(), false, "xzy");
                crossing = c.next(out != null ? Crossing.Stage.OUT : Crossing.Stage.IN, tick);
            } else {
                // Not in it and not on its way: walk into it again.
                LiveBodyState body = liveBodyState.get();
                if (body == null || body.taskId() == null) goTo(c.portal(), java.util.List.of(), false, "xzy");
                crossing = c.next(Crossing.Stage.IN, tick);
            }
        }
    }

    /**
     * "Where did you see diamonds?", "where did you put the iron?", "what did we do yesterday?" (MA5.7, MA5.9),
     * answered from its memory, never the model's guess.
     */
    private boolean memoryCommand(com.mineagent.engine.chat.CommandParser.Command c,
                                  com.mineagent.engine.entity.CompanionEntity self) {
        var me = self.serverPlayer();
        if (me == null) return false;
        var owner = self.serverPlayerOwner();
        var from = owner != null && owner.serverLevel() == me.serverLevel()
                ? com.mineagent.engine.world.PlaceKeeper.point(owner) : com.mineagent.engine.world.PlaceKeeper.point(me);
        switch (c.kind()) {
            case WHERE_SEEN -> {
                String answer = whereIs(c.value(), from, false, true);
                speakToOwner(answer != null ? answer : "I haven't seen any " + plainWords(c.value()) + " yet.");
            }
            case WHERE_PUT -> {
                String answer = whereIs(c.value(), from, true, false);
                if (answer == null) {
                    var stored = journal.search(com.mineagent.engine.memory.Sightings.fromWords(c.value()).replace('_', ' '), "stored", 1);
                    answer = stored.isEmpty() ? "I don't know of any " + plainWords(c.value()) + " in a chest."
                            : "Last I noted: " + stored.get(0).text() + ".";
                }
                speakToOwner(answer);
            }
            case WHAT_HAPPENED -> speakToOwner(whatHappened(c.value()));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String plainWords(String words) {
        return words == null ? "" : words.strip().replaceFirst("^(?:the|my|our|some|any) ", "");
    }

    /** Where something is: chests holding it, then where it was seen; null when it knows of neither. */
    private String whereIs(String words, com.mineagent.engine.world.RouteMemory.Point from, boolean inChests, boolean seen) {
        if (from == null) return null;
        String what = com.mineagent.engine.memory.Sightings.fromWords(words);
        if (what.isEmpty()) return null;
        String name = what.replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        if (inChests) {
            var held = chests.holding(words, from.dim(), from.x(), from.y(), from.z(), 3);
            for (var h : held) {
                var cp = new com.mineagent.engine.world.RouteMemory.Point(h.chest().dim(), h.chest().x(), h.chest().y(), h.chest().z());
                if (sb.length() > 0) sb.append(" ");
                sb.append(h.count()).append(" ").append(h.item().replace('_', ' ')).append(" in the chest ")
                        .append(com.mineagent.engine.world.PlaceKeeper.whereFrom(from, cp));
            }
        }
        if (seen && sb.length() == 0) {
            long now = currentGameTickSafe();
            var found = placeKeeper.sightings().nearest(what, from.dim(), from.x(), from.y(), from.z(), 3, 30_000);
            for (var f : found) {
                var s = f.sighting();
                var sp = new com.mineagent.engine.world.RouteMemory.Point(s.dim(), s.x(), s.y(), s.z());
                if (sb.length() > 0) sb.append(" Also ");
                else sb.append("I saw ");
                sb.append(s.count() > 1 ? s.count() + " " : "").append(name)
                        .append(s.kind() == com.mineagent.engine.memory.Sightings.Kind.ORE ? " ore" : "")
                        .append(" ").append(ago(now - s.last())).append(", ")
                        .append(com.mineagent.engine.world.PlaceKeeper.whereFrom(from, sp));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** "just now", "12 minutes ago", "3 hours ago", "2 days ago", in the game's time. */
    static String ago(long ticks) {
        long minutes = Math.max(0, ticks) / 1200L;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    /** From the journal: today, yesterday, or since its owner left or went AFK. */
    private String whatHappened(String when) {
        var today = java.time.LocalDate.now();
        if ("yesterday".equals(when)) return journal.describe(today.minusDays(1), "Yesterday");
        if ("today".equals(when)) return journal.describe(today, "Today");
        var recent = journal.recent(200);
        int since = -1;
        for (int i = recent.size() - 1; i >= 0; i--) {
            String k = recent.get(i).kind();
            if ("left".equals(k) || "afk".equals(k)) {
                since = i;
                break;
            }
        }
        if (since < 0) return journal.describe(today, "Today");
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (var e : recent.subList(since + 1, recent.size())) {
            if ("said".equals(e.kind()) || "back".equals(e.kind())) continue;
            counts.merge(e.kind(), 1, Integer::sum);
            if (java.util.List.of("death", "find", "fight", "stored", "took", "goal", "place").contains(e.kind())) lines.add(e.text());
        }
        if (counts.isEmpty()) return "While you were away: all quiet.";
        StringBuilder sb = new StringBuilder("While you were away: ");
        sb.append(String.join("; ", lines.subList(Math.max(0, lines.size() - 6), lines.size())));
        if (lines.isEmpty()) sb.append(counts.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                .collect(java.util.stream.Collectors.joining(", ")));
        return sb.append('.').toString();
    }

    /** A trip by the goto task, through waypoints first; done early in the open when {@code stopInOpen}. */
    private void goTo(com.mineagent.engine.world.RouteMemory.Point to,
                      java.util.List<com.mineagent.engine.world.RouteMemory.Point> via, boolean stopInOpen) {
        goTo(to, via, stopInOpen, "block");
    }

    private void goTo(com.mineagent.engine.world.RouteMemory.Point to,
                      java.util.List<com.mineagent.engine.world.RouteMemory.Point> via, boolean stopInOpen, String mode) {
        var args = new com.google.gson.JsonObject();
        args.addProperty("goal_mode", mode);
        args.addProperty("x", to.x());
        args.addProperty("y", to.y());
        args.addProperty("z", to.z());
        var points = new com.google.gson.JsonArray();
        for (var p : via) {
            if (!p.dim().equals(to.dim())) continue;
            var a = new com.google.gson.JsonArray();
            a.add(p.x());
            a.add(p.y());
            a.add(p.z());
            points.add(a);
        }
        args.add("waypoints", points);
        if (stopInOpen) args.addProperty("stop_in_open", true);
        runDirect("goto", args);
    }

    private static boolean isHomeWord(String words) {
        String w = words == null ? "" : words.strip().replaceFirst("^(?:the|my|our) ", "");
        return w.equals("home") || w.equals("base");
    }

    private static String capitalised(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String aOrAn(String word) {
        return ("aeiou".indexOf(Character.toLowerCase(word.isEmpty() ? 'x' : word.charAt(0))) >= 0 ? "an " : "a ") + word;
    }

    /** Its places and routes (the game tests). */
    public com.mineagent.engine.world.PlaceKeeper placeKeeper() {
        return placeKeeper;
    }

    /** "Guard here", "guard Alex", "stand down" (the owner's only): set or clear guard duty. */
    private boolean guardCommand(com.mineagent.engine.chat.CommandParser.Command c,
                                 com.mineagent.engine.entity.CompanionEntity self, UUID id) {
        switch (c.kind()) {
            case GUARD_HERE -> {
                // "That": what the owner is looking at (a block, or a pet or villager to keep); "this": where the owner
                // stands (they are at the thing they mean); else where the companion is.
                var me = self.serverPlayer();
                var owner = self.serverPlayerOwner();
                if (me == null) return false;
                var post = owner != null && owner.distanceTo(me) < 24.0 ? owner.position() : me.position();
                if ("that".equals(c.value()) && owner != null) {
                    var looked = lookedAt(owner);
                    if (looked instanceof net.minecraft.world.entity.Entity e && !(e instanceof net.minecraft.world.entity.player.Player)) {
                        haltNow();
                        setGuard(null, e.getUUID(), 8.0);
                        MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FREE);
                        speakToOwner("Guarding the " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()
                                .replace('_', ' ') + ".");
                        return true;
                    }
                    if (looked instanceof net.minecraft.core.BlockPos b) post = net.minecraft.world.phys.Vec3.atBottomCenterOf(b.above());
                }
                double radius = c.count() == null ? 12.0 : c.count();
                haltNow();
                setGuard(post, null, radius);
                MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FREE);
                speakToOwner("Guarding this spot, " + Math.round(Math.max(4, Math.min(32, radius)))
                        + " blocks around. Say \"stand down\" when you're done.");
            }
            case GUARD_PLAYER -> {
                var me = self.serverPlayer();
                if (me == null) return false;
                var who = me.serverLevel().getServer().getPlayerList().getPlayerByName(c.value());
                if (who == null || who == me) return false;
                haltNow();
                setGuard(null, who.getUUID(), 8.0);
                MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FREE);
                speakToOwner("Guarding " + who.getName().getString() + ".");
            }
            case GUARD_OFF -> {
                boolean was = standDown();
                speakToOwner(was ? "Standing down." : "I wasn't guarding anything.");
                // Off guard, back to the long goal if one is waiting.
                if (was) goalBook.active().ifPresent(g -> onSelfGoal(goalPrompt(g, null)));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** What a player is looking at within 24 blocks: an entity, else a block position, else null. */
    private static Object lookedAt(net.minecraft.server.level.ServerPlayer p) {
        var eye = p.getEyePosition();
        var look = p.getLookAngle();
        var end = eye.add(look.scale(24.0));
        var box = p.getBoundingBox().expandTowards(look.scale(24.0)).inflate(1.0);
        var hitEntity = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> !e.isSpectator() && e.isPickable(), 24.0 * 24.0);
        var hitBlock = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        double blockD = hitBlock.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? Double.MAX_VALUE : hitBlock.getLocation().distanceToSqr(eye);
        if (hitEntity != null && hitEntity.getLocation().distanceToSqr(eye) < blockD) return hitEntity.getEntity();
        return hitBlock.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hitBlock.getBlockPos() : null;
    }

    /** Off guard duty; whether it was on it. */
    private boolean standDown() {
        boolean was = guarding();
        if (was) setGuard(null, null, 12.0);
        return was;
    }

    // ── The goal book and the goal loop (SMH plan 69, MA3.1 and MA3.2) ──

    public com.mineagent.engine.planning.GoalBook goalBook() {
        return goalBook;
    }

    /**
     * A step toward a long goal, given to the body as the owner's own order would be (what onOwnerMessage does,
     * without the praise and scold reading): the goal is set, the tools picked for it, and a turn started.
     */
    public boolean onSelfGoal(String goal) {
        if (goal == null || goal.isBlank()) return false;
        // Parked it only talks; on guard it stays on guard. The goal waits in the book until then.
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED || guarding()) return false;
        curriculum.abandon();
        long tick = currentGameTickSafe();
        lastOwnerCommand = goal.length() > 120 ? goal.substring(0, 120) : goal;
        activeOwnerGoal = truncateForLog(goal, 360);
        lastOwnerMessageTime = System.currentTimeMillis();
        lastGoalStepMs = lastOwnerMessageTime;
        prepareToolsForOwnerIntent(goal);
        semanticWorldModel.recordOwnerIntent(goal, tick);
        layeredMemory.remember(LayeredMemory.Tier.WORKING, "owner.intent",
                truncateForLog(goal, 360), "goal_book", 1.0, tick, 400L, "current", "current");
        cognitiveState.append("owner_intent", "goal_book", truncateForLog(goal, 300), tick);
        if (enqueueInbox("[GOAL] " + goal)) wake("goal_book");
        return true;
    }

    /** Add a goal to the book (the owner or a trusted player): started now if none is active. What to say back. */
    public String addGoal(String text, String setBy) {
        journal.record("goal", "new goal (" + setBy + "): " + text, currentGameTickSafe());
        com.mineagent.engine.planning.GoalBook.Goal g;
        try {
            g = goalBook.add(text, setBy, currentGameTickSafe(), AgentLoop::isItemId);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            return "I can't add that: " + refused.getMessage() + ".";
        }
        saveMemoryNow();
        if (g.status() == com.mineagent.engine.planning.GoalBook.Status.ACTIVE) {
            String said = "New goal " + g.id() + ": " + truncateForLog(g.text(), 120) + ".";
            if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return said + " I'm parked, so I'll start when you take me out of parked mode.";
            if (guarding()) return said + " I'll start on it when you say \"stand down\".";
            haltNow();
            onSelfGoal(goalPrompt(g, null));
            return said + " I'll keep at it until it's done.";
        }
        return "Queued goal " + g.id() + " after " + goalBook.active().map(com.mineagent.engine.planning.GoalBook.Goal::id).orElse("the current one") + ".";
    }

    /** The book in lines a person reads, the active goal first with what it is still short of. */
    public List<String> goalLines() {
        var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
        Map<String, Integer> have = self == null ? Map.of() : carried(self);
        List<String> lines = new ArrayList<>();
        for (var g : goalBook.list()) {
            boolean active = g.status() == com.mineagent.engine.planning.GoalBook.Status.ACTIVE;
            lines.add(com.mineagent.engine.planning.GoalBook.line(g, active ? goalBook.unmet(i -> have.getOrDefault(i, 0)) : null));
        }
        for (var st : goalBook.stock()) lines.add("keep " + st.count() + " " + st.item().replace('_', ' ') + " (have " + have.getOrDefault(st.item(), 0) + ")");
        return lines;
    }

    public String switchGoal(String id) {
        if (!goalBook.switchTo(id)) return "No open goal " + id + ".";
        var g = goalBook.active().orElseThrow();
        haltNow();
        saveMemoryNow();
        onSelfGoal(goalPrompt(g, null));
        return "Working on " + g.id() + ": " + truncateForLog(g.text(), 120) + ".";
    }

    public String dropGoal(String id) {
        boolean wasActive = goalBook.active().map(g -> g.id().equalsIgnoreCase(id)).orElse(false);
        if (goalBook.get(id).isEmpty()) return "No goal " + id + ".";
        var next = goalBook.drop(id);
        saveMemoryNow();
        if (!wasActive) return "Dropped " + id + ".";
        haltNow();
        activeOwnerGoal = "";
        if (next.isEmpty()) return "Dropped " + id + ". No goals left.";
        onSelfGoal(goalPrompt(next.get(), null));
        return "Dropped " + id + ". Next: " + next.get().id() + ", " + truncateForLog(next.get().text(), 100) + ".";
    }

    /** The owner closes a goal the count would not (a misread amount, or done another way). */
    public String finishGoal(String id) {
        boolean wasActive = goalBook.active().map(g -> g.id().equalsIgnoreCase(id)).orElse(false);
        if (goalBook.get(id).isEmpty()) return "No goal " + id + ".";
        var next = goalBook.markDone(id);
        journal.record("goal", "goal " + id + " marked done", currentGameTickSafe());
        saveMemoryNow();
        if (!wasActive) return "Marked " + id + " done.";
        haltNow();
        activeOwnerGoal = "";
        if (next.isEmpty()) return "Marked " + id + " done. No goals left.";
        onSelfGoal(goalPrompt(next.get(), null));
        return "Marked " + id + " done. Next: " + next.get().id() + ".";
    }

    /** A standing order: keep at least {@code count} of an item (0 cancels it), topped up when idle. */
    public String setStock(String item, int count) {
        String id = item == null ? "" : item.strip().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (count > 0 && !isItemId(id)) return "No item called " + item + ".";
        if (!goalBook.setStock(id, count)) return "I can keep at most " + com.mineagent.engine.planning.GoalBook.MAX_STOCK + " standing orders.";
        saveMemoryNow();
        return count <= 0 ? "No longer keeping " + id.replace('_', ' ') + "." : "I'll keep at least " + count + " " + id.replace('_', ' ') + " on me.";
    }

    /** Pause the long goal (the owner said stop); the words to add to the answer. */
    private String pauseGoal() {
        var g = goalBook.active();
        if (g.isEmpty()) return "";
        goalBook.pauseActive();
        saveMemoryNow();
        return " Goal " + g.get().id() + " is paused; say \"carry on\" to go back to it.";
    }

    /** What the body is told for a goal: the goal, where it stands, what is short, and a step if there is one. */
    private String goalPrompt(com.mineagent.engine.planning.GoalBook.Goal g, String step) {
        var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
        Map<String, Integer> have = self == null ? Map.of() : carried(self);
        var shortBy = com.mineagent.engine.planning.GoalBook.unmetOf(g, i -> have.getOrDefault(i, 0));
        StringBuilder b = new StringBuilder("Long goal ").append(g.id()).append(": ").append(g.text()).append('.');
        if (!g.milestones().isEmpty()) {
            b.append(" Done so far: ");
            var ms = g.milestones();
            for (int i = Math.max(0, ms.size() - 4); i < ms.size(); i++) {
                b.append(truncateForLog(ms.get(i).text(), 80)).append(i < ms.size() - 1 ? "; " : ".");
            }
        }
        if (!shortBy.isEmpty()) b.append(" Still short (counted): ").append(com.mineagent.engine.planning.GoalBook.describeNeeds(shortBy)).append('.');
        if (step != null && !step.isBlank()) b.append(" Next step: ").append(step);
        else b.append(" Work out the next step and do it.");
        if (g.repeats() >= 1) b.append(" This step has come back ").append(g.repeats() + 1).append(" times: try a different way.");
        return b.toString();
    }

    private static final String GOAL_PROMPT = "You keep an AI companion in Minecraft working toward a long goal, one "
            + "step at a time. You get the goal, the steps done, the step just verified, what the game counts as still "
            + "short, and the situation. Answer with JSON only: {\"done\":true|false,\"next\":\"one concrete step "
            + "it can do now, in one sentence\"}. Say done only when the whole goal is met. A step should take minutes, "
            + "not hours. If the same step keeps coming back, choose a different way.";

    /** A step was verified while a long goal is active: one smh/plan call decides whether it is done or what next. */
    private void goalStep(String verified, net.minecraft.server.level.ServerPlayer player) {
        var active = goalBook.active();
        if (active.isEmpty() || player == null || !goalStepInFlight.compareAndSet(false, true)) return;
        var g = active.get();
        long tick = currentGameTickSafe();
        goalBook.milestone(verified == null || verified.isBlank() ? "a step" : verified, tick);
        Map<String, Integer> have = carried(player);
        var shortBy = goalBook.unmet(i -> have.getOrDefault(i, 0));
        com.google.gson.JsonObject ask = new com.google.gson.JsonObject();
        ask.addProperty("goal", g.text());
        com.google.gson.JsonArray done = new com.google.gson.JsonArray();
        var ms = goalBook.active().map(com.mineagent.engine.planning.GoalBook.Goal::milestones).orElse(List.of());
        for (int i = Math.max(0, ms.size() - 8); i < ms.size(); i++) done.add(truncateForLog(ms.get(i).text(), 160));
        ask.add("done_so_far", done);
        ask.addProperty("just_verified", truncateForLog(verified, 240));
        ask.addProperty("still_short_by_count", com.mineagent.engine.planning.GoalBook.describeNeeds(shortBy));
        ask.addProperty("brief", situationBrief());
        ask.addProperty("situation", truncateForLog(realtimeCognition.summarizeForPrompt(), 1200));
        var request = new LLMRequest(baseUrl, apiKey, com.mineagent.engine.llm.RoleRouting.modelFor(model, "plan"),
                List.of(ChatMessage.system(GOAL_PROMPT), ChatMessage.user(ask.toString())),
                List.of(), List.of(), temperature, 300, null, null, null, null, Duration.ofSeconds(120), false);
        try {
            talkExecutor.submit(() -> {
                try {
                    var step = com.mineagent.engine.planning.GoalBook.parseStep(replyContent(sideCall(request)), shortBy);
                    Services.platform().scheduleOnServer(() -> applyGoalStep(g.id(), step));
                } catch (Exception e) {
                    System.err.println("[MineAgent] Goal step failed: " + e.getMessage());
                    Services.platform().scheduleOnServer(() -> applyGoalStep(g.id(), Optional.empty()));
                } finally {
                    goalStepInFlight.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            goalStepInFlight.set(false);
        }
    }

    private void applyGoalStep(String goalId, Optional<com.mineagent.engine.planning.GoalBook.Step> step) {
        var now = goalBook.active();
        if (now.isEmpty() || !now.get().id().equals(goalId)) return; // switched, dropped or stopped meanwhile
        var g = now.get();
        if (step.isPresent() && step.get().done()) {
            var next = goalBook.complete();
            journal.record("goal", "finished goal " + g.id() + ": " + truncateForLog(g.text(), 120), currentGameTickSafe());
            saveMemoryNow();
            speakToOwner("Goal " + g.id() + " done: " + truncateForLog(g.text(), 120) + "."
                    + next.map(n -> " Next up, " + n.id() + ": " + truncateForLog(n.text(), 100) + ".").orElse(""));
            activeOwnerGoal = "";
            next.ifPresent(n -> onSelfGoal(goalPrompt(n, null)));
            return;
        }
        String nextStep = step.map(com.mineagent.engine.planning.GoalBook.Step::next).orElse("");
        boolean claimedShort = step.map(com.mineagent.engine.planning.GoalBook.Step::claimedShort).orElse(false);
        var stepped = goalBook.stepping(nextStep.isBlank() ? g.lastNext() : nextStep, null, claimedShort);
        saveMemoryNow();
        if (stepped.isEmpty()) {
            speakToOwner(claimedShort
                    ? "I think goal " + g.id() + " is done, but I count it short. Paused it; say \"/mineagent goal done " + g.id() + "\" to close it, or \"carry on\"."
                    : "Goal " + g.id() + " isn't getting anywhere after " + g.steps() + " steps, so I've paused it. Say \"carry on\" to try again.");
            activeOwnerGoal = "";
            return;
        }
        var ms = stepped.get().milestones();
        if (!ms.isEmpty()) {
            sayMilestone(com.mineagent.engine.chat.MilestoneTalk.Kind.MILESTONE, "you finished a step toward your goal \""
                    + truncateForLog(g.text(), 120) + "\": " + truncateForLog(ms.get(ms.size() - 1).text(), 160));
        }
        onSelfGoal(goalPrompt(stepped.get(), nextStep));
    }

    /** The skills of one lineage: a base and its repaired versions (learning/SkillRepair). */
    private List<String> lineageOf(String skill) {
        String base = com.mineagent.engine.learning.SkillRepair.baseOf(skill);
        List<String> out = new ArrayList<>();
        for (SkillLibrary.Skill s : skillLib.exportAll()) {
            if (com.mineagent.engine.learning.SkillRepair.baseOf(s.name()).equals(base)) out.add(s.name());
        }
        return out;
    }

    /** A version on trial that has proved itself takes over; one that has proved worse is removed (rolled back). */
    private void settleVersions(String skill) {
        List<String> lineage = lineageOf(skill);
        if (lineage.size() < 2) return;
        for (String name : lineage) {
            if (!com.mineagent.engine.learning.SkillRepair.isVersion(name)) continue;
            var verdict = com.mineagent.engine.learning.SkillRepair.judge(scorecard, name, lineage);
            if (verdict == com.mineagent.engine.learning.SkillRepair.Verdict.PROMOTE) {
                for (String other : lineage) if (!other.equals(name)) skillLib.remove(other);
                onBodyLog("[SKILL_REPAIR] " + name + " does better than the older way and replaces it: "
                        + scorecard.describe("skill:" + name));
                sayMilestone(com.mineagent.engine.chat.MilestoneTalk.Kind.SKILL, "you found a better way to do "
                        + truncateForLog(skillLib.get(name).map(SkillLibrary.Skill::description).orElse(name), 120));
                saveMemoryNow();
                return;
            }
            if (verdict == com.mineagent.engine.learning.SkillRepair.Verdict.ROLL_BACK) {
                skillLib.remove(name);
                onBodyLog("[SKILL_REPAIR] " + name + " did worse than the older way and was removed: "
                        + scorecard.describe("skill:" + name));
                saveMemoryNow();
                return;
            }
        }
    }

    /**
     * A skill that keeps failing at one step gets that step rewritten by the model, as a new version beside the old
     * (both are then offered and rated; settleVersions keeps the better). Not on low experiments or the calm call
     * rate; one repair at a time per skill; never a healthy certified skill.
     */
    private void repairSkill(String skill) {
        if ("low".equals(agentSettings.experiments()) || "calm".equals(agentSettings.callRate())) return;
        String sequence = skillLib.getActionSequence(skill);
        if (sequence == null) return;
        int count;
        try {
            count = com.google.gson.JsonParser.parseString(sequence).getAsJsonArray().size();
        } catch (RuntimeException bad) {
            return;
        }
        var weak = com.mineagent.engine.learning.SkillRepair.weakestStep(scorecard, skill, count);
        if (weak.isEmpty()) return;
        List<String> lineage = lineageOf(skill);
        if (!com.mineagent.engine.learning.SkillRepair.mayRepair(scorecard, skill, lineage)) return;
        String base = com.mineagent.engine.learning.SkillRepair.baseOf(skill);
        if (!repairsInFlight.add(base)) return;
        int index = weak.getAsInt();
        var original = skillLib.get(skill).orElse(null);
        if (original == null) {
            repairsInFlight.remove(base);
            return;
        }
        List<String> evidence;
        var seen = skillFailures.getOrDefault(skill, new ArrayDeque<>());
        synchronized (seen) {
            evidence = List.copyOf(seen);
        }
        var request = com.mineagent.engine.learning.SkillRepair.request(baseUrl, apiKey, model, temperature, skill,
                original.description(), sequence, index, evidence, scorecard.failureCondition("skill:" + skill), SKILL_ACTION_TOOLS);
        try {
            talkExecutor.submit(() -> {
                try {
                    var fixed = com.mineagent.engine.learning.SkillRepair.apply(sequence, index,
                            replyContent(sideCall(request)), SKILL_ACTION_TOOLS);
                    fixed.ifPresent(f -> Services.platform().scheduleOnServer(() -> {
                        String name = com.mineagent.engine.learning.SkillRepair.nextVersion(base, lineageOf(skill));
                        skillLib.registerSequence(name, original.description(), original.triggerCondition(), f, true);
                        if (skillLib.get(name).isPresent()) {
                            onBodyLog("[SKILL_REPAIR] step " + (index + 1) + " of " + skill + " kept failing; trying "
                                    + name + " with that step done another way. The better of the two stays.");
                            saveMemoryNow();
                        }
                    }));
                } catch (Exception e) {
                    System.err.println("[MineAgent] Skill repair failed: " + e.getMessage());
                } finally {
                    repairsInFlight.remove(base);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            repairsInFlight.remove(base);
        }
    }

    /** The planner took a new plan (todowrite): four steps or more get a second read (MA4.1). */
    public void onPlanAccepted(String goal) {
        int steps = planner.exportState().nodes().size();
        if (planReview.onPlan(goal, steps)) reviewPlan(goal, "a new plan of " + steps + " steps");
    }

    /**
     * One smh/review call in a fresh conversation (MA4.1): the goal, the plan's steps in order, the situation brief.
     * A fix goes back as a replan; nothing on the calm call rate.
     */
    private void reviewPlan(String goal, String why) {
        if ("calm".equals(agentSettings.callRate())) return;
        List<String> steps = new ArrayList<>();
        for (var node : planner.exportState().nodes()) steps.add(node.description());
        if (steps.isEmpty()) return;
        var request = com.mineagent.engine.planning.PlanReview.request(baseUrl, apiKey, model, temperature,
                goal, steps, situationBrief(), why);
        try {
            talkExecutor.submit(() -> {
                try {
                    com.mineagent.engine.planning.PlanReview.fix(replyContent(sideCall(request))).ifPresent(fix ->
                            Services.platform().scheduleOnServer(() -> {
                                if (enqueueInbox("[REVIEW] A second read of the plan for \"" + truncateForLog(goal, 120)
                                        + "\" found a problem: " + fix + " Repair the plan.")) wake("rolling_replan");
                            }));
                } catch (Exception e) {
                    System.err.println("[MineAgent] Plan review failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private volatile long lastSmhHintMs;

    /**
     * A failed call, told to the owner: in plain words when it is SMH that failed (not running, no model, a key it
     * refused, its game endpoint off; llm/SmhHint), at most once a minute; else the provider's own message.
     */
    private void sayCallFailure(String fallback, RuntimeException e, String msg) {
        Integer status = e instanceof com.mineagent.api.llm.provider.LLMProviderException p ? p.statusCode() : null;
        String hint = com.mineagent.engine.llm.SmhHint.forFailure(baseUrl, status, msg, e.getCause() != null ? e.getCause() : e);
        if (hint == null) {
            speakToOwner(fallback);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSmhHintMs < 60_000L) return;
        lastSmhHintMs = now;
        speakToOwner(hint);
    }

    /** Hostiles within 16 blocks, nearest first, read on the server thread (the brief is built on the model's). */
    private void noteHostiles(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        List<com.mineagent.engine.planning.SituationBrief.Hostile> found = new ArrayList<>();
        for (var mob : player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(16.0), m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy)) {
            int d = (int) Math.round(mob.distanceTo(player));
            if (d > 16) continue;
            found.add(new com.mineagent.engine.planning.SituationBrief.Hostile(
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath(), d));
        }
        found.sort(Comparator.comparingInt(com.mineagent.engine.planning.SituationBrief.Hostile::distance));
        nearbyHostiles = List.copyOf(found);
        guardLine = guarding() ? guardState() : "";
    }

    /**
     * The situation brief (MA4.2), at most 600 characters, built in code: what it is doing, the long goal and where it
     * stands, hostiles within 16 blocks, the last three lessons that match. Never sent to a stranger.
     */
    public String situationBrief() {
        try {
            return briefNow();
        } catch (RuntimeException e) {
            // Never the reason a call fails: the brief is a help, not a need.
            return com.mineagent.engine.planning.SituationBrief.build(null, null, nearbyHostiles, List.of());
        }
    }

    private String briefNow() {
        String doing;
        if (skillRuntime.active()) doing = "running the skill " + skillRuntime.snapshot().skillName();
        else if (curriculum.current() != null) doing = "practising: " + curriculum.current();
        else {
            LiveBodyState body = liveBodyState.get();
            String goal = planner.exportState().goal();
            doing = body != null && body.taskId() != null ? body.taskName()
                    : goal != null && !goal.isBlank() ? goal : guarding() ? "guarding " + guardLine : "nothing";
        }
        String goalLine = goalBook.active().map(g -> com.mineagent.engine.planning.GoalBook.line(g, null)).orElse(null);
        List<String> lessons = reflection.matchingLessons(activeOwnerGoal, 3);
        return com.mineagent.engine.planning.SituationBrief.build(doing, goalLine, nearbyHostiles, lessons);
    }

    /** Health under the flee line, then back to three quarters: worth a word (MA3.3). */
    private void watchCloseCalls(net.minecraft.server.level.ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) {
            closeCall = false;
            return;
        }
        double flee = MineAgentEngine.getConfig().survival().healthFlee();
        if (player.getHealth() <= flee) {
            if (!closeCall) progress.closeCall(currentGameTickSafe());
            closeCall = true;
        }
        else if (closeCall && player.getHealth() >= player.getMaxHealth() * 0.75) {
            closeCall = false;
            sayMilestone(com.mineagent.engine.chat.MilestoneTalk.Kind.RECOVERED, "you were nearly killed a moment ago and have healed back up");
        }
    }

    /** One line to the owner in its own voice, at most one of each kind a minute; none on the calm call rate. */
    private void sayMilestone(com.mineagent.engine.chat.MilestoneTalk.Kind kind, String what) {
        if (!milestoneTalk.take(kind, System.currentTimeMillis())) return;
        if ("calm".equals(agentSettings.callRate())) return;
        var request = com.mineagent.engine.chat.MilestoneTalk.request(baseUrl, apiKey, model, temperature,
                companion.companionName(), agentSettings.persona(), what, situationBrief());
        try {
            talkExecutor.submit(() -> {
                try {
                    String line = com.mineagent.engine.chat.MilestoneTalk.sentence(replyContent(sideCall(request)));
                    if (line != null) Services.platform().scheduleOnServer(() -> speakToOwner(line));
                } catch (Exception e) {
                    System.err.println("[MineAgent] Milestone line failed: " + e.getMessage());
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down
        }
    }

    /**
     * Long unattended runs: when the body has sat idle for 45 s with a long goal active (a step failed, or the turn
     * ended without one), it is given the goal again, at most every 90 s, counted as a step (so a goal that is not
     * getting there pauses). With no goal, a standing stock order it is short of is topped up, at most every 5 min.
     */
    private void goalHeartbeat(net.minecraft.server.level.ServerPlayer player) {
        if (player == null || suspended || inProgress || goalStepInFlight.get() || guarding()) return;
        if (afkShelterStarted && afkSheltering()) return; // walled in until they're back
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        var active = goalBook.active();
        if (active.isEmpty() && goalBook.stock().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastOwnerMessageTime < 30_000L || now - lastGoalStepMs < 45_000L) return;
        // SMH just said it could not be reached (or had no model): waiting spends no steps of the goal.
        if (now - lastSmhHintMs < 300_000L) return;
        if (skillRuntime.active() || curriculum.current() != null) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null) return;
        synchronized (inboxLock) {
            if (!inbox.isEmpty()) return;
        }
        if (player.getHealth() <= MineAgentEngine.getConfig().survival().healthFlee()) return;
        if (active.isPresent() && orderIn(active.get().text(), companion.companionName()).isPresent()) return; // code runs it
        if (active.isPresent()) {
            if (now - lastGoalNudgeMs < 90_000L) return;
            lastGoalNudgeMs = now;
            var g = active.get();
            var stepped = goalBook.stepping(g.lastNext().isBlank() ? "work on the goal" : g.lastNext(), null, false);
            saveMemoryNow();
            if (stepped.isEmpty()) {
                speakToOwner("Goal " + g.id() + " isn't getting anywhere, so I've paused it. Say \"carry on\" to try again.");
                activeOwnerGoal = "";
                return;
            }
            onSelfGoal(goalPrompt(stepped.get(), stepped.get().lastNext()));
            return;
        }
        if (now - lastStockNudgeMs < 300_000L) return;
        Map<String, Integer> have = carried(player);
        var due = goalBook.dueStock(i -> have.getOrDefault(i, 0));
        if (due.isEmpty()) return;
        lastStockNudgeMs = now;
        var st = due.get().getKey();
        onSelfGoal("Standing order: keep at least " + st.count() + " " + st.item().replace('_', ' ') + " on you; you have "
                + due.get().getValue() + ". Get more (gather, craft or take from your own chests), then stop.");
    }

    /**
     * A death teaches (SMH plan 69, MA3.9): a lesson about what killed it, a note on the goal it was on, and, when
     * its things are lying there to be had (not burnt in lava, not in the void, keepInventory off, not hardcore), a
     * goal to go back for them, ahead of the goal it was on, which comes back after.
     */
    public void onDied(net.minecraft.server.level.ServerPlayer body, boolean hardcore) {
        if (body == null) return;
        try {
            long tick = currentGameTickSafe();
            progress.died(tick);
            String cause = body.getCombatTracker().getDeathMessage().getString();
            var source = body.getLastDamageSource();
            String type = source == null ? "" : source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
            var pos = body.blockPosition();
            journal.record("death", cause + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), tick,
                    body.serverLevel().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
            // Three deaths here in 15 minutes: a place to keep away from (MA8.6).
            var zone = com.mineagent.engine.learning.DangerZones.of(body.getUUID()).died(
                    body.serverLevel().dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(), body.level().getGameTime());
            zone.ifPresent(z -> {
                journal.record("danger", "died three times near " + z.x() + ", " + z.y() + ", " + z.z() + " in 15 minutes: keeping away",
                        tick, z.dim(), z.x(), z.y(), z.z());
                speakToOwner("I've died three times near " + z.x() + ", " + z.y() + ", " + z.z()
                        + " in 15 minutes. I'll keep away from there for a few days, and my paths go round it.");
            });
            if (goalBook.active().isPresent()) {
                goalBook.milestone(com.mineagent.engine.learning.DeathNote.note(cause, pos.getX(), pos.getY(), pos.getZ()), tick);
            }
            boolean keep = body.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
            // The inventory has already been dropped by now: what matters is whether its things lie here.
            boolean dropped = !body.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    body.getBoundingBox().inflate(4.0)).isEmpty();
            if (com.mineagent.engine.learning.DeathNote.recoverable(type, keep, hardcore, dropped)) {
                var g = goalBook.urgent(com.mineagent.engine.learning.DeathNote.recoveryGoal(pos.getX(), pos.getY(), pos.getZ(),
                        body.level().dimension().location().toString()), "death", tick,
                        com.mineagent.engine.learning.DeathNote.RECOVER_PREFIX);
                notifyRecovery(g.id(), pos);
            }
            saveMemoryNow();
            var episode = new com.mineagent.engine.learning.Scorecard.Episode("survive",
                    com.mineagent.engine.learning.Scorecard.Outcome.FAILED, true, 0L, body.getMaxHealth(), body.getMaxHealth());
            writeLesson("staying alive (" + truncateForLog(cause, 120) + ")", episode, 0.0);
        } catch (RuntimeException e) {
            System.err.println("[MineAgent] Death lesson failed: " + e.getMessage());
        }
    }

    private void notifyRecovery(String goalId, net.minecraft.core.BlockPos pos) {
        speakToOwner("When I'm back, I'll go for my things at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " (goal " + goalId + "), then back to what I was doing.");
    }

    /** What it carries, by item id without the namespace, with any log and any planks counted as families too. */
    private static Map<String, Integer> carried(net.minecraft.server.level.ServerPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.merge(id, stack.getCount(), Integer::sum);
            if (id.endsWith("_log") || id.endsWith("_stem")) counts.merge(com.mineagent.engine.planning.GoalBook.LOGS, stack.getCount(), Integer::sum);
            if (id.endsWith("_planks")) counts.merge(com.mineagent.engine.planning.GoalBook.PLANKS, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static boolean isItemId(String id) {
        if (id == null || !id.matches("[a-z0-9_]{1,64}")) return false;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
    }

    /** Stop now: the turn, the inbox, the task on the body and any practice. Reflexes stay. */
    private void haltNow() {
        curriculum.abandon();
        cancel();
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null) {
            var args = new com.google.gson.JsonObject();
            args.addProperty("task_id", body.taskId());
            runDirect("task_stop", args);
        }
    }

    /** A tool run by code (a plain command), through the same dispatch and audit as a skill's steps. */
    private void runDirect(String tool, com.google.gson.JsonObject args) {
        try {
            dispatchSkillAction("cmd-" + UUID.randomUUID(), tool, args);
        } catch (RuntimeException e) {
            System.err.println("[MineAgent] Command " + tool + " failed: " + e.getMessage());
        }
    }

    /** "status": what it is doing, from the facts, never the model's account. */
    private String statusLine() {
        String goal = planner.exportState().goal();
        String practising = curriculum.current();
        LiveBodyState task = liveBodyState.get();
        String doing = task != null && task.taskId() != null && task.snapshot() != null ? task.snapshot().summary() : null;
        var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
        int certified = 0;
        for (SkillLibrary.Skill skill : skillLib.exportAll()) {
            if (scorecard.tier("skill:" + skill.name()).compareTo(com.mineagent.engine.learning.Scorecard.Tier.CERTIFIED) >= 0) certified++;
        }
        StringBuilder sb = new StringBuilder(agentSettings.workMode().id()).append(" mode");
        goalBook.active().ifPresent(g -> sb.append(", long goal ").append(g.id()).append(": ").append(truncateForLog(g.text(), 60)));
        if (doing != null) sb.append(", ").append(truncateForLog(doing, 120));
        else if (goal != null && !goal.isBlank()) sb.append(", working on: ").append(truncateForLog(goal, 80));
        else if (practising != null) sb.append(", practising: ").append(truncateForLog(practising, 80));
        else sb.append(", nothing on");
        if (self != null) {
            sb.append(", health ").append(Math.round(self.getHealth())).append("/").append(Math.round(self.getMaxHealth()))
              .append(", food ").append(self.getFoodData().getFoodLevel()).append("/20");
        }
        return sb.append(", ").append(skillLib.size()).append(" skills (").append(certified).append(" certified).").toString();
    }

    /** Its guard duty, to keep across a leave and a return (SMH plan 70, MA5.9). */
    public net.minecraft.nbt.CompoundTag exportDuty() {
        var tag = new net.minecraft.nbt.CompoundTag();
        var post = guardPost;
        if (post != null) {
            tag.putDouble("px", post.x);
            tag.putDouble("py", post.y);
            tag.putDouble("pz", post.z);
            var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
            if (self != null) tag.putString("dim", self.level().dimension().location().toString());
        }
        UUID ward = guardWard;
        if (ward != null) tag.putUUID("ward", ward);
        tag.putDouble("radius", guardRadius);
        return tag;
    }

    /** The guard duty it had when it left; a post in another dimension than where it is now is dropped. */
    public void importDuty(net.minecraft.nbt.CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return;
        net.minecraft.world.phys.Vec3 post = null;
        if (tag.contains("px")) {
            var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
            String here = self == null ? "" : self.level().dimension().location().toString();
            if (!tag.contains("dim") || tag.getString("dim").equals(here)) {
                post = new net.minecraft.world.phys.Vec3(tag.getDouble("px"), tag.getDouble("py"), tag.getDouble("pz"));
            }
        }
        UUID ward = tag.hasUUID("ward") ? tag.getUUID("ward") : null;
        if (post != null || ward != null) setGuard(post, ward, tag.contains("radius") ? tag.getDouble("radius") : 12.0);
    }

    /** The one line it says when its owner is back in the world: where it stands and what it carries on with. */
    /** The owner left the game: noted, so "what happened while I was away" starts here. */
    public void noteLeaving() {
        journal.record("left", "you left the game", currentGameTickSafe());
    }

    public void sayBack() {
        journal.record("back", "you came back into the game", currentGameTickSafe());
        StringBuilder line = new StringBuilder("Back. ");
        var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
        if (self != null) {
            var at = self.blockPosition();
            line.append("I'm at ").append(at.getX()).append(" ").append(at.getY()).append(" ").append(at.getZ()).append(". ");
        }
        String guard = guardState();
        if (!guard.isEmpty()) line.append("Still guarding ").append(guard).append(". ");
        goalBook.active().ifPresent(g -> line.append("Carrying on with: ").append(truncateForLog(g.text(), 80)).append("."));
        speakToOwner(line.toString().strip());
    }

    /** Who decides its work (settings/WorkMode): the owner's alone to change. */
    public void changeWorkMode(String mode) {
        var next = com.mineagent.engine.settings.WorkMode.of(mode);
        boolean wasParked = agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED;
        setAgentSettings(agentSettings.withMode(next.id()));
        MineAgentEngine.persistCompanionNow(companion.companionId());
        UUID id = companion.companionId();
        if (next != com.mineagent.engine.settings.WorkMode.AUTO) curriculum.abandon();
        switch (next) {
            case ESCORT -> MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FOLLOW);
            case PARKED -> {
                haltNow();
                MineAgentEngine.setCompanionMode(id, MineAgentEngine.CompanionMode.FREE);
            }
            default -> { }
        }
        speakToOwner(switch (next) {
            case SERVANT -> "Servant mode: I'll work on what you ask, and nothing of my own.";
            case AUTO -> "Auto mode: I'll find my own work and practise. Tell me anything and I'll drop it for you.";
            case ESCORT -> "Escort mode: I'm sticking with you and watching your back.";
            case PARKED -> "Parked: I'll hold here and only talk.";
        });
        // Out of parked mode, back to the long goal it was on.
        if (wasParked && next != com.mineagent.engine.settings.WorkMode.PARKED) {
            goalBook.active().ifPresent(g -> onSelfGoal(goalPrompt(g, null)));
        }
    }

    /** A call on the side (talk, curriculum): its own request; the loop's provider session is put back after. */
    private com.mineagent.api.llm.LLMResponse sideCall(com.mineagent.api.llm.LLMRequest request) {
        LLMProvider p = resolveProvider();
        if (p == null) return null;
        // Through SMH (smh/auto: stateless chat completions, queued by role there) no session needs guarding, so a
        // side call never waits for the loop's own turn to finish on this side.
        progress.modelCall(currentGameTickSafe());
        if (com.mineagent.engine.llm.RoleRouting.AUTO.equals(model)) return p.complete(request);
        synchronized (p) {
            var before = p.sessionState(model);
            try {
                return p.complete(request);
            } finally {
                p.restoreSessionState(before);
            }
        }
    }

    private static String replyContent(com.mineagent.api.llm.LLMResponse r) {
        return r == null || r.choice() == null || r.choice().message() == null ? null : r.choice().message().content();
    }

    public void onServerStateTick(long gameTick) {
        publishWorldSnapshot(gameTick, false);
        try {
            actionLedger.expire(gameTick, 1_200L, semanticWorldModel);
            reservationLedger.expire(gameTick);
            if (gameTick % 200L == 0L) layeredMemory.consolidate(gameTick);
            var player = TaskContext.serverPlayer(companion);
            var owner = companion instanceof com.mineagent.engine.entity.CompanionEntity entity
                    ? entity.serverPlayerOwner() : null;
            RealtimeCognition.TickResult result = realtimeCognition.tick(
                    player, owner, toTaskObservation(liveBodyState.get()), gameTick);
            semanticWorldModel.observeSituation(realtimeCognition.currentFrame());
            // Evaluate strategic acceptance only after the authoritative live
            // snapshot has refreshed inventory, position, actors and task
            // facts. This prevents a completed tactical suffix from being
            // mistaken for completion of the owner's actual objective.
            boolean goalVerified = planner.verifyGoal(semanticWorldModel, gameTick);
            measureEpisodes(player, goalVerified, gameTick);
            practice(player, goalVerified, gameTick);
            if (gameTick % 20L == 0L) orderHeartbeat(player);
            if (gameTick % 100L == 0L) nightCare(player);
            if (gameTick % 20L == 0L) lookAfterOwner(player);
            if (gameTick % 20L == 0L) notePlayers(player);
            if (gameTick % 100L == 0L) guardWatch(player);
            if (gameTick % 40L == 0L) partner(player);
            if (gameTick % 200L == 0L) watchProgress(player);
            if (gameTick % 200L == 0L) syncProtected();
            if (gameTick % 100L == 0L) goldInTheNether(player);
            if (gameTick % 100L == 0L) schedule(player);
            if (gameTick % 100L == 0L) stayNearHome(player);
            if (gameTick % 200L == 0L) fillHolesWhenIdle(player);
            // Its own pursuits in free time: not straight after it arrives, nor while its owner may be about to say
            // what they want (what it would make might be what they need).
            boolean ownTime = freeTime(gameTick);
            if (ownTime && gameTick % 1200L == 600L) gearGoal(player);
            if (ownTime && gameTick % 1200L == 900L) kitGoal(player);
            if (ownTime && gameTick % 1200L == 1100L) godGearGoal(player);
            if (ownTime && gameTick % 2400L == 1500L) masterTrading(player);
            if (ownTime && gameTick % (onItsOwn ? 600L : 2400L) == 300L) hoard(player);
            if (gameTick % 200L == 100L) surviveOnItsOwn(player);
            for (Runnable afterRun; (afterRun = afterReady.poll()) != null; ) afterRun.run();
            if (gameTick % 20L == 0L) asking.tick(gameTick).ifPresent(st -> {
                journal.record("assumed", st.key() + ": " + (st.yes() ? "yes" : "no"), gameTick);
                speakToOwner("No answer, so I'm taking that as a " + (st.yes() ? "yes" : "no") + " for now.");
            });
            goalHeartbeat(player);
            watchCloseCalls(player);
            if (gameTick % 20L == 0L) noteHostiles(player);
            doorKeeper.tick(player, gameTick);
            watchCrossing(player, gameTick);
            if (com.mineagent.engine.settings.Switches.routeMemory()) placeKeeper.tick(player, gameTick).ifPresent(place -> {
                journal.record("find", "found " + aOrAn(place.kind()) + " (" + place.name() + ") at " + place.at().x() + ", "
                        + place.at().y() + ", " + place.at().z(), gameTick, place.at().dim(), place.at().x(), place.at().y(), place.at().z());
                speakToOwner("Found " + aOrAn(place.kind()) + ". I'll remember it as " + place.name() + ".");
                saveMemoryNow();
            });
            watchOwnerActivity(owner);
            watchAfkOrder();
            keepNearAfkOwner(player, owner, gameTick);
            if (result.ownerIntent() != null) {
                var signal = result.ownerIntent();
                theoryOfMind.observeIntent(signal.intent(), signal.confidence(),
                        signal.evidence(), signal.gameTick());
            }
            // Skill and experiment transitions run on the authoritative server
            // thread. Each transition is bounded to one step per tick.
            skillRuntime.tick(gameTick);
            mechanismExplorer.tick(gameTick);
            if (skillRuntime.active()) {
                SkillRuntime.Snapshot skill = skillRuntime.snapshot();
                rollingPlanner.onTaskProgress(skill.runId(),
                        TaskSnapshot.progress("skill", skill.lastEvidence(),
                                skill.stepIndex(), skill.stepCount(),
                                null, null, null, null, skill.lastEvidence(),
                                skill.stepIndex()), gameTick);
            }
            HierarchicalRollingPlanner.ReplanSignal replan =
                    skillRuntime.active() ? null : rollingPlanner.tick(gameTick);
            if (replan != null && replan.reason() == HierarchicalRollingPlanner.Reason.REPEATED_FAILURE
                    && planReview.onRepeatedFailure(replan.goal())) {
                reviewPlan(replan.goal(), "it has failed the same way twice: " + truncateForLog(replan.detail(), 160));
            }
            // Tactical cognition and the rolling planner can identify the same
            // blocked executor frame in one tick. Prefer the structured rolling
            // signal and admit only one paid deliberation event.
            String deliberation = replan != null
                    ? replan.event() : result.deliberationEvent();
            if (goalVerified && goalBook.active().isPresent() && curriculum.current() == null) {
                // A long goal is under way: the goal loop decides what comes next (MA3.2), not a fresh turn.
                goalStep(planner.exportState().goal(), player);
                deliberation = null;
            } else if (goalVerified) {
                deliberation = "[GOAL_VERIFIED] goal="
                        + truncateForLog(planner.exportState().goal(), 240)
                        + "; all required milestones and acceptance conditions are satisfied";
            }
            if (deliberation != null && enqueueInbox(deliberation)) {
                wake(goalVerified ? "goal_verified"
                        : replan != null ? "rolling_replan" : "cognition_decision");
            }
        } catch (Throwable failure) {
            // Entity teardown can race the final engine tick. Cognitive
            // observation must never take down the server or the body loop.
            System.err.println("[MineAgent] Realtime cognition failed for "
                    + companion.companionName() + ": " + failure.getMessage());
        }
    }

    private static SituationSnapshot.TaskObservation toTaskObservation(LiveBodyState body) {
        if (body == null || body.taskId() == null) {
            return SituationSnapshot.TaskObservation.idle();
        }
        TaskSnapshot snapshot = body.snapshot();
        return new SituationSnapshot.TaskObservation(body.taskId(), body.taskName(),
                body.state(), snapshot == null ? "running" : snapshot.stage(),
                snapshot == null ? null : snapshot.blockedReason(), body.gameTick());
    }

    private static String taskTarget(TaskSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasTarget()) return null;
        return snapshot.targetX() + "," + snapshot.targetY() + "," + snapshot.targetZ();
    }

    private static boolean isDeliberationEvent(String narrative) {
        String value = narrative.stripLeading();
        return value.startsWith("[TASK_FINISHED]")
                || value.startsWith("[SKILL_FINISHED]")
                || value.startsWith("[GOAL_VERIFIED]")
                || value.startsWith("[ROLLING_REPLAN]")
                || value.startsWith("[COGNITION_DECISION]")
                || value.startsWith("[TEAM_SUPPORT]");
    }

    private long currentGameTickSafe() {
        long published = liveAssetGameTick.get();
        if (published > 0L) return published;
        try {
            return TaskContext.serverPlayer(companion).level().getGameTime();
        } catch (Throwable ignored) {
            // A monotonic local fallback preserves ordering during spawn before
            // the first world snapshot; it is never mixed with epoch time.
            return Math.max(0L, eventGeneration.get());
        }
    }

    private void publishWorldSnapshot(long gameTick, boolean force) {
        if (!force && lastAssetSnapshotTick != Long.MIN_VALUE
                && gameTick - lastAssetSnapshotTick < 20L) return;
        try {
            var sp = TaskContext.serverPlayer(companion);
            WorldAssetIndex.Position position = WorldAssetObserver.position(sp);
            List<WorldAssetIndex.ItemObservation> inventory =
                    WorldAssetObserver.inventory(sp);
            worldAssetIndex.observeInventory(position, inventory, gameTick);
            semanticWorldModel.observeInventory(position, inventory, gameTick, planner.watchedInventoryItems());
            semanticWorldModel.observeAssets(worldAssetIndex.snapshot(), gameTick);
            liveAssetPosition.set(position);
            liveAssetGameTick.set(Math.max(0L, gameTick));
            lastAssetSnapshotTick = gameTick;
        } catch (Throwable failure) {
            // Companion construction/teardown can race a final tick. Missing
            // one snapshot is preferable to crashing the shared server loop.
            System.err.println("[MineAgent] Asset snapshot failed for "
                    + companion.companionName() + ": " + failure.getMessage());
        }
    }

    /** Safe callback entry: argument evaluation cannot escape during teardown. */
    private void publishWorldSnapshotNow(boolean force) {
        long gameTick = liveAssetGameTick.get();
        try {
            gameTick = TaskContext.serverPlayer(companion).level().getGameTime();
        } catch (Throwable ignored) {
            // publishWorldSnapshot performs the guarded body lookup and emits
            // one diagnostic if teardown has already detached the player.
        }
        publishWorldSnapshot(gameTick, force);
    }

    /**
     * Parse a body log narrative for place events and record them
     * in the PlaceEventMemory. Detects patterns like:
     * "saw iron_ore at (10, 64, -5)" or "found cow near 10,64,-5"
     */
    private void recordPlaceEventFromLog(String narrative) {
        if (narrative == null) return;
        // Look for coordinate patterns: (x, y, z) or x,y,z
        java.util.regex.Matcher m = COORD_PATTERN.matcher(narrative);
        if (m.find()) {
            try {
                int x = Integer.parseInt(m.group(1));
                int y = Integer.parseInt(m.group(2));
                int z = Integer.parseInt(m.group(3));
                // Determine subject and type from keywords
                String subject = "unknown";
                String type = "event";
                String lower = narrative.toLowerCase();
                if (lower.contains("iron_ore") || lower.contains("铁矿")) { subject = "iron_ore"; type = "resource"; }
                else if (lower.contains("coal") || lower.contains("煤")) { subject = "coal_ore"; type = "resource"; }
                else if (lower.contains("diamond") || lower.contains("钻石")) { subject = "diamond_ore"; type = "resource"; }
                else if (lower.contains("gold") || lower.contains("金矿")) { subject = "gold_ore"; type = "resource"; }
                else if (lower.contains("cow") || lower.contains("牛")) { subject = "cow"; type = "entity"; }
                else if (lower.contains("sheep") || lower.contains("羊")) { subject = "sheep"; type = "entity"; }
                else if (lower.contains("village") || lower.contains("村庄")) { subject = "village"; type = "structure"; }
                else if (lower.contains("creeper") || lower.contains("苦力怕")) { subject = "creeper"; type = "danger"; }
                else if (lower.contains("zombie") || lower.contains("僵尸")) { subject = "zombie"; type = "danger"; }
                else if (lower.contains("tree") || lower.contains("树")) { subject = "tree"; type = "resource"; }

                String dimension = companion.dimensionKey();
                placeMemory.remember(type, subject, x, y, z, dimension,
                        System.currentTimeMillis(), narrative);

                // Also record in the cognitive map (spatial POI store)
                // with a semantic category for prompt-level recall.
                String category;
                String label;
                switch (type) {
                    case "resource" -> {
                        category = "resource:" + subject;
                        label = subject + " (resource)";
                    }
                    case "structure" -> {
                        category = "structure:" + subject;
                        label = subject;
                    }
                    case "danger" -> {
                        category = "hazard:" + subject;
                        label = subject + " (hazard)";
                    }
                    case "entity" -> {
                        category = "entity:" + subject;
                        label = subject;
                    }
                    default -> {
                        category = "event:" + subject;
                        label = subject;
                    }
                }
                cognitiveMap.recordPoi(new net.minecraft.core.BlockPos(x, y, z),
                        category, label, dimension, System.currentTimeMillis());
            } catch (NumberFormatException ignored) {
                // Coordinate parse failed — skip
            }
        }
    }

    private static final java.util.regex.Pattern COORD_PATTERN =
            java.util.regex.Pattern.compile("\\(?(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\)?");

    /** Replace characters that are illegal in file paths (Windows/Unix). */
    private static String sanitizeForPath(String name) {
        if (name == null || name.isBlank()) return "unknown";
        return name.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
    }

    /** Stable identity used solely for provider transport/session isolation. */
    private static String stableConversationIdentity(AgentPlayer player) {
        String owner = player == null || player.ownerUuid() == null
                ? "owner" : player.ownerUuid().toString();
        String name = player == null ? "companion" : sanitizeForPath(player.companionName());
        return "mineagent:" + owner + ":" + name;
    }

    /**
     * Migrate memory storage to a new companion name (called on rename).
     * Keeps the companion's memories across renames.
     */
    public void migrateMemoryStorage(String newName) {
        java.nio.file.Path worldDir = MineAgentEngine.getWorldDataDir();
        if (persistence != null && worldDir != null) {
            String newKey = companion.ownerUuid() + "_" + sanitizeForPath(newName);
            persistence.migrateTo(worldDir.resolve("mineagent_memory").resolve(newKey));
        }
    }

    /**
     * Cancel the current turn and clear the inbox.
     */
    public synchronized void cancel() {
        synchronized (inboxLock) {
            inbox.clear();
        }
        // Invalidate the response currently being generated and any tool call
        // that is still queued for the server thread. An action that has already
        // entered vanilla code cannot be rolled back generically, but no stale
        // response is allowed to start new work after this point.
        eventGeneration.incrementAndGet();
        Thread activeCall = activeLLMCallThread.get();
        if (activeCall != null) activeCall.interrupt();
    }

    /** Suspend new turns while preserving events that arrive during the pause. */
    public synchronized void pause() {
        suspended = true;
        cancel();
    }

    /** Resume from a lifecycle pause and process the accumulated state once. */
    public synchronized void resume(String reason) {
        if (!suspended) {
            wake(reason);
            return;
        }
        suspended = false;
        wake(reason);
    }

    /** Is a turn currently in progress? */
    public boolean isInProgress() { return inProgress; }

    // ── Getters for persistence ──

    public String getProviderId() { return providerId; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public String getBaseUrl() { return baseUrl; }
    public double getTemperature() { return temperature; }
    public com.mineagent.engine.settings.AgentSettings getAgentSettings() { return agentSettings; }
    public com.mineagent.engine.learning.Scorecard scorecard() { return scorecard; }

    /** The duty combat works within (combat/CombatBrain.Duty): guard, escort, working; null anchor: where it stands. */
    public record CombatSetup(com.mineagent.engine.combat.CombatBrain.Duty duty, net.minecraft.world.phys.Vec3 anchor,
                              UUID ward, double perimeter) {}

    public CombatSetup combatSetup(net.minecraft.server.level.ServerPlayer self, net.minecraft.server.level.ServerPlayer owner) {
        // Sent far from its post on an errand, it fights as it would at work; it walks back when the errand is done.
        double errand = guardRadius + 32.0;
        UUID ward = guardWard;
        if (ward != null) {
            var entity = self.serverLevel().getEntity(ward);
            if (entity != null && entity.isAlive() && entity.distanceTo(self) <= errand) {
                return new CombatSetup(com.mineagent.engine.combat.CombatBrain.Duty.GUARD, entity.position(), ward, guardRadius);
            }
        }
        var post = guardPost;
        if (post != null && post.distanceTo(self.position()) <= errand) {
            return new CombatSetup(com.mineagent.engine.combat.CombatBrain.Duty.GUARD, post, null, guardRadius);
        }
        // An AFK owner who is exposed is guarded, as on "guard me" (SMH plan 70, MA5.4); fishing, close by. At a
        // mob farm's hoppers, exposed or not, only what comes right at them: the farm's own mobs are theirs.
        // "Going AFK, make sure I don't die": guarded wherever they are, while they're away.
        boolean keepThemSafe = afkOrder.guardMe();
        if (owner != null && (com.mineagent.engine.settings.Switches.afkGuard() || keepThemSafe)
                && (ownerActivity.afk() || (keepThemSafe && ownerActivity.awaitingAfk()))
                && owner.isAlive() && owner.serverLevel() == self.serverLevel()
                && (exposed(owner) || ownerAtFarm || keepThemSafe)) {
            double radius = owner.fishing != null ? 6.0 : ownerAtFarm ? 4.0 : 12.0;
            return new CombatSetup(com.mineagent.engine.combat.CombatBrain.Duty.GUARD, owner.position(), owner.getUUID(), radius);
        }
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.ESCORT && owner != null) {
            return new CombatSetup(com.mineagent.engine.combat.CombatBrain.Duty.ESCORT, owner.position(), owner.getUUID(), 12.0);
        }
        LiveBodyState body = liveBodyState.get();
        boolean working = (body != null && body.taskId() != null) || skillRuntime.active();
        // In a farm its owner is AFK at, the farm's mobs are theirs: only what comes right at it (MA5.4).
        boolean inAfkFarm = com.mineagent.engine.world.AfkFarms.protects(self.serverLevel().dimension().location().toString(),
                self.getBlockX(), self.getBlockY(), self.getBlockZ());
        return new CombatSetup(working ? com.mineagent.engine.combat.CombatBrain.Duty.WORKING : com.mineagent.engine.combat.CombatBrain.Duty.IDLE,
                null, owner != null ? owner.getUUID() : null, inAfkFarm ? 3.0 : working ? 6.0 : 10.0);
    }

    public boolean guarding() {
        return guardPost != null || guardWard != null;
    }

    /** What it guards, as a person reads it ("the spot at 10 64 -5", a player's name), or "" when not guarding. */
    public String guardState() {
        UUID ward = guardWard;
        var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
        if (ward != null) {
            var p = self == null ? null : self.serverLevel().getServer().getPlayerList().getPlayer(ward);
            return p != null ? p.getName().getString() : "a player who is away";
        }
        var post = guardPost;
        if (post != null) return "the spot at " + Math.round(post.x) + " " + Math.round(post.y) + " " + Math.round(post.z);
        return "";
    }

    public int guardRadiusBlocks() {
        return (int) Math.round(guardRadius);
    }

    /** One of the owner's plain commands given another way (the settings panel): as if said in chat by the owner. */
    public boolean ownerCommand(com.mineagent.engine.chat.CommandParser.Command c) {
        return c != null && runCommand(c, true);
    }

    /** "guard here" (a spot) or "guard Alex" (a player), {@code radius} blocks around; null for both stands down. */
    public void setGuard(net.minecraft.world.phys.Vec3 post, UUID ward, double radius) {
        guardPost = post;
        guardWard = ward;
        guardRadius = Math.max(4.0, Math.min(32.0, radius));
        if (post != null || ward != null) curriculum.abandon();
        // A new watch: the perimeter lit again, what's there counted afresh.
        litNight = -1;
        guardBlocks = null;
        visitorsSaid.clear();
    }

    /**
     * The orders that get what a brew lacks, by the production planner: its ingredient, blaze powder for the stand's
     * fuel, and bottles to fill (water it fills them at itself); "" when it has them all. Nether wart comes from its
     * chests if it has some stored, else the brew says it has none.
     */
    private String brewNeeds(String potion) {
        if (!(companion instanceof com.mineagent.engine.entity.CompanionEntity self) || self.serverPlayer() == null) return "";
        var sp = self.serverPlayer();
        var ingredient = com.mineagent.engine.task.BrewTask.INGREDIENT.get(com.mineagent.engine.task.BrewTask.base(potion));
        if (ingredient == null) return "";
        List<String> make = new ArrayList<>();
        java.util.function.ToIntFunction<net.minecraft.world.item.Item> has = item -> com.mineagent.engine.task.ProductionTask.count(sp, item);
        if (potion.startsWith("splash_") && has.applyAsInt(net.minecraft.world.item.Items.GUNPOWDER) == 0) make.add("make me 1 gunpowder");
        boolean powderToo = ingredient == net.minecraft.world.item.Items.BLAZE_POWDER; // the ingredient and the fuel: one order for both
        if (!powderToo && has.applyAsInt(ingredient) == 0) {
            make.add("make me 1 " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient).getPath().replace('_', ' '));
        }
        if (has.applyAsInt(net.minecraft.world.item.Items.BLAZE_POWDER) < (powderToo ? 2 : 1)) make.add("make me " + (powderToo ? 2 : 1) + " blaze powder");
        int water = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            var pc = st.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (st.is(net.minecraft.world.item.Items.POTION) && pc != null && pc.is(net.minecraft.world.item.alchemy.Potions.WATER)) water += st.getCount();
        }
        int bottles = has.applyAsInt(net.minecraft.world.item.Items.GLASS_BOTTLE);
        if (water + bottles < 3) make.add("make me " + (3 - water) + " glass bottles");
        return String.join(", then ", make);
    }

    /**
     * What the villagers near pay an emerald for (a farmer's crops, a fletcher's sticks), a trade's worth at a time; a
     * farmer's four crops when none is near: when it carries enough of none, the one it can get soonest from what it has
     * seen (its fields first, ripe), as an order ("make me 20 wheat"); "" when it has enough to sell or can get none.
     */
    private String cropToSell() {
        if (!(companion instanceof com.mineagent.engine.entity.CompanionEntity self) || self.serverPlayer() == null) return "";
        var sp = self.serverPlayer();
        // What the villagers near buy for an emerald, a trade's worth of each (a novice farmer has two of the four).
        Map<net.minecraft.world.item.Item, Integer> lots = new java.util.LinkedHashMap<>();
        for (var v : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, sp.getBoundingBox().inflate(48, 12, 48),
                v -> v.isAlive() && !v.isBaby())) {
            for (var o : v.getOffers()) {
                if (o.isOutOfStock() || !o.getResult().is(net.minecraft.world.item.Items.EMERALD) || !o.getCostB().isEmpty()) continue;
                lots.merge(o.getCostA().getItem(), o.getCostA().getCount(), Math::min);
            }
        }
        if (lots.isEmpty()) {
            lots.put(net.minecraft.world.item.Items.WHEAT, 20);
            lots.put(net.minecraft.world.item.Items.POTATO, 26);
            lots.put(net.minecraft.world.item.Items.CARROT, 22);
            lots.put(net.minecraft.world.item.Items.BEETROOT, 15);
        }
        for (var e : lots.entrySet()) if (com.mineagent.engine.task.ProductionTask.count(sp, e.getKey()) >= e.getValue()) return "";
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(sp);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(sp, this, null);
        String best = "";
        double bestTicks = Double.MAX_VALUE;
        for (var e : lots.entrySet()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
            var plan = com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, id, e.getValue(),
                    java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN));
            if (!plan.possible()) continue;
            if (plan.ticks() < bestTicks) {
                bestTicks = plan.ticks();
                best = "make me " + e.getValue() + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(id, e.getValue());
            }
        }
        return best;
    }

    /**
     * "Repair the hut", "fix the dirt shelter" (MA9.1): the nearest of its builds of that kind, built back from its saved
     * blueprint at the corner it stands on (what is missing put back, the rest left). False when the words are not a
     * building it has built.
     */
    private boolean repairBuilding(String what) {
        String w = what == null ? "" : what.toLowerCase(java.util.Locale.ROOT).strip();
        String print = w.contains("dirt") ? "dirt_shelter" : w.contains("wood") ? "small_wood_house"
                : w.matches("(?:the |our |my )?(?:hut|house|shelter|base|home|cabin)") ? "hut" : null;
        if (print == null || !(companion instanceof com.mineagent.engine.entity.CompanionEntity self) || self.serverPlayer() == null) return false;
        var me = com.mineagent.engine.world.PlaceKeeper.point(self.serverPlayer());
        com.mineagent.engine.world.RouteMemory.Place best = null;
        for (var p : placeKeeper.memory().places()) {
            if (!"build".equals(p.kind()) || !p.at().dim().equals(me.dim())) continue;
            String name = p.name() == null ? "" : p.name();
            if (!name.startsWith(print + "|") && !("hut".equals(print) && name.startsWith("dirt_shelter|") && w.contains("shelter"))) continue;
            if (best == null || p.at().distance(me) < best.at().distance(me)) best = p;
        }
        if (best == null) return false;
        String[] f = best.name().split("\\|");
        String wall = f.length > 1 ? f[1] : "oak_planks", door = f.length > 2 ? f[2] : "oak_door";
        if (!wall.contains(":")) wall = "minecraft:" + wall;
        if (!door.contains(":")) door = "minecraft:" + door;
        haltNow();
        standDown();
        String said = "repair the " + f[0].replace('_', ' ');
        journal.record("order", said, currentGameTickSafe());
        orderGoal(said);
        speakToOwner("Repairing the " + f[0].replace('_', ' ') + " at " + best.at().x() + ", " + best.at().y() + ", " + best.at().z() + " from its blueprint.");
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.BlueprintTask.BlueprintRecord(
                "build-" + UUID.randomUUID(), f[0], wall, door, new net.minecraft.core.BlockPos(best.at().x(), best.at().y(), best.at().z())), reply -> { });
        return true;
    }

    /** "Mending", "silk touch", "unbreaking 3": the enchantment's id ("minecraft:mending"), or null. */
    private String enchantmentId(String words) {
        if (!(companion instanceof com.mineagent.engine.entity.CompanionEntity self) || self.serverPlayer() == null) return null;
        String w = words.toLowerCase(java.util.Locale.ROOT).replaceAll("\\b(?:i{1,3}|iv|v|[1-5])$", "").strip().replace(' ', '_');
        var key = net.minecraft.resources.ResourceLocation.tryParse("minecraft:" + w);
        if (key == null) return null;
        var reg = self.serverPlayer().serverLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        return reg.containsKey(key) ? key.toString() : null;
    }

    // ── The Nether (SMH plan 70, MA11.1) ──

    /**
     * In the Nether, one piece of gold armour on if it carries one (piglins leave a player wearing gold alone, and
     * barter with them): in the slot where its armour is worst. Back out, its best armour goes on again (GearUp).
     */
    private void goldInTheNether(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || self.serverLevel().dimension() != net.minecraft.world.level.Level.NETHER) return;
        for (var slot : List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET)) {
            var worn = self.getItemBySlot(slot);
            if (worn.getItem() instanceof net.minecraft.world.item.ArmorItem && golden(worn)) return;
        }
        var inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!(st.getItem() instanceof net.minecraft.world.item.ArmorItem a) || !golden(st)) continue;
            var slot = a.getEquipmentSlot();
            var old = self.getItemBySlot(slot);
            self.setItemSlot(slot, st.copy());
            inv.setItem(i, old);
            TaskContext.syncInventory(self);
            journal.record("nether", "gold armour on, for the piglins", currentGameTickSafe());
            return;
        }
    }

    private static boolean golden(net.minecraft.world.item.ItemStack st) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().startsWith("golden_");
    }

    // ── Talking (SMH plan 70, MA10.2) ──

    /** What "that", "it", "there" may mean for the turn after its owner's words, and until when (game ticks). */
    private volatile List<com.mineagent.engine.chat.Referents.Candidate> referents = List.of();
    private volatile long referentsUntil;
    private volatile boolean referentPicked;
    /** Calls that name a place, checked against the referents. */
    private static final java.util.Set<String> PLACE_TOOLS = java.util.Set.of("goto", "interact_at", "inspect_block",
            "inspect_block_storage", "store_items", "take_items", "place_block", "break_block", "open_container");

    /** The candidates, made in code: what its owner looks at, the chest nearest them, where they stand. */
    private void noteReferents() {
        var self = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
        var owner = self == null ? null : self.serverPlayerOwner();
        if (owner == null) {
            referents = List.of();
            return;
        }
        var level = owner.serverLevel();
        String dim = level.dimension().location().toString();
        List<com.mineagent.engine.chat.Referents.Candidate> list = new ArrayList<>();
        var looked = lookedAt(owner);
        if (looked instanceof net.minecraft.world.entity.Entity e) {
            list.add(new com.mineagent.engine.chat.Referents.Candidate("the " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(e.getType()).getPath().replace('_', ' ') + " they are looking at", dim, e.getBlockX(), e.getBlockY(), e.getBlockZ()));
        } else if (looked instanceof net.minecraft.core.BlockPos b) {
            String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(b).getBlock()).getPath().replace('_', ' ');
            list.add(new com.mineagent.engine.chat.Referents.Candidate("the " + block + " they are looking at", dim, b.getX(), b.getY(), b.getZ()));
        }
        net.minecraft.core.BlockPos chest = null;
        var at = owner.blockPosition();
        for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-8, -3, -8), at.offset(8, 3, 8))) {
            var st = level.getBlockState(p);
            if ((st.is(net.minecraft.world.level.block.Blocks.CHEST) || st.is(net.minecraft.world.level.block.Blocks.BARREL))
                    && (chest == null || p.distSqr(at) < chest.distSqr(at)) && !(looked instanceof net.minecraft.core.BlockPos lb && lb.equals(p))) {
                chest = p.immutable();
            }
        }
        if (chest != null) list.add(new com.mineagent.engine.chat.Referents.Candidate("the chest nearest them", dim, chest.getX(), chest.getY(), chest.getZ()));
        list.add(new com.mineagent.engine.chat.Referents.Candidate("where they stand", dim, at.getX(), at.getY(), at.getZ()));
        referents = List.copyOf(list);
        referentsUntil = currentGameTickSafe() + 2_400L;
        referentPicked = false;
    }

    private boolean referentsActive() {
        return !referents.isEmpty() && !referentPicked && currentGameTickSafe() <= referentsUntil;
    }

    /** The referents for the model's prompt, while they stand. */
    private String referentsBrief() {
        return referentsActive() ? com.mineagent.engine.chat.Referents.brief(referents) : "";
    }

    /**
     * The first call after "that", "it" or "there" that names a place must be at one of the referents: null when it is
     * (or when no check applies), else the refusal with the list.
     */
    private String referentRefusal(String tool, com.google.gson.JsonObject args) {
        if (!referentsActive() || tool == null || !PLACE_TOOLS.contains(tool) || args == null) return null;
        if (!args.has("x") || !args.has("y") || !args.has("z")) return null;
        try {
            int x = (int) Math.floor(args.get("x").getAsDouble()), y = (int) Math.floor(args.get("y").getAsDouble()),
                    z = (int) Math.floor(args.get("z").getAsDouble());
            if (com.mineagent.engine.chat.Referents.match(referents, x, y, z, 3.0).isPresent()) {
                referentPicked = true;
                return null;
            }
            journal.record("referent", "a call named " + x + ", " + y + ", " + z + ": none of the referents", currentGameTickSafe());
            return com.mineagent.engine.chat.Referents.refusal(referents);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private final com.mineagent.engine.chat.PhraseBook phraseBook = new com.mineagent.engine.chat.PhraseBook();
    /** The orders the talk summary counts: the jobs given, not "stop" or "where are you". */
    private static final java.util.Set<com.mineagent.engine.chat.CommandParser.Kind> TALK_ORDERS = java.util.EnumSet.of(
            com.mineagent.engine.chat.CommandParser.Kind.PRODUCE, com.mineagent.engine.chat.CommandParser.Kind.MINE_ORE,
            com.mineagent.engine.chat.CommandParser.Kind.GIVE, com.mineagent.engine.chat.CommandParser.Kind.BUILD,
            com.mineagent.engine.chat.CommandParser.Kind.LIGHT, com.mineagent.engine.chat.CommandParser.Kind.PRIME,
            com.mineagent.engine.chat.CommandParser.Kind.HUNT_PLAYER, com.mineagent.engine.chat.CommandParser.Kind.PROTECT,
            com.mineagent.engine.chat.CommandParser.Kind.PORTAL, com.mineagent.engine.chat.CommandParser.Kind.ENCHANT,
            com.mineagent.engine.chat.CommandParser.Kind.TRADE, com.mineagent.engine.chat.CommandParser.Kind.BREW,
            com.mineagent.engine.chat.CommandParser.Kind.BREED, com.mineagent.engine.chat.CommandParser.Kind.FISH,
            com.mineagent.engine.chat.CommandParser.Kind.GUARD_HERE, com.mineagent.engine.chat.CommandParser.Kind.GOAL_ADD,
            com.mineagent.engine.chat.CommandParser.Kind.FOOD_BOTH, com.mineagent.engine.chat.CommandParser.Kind.PATROL,
            com.mineagent.engine.chat.CommandParser.Kind.REPAIR, com.mineagent.engine.chat.CommandParser.Kind.GRIND,
            com.mineagent.engine.chat.CommandParser.Kind.BARTER, com.mineagent.engine.chat.CommandParser.Kind.PUT_AWAY,
            com.mineagent.engine.chat.CommandParser.Kind.TIDY, com.mineagent.engine.chat.CommandParser.Kind.CHEST_PURPOSE);
    /** The running summary of talks in this world (MA10.2), fed the orders its owner gives. */
    private final com.mineagent.engine.chat.TalkSummary talkSummary = new com.mineagent.engine.chat.TalkSummary();

    public com.mineagent.engine.chat.TalkSummary talkSummary() {
        return talkSummary;
    }

    // ── Schedules and a home to stay near (SMH plan 70, MA6.3) ──

    /** "Farm by day, guard by night": what it does by day and by night; null for none. Kept. */
    private volatile String dayOrder, nightOrder;
    /** Which half it is in now: 1 day, 2 night, 0 not started. */
    private int schedulePhase;
    /** "Stay within 24 blocks of here when you're idle": the home and how far; null for none. Kept. */
    private volatile com.mineagent.engine.world.RouteMemory.Point idleHome;
    private volatile int idleRadius;

    private static final java.util.regex.Pattern DAY_NIGHT = java.util.regex.Pattern.compile(
            "^(.{3,80}?),? (?:by day|in the day(?:time)?|during the day|in the daytime)(?:,| and| then)* (.{3,80}?) (?:by night|at night|in the night|during the night)$");
    private static final java.util.regex.Pattern NIGHT_DAY = java.util.regex.Pattern.compile(
            "^(.{3,80}?),? (?:by night|at night|in the night|during the night)(?:,| and| then)* (.{3,80}?) (?:by day|in the day(?:time)?|during the day)$");
    private static final java.util.regex.Pattern BY_DAY_FIRST = java.util.regex.Pattern.compile(
            "^(?:by day|in the day(?:time)?|during the day),? (.{3,80}?),? (?:and |then )?(?:by night|at night|during the night),? (.{3,80})$");
    private static final java.util.regex.Pattern STAY_NEAR = java.util.regex.Pattern.compile(
            "^(?:stay|keep|remain)(?: within (\\d{1,3}) blocks? of| near| close to| around) (?:here|this spot|this place|home|the base)(?: when you'?re idle| when idle| when you have nothing to do| when you'?re not busy)?$");

    /**
     * "Farm by day, guard by night", "no schedule", "stay within 24 blocks of here when idle", "you can wander": set,
     * said and kept. True when the words were one of these.
     */
    private boolean scheduleCommand(String t) {
        if (t.matches("^(?:no|stop the|drop the|cancel the|clear the|forget the) schedule$")) {
            dayOrder = nightOrder = null;
            schedulePhase = 0;
            speakToOwner("No schedule.");
            saveMemoryNow();
            return true;
        }
        if (t.matches("^(?:you can wander|wander freely|no home radius|go where you like|you can roam)$")) {
            idleHome = null;
            speakToOwner("I'll go where the work takes me.");
            saveMemoryNow();
            return true;
        }
        var near = STAY_NEAR.matcher(t);
        if (near.matches()) {
            var me = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
            var owner = me == null ? null : me.serverPlayerOwner();
            if (owner == null) return false;
            int r = near.group(1) == null ? 24 : Math.max(4, Math.min(256, Integer.parseInt(near.group(1))));
            idleHome = new com.mineagent.engine.world.RouteMemory.Point(owner.serverLevel().dimension().location().toString(),
                    owner.getBlockX(), owner.getBlockY(), owner.getBlockZ());
            idleRadius = r;
            speakToOwner("When I've nothing to do, I'll stay within " + r + " blocks of here.");
            saveMemoryNow();
            return true;
        }
        String day = null, night = null;
        var m = DAY_NIGHT.matcher(t);
        if (m.matches()) {
            day = m.group(1);
            night = m.group(2);
        } else if ((m = NIGHT_DAY.matcher(t)).matches()) {
            night = m.group(1);
            day = m.group(2);
        } else if ((m = BY_DAY_FIRST.matcher(t)).matches()) {
            day = m.group(1);
            night = m.group(2);
        }
        if (day == null) return false;
        day = scheduleLine(day.strip());
        night = scheduleLine(night.strip());
        if (day == null || night == null) return false;
        dayOrder = day;
        nightOrder = night;
        schedulePhase = 0;
        speakToOwner("By day: " + day + ". By night: " + night + ". Say \"no schedule\" to stop.");
        journal.record("order", day + " by day, " + night + " by night", currentGameTickSafe());
        saveMemoryNow();
        return true;
    }

    /** Half of a schedule as an order code runs: a guard order, "farm", or an order it knows; null otherwise. */
    private String scheduleLine(String w) {
        if (w.matches("^(?:guard|stand guard|keep watch|watch)(?: here| this place| the base| me)?$")) return w.contains("me") ? "guard me" : "guard here";
        if (w.matches("^(?:farm|tend the farm|tend the fields|work the fields|harvest|do the farming)$")) return "farm";
        var c = orderIn(w, companion.companionName());
        return c.isPresent() ? w : null;
    }

    /** At dawn and at dusk, the other half: what it was doing put down, the half's order taken up. */
    private void schedule(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || dayOrder == null || nightOrder == null || suspended) return;
        long time = self.serverLevel().getDayTime() % 24_000L;
        int phase = time < 12_500L || time >= 23_500L ? 1 : 2;
        if (phase == schedulePhase) return;
        schedulePhase = phase;
        String line = phase == 1 ? dayOrder : nightOrder;
        haltNow();
        standDown();
        goalBook.active().ifPresent(g -> goalBook.drop(g.id()));
        orderGoalId = null;
        speakToOwner((phase == 1 ? "Morning: " : "Evening: ") + line + ".");
        journal.record("schedule", (phase == 1 ? "day: " : "night: ") + line, currentGameTickSafe());
        if (line.startsWith("guard")) {
            onOwnerMessage(line, true);
            return;
        }
        if (line.equals("farm")) {
            String crop = cropToFarm(self);
            if (crop == null) {
                speakToOwner("No ripe crops near to farm; I'll wait for them.");
                schedulePhase = 0; // tried again at the next look
                return;
            }
            line = crop;
        }
        onOwnerMessage(line + " until I say stop", true);
    }

    /** "Farm": the crop whose fields in sight are ripest, as a standing order ("make me 64 wheat"). */
    private String cropToFarm(net.minecraft.server.level.ServerPlayer self) {
        var level = self.serverLevel();
        Map<String, Integer> ripe = new java.util.HashMap<>();
        var at = self.blockPosition();
        for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-24, -4, -24), at.offset(24, 4, 24))) {
            var st = level.getBlockState(p);
            if (st.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(st)) {
                ripe.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(crop.asItem() == net.minecraft.world.item.Items.AIR
                        ? net.minecraft.world.item.Items.WHEAT : cropItem(crop)).toString(), 1, Integer::sum);
            }
        }
        return ripe.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> "make me 64 " + com.mineagent.engine.planning.production.ProductionPlanner.plural(e.getKey(), 64)).orElse(null);
    }

    private static net.minecraft.world.item.Item cropItem(net.minecraft.world.level.block.CropBlock crop) {
        if (crop == net.minecraft.world.level.block.Blocks.WHEAT) return net.minecraft.world.item.Items.WHEAT;
        if (crop == net.minecraft.world.level.block.Blocks.CARROTS) return net.minecraft.world.item.Items.CARROT;
        if (crop == net.minecraft.world.level.block.Blocks.POTATOES) return net.minecraft.world.item.Items.POTATO;
        if (crop == net.minecraft.world.level.block.Blocks.BEETROOTS) return net.minecraft.world.item.Items.BEETROOT;
        return crop.asItem();
    }

    /** Idle and further from its home than it was told to stay: back there (MA6.3). */
    private void stayNearHome(net.minecraft.server.level.ServerPlayer self) {
        var home = idleHome;
        if (self == null || home == null || suspended) return;
        if (!home.dim().equals(self.serverLevel().dimension().location().toString())) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        if (MineAgentEngine.getCompanionMode(companion.companionId()) == MineAgentEngine.CompanionMode.FOLLOW) return;
        double dx = self.getX() - home.x(), dz = self.getZ() - home.z();
        if (dx * dx + dz * dz <= (double) idleRadius * idleRadius) return;
        journal.record("home", "back within " + idleRadius + " blocks of home", currentGameTickSafe());
        goTo(home, List.of(), false);
    }

    // ── Its workshop: the furnaces it put down, remembered (SMH plan 70, production) ──

    /** The furnaces, smokers and blast furnaces it put down, by dimension and block (at most 64). */
    private final java.util.LinkedHashSet<String> stations = new java.util.LinkedHashSet<>();

    public synchronized void noteStation(String dim, net.minecraft.core.BlockPos p) {
        stations.remove(dim + "|" + p.asLong());
        stations.add(dim + "|" + p.asLong());
        while (stations.size() > 64) stations.remove(stations.iterator().next());
    }

    /** The stations it put down in this dimension within {@code radius} of a point, nearest first. */
    public synchronized List<net.minecraft.core.BlockPos> stationsNear(String dim, net.minecraft.core.BlockPos at, double radius) {
        List<net.minecraft.core.BlockPos> out = new ArrayList<>();
        for (String k : stations) {
            int bar = k.lastIndexOf('|');
            if (!k.substring(0, bar).equals(dim)) continue;
            var p = net.minecraft.core.BlockPos.of(Long.parseLong(k.substring(bar + 1)));
            if (p.distSqr(at) <= radius * radius) out.add(p);
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(at)));
        return out;
    }

    // ── Hard: the gear goal rises with the danger (SMH plan 70, MA8.7) ──

    /**
     * On Hard, where the danger grows with the time spent in a place (mobs there wear better armour over the weeks), with
     * nothing to do, it makes the iron armour it lacks, a piece at a time, from what it has and has seen
     * ({@code behaviour.gearGoal}). Its owner's orders always come first.
     */
    private void gearGoal(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || !com.mineagent.engine.settings.Switches.gearGoal()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        var level = self.serverLevel();
        // Hard only: there zombies break doors and the local difficulty climbs past anything Normal reaches.
        if (level.getDifficulty() != net.minecraft.world.Difficulty.HARD) return;
        var want = new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.IRON_CHESTPLATE, net.minecraft.world.item.Items.IRON_LEGGINGS,
                net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.IRON_BOOTS};
        var slots = new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.FEET};
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
        com.mineagent.engine.planning.production.Production.Have have = null;
        for (int i = 0; i < want.length; i++) {
            var worn = self.getItemBySlot(slots[i]);
            double wornValue = worn.getItem() instanceof net.minecraft.world.item.ArmorItem a ? a.getDefense() : 0;
            if (wornValue >= ((net.minecraft.world.item.ArmorItem) want[i]).getDefense()) continue;
            if (self.getInventory().countItem(want[i]) > 0) continue; // GearUp puts it on
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(want[i]).toString();
            if (have == null) have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
            var plan = com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, id, 1,
                    java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN));
            if (!plan.possible()) continue;
            journal.record("gear", "the danger here has grown: making " + com.mineagent.engine.planning.production.ProductionPlanner.name(id), currentGameTickSafe());
            com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                    "gear-" + UUID.randomUUID(), id, 1, null, false, true), reply -> com.mineagent.engine.survival.GearUp.run(self));
            return;
        }
    }

    /** When it first ticked in this world, and when its owner last said anything to it (game ticks). */
    private volatile long firstTickAt = -1L, ownerWordsAt = -1_000_000L;

    /** Two minutes after it arrives, and a minute since its owner last spoke to it: its own pursuits may start. */
    private boolean freeTime(long gameTick) {
        if (firstTickAt < 0 || gameTick < firstTickAt) firstTickAt = gameTick;
        return gameTick - firstTickAt >= 2_400L && gameTick - ownerWordsAt >= 1_200L;
    }

    // ── God-tier gear, its end goal (SMH plan 70: "gear that makes a Minecraft sweat blush ... Unless told to speed run") ──

    /** God-tier tries that failed, by {@link com.mineagent.engine.gear.GodGear.Next#key}, with when; its jobs by task id. */
    private final java.util.Map<String, Long> godGearFailed = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> godGearJobs = new java.util.concurrent.ConcurrentHashMap<>();
    /** What to run once a task it started for itself ends, however it ended. */
    private final java.util.Map<String, Runnable> afterTaskAlways = new java.util.concurrent.ConcurrentHashMap<>();

    /** Enchantments by name ("sharpness") and level. */
    private static java.util.Map<String, Integer> enchantsOf(net.minecraft.world.item.enchantment.ItemEnchantments e) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        if (e == null) return out;
        for (var en : e.entrySet()) {
            int lvl = en.getIntValue();
            en.getKey().unwrapKey().ifPresent(k -> out.merge(k.location().getPath(), lvl, Math::max));
        }
        return out;
    }

    /**
     * In free time, unless speedrunning ({@code behaviour.godGear}): one step up the ladder to god-tier gear (gear/GodGear):
     * each piece made in diamond (iron while diamond can't be had), then the best enchantments it lacks, from the books it
     * carries, put on in the cheapest order no step of which is too expensive, or bought from a villager it keeps. A try
     * that failed waits a day. Never its owner's things: what it makes comes from its own.
     */
    private void godGearGoal(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || speedrun || !com.mineagent.engine.settings.Switches.godGear()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        var level = self.serverLevel();
        long now = currentGameTickSafe();
        godGearFailed.values().removeIf(t -> now - t > 24_000L);
        var inv = self.getInventory();
        java.util.Map<String, com.mineagent.engine.gear.GodGear.Piece> pieces = new java.util.HashMap<>();
        java.util.Map<String, Integer> books = new java.util.HashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) { // the pack, the armour worn and the off hand
            var st = inv.getItem(i);
            if (st.isEmpty()) continue;
            if (st.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) {
                enchantsOf(st.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)).forEach((k, v) -> books.merge(k, v, Math::max));
                continue;
            }
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            String kind = com.mineagent.engine.gear.GodGear.kindOf(id);
            if (kind == null || !com.mineagent.engine.gear.GodGear.LADDER.contains(kind)) continue;
            var piece = new com.mineagent.engine.gear.GodGear.Piece(kind, com.mineagent.engine.gear.GodGear.tier(id), enchantsOf(st.getEnchantments()));
            var had = pieces.get(kind);
            if (had == null || piece.tier() > had.tier() || piece.tier() == had.tier()
                    && piece.ench().values().stream().mapToInt(Integer::intValue).sum() > had.ench().values().stream().mapToInt(Integer::intValue).sum()) {
                pieces.put(kind, piece);
            }
        }
        java.util.Map<String, Integer> sold = new java.util.HashMap<>();
        java.util.Map<String, UUID> seller = new java.util.HashMap<>();
        for (var v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, self.getBoundingBox().inflate(48, 12, 48),
                net.minecraft.world.entity.npc.Villager::isAlive)) {
            if (!keepers.contains(v.getUUID().toString())) continue;
            for (var o : com.mineagent.engine.task.LibrarianTask.offers(v)) {
                if (o.enchantment() == null) continue;
                String e = o.enchantment().substring(o.enchantment().indexOf(':') + 1);
                if (o.level() > sold.getOrDefault(e, 0)) {
                    sold.put(e, o.level());
                    seller.put(e, v.getUUID());
                }
            }
        }
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
        java.util.Set<String> without = onItsOwn ? java.util.Set.of() : java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN);
        java.util.Map<String, Boolean> makeable = new java.util.HashMap<>();
        java.util.function.Predicate<String> canMake = item -> makeable.computeIfAbsent(item,
                k -> com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, k, 1, without).possible());
        // Netherite: a template and an ingot it carries (or the ingot it can make: four scrap and four gold).
        boolean template = inv.countItem(net.minecraft.world.item.Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) > 0;
        boolean ingot = inv.countItem(net.minecraft.world.item.Items.NETHERITE_INGOT) > 0 || template && canMake.test("minecraft:netherite_ingot");
        java.util.function.Predicate<String> canSmith = k -> template && ingot;
        var next = com.mineagent.engine.gear.GodGear.next(pieces, books, sold, canMake, canSmith, godGearFailed.keySet());
        if (next == null) return;
        final String job = "god-" + UUID.randomUUID();
        switch (next.move()) {
            case MAKE -> {
                journal.record("gear", "toward god-tier gear: making " + com.mineagent.engine.planning.production.ProductionPlanner.name(next.what()), now);
                afterTaskAlways.put(job, () -> com.mineagent.engine.survival.GearUp.run(self));
                godGearJobs.put(job, next.key());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                        job, next.what(), 1, null, false, !onItsOwn), reply -> { });
            }
            case APPLY_BOOKS -> {
                if (self.experienceLevel < 5) return; // not the levels for an anvil step yet
                if (!intoPackForAnvil(self, next.kind())) return;
                journal.record("gear", "toward god-tier gear: my books on my " + next.kind() + " in the best order", now);
                afterTaskAlways.put(job, () -> com.mineagent.engine.survival.GearUp.run(self));
                godGearJobs.put(job, next.key());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.AnvilTask.AnvilRecord(
                        job, "best", next.kind()), reply -> { });
            }
            case SMITH -> {
                if (!intoPackForAnvil(self, next.kind())) return;
                journal.record("gear", "toward god-tier gear: my " + next.kind() + " to netherite", now);
                afterTaskAlways.put(job, () -> com.mineagent.engine.survival.GearUp.run(self));
                godGearJobs.put(job, next.key());
                Runnable smith = () -> com.mineagent.api.task.TaskDispatch.dispatchAsync(companion,
                        new com.mineagent.engine.task.SmithingTask.SmithRecord(job, next.kind()), reply -> { });
                if (inv.countItem(net.minecraft.world.item.Items.NETHERITE_INGOT) == 0) {
                    String make = "god-ingot-" + UUID.randomUUID();
                    godGearJobs.put(make, next.key());
                    afterTask.put(make, smith);
                    com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                            make, "minecraft:netherite_ingot", 1, null, false, true), reply -> { });
                } else {
                    smith.run();
                }
            }
            case BUY_BOOK -> {
                UUID who = seller.get(next.what());
                if (who == null) return;
                journal.record("gear", "toward god-tier gear: buying " + next.what() + " from my villager", now);
                godGearJobs.put(job, next.key());
                Runnable trade = () -> com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.TradeTask.TradeRecord(
                        job, "minecraft:enchanted_book#minecraft:" + next.what(), 1, null, who), reply -> { });
                if (inv.countItem(net.minecraft.world.item.Items.BOOK) == 0) { // a librarian's book costs a book as well
                    String make = "god-book-" + UUID.randomUUID();
                    godGearJobs.put(make, next.key());
                    afterTask.put(make, trade);
                    com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                            make, "minecraft:book", 1, null, false, true), reply -> { });
                } else {
                    trade.run();
                }
            }
        }
    }

    /** A piece worn, or a shield in the off hand, moved into the pack for the anvil (it puts it back on after). */
    private static boolean intoPackForAnvil(net.minecraft.server.level.ServerPlayer self, String kind) {
        net.minecraft.world.entity.EquipmentSlot slot = switch (kind) {
            case "helmet" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chestplate" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "leggings" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "boots" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case "shield" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            default -> null;
        };
        if (slot == null) return true;
        var worn = self.getItemBySlot(slot);
        if (worn.isEmpty() || !kind.equals(com.mineagent.engine.gear.GodGear.kindOf(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(worn.getItem()).toString()))) return true;
        int free = self.getInventory().getFreeSlot();
        if (free < 0) return false;
        self.getInventory().setItem(free, worn.copy());
        self.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        return true;
    }

    // ── The kit it keeps: a shield and golden apples (SMH plan 70) ──

    /** Golden apples it keeps on it, made in free time. */
    public static final int KIT_APPLES = 4;

    /**
     * In any world, with nothing to do, it makes what saves a life and keeps it on it: a shield (into the off hand), then
     * golden apples up to {@link #KIT_APPLES}, from what it has and has seen ({@code behaviour.kitGoal}). An enchanted
     * golden apple can't be made: it is kept when found. Its owner's orders always come first.
     */
    private void kitGoal(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || !com.mineagent.engine.settings.Switches.kitGoal()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        var inv = self.getInventory();
        boolean shield = self.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) || inv.countItem(net.minecraft.world.item.Items.SHIELD) > 0;
        int apples = inv.countItem(net.minecraft.world.item.Items.GOLDEN_APPLE);
        String id;
        int want;
        if (!shield) {
            id = "minecraft:shield";
            want = 1;
        } else if (apples < KIT_APPLES) {
            id = "minecraft:golden_apple";
            want = KIT_APPLES - apples;
        } else {
            return;
        }
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
        var unseen = java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN);
        var plan = com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, id, want, unseen);
        if (!plan.possible() && want > 1) {
            want = 1;
            plan = com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, id, want, unseen);
        }
        if (!plan.possible()) return;
        journal.record("kit", "free time: making " + (want > 1 ? want + " " : "") + com.mineagent.engine.planning.production.ProductionPlanner.name(id)
                + (want > 1 ? "s" : "") + " to keep on me", currentGameTickSafe());
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                "kit-" + UUID.randomUUID(), id, want, null, false, true), reply -> com.mineagent.engine.survival.GearUp.run(self));
    }

    // ── Leaving no trace: holes near home filled (SMH plan 70, MA9.4) ──

    private final com.mineagent.engine.world.Holes holes = new com.mineagent.engine.world.Holes();

    public com.mineagent.engine.world.Holes holes() {
        return holes;
    }

    /**
     * It broke a block: kept as a hole to fill when it is the top of the ground (the sky above it), near home (a home it
     * remembers, or the one it was told to stay near), not what its order is for ("make me 64 dirt" keeps its dirt), and
     * not in a place it was told to keep out of. Off with {@code behaviour.fillHoles}.
     */
    public void noteDug(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!com.mineagent.engine.settings.Switches.fillHoles()) return;
        boolean ground = state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.world.level.block.Blocks.SAND)
                || state.is(net.minecraft.world.level.block.Blocks.GRAVEL) || state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE);
        if (!ground || !level.canSeeSky(pos.above())) return;
        String dim = level.dimension().location().toString();
        if (!nearHome(dim, pos)) return;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        String goal = goalBook.active().map(g -> g.text().toLowerCase(java.util.Locale.ROOT)).orElse("");
        if (!goal.isEmpty() && (goal.contains(name) || goal.contains(name.replace(" block", "")))) return;
        holes.dug(dim, pos.getX(), pos.getY(), pos.getZ(), id, currentGameTickSafe());
    }

    private boolean nearHome(String dim, net.minecraft.core.BlockPos pos) {
        var home = idleHome;
        if (home != null && home.dim().equals(dim)) {
            double dx = pos.getX() - home.x(), dz = pos.getZ() - home.z();
            if (dx * dx + dz * dz <= (double) (idleRadius + 16) * (idleRadius + 16)) return true;
        }
        for (var p : placeKeeper.memory().places()) {
            if (!"home".equals(p.kind()) || !p.at().dim().equals(dim)) continue;
            double dx = pos.getX() - p.at().x(), dz = pos.getZ() - p.at().z();
            if (dx * dx + dz * dz <= 48.0 * 48.0) return true;
        }
        return false;
    }

    /**
     * A creeper blew a hole near home (SMH plan 70): the ground it took is kept as holes to fill, all with what the ground
     * round it is mostly made of on top (sand where it is sand, dirt where it is grass, rock where it is rock), not
     * whatever lay underneath. Off with {@code behaviour.fillCraters}.
     */
    public void noteBlast(net.minecraft.server.level.ServerLevel level, List<net.minecraft.core.BlockPos> blown,
                          List<net.minecraft.world.level.block.state.BlockState> was) {
        if (!com.mineagent.engine.settings.Switches.fillCraters() || blown.isEmpty()) return;
        String dim = level.dimension().location().toString();
        long sx = 0, sy = 0, sz = 0;
        for (var p : blown) {
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        var centre = new net.minecraft.core.BlockPos((int) (sx / blown.size()), (int) (sy / blown.size()), (int) (sz / blown.size()));
        if (!nearHome(dim, centre)) return;
        java.util.Map<String, Integer> tops = new java.util.HashMap<>();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x, centre.getY(), z))) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                var top = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                tops.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(top.getBlock()).toString(), 1, Integer::sum);
            }
        }
        String surface = com.mineagent.engine.world.Craters.surface(tops);
        long tick = currentGameTickSafe();
        int n = 0;
        for (int i = 0; i < blown.size(); i++) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(was.get(i).getBlock()).toString();
            if (!com.mineagent.engine.world.Craters.ground(id)) continue;
            var p = blown.get(i);
            holes.dug(dim, p.getX(), p.getY(), p.getZ(), surface != null ? surface : id, tick);
            n++;
        }
        // Sand and gravel over what it blew falls into the crater, and leaves its own place empty: kept too (one still
        // there when it comes to fill is struck off as filled).
        java.util.Set<net.minecraft.core.BlockPos> gone = new java.util.HashSet<>(blown);
        for (var p : blown) {
            var up = p.above();
            for (int k = 0; k < 4 && !gone.contains(up); k++, up = up.above()) {
                var st = level.getBlockState(up);
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                if (!(st.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) || !com.mineagent.engine.world.Craters.ground(id)) break;
                holes.dug(dim, up.getX(), up.getY(), up.getZ(), surface != null ? surface : id, tick);
            }
        }
        if (n > 0) journal.record("crater", "a creeper blew a hole of " + n + " blocks near home, at " + centre.getX() + ", " + centre.getY()
                + ", " + centre.getZ(), tick, dim, centre.getX(), centre.getY(), centre.getZ());
    }

    /** With nothing to do, the holes near it filled (at most 16 at a time). */
    private void fillHolesWhenIdle(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || holes.size() == 0 || !com.mineagent.engine.settings.Switches.fillHoles()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        String dim = self.serverLevel().dimension().location().toString();
        var level = self.serverLevel();
        List<int[]> todo = new ArrayList<>();
        // All the holes near, lowest first, then a batch: a crater is filled from its floor up (a block of sand put over
        // a hole not yet filled would fall into it).
        List<int[]> all = new ArrayList<>();
        for (var h : holes.near(dim, self.getBlockX(), self.getBlockY(), self.getBlockZ(), 24, com.mineagent.engine.world.Holes.MAX)) {
            all.add(new int[] {h.x(), h.y(), h.z()});
        }
        for (int[] hh : com.mineagent.engine.world.Craters.bottomUp(all, self.getBlockX(), self.getBlockZ())) {
            var h = new com.mineagent.engine.world.Holes.Hole(dim, hh[0], hh[1], hh[2], "", 0);
            var p = new net.minecraft.core.BlockPos(h.x(), h.y(), h.z());
            if (!level.isLoaded(p)) continue;
            if (!level.getBlockState(p).canBeReplaced()) {
                holes.filled(dim, h.x(), h.y(), h.z()); // filled already
                continue;
            }
            // A pocket sealed under filled ground (nothing round it open): out of sight, and nothing goes in: let be.
            boolean sealed = true;
            for (var d : net.minecraft.core.Direction.values()) if (level.getBlockState(p.relative(d)).canBeReplaced()) sealed = false;
            if (sealed) {
                holes.filled(dim, h.x(), h.y(), h.z());
                continue;
            }
            if (todo.size() < 48) todo.add(new int[] {h.x(), h.y(), h.z()});
        }
        if (todo.isEmpty()) return;
        todo = com.mineagent.engine.world.Craters.bottomUp(todo, self.getBlockX(), self.getBlockZ()); // a crater from its floor up
        journal.record("tidy", "filling " + todo.size() + " holes near home", currentGameTickSafe());
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.FillHolesTask.FillRecord(
                "fill-holes-" + UUID.randomUUID(), todo), reply -> { });
    }

    /** As kept with the world: the schedule, and the home to stay near. */
    public String[] scheduleState() {
        var h = idleHome;
        return new String[] {dayOrder, nightOrder, h == null ? null : h.dim() + "|" + h.x() + "|" + h.y() + "|" + h.z() + "|" + idleRadius};
    }

    public void scheduleState(String day, String night, String home) {
        dayOrder = day;
        nightOrder = night;
        if (home != null) {
            String[] f = home.split("\\|");
            if (f.length == 5) {
                try {
                    idleHome = new com.mineagent.engine.world.RouteMemory.Point(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]));
                    idleRadius = Integer.parseInt(f[4]);
                } catch (NumberFormatException ignored) {
                    idleHome = null;
                }
            }
        }
    }

    /**
     * Whether it may take from its owner's chests (MA10.5): only once told "use my chests" (its owner, SMH plan 70: "Don't
     * have it rob me blind unless I say it can"); "leave my chests" says no again. Its own chests are always its to use.
     * Kept, with whether its owner has said either.
     */
    private volatile boolean chestsAllowed = false;
    private volatile boolean chestsDecided;
    /** The chests that are its own stockpile ("dim|x|y|z"); kept with the world. */
    private final java.util.Set<String> ownChests = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    /** Whether it may take from this chest: one of its own, or any once its owner said it may. */
    public boolean mayTakeFrom(String dim, int x, int y, int z) {
        return chestsAllowed || ownChests.contains(dim + "|" + x + "|" + y + "|" + z);
    }

    public boolean ownsChest(String dim, int x, int y, int z) {
        return ownChests.contains(dim + "|" + x + "|" + y + "|" + z);
    }

    /** A chest of its own, its stockpile. */
    public void noteOwnChest(String dim, int x, int y, int z) {
        if (ownChests.size() < 512) ownChests.add(dim + "|" + x + "|" + y + "|" + z);
    }

    public java.util.Set<String> ownChests() {
        synchronized (ownChests) {
            return java.util.Set.copyOf(ownChests);
        }
    }

    public void ownChests(java.util.Collection<String> keys) {
        ownChests.clear();
        if (keys != null) for (String k : keys) if (k != null && k.split("\\|").length == 4 && ownChests.size() < 512) ownChests.add(k);
    }

    public boolean chestsDecided() {
        return chestsDecided;
    }

    public void chestsDecided(boolean d) {
        chestsDecided = d;
    }
    /** Whether it has said, in this world, that it takes from the chests. Kept. */
    private volatile boolean chestsTold;

    public boolean mayUseChests() {
        return chestsAllowed;
    }

    public boolean chestsTold() {
        return chestsTold;
    }

    /** As kept with the world. */
    // ── Asking instead of guessing (SMH plan 70) ──

    private final com.mineagent.engine.chat.Asking asking = new com.mineagent.engine.chat.Asking();

    public com.mineagent.engine.chat.Asking asking() {
        return asking;
    }

    /** How long a question waits for its owner before the convenient answer is taken: a minute. */
    static final long ASK_WAITS = 1_200L;

    /** The answer it goes by on this, if it has one (told, or assumed within the day). */
    public java.util.Optional<Boolean> answer(String key) {
        return asking.answer(key, currentGameTickSafe());
    }

    /**
     * Ask its owner, yes or no, when it has no answer on this and no other question waits; {@code convenient}: the answer
     * it takes after a minute without one (the one that lets the job go on), said in the question.
     */
    public void ask(String key, String question, boolean convenient) {
        if (asking.ask(key, question, convenient, currentGameTickSafe(), ASK_WAITS)) {
            journal.record("asked", question, currentGameTickSafe());
            speakToOwner(question + " (Yes or no; if I don't hear back in a minute, I'll take it as a " + (convenient ? "yes" : "no") + ".)");
        }
    }

    /** The mob a job of its is hunting now, for its drops, and when it last said so. */
    private volatile String huntingFor;
    private volatile long huntingAt;

    /** A job hunts this mob ("spider") for what it drops: a reason to fight one. */
    public void noteHunting(String mob) {
        huntingFor = mob;
        huntingAt = currentGameTickSafe();
    }

    /** Its own reasons to fight now (combat/FightWeighing): the drops a job needs, and wanting experience. */
    public com.mineagent.engine.combat.FightWeighing.Reasons fightReasons() {
        String h = huntingFor;
        java.util.Set<String> drops = h != null && currentGameTickSafe() - huntingAt < 200 ? java.util.Set.of(h) : java.util.Set.of();
        return new com.mineagent.engine.combat.FightWeighing.Reasons(drops, wantsXp());
    }

    /** It wants experience: a goal of its owner's asks for levels or an enchanting it can't yet pay for. */
    public boolean wantsXp() {
        String goal = goalBook.active().map(g -> g.text().toLowerCase(java.util.Locale.ROOT)).orElse("");
        return goal.contains(" xp") || goal.startsWith("xp") || goal.contains("experience") || goal.contains("levels")
                || goal.contains("enchant");
    }

    /**
     * What its gear lacks of god tier (gear/GodGear): each piece it wears and its best tools, by what is missing; or that
     * it is all there.
     */
    private String gearReport() {
        var self = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayer();
        if (self == null) return "I'm not in the world right now.";
        java.util.List<net.minecraft.world.item.ItemStack> pieces = new java.util.ArrayList<>();
        for (var slot : List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.OFFHAND)) {
            pieces.add(self.getItemBySlot(slot));
        }
        var inv = self.getInventory();
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (int i = 0; i < Math.min(36, inv.getContainerSize()); i++) pieces.add(inv.getItem(i));
        List<String> parts = new ArrayList<>();
        for (var st : pieces) {
            if (st.isEmpty()) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            String kind = com.mineagent.engine.gear.GodGear.kindOf(id);
            if (kind == null || !kinds.add(kind)) continue;
            java.util.Map<String, Integer> has = new java.util.HashMap<>();
            var e = st.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (e != null) for (var x : e.entrySet()) x.getKey().unwrapKey().ifPresent(k -> has.put(k.location().getPath(), x.getIntValue()));
            var lacks = com.mineagent.engine.gear.GodGear.missing(kind, has);
            String material = id.substring(id.indexOf(':') + 1).contains("netherite") ? "" : " (not netherite yet)";
            if (lacks.isEmpty()) {
                parts.add("my " + kind.replace('_', ' ') + " is done" + material);
                continue;
            }
            List<String> names = new ArrayList<>();
            for (var l : lacks) names.add(l.id().replace('_', ' ') + " " + roman(l.level()));
            var order = com.mineagent.engine.gear.GodGear.order(lacks);
            parts.add("my " + kind.replace('_', ' ') + " needs " + String.join(", ", names)
                    + (order.possible() ? " (about " + order.total() + " levels in the best order)" : "") + material);
        }
        return parts.isEmpty() ? "I've no gear worth enchanting yet." : "For god-tier gear: " + String.join("; ", parts) + ".";
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // ── Hoarding: its own stockpile (SMH plan 70: "a fair base, filled to the brim with valuables") ──

    /** "Speedrun": no hoarding, the end the goal. Kept with the world. */
    private volatile boolean speedrun;
    /** "Go survive on your own": it goes looking for what it lacks, further and more often. Kept with the world. */
    private volatile boolean onItsOwn;

    public boolean speedrun() {
        return speedrun;
    }

    public void speedrun(boolean on) {
        speedrun = on;
    }

    public boolean onItsOwn() {
        return onItsOwn;
    }

    public void onItsOwn(boolean on) {
        onItsOwn = on;
    }

    /** On its own: the swamp it is going to for its base, once found (dimension, x, z). */
    private volatile com.mineagent.engine.world.RouteMemory.Point surviveAt;
    private volatile boolean surviveFailed;

    /**
     * On its own (SMH plan 70: "locate a good SWAMP biome, build a base and basic farms, and stock up on resources. In
     * mass. It should be highly favoring of slime"): with no base yet, the nearest swamp or mangrove swamp (slimes spawn in
     * both) found as a seed map finds it, gone to, and its base made there: its home, then the world primed (the hut,
     * beds, food, light). Then the stockpile ({@link #hoard}) takes over, searching as far as it needs.
     */
    private void surviveOnItsOwn(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || !onItsOwn) return;
        if (MineAgentEngine.getCompanionMode(companion.companionId()) == MineAgentEngine.CompanionMode.FOLLOW) {
            MineAgentEngine.setCompanionMode(companion.companionId(), MineAgentEngine.CompanionMode.FREE);
        }
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || skillRuntime.active()) return;
        var level = self.serverLevel();
        String dim = level.dimension().location().toString();
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (idleHome != null && idleHome.dim().equals(dim)) return; // its base is made
        if (surviveAt == null && !surviveFailed) {
            var found = level.findClosestBiome3d(b -> b.is(net.minecraft.world.level.biome.Biomes.SWAMP) || b.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP),
                    self.blockPosition(), 3200, 32, 64);
            if (found == null) {
                surviveFailed = true;
                speakToOwner("I found no swamp within 3200 blocks: I'll make my base here instead.");
                surviveAt = new com.mineagent.engine.world.RouteMemory.Point(dim, self.getBlockX(), self.getBlockY(), self.getBlockZ());
            } else {
                var p = found.getFirst();
                int y = level.isLoaded(p) ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) : self.getBlockY();
                surviveAt = new com.mineagent.engine.world.RouteMemory.Point(dim, p.getX(), y, p.getZ());
                int far = (int) Math.sqrt(self.blockPosition().distSqr(p));
                speakToOwner("There's a swamp " + far + " blocks off, at " + p.getX() + ", " + p.getZ() + ": I'm going to make my base there.");
                journal.record("survive", "going to the swamp at " + p.getX() + ", " + p.getZ(), currentGameTickSafe(), dim, p.getX(), y, p.getZ());
            }
        }
        var to = surviveAt;
        if (to == null) return;
        double dx = self.getX() - to.x(), dz = self.getZ() - to.z();
        if (dx * dx + dz * dz > 24.0 * 24.0) {
            goTo(to, List.of(), false);
            return;
        }
        // There: its base, its home, then the world primed.
        idleHome = new com.mineagent.engine.world.RouteMemory.Point(dim, self.getBlockX(), self.getBlockY(), self.getBlockZ());
        idleRadius = 48;
        journal.record("survive", "made my base here", currentGameTickSafe(), dim, self.getBlockX(), self.getBlockY(), self.getBlockZ());
        speakToOwner("Made my base here. Priming it first: a hut, beds, food and light; then a farm and a slime farm, then the stockpile.");
        saveMemoryNow();
        queueOrders("prime the world, then make a farm, then make a slime farm");
    }

    /** Mobs its fighting leaves be (a zombie villager it is curing), by id. */
    private final java.util.Set<UUID> spared = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void spare(UUID id) {
        if (id != null && spared.size() < 64) spared.add(id);
    }

    public void unspare(UUID id) {
        if (id != null) spared.remove(id);
    }

    public boolean spared(UUID id) {
        return id != null && spared.contains(id);
    }

    /** Places it has looted (structures, by dimension and chunk), kept with the world, so "go looting" goes on to new ones. */
    private final java.util.Set<String> looted = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    private static String lootKey(String dim, net.minecraft.core.BlockPos at) {
        return dim + "|" + (at.getX() >> 4) + "|" + (at.getZ() >> 4);
    }

    public void noteLooted(String dim, net.minecraft.core.BlockPos at) {
        if (looted.size() < 4096) looted.add(lootKey(dim, at));
        saveMemoryNow();
    }

    /** Looted already: the structure at {@code at} or one in the chunks beside it. */
    public boolean looted(String dim, net.minecraft.core.BlockPos at) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (looted.contains(lootKey(dim, at.offset(dx * 16, 0, dz * 16)))) return true;
        }
        return false;
    }

    public java.util.Set<String> lootedPlaces() {
        synchronized (looted) {
            return java.util.Set.copyOf(looted);
        }
    }

    public void lootedPlaces(java.util.Collection<String> keys) {
        looted.clear();
        if (keys != null) for (String k : keys) if (k != null && looted.size() < 4096) looted.add(k);
    }

    /** A cure whose potion and apple were queued to be made first: the next cure order goes ahead. */
    private volatile boolean cureReady;

    /** A farm order whose hoe and bucket were queued to be made first: the next farm order goes ahead. */
    private volatile boolean farmReady;

    /** Its home in a dimension, as {@link #home}: for its tasks. */
    public net.minecraft.core.BlockPos homeIn(String dim) {
        return home(dim);
    }

    /** Its home: the one it was told to stay near, else the one it remembers; null with none. */
    private net.minecraft.core.BlockPos home(String dim) {
        var h = idleHome;
        if (h != null && h.dim().equals(dim)) return new net.minecraft.core.BlockPos(h.x(), h.y(), h.z());
        for (var p : placeKeeper.memory().places()) {
            if ("home".equals(p.kind()) && p.at().dim().equals(dim)) return new net.minecraft.core.BlockPos(p.at().x(), p.at().y(), p.at().z());
        }
        return null;
    }

    /** Its own stockpile chest nearest home, within 48 blocks of it, or null. */
    private net.minecraft.core.BlockPos stash(net.minecraft.server.level.ServerLevel level, String dim, net.minecraft.core.BlockPos home) {
        net.minecraft.core.BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (String k : ownChests()) {
            String[] f = k.split("\\|");
            if (f.length != 4 || !f[0].equals(dim)) continue;
            try {
                var p = new net.minecraft.core.BlockPos(Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]));
                if (level.isLoaded(p) && !(level.getBlockEntity(p) instanceof net.minecraft.world.Container)) continue; // gone
                double d = p.distSqr(home);
                if (d <= 48.0 * 48.0 && d < bestD) {
                    bestD = d;
                    best = p;
                }
            } catch (NumberFormatException ignored) {
                // a key from elsewhere
            }
        }
        return best;
    }

    /**
     * With nothing to do and a home: its stockpile filled toward its targets (gear/Hoard), a batch at a time, into a chest
     * of its own by home (put down first if it has none), from what it can make here: what it has seen, and on its own
     * whatever it must go looking for. Never its owner's things. Off with "speedrun" or {@code behaviour.hoard}.
     */
    private void hoard(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || speedrun || !com.mineagent.engine.settings.Switches.hoard()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        // Following its owner: it stays with them (its stockpile waits till it is free).
        if (!onItsOwn && MineAgentEngine.getCompanionMode(companion.companionId()) == MineAgentEngine.CompanionMode.FOLLOW) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        var level = self.serverLevel();
        String dim = level.dimension().location().toString();
        var home = home(dim);
        if (home == null) return; // no base yet: nowhere to keep a stockpile
        var stash = stash(level, dim, home);
        if (stash == null) {
            if (self.getInventory().countItem(net.minecraft.world.item.Items.CHEST) > 0) {
                for (int r = 1; r <= 3 && stash == null; r++) {
                    for (var p : com.mineagent.engine.task.ProductionTask.ringOf(home, r)) {
                        var st = level.getBlockState(p);
                        if (!st.canBeReplaced() || !level.getFluidState(p).isEmpty()
                                || !com.mineagent.engine.pathing.util.BlockHelper.canStandOn(level.getBlockState(p.below()))) continue;
                        if (p.distToCenterSqr(self.getEyePosition()) > 20.0) continue; // within reach: else next time, nearer
                        if (com.mineagent.engine.act.Placement.placeBlock(self, p, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState())) {
                            noteOwnChest(dim, p.getX(), p.getY(), p.getZ());
                            noteChest(dim, p.getX(), p.getY(), p.getZ(), java.util.Map.of(), currentGameTickSafe());
                            journal.record("hoard", "a chest of my own by home, for my stockpile", currentGameTickSafe(), dim, p.getX(), p.getY(), p.getZ());
                            saveMemoryNow();
                            stash = p;
                            break;
                        }
                    }
                }
                if (stash == null) {
                    if (self.blockPosition().distSqr(home) > 16) {
                        goTo(new com.mineagent.engine.world.RouteMemory.Point(dim, home.getX(), home.getY(), home.getZ()), List.of(), false);
                    }
                    return;
                }
                // Its chest is down: on to filling it.
            } else {
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                        "hoard-chest-" + UUID.randomUUID(), "minecraft:chest", 1, null, false, true), reply -> { });
                return;
            }
        }
        // What it holds of its own: its pack and its own chests.
        java.util.Map<String, Integer> has = new java.util.HashMap<>();
        var inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty()) has.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        for (var c : chestMemory().near(dim, home.getX(), home.getY(), home.getZ(), 64.0)) {
            if (!ownsChest(dim, c.x(), c.y(), c.z())) continue;
            c.items().forEach((item, n) -> has.merge(item.contains(":") ? item : "minecraft:" + item, n, Integer::sum));
        }
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
        java.util.Set<String> without = onItsOwn ? java.util.Set.of() : java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN);
        // Slimeballs from its slime farm, when it has one (living on its own: "highly favoring of slime").
        var slimeFarm = placeKeeper.memory().find("slime chunk", com.mineagent.engine.world.PlaceKeeper.point(self));
        boolean fromFarm = slimeFarm.isPresent() && slimeFarm.get().at().dim().equals(dim);
        var next = com.mineagent.engine.gear.Hoard.next(has, t -> "minecraft:slime_ball".equals(t.item()) && fromFarm
                || com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, t.item(), t.count(), without).possible());
        if (next == null) return;
        journal.record("hoard", "for my stockpile: " + next.count() + " " + com.mineagent.engine.planning.production.ProductionPlanner.plural(next.item(), next.count()),
                currentGameTickSafe());
        if ("minecraft:slime_ball".equals(next.item()) && fromFarm) {
            var at = slimeFarm.get().at();
            com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.SlimeFarmTask.SlimeRecord(
                    "hoard-slime-" + UUID.randomUUID(), at.x() - 8, at.z() - 8, at.y(), 16, next.count()), reply -> { });
            return;
        }
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                "hoard-" + UUID.randomUUID(), next.item(), next.count(), stash, false, !onItsOwn), reply -> { });
    }

    // ── Trading mastery: the villagers it keeps (SMH plan 70) ──

    /** Villagers it keeps (walled in, traded to master), by their ids; kept with the world. */
    private final java.util.Set<String> keepers = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    public void keepVillager(UUID id) {
        if (id != null && keepers.size() < 256) keepers.add(id.toString());
        saveMemoryNow();
    }

    public java.util.Set<String> keepers() {
        synchronized (keepers) {
            return java.util.Set.copyOf(keepers);
        }
    }

    public void keepers(java.util.Collection<String> ids) {
        keepers.clear();
        if (ids != null) for (String s : ids) if (s != null && keepers.size() < 256) keepers.add(s);
    }

    /**
     * With nothing to do, near a village and with no Mending librarian of its own yet: it asks once (taking a yes after
     * a minute) and rolls one, fixes it and walls it in ({@code behaviour.tradingMastery}).
     */
    private void masterTrading(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || !com.mineagent.engine.settings.Switches.tradingMastery()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        var level = self.serverLevel();
        boolean haveMending = false;
        boolean candidate = false;
        for (var v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, self.getBoundingBox().inflate(48, 12, 48),
                net.minecraft.world.entity.npc.Villager::isAlive)) {
            if (keepers.contains(v.getUUID().toString())
                    && com.mineagent.engine.villager.VillagerWorth.sells(com.mineagent.engine.task.LibrarianTask.offers(v), "minecraft:mending")) haveMending = true;
            var prof = v.getVillagerData().getProfession();
            if (!v.isBaby() && (prof == net.minecraft.world.entity.npc.VillagerProfession.NONE
                    || prof == net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN && v.getVillagerXp() == 0)) candidate = true;
        }
        if (!haveMending && candidate) {
            var answer = answer("villagers");
            if (answer.isEmpty()) {
                ask("villagers", "There's a villager here I could make a Mending librarian: a lectern put down and rolled until it sells"
                        + " Mending, one bought, and the villager walled in with it to trade from. Shall I?", true);
                return;
            }
            if (answer.get()) {
                journal.record("mastery", "rolling a librarian for Mending", currentGameTickSafe());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.LibrarianTask.RollRecord(
                        "mastery-" + UUID.randomUUID(), "minecraft:mending"), reply -> { });
                return;
            }
        }
        trainKeepers(self, level);
    }

    /** What to run once a task it started for itself is done (by task id), and what is ready to run on the next tick. */
    private final java.util.Map<String, Runnable> afterTask = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Queue<Runnable> afterReady = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** Keepers it has said are masters, so it says so once. */
    private final java.util.Set<String> mastersSaid = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * Its keepers trained to master over many trades (SMH plan 70: "max out their level over the course of many trades"):
     * the first keeper near that isn't a master yet, traded with a round at a time ({@link
     * com.mineagent.engine.villager.VillagerWorth#toTrain}): its cheap goods sold to it for emeralds (made first when it
     * has none, from its own things), or emeralds spent above a reserve. A villager restocks at its job site, so the
     * next round comes later.
     */
    private boolean trainKeepers(net.minecraft.server.level.ServerPlayer self, net.minecraft.server.level.ServerLevel level) {
        java.util.Map<String, Integer> has = new java.util.HashMap<>();
        var inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty()) has.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
        var unseen = java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN);
        java.util.Map<String, Boolean> makeable = new java.util.HashMap<>();
        java.util.function.Predicate<String> canMake = item -> makeable.computeIfAbsent(item,
                k -> com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, k, 16, unseen).possible());
        for (var v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, self.getBoundingBox().inflate(48, 12, 48),
                net.minecraft.world.entity.npc.Villager::isAlive)) {
            String id = v.getUUID().toString();
            if (!keepers.contains(id)) continue;
            if (v.getVillagerData().getLevel() >= 5) {
                if (mastersSaid.add(id)) {
                    String prof = v.getVillagerData().getProfession().name();
                    speakToOwner("My " + (prof.isBlank() ? "villager" : prof) + " is a master now: every trade it has is open.");
                    journal.record("mastery", "a keeper made master", currentGameTickSafe());
                }
                continue;
            }
            List<com.mineagent.engine.villager.VillagerWorth.Deal> deals = new ArrayList<>();
            var offers = v.getOffers();
            for (int i = 0; i < offers.size(); i++) {
                var o = offers.get(i);
                if (!o.getCostB().isEmpty() || o.getCostA().isEmpty()) continue; // two-item trades: not for training
                deals.add(new com.mineagent.engine.villager.VillagerWorth.Deal(i,
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(o.getCostA().getItem()).toString(), o.getCostA().getCount(),
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).toString(), o.getResult().getCount(),
                        o.getXp(), o.getMaxUses() - o.getUses()));
            }
            var d = com.mineagent.engine.villager.VillagerWorth.toTrain(deals, has, canMake);
            if (d == null) continue;
            int xpLeft = com.mineagent.engine.villager.VillagerWorth.toMaster(v.getVillagerXp());
            int times = com.mineagent.engine.villager.VillagerWorth.trainTimes(d, has, xpLeft);
            if (times <= 0) continue;
            UUID who = v.getUUID();
            String what = com.mineagent.engine.planning.production.ProductionPlanner.plural(d.cost(), d.costCount() * times);
            Runnable trade = () -> com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.TradeTask.TradeRecord(
                    "train-" + UUID.randomUUID(), d.result(), times, d.cost(), who), reply -> { });
            int carried = has.getOrDefault(d.cost(), 0);
            if (carried >= d.costCount() * times) {
                journal.record("mastery", "training a keeper: " + times + " trades of " + what, currentGameTickSafe());
                trade.run();
            } else {
                String job = "train-make-" + UUID.randomUUID();
                afterTask.put(job, trade);
                journal.record("mastery", "making " + (d.costCount() * times - carried) + " " + what + " to train a keeper", currentGameTickSafe());
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                        job, d.cost(), d.costCount() * times - carried, null, false, true), reply -> { });
            }
            return true;
        }
        return false;
    }

    /** Whether it steps in for its owner (unless told "don't protect me"). */
    private volatile boolean protectOwner = true;

    public boolean protectsOwner() {
        return protectOwner;
    }

    public void protectOwner(boolean on) {
        protectOwner = on;
    }

    public void chestsPermission(boolean allowed, boolean told) {
        this.chestsAllowed = allowed;
        this.chestsTold = told;
    }

    /** Before the first take from the chests in a world: said once, and how to stop it. */
    public void beforeTakingFromChests(int n, String what, net.minecraft.core.BlockPos chest) {
        if (chestsTold || !com.mineagent.engine.settings.Switches.manners()) return;
        chestsTold = true;
        speakToOwner("I'll take " + n + " " + what + " from your chest at " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ()
                + ". Say \"leave my chests\" and I won't use them.");
        saveMemoryNow();
    }

    /** Chests with a purpose in this world (MA9.2): "this chest is for tools". */
    private final com.mineagent.engine.world.ChestPurposes chestPurposes = new com.mineagent.engine.world.ChestPurposes();

    public com.mineagent.engine.world.ChestPurposes chestPurposes() {
        return chestPurposes;
    }
    /** Its owner's last words code didn't understand, and the last fixed phrase used (with when, game ticks). */
    private String lastUnparsed;
    private long lastUnparsedAt;
    private String lastFixedPhrase;
    private long lastFixedAt;

    public com.mineagent.engine.chat.PhraseBook phraseBook() {
        return phraseBook;
    }

    /** "When I say X, I mean Y", and "no, that's not what I meant" after a fixed phrase. True when that was the message. */
    private boolean phraseLesson(String said) {
        var teach = java.util.regex.Pattern.compile("^when i say (.{2,60}?),? i mean (.{2,120})$").matcher(said);
        if (teach.matches()) {
            String meaning = teach.group(2).strip();
            var c = com.mineagent.engine.chat.CommandParser.parse(meaning, companion.companionName());
            if (c.isEmpty() || !learnable(c.get().kind())) {
                speakToOwner("I can only learn a phrase for an order I already know.");
                return true;
            }
            phraseBook.teach(teach.group(1), meaning);
            speakToOwner("Got it: \"" + teach.group(1).strip() + "\" means \"" + meaning + "\".");
            return true;
        }
        boolean correction = said.matches("^(?:no,? )?(?:that'?s not what i meant|i didn'?t mean that|not that|wrong one)$");
        if (correction && lastFixedPhrase != null && currentGameTickSafe() - lastFixedAt <= 600) {
            String w = lastFixedPhrase;
            phraseBook.correct(w);
            lastFixedPhrase = null;
            haltNow();
            speakToOwner("Sorry. I won't read \"" + w + "\" that way again.");
            return true;
        }
        return false;
    }

    /** Three times they said something it didn't follow and then put it another way it did: the phrase is learned. */
    private void learnPhrase(String said, java.util.Optional<com.mineagent.engine.chat.CommandParser.Command> command) {
        long now = currentGameTickSafe();
        if (command.isEmpty()) {
            if (said.split(" ").length <= 8) {
                lastUnparsed = said;
                lastUnparsedAt = now;
            }
            return;
        }
        if (lastUnparsed != null && now - lastUnparsedAt <= 600 && learnable(command.get().kind())
                && phraseBook.rephrased(lastUnparsed, said)) {
            speakToOwner("I'll take \"" + lastUnparsed + "\" to mean \"" + said + "\" from now on.");
        }
        lastUnparsed = null;
    }

    /** Orders a phrase may stand for: never one that names someone to fight or guard, or changes how it works. */
    private static boolean learnable(com.mineagent.engine.chat.CommandParser.Kind k) {
        return switch (k) {
            case HUNT_PLAYER, GUARD_PLAYER, MODE, AFK, BACK, CANCEL, STOP -> false;
            default -> true;
        };
    }

    /** "Don't touch my garden": a protected place, saved with its memory; "you can touch the garden": not any more. */
    private boolean protectCommand(com.mineagent.engine.chat.CommandParser.Command c) {
        var self = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
        if (self == null || self.serverPlayer() == null) return false;
        var sp = self.serverPlayer();
        var owner = self.serverPlayerOwner();
        String name = c.value();
        long tick = currentGameTickSafe();
        if (c.kind() == com.mineagent.engine.chat.CommandParser.Kind.UNPROTECT) {
            boolean was = placeKeeper.memory().forget(name, "protected");
            syncProtected();
            saveMemoryNow();
            speakToOwner(was ? "All right: the " + name + " isn't off limits any more." : "I wasn't keeping out of any " + name + ".");
            return true;
        }
        var at = owner != null ? owner.blockPosition() : sp.blockPosition();
        if (owner != null && lookedAt(owner) instanceof net.minecraft.core.BlockPos b && b.distSqr(owner.blockPosition()) <= 24 * 24) at = b;
        String dim = (owner != null ? owner : sp).serverLevel().dimension().location().toString();
        placeKeeper.memory().name("protected", name, new com.mineagent.engine.world.RouteMemory.Point(dim, at.getX(), at.getY(), at.getZ()), tick);
        syncProtected();
        saveMemoryNow();
        journal.record("order", "keep out of the " + name, tick, dim, at.getX(), at.getY(), at.getZ());
        speakToOwner("I won't touch the " + name + ": nothing broken or built within " + Math.round(com.mineagent.engine.world.ProtectedAreas.RADIUS)
                + " blocks of " + at.getX() + ", " + at.getY() + ", " + at.getZ() + ".");
        return true;
    }

    /** Its protected places, as it remembers them, set where every companion's hands check them. */
    private void syncProtected() {
        List<com.mineagent.engine.world.ProtectedAreas.Area> areas = new ArrayList<>();
        for (var p : placeKeeper.memory().places()) {
            if ("protected".equals(p.kind())) {
                areas.add(new com.mineagent.engine.world.ProtectedAreas.Area(p.name(), p.at().dim(), p.at().x(), p.at().y(), p.at().z(),
                        com.mineagent.engine.world.ProtectedAreas.RADIUS));
            }
        }
        com.mineagent.engine.world.ProtectedAreas.set(companion.companionId(), areas);
    }

    // ── The progress record (SMH plan 70, MA10.3) ──

    private final com.mineagent.engine.learning.ProgressRecord progress = new com.mineagent.engine.learning.ProgressRecord();
    /** What it carried and where it stood at the last look, for items gained and ground covered. */
    private Map<String, Integer> carriedBefore;
    private net.minecraft.core.BlockPos stoodBefore;

    public com.mineagent.engine.learning.ProgressRecord progress() {
        return progress;
    }

    /**
     * Every ten seconds: what it gained since the last look, how far it got, and the stall alarm, once, when it has
     * an order in hand and nothing has moved for five minutes.
     */
    private void watchProgress(net.minecraft.server.level.ServerPlayer self) {
        if (self == null) return;
        long now = currentGameTickSafe();
        Map<String, Integer> carried = new java.util.HashMap<>();
        var inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty()) carried.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        if (carriedBefore != null) {
            for (var e : carried.entrySet()) progress.gained(e.getKey(), e.getValue() - carriedBefore.getOrDefault(e.getKey(), 0), now);
        }
        carriedBefore = carried;
        var here = self.blockPosition();
        if (stoodBefore != null && stoodBefore.distSqr(here) > 64) progress.moved(now);
        stoodBefore = here;
        boolean busy = busyWithOrder();
        if (progress.stalled(busy, now)) {
            String what = goalBook.active().map(g -> com.mineagent.engine.goal.Orders.withoutMark(g.text())).orElse("what you asked");
            journal.record("stall", "nothing moved for 5 minutes on " + what, now);
            speakToOwner("Nothing has moved on \"" + what + "\" for five minutes; I may be stuck. Say \"why\" for what's in the way.");
        }
    }

    // ── Taking the other half of the job (SMH plan 70, MA10.4) ──

    private final com.mineagent.engine.partner.OwnerWork ownerWork = new com.mineagent.engine.partner.OwnerWork();
    /** When it last lit a miner's trail, and last brought a builder each block (game ticks). */
    private long partnerLitAt = Long.MIN_VALUE / 2;
    private final Map<String, Long> partnerBrought = new java.util.HashMap<>();

    public com.mineagent.engine.partner.OwnerWork ownerWork() {
        return ownerWork;
    }

    /**
     * With nothing of its own to do ({@code behaviour.partner}), the other half of its owner's job: a miner's tunnel lit
     * behind them; a builder brought more of the block they are placing before they run out. Their orders always come
     * first: only when it has no task, goal or guard duty.
     */
    private void partner(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended || !com.mineagent.engine.settings.Switches.partner()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding() || skillRuntime.active()) return;
        var owner = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e.serverPlayerOwner() : null;
        if (owner == null || !owner.isAlive() || owner.serverLevel() != self.serverLevel() || ownerActivity.afk()) return;
        if (owner.distanceTo(self) > 32) return;
        long now = currentGameTickSafe();
        var level = self.serverLevel();
        switch (ownerWork.doing(now)) {
            case MINING -> {
                if (now - partnerLitAt < 400) return;
                if (self.getInventory().countItem(net.minecraft.world.item.Items.TORCH) == 0) {
                    mineBeside(self, now);
                    return;
                }
                // The trail behind them: a dark spot a mob could stand on near where they have dug.
                var trail = ownerWork.trail(now);
                net.minecraft.core.BlockPos dark = null;
                for (int[] t : trail) {
                    var at = new net.minecraft.core.BlockPos(t[0], t[1], t[2]);
                    for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
                        if (com.mineagent.engine.task.LightTask.spawnable(level, p)
                                && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) == 0) {
                            dark = at;
                            break;
                        }
                    }
                    if (dark != null) break;
                }
                if (dark == null) {
                    mineBeside(self, now);
                    return;
                }
                partnerLitAt = now;
                journal.record("partner", "lighting the tunnel behind you", now);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.LightTask.LightRecord(
                        "partner-light-" + UUID.randomUUID(), dark, 5), reply -> { });
            }
            case FARMING -> {
                // Harvesting and replanting beside them: the crop they are working, the ripe ones near them.
                if (now - partnerFarmedAt < 600) return;
                String crop = ownerWork.farming(now);
                if (crop == null) return;
                var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.tryParse(crop));
                if (!(block instanceof net.minecraft.world.level.block.CropBlock kind)) return;
                int ripe = 0;
                for (var p : net.minecraft.core.BlockPos.betweenClosed(owner.blockPosition().offset(-12, -3, -12), owner.blockPosition().offset(12, 3, 12))) {
                    var st = level.getBlockState(p);
                    if (st.getBlock() == block && kind.isMaxAge(st)) ripe++;
                }
                if (ripe < 2) return;
                var item = cropItem(kind);
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                partnerFarmedAt = now;
                int have = self.getInventory().countItem(item);
                journal.record("partner", "harvesting the " + com.mineagent.engine.planning.production.ProductionPlanner.plural(itemId, 2) + " beside you", now);
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                        "partner-farm-" + UUID.randomUUID(), itemId, have + Math.min(32, ripe), null, false, true), reply -> { });
            }
            case BUILDING -> {
                String block = ownerWork.placing(now);
                if (block == null) return;
                var item = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.tryParse(block)).asItem();
                if (item == net.minecraft.world.item.Items.AIR) return;
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                if (owner.getInventory().countItem(item) >= 16) return;
                if (now - partnerBrought.getOrDefault(itemId, Long.MIN_VALUE / 2) < 1_200) return;
                int mine = self.getInventory().countItem(item);
                partnerBrought.put(itemId, now);
                String name = com.mineagent.engine.planning.production.ProductionPlanner.plural(itemId, 2);
                if (mine >= 8) {
                    journal.record("partner", "bringing you " + name, now);
                    speakToOwner("More " + name + " for you.");
                    com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.GiveTask.GiveRecord(
                            "partner-give-" + UUID.randomUUID(), itemId, Math.min(mine, 32)), reply -> { });
                    return;
                }
                var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
                var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
                if (com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, itemId, 32,
                        java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN)).possible()) {
                    journal.record("partner", "making you " + name, now);
                    partnerBrought.put(itemId, now - 1_200 + 200); // brought once they're made
                    com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                            "partner-make-" + UUID.randomUUID(), itemId, 32, null, false, true), reply -> { });
                }
            }
            default -> { }
        }
    }

    /** When it last harvested beside its owner (game ticks). */
    private long partnerFarmedAt = Long.MIN_VALUE / 2;
    /** When it last went for ore beside its owner (game ticks). */
    private long partnerMinedAt = Long.MIN_VALUE / 2;

    /**
     * Mining beside its owner (MA10.8): the ore their digging has laid open near them (a face to the air), the most worth
     * first, dug out and carried; no more than a few blocks at a time, and only with a pickaxe that takes it.
     */
    private void mineBeside(net.minecraft.server.level.ServerPlayer self, long now) {
        if (now - partnerMinedAt < 300) return;
        var trail = ownerWork.trail(now);
        if (trail.isEmpty()) return;
        var level = self.serverLevel();
        int[] last = trail.get(trail.size() - 1);
        var near = new net.minecraft.core.BlockPos(last[0], last[1], last[2]);
        net.minecraft.world.level.block.Block best = null;
        float bestWorth = 0;
        int count = 0;
        for (var p : net.minecraft.core.BlockPos.betweenClosed(near.offset(-6, -3, -6), near.offset(6, 3, 6))) {
            var st = level.getBlockState(p);
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
            if (!id.endsWith("_ore") || !open(level, p)) continue;
            boolean tool = false;
            var inv = self.getInventory();
            for (int i = 0; i < inv.getContainerSize() && !tool; i++) tool = inv.getItem(i).isCorrectToolForDrops(st);
            if (!tool) continue;
            float worth = com.mineagent.engine.survival.MiningEconomics.blockValue(st);
            if (best == null || worth > bestWorth) {
                best = st.getBlock();
                bestWorth = worth;
                count = 0;
            }
            if (st.getBlock() == best) count++;
        }
        if (best == null) return;
        partnerMinedAt = now;
        String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(best).toString();
        journal.record("partner", "mining the " + best.getName().getString().toLowerCase(java.util.Locale.ROOT) + " beside you", now);
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.tools.block.AutoMineTool.MineBlockTaskRecord(
                "partner-mine-" + UUID.randomUUID(), block, Math.min(4, Math.max(1, count)), 8), reply -> { });
    }

    /** A block with a face to the air (dug open, or a cave wall). */
    private static boolean open(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos p) {
        for (var d : net.minecraft.core.Direction.values()) if (level.getBlockState(p.relative(d)).isAir()) return true;
        return false;
    }

    // ── A better guard (SMH plan 70, MA10.1) ──

    /** The night it last lit the perimeter for (game days), the blocks people built inside it, whom it has reported. */
    private long litNight = -1;
    private java.util.Set<Long> guardBlocks;
    private final Map<UUID, Long> visitorsSaid = new java.util.HashMap<>();

    /**
     * On guard at a post ({@code behaviour.guardPerimeter}): the perimeter lit (a light job, once each watch and again
     * each dusk; farmland and crops get no torches), and what happens there reported in a few words: someone coming in
     * who isn't its owner's, and blocks people built there broken.
     */
    /** The points a guard walks between (its owner's "add a patrol point here"), and when it last moved on. */
    private final List<net.minecraft.world.phys.Vec3> patrol = new java.util.concurrent.CopyOnWriteArrayList<>();
    private int patrolAt;
    private long patrolMoved;

    private void guardWatch(net.minecraft.server.level.ServerPlayer self) {
        // On a patrol: on to the next point every 30 seconds (the fighting chain walks it there and keeps it).
        if (guardPost != null && patrol.size() >= 2 && currentGameTickSafe() - patrolMoved >= 600) {
            patrolMoved = currentGameTickSafe();
            patrolAt = (patrolAt + 1) % patrol.size();
            guardPost = patrol.get(patrolAt);
        }
        var post = guardPost;
        if (self == null || post == null || !com.mineagent.engine.settings.Switches.guardPerimeter()) return;
        var level = self.serverLevel();
        long now = currentGameTickSafe();
        var center = net.minecraft.core.BlockPos.containing(post);
        if (center.distSqr(self.blockPosition()) > (guardRadius + 32) * (guardRadius + 32)) return;
        // Lit: once a watch, and each night it is still on watch (dusk).
        long night = (level.getDayTime() + 1_000L) / 24_000L;
        boolean dusk = level.getDayTime() % 24_000L >= 11_000L;
        LiveBodyState body = liveBodyState.get();
        boolean idle = body == null || body.taskId() == null;
        if (idle && (litNight < 0 || dusk && litNight != night)) {
            litNight = night;
            journal.record("guard", "lighting the perimeter", now);
            com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.LightTask.LightRecord(
                    "guard-light-" + UUID.randomUUID(), center, (int) Math.round(guardRadius)), reply -> { });
            return;
        }
        // Someone coming in.
        var owner = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e.serverPlayerOwner() : null;
        for (var p : level.players()) {
            if (p == self || p == owner || p.isSpectator() || MineAgentEngine.isCompanionPlayer(p.getUUID())) continue;
            if (agentSettings.trusted().contains(p.getUUID().toString().toLowerCase(java.util.Locale.ROOT))) continue;
            if (p.position().distanceTo(post) > guardRadius) continue;
            if (now - visitorsSaid.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < CARE_PAUSE) continue;
            visitorsSaid.put(p.getUUID(), now);
            journal.record("guard", p.getName().getString() + " came inside the perimeter", now);
            speakToOwner("Someone's at the post: " + p.getName().getString() + ".");
        }
        // Blocks people built inside, counted; fewer than before is damage.
        var placed = com.mineagent.engine.world.PlacedBlocks.of(level);
        int r = (int) Math.ceil(guardRadius);
        java.util.Set<Long> there = new java.util.HashSet<>();
        for (var p : net.minecraft.core.BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 8, r))) {
            if (placed.isPlaced(p.getX(), p.getY(), p.getZ()) && !level.getBlockState(p).isAir()) there.add(p.asLong());
        }
        var before = guardBlocks;
        guardBlocks = there;
        if (before == null) return;
        int broken = 0;
        for (long k : before) if (!there.contains(k)) broken++;
        if (broken > 0) {
            String who = "";
            for (var p : level.players()) {
                if (p != self && p != owner && !MineAgentEngine.isCompanionPlayer(p.getUUID()) && p.position().distanceTo(post) <= guardRadius + 4) {
                    who = " (" + p.getName().getString() + " is there)";
                }
            }
            journal.record("guard", broken + " built blocks broken at the post", now);
            speakToOwner("Damage at the post: " + broken + (broken == 1 ? " block" : " blocks") + " broken" + who + ".");
        }
    }

    /**
     * The stance for a fight against {@code mobType}: the model's, if it advised in the last 10 s, else the one the
     * game has rated best against this mob (a stance never tried gets its trial first: learning by results).
     */
    public com.mineagent.engine.combat.Tactics tacticsFor(String mobType) {
        return choiceFor(mobType).tactics();
    }

    /**
     * How to fight {@code mobType}, and the name the fight is rated under: the model's advice of the last 10 s (kept
     * in the tactic book as a variant of its own), else the way the game has rated best against this mob, the four
     * stances and the kept variants alike (one never tried gets its trial first).
     */
    public com.mineagent.engine.combat.TacticBook.Choice choiceFor(String mobType) {
        double flee = MineAgentEngine.getConfig().survival().healthFlee();
        var advised = advisedTactics;
        if (advised != null && System.currentTimeMillis() - advisedAtMs < 10_000L) {
            String name = tacticBook.adopt(mobType, advised, flee, scorecard, System.currentTimeMillis());
            return new com.mineagent.engine.combat.TacticBook.Choice(name, advised);
        }
        return tacticBook.best(mobType, scorecard, flee);
    }

    /** The model's word on which mob to strike first, mid-fight (the rest of its advice waits for the next fight). */
    public List<String> advisedTargetOrder() {
        var advised = advisedTactics;
        return advised != null && System.currentTimeMillis() - advisedAtMs < 10_000L ? advised.targetOrder() : List.of();
    }

    /** Every way it may fight this mob, rated: the four stances, then the variants kept. */
    public List<String> tacticNames(String mobType) {
        return tacticBook.names(mobType);
    }

    /** A fight began against {@code mobType}, fought in {@code stance}: measured from here (health, time, a death). */
    public void fightBegan(String mobType, String stance, net.minecraft.server.level.ServerPlayer self) {
        if (fightEpisode.active()) return;
        fightMob = mobType;
        fightEpisode.begin("fight:" + mobType + ":" + stance, currentGameTickSafe(), self.getHealth(), self.getMaxHealth(), conditionsOf(self));
    }

    /** The fight ended: won, broken off, or lost; rated and kept, with a lesson when it went badly. */
    public void fightEnded(com.mineagent.engine.learning.Scorecard.Outcome outcome) {
        if (!fightEpisode.active()) return;
        journal.record("fight", switch (outcome) {
            case DONE -> "won a fight";
            case RETREATED -> "got away from a fight";
            default -> "a fight ended: " + outcome.name().toLowerCase(java.util.Locale.ROOT);
        }, currentGameTickSafe());
        if (outcome == com.mineagent.engine.learning.Scorecard.Outcome.DONE) ownerActivity.wonFight();
        var episode = fightEpisode.end(outcome, currentGameTickSafe());
        double score = scorecard.record(episode, currentGameTickSafe());
        advisedTactics = null;
        // A variant that has had its trial and does worse than the best stance is dropped.
        for (String dropped : tacticBook.prune(fightMob, scorecard)) {
            onBodyLog("[TACTICS] dropped " + dropped + " against " + fightMob + ": the game rated it worse than the best stance");
        }
        if (episode != null && (episode.died() || outcome != com.mineagent.engine.learning.Scorecard.Outcome.DONE || score < 60)) {
            writeLesson("fighting " + fightMob, episode, score);
        }
    }

    /**
     * The model's advice for the fight under way (smh/fight, queued first by SMH), every 2 s at most and one at a
     * time, on its own thread. The fight never waits: the rules and learning's stance fight it until advice comes,
     * and the advice is checked (Tactics.parse) and stays under the mob table's "never" rules.
     */
    public void adviseFight(String situation) {
        // The calm call rate keeps the model for the owner's words; the fight runs on the rules and learning alone.
        if ("calm".equals(agentSettings.callRate())) return;
        long now = System.currentTimeMillis();
        if (now - lastAdviceMs < 2_000L || !adviceInFlight.compareAndSet(false, true)) return;
        lastAdviceMs = now;
        double flee = MineAgentEngine.getConfig().survival().healthFlee();
        var request = new LLMRequest(baseUrl, apiKey, com.mineagent.engine.llm.RoleRouting.modelFor(model, "fight"),
                List.of(ChatMessage.system(FIGHT_PROMPT), ChatMessage.user(situation)),
                List.of(), List.of(), temperature, 160, null, null, null, null, Duration.ofSeconds(8), false);
        try {
            fightExecutor.submit(() -> {
                try {
                    com.mineagent.engine.combat.Tactics.parse(replyContent(sideCall(request)), flee).ifPresent(t -> {
                        advisedTactics = t;
                        advisedAtMs = System.currentTimeMillis();
                        // Kept as a way to fight this mob, to be tried and rated in later fights.
                        String mob = fightMob;
                        if (mob != null && !mob.isBlank()) tacticBook.adopt(mob, t, flee, scorecard, advisedAtMs);
                    });
                } catch (Exception e) {
                    System.err.println("[MineAgent] Fight advice failed: " + e.getMessage());
                } finally {
                    adviceInFlight.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            adviceInFlight.set(false);
        }
    }

    private static final String FIGHT_PROMPT = "You advise an AI companion in a Minecraft fight, every two seconds. "
            + "Read the fight (JSON: the hostiles, the companion, its duty, and how each stance has done against this mob, "
            + "as the game measured it) and answer with JSON only: {\"stance\":\"aggressive|balanced|defensive|kite|retreat\","
            + "\"target_order\":[\"mob type\"],\"retreat_health\":8,\"shield\":true,\"crits\":true}. Prefer the stance with the "
            + "best record unless this fight is clearly different. Kite skeletons and witches only with good reason; never "
            + "press a creeper without a shield. Retreat when outnumbered and hurt. You may also try a new combination: "
            + "it is kept, rated by the game like the stances, and dropped if it does worse.";

    /** Save this companion's memory and library now, on this thread (the game tests; shutdown saves the same way). */
    public void saveMemoryBlocking() {
        if (persistence != null) persistence.saveAll();
        var s = store;
        if (s != null) s.flushBlocking();
    }

    /**
     * Open the bigger memory beside the memory files (MA5.7), put back any changes a crash left only in its journal,
     * write every minute from its own thread, and report what it holds: counts and sizes, never what is in it.
     */
    private void openStore(java.nio.file.Path dir) {
        try {
            var s = new com.mineagent.engine.memory.store.MemoryStore(dir);
            placeKeeper.attachStore(s, com.mineagent.engine.settings.Switches.memory().thingsSeen());
            journal.attach(s, com.mineagent.engine.settings.Switches.memory().journalDays());
            chests = new com.mineagent.engine.memory.ChestMemory(s, com.mineagent.engine.settings.Switches.memory().chests());
            int replayed = s.replayJournals();
            s.startAutoFlush(60);
            store = s;
            var r = s.report();
            System.out.println("[MineAgent] Memory for " + companion.companionName() + ": " + r.counts() + "; " + r.files()
                    + " files, " + (r.bytesOnDisk() / 1024) + " KB on disk"
                    + (replayed > 0 ? "; " + replayed + " changes put back from the journal" : "")
                    + (r.recovered() > 0 ? "; " + r.recovered() + " files put back from their last good copy" : "")
                    + (r.quarantined() > 0 ? "; " + r.quarantined() + " damaged files set aside" : ""));
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("[MineAgent] Memory store could not open: " + e.getMessage());
        }
    }

    /**
     * After a crash the world is back at its last save, and its game time with it (the game saves that with the
     * world), so anything it noted at a later tick happened after that save (SMH plan 70, MA5.9). Chests seen since
     * are looked in again before it relies on them; the journal says so. Its own body and items are saved with the
     * world, so they went back with it.
     */
    private void checkAfterWorldSave() {
        long worldTime;
        try {
            worldTime = TaskContext.serverPlayer(companion).level().getGameTime();
        } catch (Throwable notYet) {
            return; // no world time to go by: nothing is forgotten on a guess
        }
        int unsure = chests.forgetAfter(worldTime);
        long later = journal.recent(200).stream().filter(e -> e.tick() > worldTime).count();
        if (unsure == 0 && later == 0) return;
        journal.record("note", "the world went back to an earlier save; what I noted after it may not be so"
                + (unsure > 0 ? ", so I'll look in " + unsure + (unsure == 1 ? " chest" : " chests") + " again before relying on them" : ""),
                worldTime);
        System.out.println("[MineAgent] Memory for " + companion.companionName() + ": the world is older than " + later
                + " journal entries; " + unsure + " chests to look in again");
    }

    /** Chests it looks in go into its chest memory; a rare find into its journal (MA5.7, MA5.9). */
    private void listenForMemory() {
        worldAssetIndex.onContainerLooked((pos, items, tick) -> noteChest(pos.dimension(), pos.x(), pos.y(), pos.z(), items, tick));
        placeKeeper.onRareSight(seen -> journal.record("find", "saw " + seen.what().replace('_', ' ')
                + (seen.kind() == com.mineagent.engine.memory.Sightings.Kind.ORE ? " ore" : "") + " at "
                + seen.x() + ", " + seen.y() + ", " + seen.z(), seen.last(), seen.dim(), seen.x(), seen.y(), seen.z()));
    }

    /** What a chest holds now, as it saw it: into its chest memory, and what changed into its journal. */
    public void noteChest(String dim, int x, int y, int z, java.util.Map<String, Integer> items, long tick) {
        var before = chests.looked(dim, x, y, z, items, tick);
        StringBuilder change = new StringBuilder();
        for (var e : before.entrySet()) {
            if (e.getValue() == 0) continue;
            if (change.length() > 0) change.append(", ");
            change.append(e.getValue() > 0 ? "+" : "").append(e.getValue()).append(' ').append(e.getKey().replace('_', ' '));
        }
        if (change.length() > 0) {
            boolean in = before.values().stream().anyMatch(v -> v > 0);
            journal.record(in ? "stored" : "took", "the chest at " + x + ", " + y + ", " + z + ": " + change, tick, dim, x, y, z);
        }
    }

    /**
     * "Fill this chest with stone bricks", "make me 64 torches" (SMH plan 70, production): the item from their words,
     * the chest they look at (or the nearest within 6 blocks of them), and the production task does the rest. Words it
     * can't match to an item go to the model.
     */
    private boolean produceCommand(com.mineagent.engine.chat.CommandParser.Command c, com.mineagent.engine.entity.CompanionEntity self,
                                   boolean whileAway) {
        var me = self.serverPlayer();
        var owner = self.serverPlayerOwner();
        if (me == null) return false;
        String words = c.value();
        boolean anyChest = words.startsWith("anychest|");
        net.minecraft.core.BlockPos at = null;
        if (words.startsWith("chestat|")) {
            // The words a goal keeps it in: the chest by where it is.
            String[] parts = words.split("\\|", 3);
            String[] xyz = parts[1].split(",");
            at = new net.minecraft.core.BlockPos(Integer.parseInt(xyz[0].strip()), Integer.parseInt(xyz[1].strip()), Integer.parseInt(xyz[2].strip()));
            words = "chest|" + parts[2];
        }
        boolean toChest = anyChest || words.startsWith("chest|");
        if (toChest) words = words.substring(words.indexOf('|') + 1);
        String item = FOOD_WORDS.matcher(words).matches() ? com.mineagent.engine.task.ProductionTask.FOOD
                : com.mineagent.engine.planning.production.GameKnowledge.itemFor(words);
        if (item == null) return false;
        int count = c.count() == null ? 1 : c.count();
        if (count < 0) {
            int stack = item.equals(com.mineagent.engine.task.ProductionTask.FOOD) ? 64 : new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(item))).getMaxStackSize();
            count = -count * stack;
        }
        net.minecraft.core.BlockPos chest = null;
        boolean placeChest = false;
        if (at != null) {
            chest = at;
        } else if (toChest) {
            chest = owner != null && owner.serverLevel() == me.serverLevel() ? chestMeant(owner) : null;
            if (chest == null && anyChest) {
                placeChest = true; // "a chest": none by them, so one of its own
            } else if (chest == null) {
                speakToOwner("Which chest? Stand by it, or look at it, and say it again.");
                return true;
            }
        }
        if (!whileAway) {
            haltNow();
            standDown();
        }
        journal.record("order", "make " + (count == 0 ? "a chest full of" : count) + " " + words, currentGameTickSafe());
        // In the goal book in words that mean the same job after a restart (MA6.2): that chest, by where it is.
        orderGoal(chest != null ? (count == 0 ? "fill the chest at " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ() + " with " + words
                : "put " + count + " " + words + " in the chest at " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ())
                : placeChest ? "fill up a chest with " + words : "make me " + Math.max(1, count) + " " + words);
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                "produce-" + UUID.randomUUID(), item, count, chest, placeChest), reply -> { });
        return true;
    }

    // ── The goal spine: orders in the goal book, run by code (SMH plan 70, MA6.2, MA6.3, MA6.5) ──────────

    /** The goal whose order it is running now (null: none), since when, and whether its task has been seen running. */
    private String orderGoalId;
    private long orderSince;
    private boolean orderSeen;
    /** Set while the heartbeat dispatches the active goal's order, so the order's handler doesn't make a second goal. */
    private String dispatchingGoal;
    /** Ticks a standing order waits before it is done again. */
    static final long STANDING_PAUSE = 1_200L;
    /** How the last order's task ended, and what it said. */
    private volatile TaskState lastOrderState;
    private volatile String lastOrderNote = "";
    /** Per goal, the rungs of the recovery ladder used so far (MA6.4). */
    private final Map<String, Integer> orderRungs = new java.util.HashMap<>();

    /** An order given now: in the goal book as the active goal (what it interrupts comes back after), being run. */
    private void orderGoal(String words) {
        String id = dispatchingGoal;
        if (id == null) {
            try {
                var g = goalBook.urgent(words, "owner", currentGameTickSafe(), null);
                id = g.id();
                saveMemoryNow();
            } catch (RuntimeException full) {
                return; // the goal book is full: it does the order, unlisted
            }
        }
        orderGoalId = id;
        orderSince = currentGameTickSafe();
        orderSeen = false;
        lastOrderState = null;
    }

    /** The order a goal's words are, when code runs it: production and mining, so far. */
    private static java.util.Optional<com.mineagent.engine.chat.CommandParser.Command> orderIn(String goalText, String name) {
        var c = com.mineagent.engine.chat.CommandParser.parse(com.mineagent.engine.goal.Orders.withoutMark(goalText), name);
        if (c.isEmpty()) return c;
        var k = c.get().kind();
        return k == com.mineagent.engine.chat.CommandParser.Kind.PRODUCE || k == com.mineagent.engine.chat.CommandParser.Kind.MINE_ORE
                || k == com.mineagent.engine.chat.CommandParser.Kind.GIVE || k == com.mineagent.engine.chat.CommandParser.Kind.BUILD
                || k == com.mineagent.engine.chat.CommandParser.Kind.HUNT_PLAYER || k == com.mineagent.engine.chat.CommandParser.Kind.LIGHT
                || k == com.mineagent.engine.chat.CommandParser.Kind.PORTAL || k == com.mineagent.engine.chat.CommandParser.Kind.ENCHANT
                || k == com.mineagent.engine.chat.CommandParser.Kind.TRADE || k == com.mineagent.engine.chat.CommandParser.Kind.BREW
                || k == com.mineagent.engine.chat.CommandParser.Kind.BREED || k == com.mineagent.engine.chat.CommandParser.Kind.FISH
                || k == com.mineagent.engine.chat.CommandParser.Kind.REPAIR || k == com.mineagent.engine.chat.CommandParser.Kind.GRIND
                || k == com.mineagent.engine.chat.CommandParser.Kind.BARTER || k == com.mineagent.engine.chat.CommandParser.Kind.PUT_AWAY
                || k == com.mineagent.engine.chat.CommandParser.Kind.TIDY || k == com.mineagent.engine.chat.CommandParser.Kind.FIND_STRONGHOLD
                || k == com.mineagent.engine.chat.CommandParser.Kind.APPLY_BOOK
                ? c : java.util.Optional.empty();
    }

    /**
     * The active goal, when it is an order code runs: dispatched; when its task ends, done (or, standing, done again),
     * and the next goal comes up. After a restart the active order is dispatched again and picks up from what is in the
     * world (the chest half full, the tunnel dug so far).
     */
    private void orderHeartbeat(net.minecraft.server.level.ServerPlayer player) {
        if (player == null || suspended || guarding()) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        var active = goalBook.active();
        if (active.isEmpty()) {
            orderGoalId = null;
            return;
        }
        var g = active.get();
        LiveBodyState body = liveBodyState.get();
        boolean busy = body != null && body.taskId() != null;
        long now = currentGameTickSafe(); // game ticks: a test server runs faster than the clock
        if (g.id().equals(orderGoalId)) {
            boolean reported = lastOrderState != null; // its task said how it ended, however quick it was
            if (busy && !reported) {
                orderSeen = true;
                return;
            }
            if (!reported && !orderSeen && now - orderSince < 200L) return; // its task is on its way
            orderGoalId = null;
            TaskState ended = lastOrderState;
            String note = lastOrderNote == null ? "" : lastOrderNote;
            lastOrderState = null;
            if (ended == TaskState.CANCELLED) {
                // Cut off by something else (a model's call), not stopped by its owner (that pauses the goal): again.
                orderSince = now;
                return;
            }
            if (ended == TaskState.FAILED) {
                if (recover(g, note)) return;
            } else {
                orderRungs.remove(g.id());
            }
            if (com.mineagent.engine.goal.Orders.stands(g.text())) {
                // Again, until it's stopped; a minute on, so a full chest is looked at, not hammered (MA9.2).
                orderSince = now + STANDING_PAUSE;
                return;
            }
            var next = goalBook.markDone(g.id());
            saveMemoryNow();
            next.ifPresent(n -> speakToOwner("Next: " + com.mineagent.engine.goal.Orders.withoutMark(n.text()) + "."));
            return;
        }
        // An owner's order doesn't wait on a model's turn (a chat reply, a practice idea): only on the body being free.
        if (busy || skillRuntime.active()) return;
        if (now - orderSince < 40L) return;
        var order = orderIn(g.text(), companion.companionName());
        if (order.isEmpty() || !(companion instanceof com.mineagent.engine.entity.CompanionEntity self)) return;
        dispatchingGoal = g.id();
        try {
            runCommand(order.get(), true);
        } finally {
            dispatchingGoal = null;
        }
        if (!g.id().equals(orderGoalId)) {
            // It couldn't start ("which chest?"): set aside, not tried every second.
            goalBook.pauseActive();
            saveMemoryNow();
        }
    }

    /**
     * The recovery ladder for an order that failed (MA6.4): the next rung that can help, used. True when the goal stays
     * (tried again, set aside for you, a tool made first); false when it is over (handed on, or given up), and the
     * queue moves on.
     */
    private boolean recover(com.mineagent.engine.planning.GoalBook.Goal g, String note) {
        var f = com.mineagent.engine.goal.RecoveryLadder.classify(note);
        int tried = orderRungs.getOrDefault(g.id(), 0);
        var self = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
        var owner = self == null ? null : self.serverPlayerOwner();
        boolean ownerHere = owner != null && !ownerActivity.afk();
        boolean model = System.currentTimeMillis() - lastSmhHintMs > 300_000L;
        var rung = com.mineagent.engine.goal.RecoveryLadder.next(f, tried, ownerHere, model);
        orderRungs.put(g.id(), com.mineagent.engine.goal.RecoveryLadder.after(rung));
        String what = com.mineagent.engine.goal.Orders.withoutMark(g.text());
        journal.record("recovery", rung + " for \"" + what + "\" (" + f + "): " + truncateForLog(note, 160), currentGameTickSafe());
        switch (rung) {
            case RETRY -> {
                orderSince = currentGameTickSafe();
                return true; // the heartbeat runs it again
            }
            case FIX -> {
                boolean shears = note.toLowerCase(java.util.Locale.ROOT).contains("shears");
                goalBook.chain(List.of("make me 1 " + (shears ? "shears" : "stone pickaxe")), "recovery", currentGameTickSafe());
                saveMemoryNow();
                speakToOwner("I need " + (shears ? "shears" : "a pickaxe") + " for that: making one first.");
                return true;
            }
            case VARIATION -> {
                // Its task already made what it could here; the rest is the next rung's.
                return recover(g, note);
            }
            case ASK -> {
                goalBook.pauseActive();
                saveMemoryNow();
                speakToOwner("I couldn't " + what + ": " + note + ". Say \"carry on\" and I'll try again, or \"cancel\" to leave it.");
                return true;
            }
            case PATCH -> {
                // The model, with a narrow brief; what code finished stays done.
                goalBook.markDone(g.id());
                orderRungs.remove(g.id());
                try {
                    goalBook.urgent(what + " (tried by code: " + truncateForLog(note, 120) + "; find another way)", "recovery", currentGameTickSafe(), null);
                } catch (RuntimeException full) {
                    speakToOwner("I've given up on " + what + ": " + note + ".");
                }
                saveMemoryNow();
                return true;
            }
            default -> {
                orderRungs.remove(g.id());
                speakToOwner("I've given up on " + what + ": " + note + ".");
                return false;
            }
        }
    }

    /**
     * "Fill this chest with stone bricks, then mine diamonds until further notice": the goals in that order, the chest
     * meant now pinned down in the words, what it was doing after them. False when the line is one order.
     */
    private boolean queueOrders(String line) {
        var orders = com.mineagent.engine.goal.Orders.split(line);
        if (orders.size() < 2) {
            // One order that stands ("make me 64 torches until I say stop"), when code runs it: a standing goal.
            var one = com.mineagent.engine.goal.Orders.order(line);
            if (!one.standing() || orderIn(one.text(), companion.companionName()).isEmpty()) return false;
            var whole = orderIn(line, companion.companionName());
            if (whole.isPresent() && whole.get().kind() == com.mineagent.engine.chat.CommandParser.Kind.MINE_ORE) {
                return false; // mining reads "until I say stop" itself
            }
            orders = List.of(one);
        }
        List<String> texts = new ArrayList<>();
        for (var o : orders) texts.add(pinned(o.text()) + (o.standing() ? com.mineagent.engine.goal.Orders.STANDS : ""));
        List<com.mineagent.engine.planning.GoalBook.Goal> made;
        try {
            made = goalBook.chain(texts, "owner", currentGameTickSafe());
        } catch (RuntimeException full) {
            return false;
        }
        saveMemoryNow();
        haltNow();
        standDown();
        orderGoalId = null;
        orderSince = 0;
        List<String> said = new ArrayList<>();
        for (var g : made) said.add(com.mineagent.engine.goal.Orders.withoutMark(g.text()) + (com.mineagent.engine.goal.Orders.stands(g.text()) ? " until you say stop" : ""));
        speakToOwner("Got it: " + String.join(", then ", said) + ".");
        journal.record("order", String.join(", then ", texts), currentGameTickSafe());
        return true;
    }

    /** An order's words with "this chest" pinned to the chest they mean now; other words as they are. */
    private String pinned(String words) {
        var c = com.mineagent.engine.chat.CommandParser.parse(words, companion.companionName());
        if (c.isEmpty() || c.get().kind() != com.mineagent.engine.chat.CommandParser.Kind.PRODUCE) return words;
        String v = c.get().value();
        if (!(v.startsWith("chest|") || v.startsWith("anychest|"))) return words;
        var self = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e : null;
        var owner = self == null ? null : self.serverPlayerOwner();
        var chest = owner == null ? null : chestMeant(owner);
        if (chest == null) return words;
        String what = v.substring(v.indexOf('|') + 1);
        int count = c.get().count() == null ? 0 : c.get().count();
        if (count < 0) return words;
        return count == 0 ? "fill the chest at " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ() + " with " + what
                : "put " + count + " " + what + " in the chest at " + chest.getX() + ", " + chest.getY() + ", " + chest.getZ();
    }

    /** It is running an owner's order now (a task of one, or its goal active). */
    public boolean busyWithOrder() {
        LiveBodyState body = liveBodyState.get();
        boolean task = body != null && body.taskId() != null;
        return task && (orderGoalId != null || goalBook.active().isPresent());
    }

    /** The night it last sheltered for, and the day it last tried to make a bed (MA8.3). */
    private long shelteredNight = -1, bedTriedDay = -1;

    /**
     * Night with its owner away and no bed (MA8.3): an emergency shelter until morning (two down or walled in, as the
     * AFK shelter), once a night, when it is out under the sky and has nothing to do. By day, with no bed, it makes one
     * as soon as it has what it takes here (a bed of the wool it carries or sheep in sight), once a day.
     */
    private void nightCare(net.minecraft.server.level.ServerPlayer player) {
        if (player == null || !com.mineagent.engine.settings.Switches.sleepWhenAway() || !ownerActivity.afk()) return;
        var level = player.serverLevel();
        if (!level.dimensionType().bedWorks()) return;
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null || goalBook.active().isPresent() || guarding()) return;
        long day = level.getDayTime() / 24_000L;
        boolean hasBed = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).getItem() instanceof net.minecraft.world.item.BedItem) hasBed = true;
        if (!hasBed) {
            var at = player.blockPosition();
            for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-24, -4, -24), at.offset(24, 4, 24))) {
                if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                    hasBed = true;
                    break;
                }
            }
        }
        if (!level.isDay()) {
            if (hasBed || shelteredNight == day || !level.canSeeSky(player.blockPosition().above())) return;
            shelteredNight = day;
            journal.record("night", "no bed: sheltering until morning", currentGameTickSafe());
            com.mineagent.api.task.TaskDispatch.dispatchAsync(companion,
                    new com.mineagent.engine.task.ShelterTask.ShelterRecord("night-" + UUID.randomUUID(), true), reply -> { });
            return;
        }
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(player);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(player, this, null);
        var none = java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN);
        if (!hasBed && bedTriedDay != day) {
            bedTriedDay = day;
            for (String bed : com.mineagent.engine.planning.production.GameKnowledge.family("any:bed")) {
                if (!com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, bed, 1, none).possible()) continue;
                journal.record("night", "making a bed for the nights", currentGameTickSafe());
                errand(bed, 1);
                return;
            }
        }
        // Living on its own (MA8.8): fed, armed, stocked, each by code from what it carries or sees, once a day each.
        if (!com.mineagent.engine.settings.Switches.liveOnOwn()) return;
        int food = 0, torches = 0, weapon = 0;
        var foods = com.mineagent.engine.planning.production.GameKnowledge.foods().keySet();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            if (foods.contains(id)) food += st.getCount();
            if (st.is(net.minecraft.world.item.Items.TORCH)) torches += st.getCount();
            weapon = Math.max(weapon, com.mineagent.engine.survival.MobDefenseChain.weaponTier(st));
        }
        int wantFood = com.mineagent.engine.survival.HardRules.tripFood(com.mineagent.engine.settings.Switches.readyFood(),
                level.getDifficulty() == net.minecraft.world.Difficulty.HARD);
        if (food < wantFood && ownTried.getOrDefault("food", -1L) != day) {
            ownTried.put("food", day);
            journal.record("own", "keeping fed: " + food + " food of " + wantFood, currentGameTickSafe());
            errand(com.mineagent.engine.task.ProductionTask.FOOD, wantFood - food);
            return;
        }
        if (weapon < 2 && ownTried.getOrDefault("sword", -1L) != day) {
            ownTried.put("sword", day);
            if (com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, "minecraft:stone_sword", 1, none).possible()) {
                journal.record("own", "arming: a stone sword", currentGameTickSafe());
                errand("minecraft:stone_sword", 1);
                return;
            }
        }
        int wantTorches = com.mineagent.engine.settings.Switches.readyTorches();
        if (torches < wantTorches && ownTried.getOrDefault("torches", -1L) != day) {
            ownTried.put("torches", day);
            if (com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, "minecraft:torch", wantTorches - torches, none).possible()) {
                journal.record("own", "stocking: torches", currentGameTickSafe());
                errand("minecraft:torch", wantTorches - torches);
            }
        }
    }

    // ── Hunting a named player (SMH plan 70, MA10.7) ──

    /** Where it last saw a player: their name, the dimension, the spot, and when (game ticks). */
    public record SeenPlayer(String name, String dim, net.minecraft.core.BlockPos at, long tick) {}

    private final Map<UUID, SeenPlayer> seenPlayers = new ConcurrentHashMap<>();

    /** Where it last saw this player, if it has. */
    public java.util.Optional<SeenPlayer> lastSeen(UUID player) {
        return java.util.Optional.ofNullable(seenPlayers.get(player));
    }

    /** The players it can see now (within 16 blocks, or in sight within 96), noted with where they are. */
    private void notePlayers(net.minecraft.server.level.ServerPlayer self) {
        if (self == null) return;
        String dim = self.serverLevel().dimension().location().toString();
        long now = currentGameTickSafe();
        for (var p : self.serverLevel().players()) {
            if (p == self || p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceTo(self);
            if (d <= 16 || d <= 96 && self.hasLineOfSight(p)) {
                seenPlayers.put(p.getUUID(), new SeenPlayer(p.getName().getString(), dim, p.blockPosition(), now));
            }
        }
        if (seenPlayers.size() > 256) {
            seenPlayers.entrySet().stream().min(java.util.Comparator.comparingLong(e -> e.getValue().tick()))
                    .ifPresent(e -> seenPlayers.remove(e.getKey()));
        }
    }

    /**
     * "Kill Steve" from its owner or a trusted player: Steve on its target list and hunted, by code. Never its owner, a
     * trusted player or a companion of the same owner. False when no player goes by that name (the words may mean
     * something else, for the model).
     */
    private boolean huntCommand(String who) {
        if (!(companion instanceof com.mineagent.engine.entity.CompanionEntity self) || self.serverPlayer() == null) return false;
        var sp = self.serverPlayer();
        var server = sp.getServer();
        if (server == null || who == null) return false;
        var target = server.getPlayerList().getPlayerByName(who);
        UUID id = target != null ? target.getUUID() : null;
        String name = target != null ? target.getName().getString() : null;
        if (id == null) {
            for (var e : seenPlayers.entrySet()) {
                if (e.getValue().name().equalsIgnoreCase(who)) {
                    id = e.getKey();
                    name = e.getValue().name();
                }
            }
        }
        if (id == null) return false;
        String key = id.toString().toLowerCase(java.util.Locale.ROOT);
        if (id.equals(self.ownerUuid()) || MineAgentEngine.isCompanionOwnedBy(id, self.ownerUuid())
                || agentSettings.trusted().contains(key) || MineAgentEngine.isCompanionPlayer(id) && id.equals(sp.getUUID())) {
            speakToOwner("I never fight " + name + ".");
            return true;
        }
        if (!com.mineagent.engine.settings.Switches.fightPlayers()) {
            speakToOwner("Fighting players is switched off (behaviour.fightPlayers).");
            return true;
        }
        if (target == null) {
            speakToOwner(name + " isn't on the server now.");
            return true;
        }
        if (!sp.canHarmPlayer(target)) {
            speakToOwner("Nobody can hurt " + name + " here (PvP is off), so I won't go after them.");
            return true;
        }
        if (!agentSettings.isAllowedTarget(id)) {
            var next = agentSettings.withTarget(id);
            if (next == agentSettings) {
                speakToOwner("My list of players to fight is full; say stop to clear it.");
                return true;
            }
            setAgentSettings(next);
            MineAgentEngine.persistCompanionNow(companion.companionId());
        }
        String words = "hunt " + name;
        journal.record("order", words, currentGameTickSafe());
        orderGoal(words);
        var seen = seenPlayers.get(id);
        speakToOwner("On it: hunting " + name + (seen == null ? "." : ", last seen at " + seen.at().getX() + ", " + seen.at().getY() + ", "
                + seen.at().getZ() + "."));
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.HuntTask.HuntRecord(
                "hunt-" + UUID.randomUUID(), name, id), reply -> { });
        return true;
    }

    // ── Looking after you (SMH plan 70, MA10.8) ──

    /**
     * Its owner died at {@code at} in {@code dim}: it goes for their things before they vanish (five minutes on the
     * ground) and brings them back, or puts them in the chest at home. False when it can't (off, another dimension, too
     * far, or they fell out of the world), and the caller does as before.
     */
    public boolean ownerDied(String dim, net.minecraft.core.BlockPos at) {
        if (!com.mineagent.engine.settings.Switches.recoverDrops() || suspended) return false;
        if (!(companion instanceof com.mineagent.engine.entity.CompanionEntity self) || self.serverPlayer() == null) return false;
        var sp = self.serverPlayer();
        if (!sp.serverLevel().dimension().location().toString().equals(dim)) return false;
        if (at.getY() < sp.serverLevel().getMinBuildHeight() || sp.blockPosition().distSqr(at) > 160 * 160) return false;
        long now = currentGameTickSafe();
        journal.record("owner", "you died at " + at.getX() + ", " + at.getY() + ", " + at.getZ() + ": fetching your things",
                now, dim, at.getX(), at.getY(), at.getZ());
        speakToOwner("I'll fetch your things from where you died.");
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.RecoverTask.RecoverRecord(
                "recover-" + UUID.randomUUID(), dim, at), reply -> { });
        return true;
    }

    /** When it last handed its owner food, and when it last offered each worn tool (game ticks). */
    private long fedOwnerAt = Long.MIN_VALUE / 2, noFoodSaidAt = Long.MIN_VALUE / 2;
    private final Map<String, Long> toolOffered = new java.util.HashMap<>();

    /** Five minutes between handing food, and between offers for the same tool. */
    static final long CARE_PAUSE = 6_000L;

    /**
     * Looking after its owner while they're about (MA10.8): food when they're low, from what it has beyond its own floor;
     * a word when the tool in their hand is nearly broken and it has, or can make, another.
     */
    private void lookAfterOwner(net.minecraft.server.level.ServerPlayer self) {
        if (self == null || suspended) return;
        if (agentSettings.workMode() == com.mineagent.engine.settings.WorkMode.PARKED) return;
        var owner = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e.serverPlayerOwner() : null;
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.serverLevel() != self.serverLevel()) return;
        if (ownerActivity.afk() || owner.distanceTo(self) > 32) return;
        long now = currentGameTickSafe();
        LiveBodyState body = liveBodyState.get();
        String task = body == null ? null : body.taskId();
        boolean handing = task != null && (task.startsWith("give-") || task.startsWith("feed-") || task.startsWith("recover-"));
        int hunger = owner.getFoodData().getFoodLevel();
        if (com.mineagent.engine.settings.Switches.feedOwner() && !handing && hunger <= 6 && now - fedOwnerAt >= CARE_PAUSE) {
            int give = Math.min(Math.max(2, Math.min(8, (20 - hunger + 5) / 6)), spareFood(self));
            if (give > 0) {
                fedOwnerAt = now;
                journal.record("owner", "you were low on food: handing you " + give, now);
                speakToOwner("You're low on food; here's some.");
                com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.GiveTask.GiveRecord(
                        "feed-" + UUID.randomUUID(), com.mineagent.engine.task.ProductionTask.FOOD, give), reply -> { });
                return;
            }
            if (now - noFoodSaidAt >= CARE_PAUSE) {
                noFoodSaidAt = now;
                speakToOwner("You're low on food, and I've none to spare.");
            }
        }
        if (!com.mineagent.engine.settings.Switches.offerTools()) return;
        var held = owner.getMainHandItem();
        if (held.isEmpty() || !held.isDamageableItem() || held.getMaxDamage() <= 0) return;
        int left = held.getMaxDamage() - held.getDamageValue();
        if (left > Math.max(3, held.getMaxDamage() / 20)) return;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (now - toolOffered.getOrDefault(id, Long.MIN_VALUE / 2) < CARE_PAUSE) return;
        toolOffered.put(id, now);
        String name = com.mineagent.engine.planning.production.ProductionPlanner.name(id);
        var inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && st.is(held.getItem()) && st.getMaxDamage() - st.getDamageValue() > left) {
                speakToOwner("Your " + name + " is nearly broken. I have another: say \"give me " + aOrAn(name) + "\".");
                return;
            }
        }
        var book = com.mineagent.engine.planning.production.GameKnowledge.book(self);
        var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
        if (com.mineagent.engine.planning.production.ProductionPlanner.plan(book, have, id, 1,
                java.util.Set.of(com.mineagent.engine.planning.production.Production.UNSEEN)).possible()) {
            speakToOwner("Your " + name + " is nearly broken. I can make another: say \"make me " + aOrAn(name) + "\".");
        }
    }

    /** Food it can hand over: what it carries beyond its own floor (half what it takes on a trip, at least 2). */
    private int spareFood(net.minecraft.server.level.ServerPlayer self) {
        var foods = com.mineagent.engine.planning.production.GameKnowledge.foods().keySet();
        int food = 0;
        var inv = self.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && foods.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString())) {
                food += st.getCount();
            }
        }
        return Math.max(0, food - Math.max(2, com.mineagent.engine.settings.Switches.readyFood() / 2));
    }

    /** What it last tried on its own, by need, and the day it tried (MA8.8): once a day each, never a loop. */
    private final Map<String, Long> ownTried = new java.util.HashMap<>();

    /** A production errand from what it carries or sees (no searching), into its own pack. */
    private void errand(String item, int count) {
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion, new com.mineagent.engine.task.ProductionTask.ProduceRecord(
                "own-" + UUID.randomUUID(), item, count, null, false, true), reply -> { });
    }

    /**
     * "Prime the world" (SMH plan 70, MA9.3): a starting point for both of you, as orders in a row that code runs:
     * the hut, the iron (mined at its height), the gear, beds, food, torches, the area lit, and your share handed over.
     * The amounts are settings (behaviour.primeMeat, primeTorches, primeFullIron).
     */
    String primeOrders() {
        boolean full = com.mineagent.engine.settings.Switches.primeFullIron();
        int meat = com.mineagent.engine.settings.Switches.primeMeat(), torches = com.mineagent.engine.settings.Switches.primeTorches();
        // Iron each: a helmet 5; a full set 24, a sword 2, a pickaxe 3; and the shears for wool, 2.
        int iron = 2 * (full ? 29 : 5) + 2;
        // Only what it is short of: raw iron and ingots it carries or has in chests it knows count.
        var self = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e.serverPlayer() : null;
        if (self != null) {
            var have = com.mineagent.engine.planning.production.GameKnowledge.have(self, this, null);
            iron -= have.count("minecraft:raw_iron") + have.count("minecraft:iron_ingot");
        }
        List<String> o = new ArrayList<>();
        o.add("build a hut");
        if (iron > 0) o.add("mine " + iron + " iron");
        if (full) {
            for (String piece : List.of("iron helmets", "iron chestplates", "iron leggings", "iron boots", "iron swords", "iron pickaxes")) {
                o.add("make me 2 " + piece);
            }
        } else {
            o.add("make me 2 iron helmets");
        }
        o.add("make me 2 white beds");
        if (meat > 0) o.add("make me " + (2 * meat) + " food");
        if (torches > 0) o.add("make me " + (2 * torches) + " torches");
        o.add("light up the area");
        // Yours: into your hands (or kept for you while you're away).
        o.add("give me 1 iron helmet");
        if (full) for (String piece : List.of("iron chestplate", "iron leggings", "iron boots", "iron sword", "iron pickaxe")) o.add("give me 1 " + piece);
        o.add("give me 1 white bed");
        if (meat > 0) o.add("give me " + meat + " food");
        if (torches > 0) o.add("give me " + torches + " torches");
        return String.join(", then ", o);
    }

    /** "What's next": the goals after this one, from its goal book. */
    private String whatsNext() {
        var up = goalBook.upcoming();
        if (up.isEmpty()) return goalBook.active().isPresent() ? "Nothing after this." : "Nothing on.";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(3, up.size()); i++) names.add(com.mineagent.engine.goal.Orders.withoutMark(up.get(i).text()));
        return "After this: " + String.join(", then ", names) + (up.size() > 3 ? ", and " + (up.size() - 3) + " more." : ".");
    }

    /** "Why": who asked for what it is doing. */
    private String why() {
        var g = goalBook.active();
        if (g.isPresent()) {
            String who = g.get().setBy() == null || g.get().setBy().isBlank() || g.get().setBy().equals("owner") ? "you" : g.get().setBy();
            return "Because " + who + " asked: " + com.mineagent.engine.goal.Orders.withoutMark(g.get().text()) + ".";
        }
        if (curriculum.current() != null) return "Practising: " + truncateForLog(curriculum.current(), 100) + ".";
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null) return "Nothing was asked of me, so I found my own work.";
        return "I'm not doing anything just now.";
    }

    /** "What do you need": what its task is stuck on, or the goal's amounts still short. */
    private String needs() {
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.snapshot() != null && body.snapshot().blockedReason() != null) {
            return "I'm stuck: " + body.snapshot().blockedReason() + ".";
        }
        var self = companion instanceof com.mineagent.engine.entity.CompanionEntity e ? e.serverPlayer() : null;
        if (self != null) {
            Map<String, Integer> have = carried(self);
            var short_ = goalBook.unmet(i -> have.getOrDefault(i, 0));
            if (!short_.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (var n : short_) parts.add(n.count() + " " + n.item().replace("minecraft:", "").replace('_', ' '));
                return "Still short of " + String.join(", ", parts) + ".";
            }
        }
        return "Nothing; I have what I need for this.";
    }

    /** Words that mean food in general, not one food: "food", "something to eat", "cooked meat". */
    private static final java.util.regex.Pattern FOOD_WORDS = java.util.regex.Pattern.compile(
            "^(?:some )?(?:good |cooked |hot )?(?:food|foods|meat|meals?|grub|something to eat|things to eat|stuff to eat)$");

    /** The chest they are looking at, within 6 blocks; else the nearest chest or barrel within 6 of them. */
    private static net.minecraft.core.BlockPos chestMeant(net.minecraft.server.level.ServerPlayer owner) {
        var level = owner.serverLevel();
        var hit = owner.pick(6.0, 1.0f, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult b
                && net.minecraft.world.level.block.entity.HopperBlockEntity.getContainerAt(level, b.getBlockPos()) != null
                && isChestLike(level.getBlockState(b.getBlockPos()))) return b.getBlockPos().immutable();
        net.minecraft.core.BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        var at = owner.blockPosition();
        for (var p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
            if (!isChestLike(level.getBlockState(p))) continue;
            double d = p.distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private static boolean isChestLike(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.world.level.block.Blocks.CHEST) || s.is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)
                || s.is(net.minecraft.world.level.block.Blocks.BARREL);
    }

    /** The journal (the game tests). */
    public com.mineagent.engine.memory.EventJournal journal() {
        return journal;
    }

    /** Chests it has looked in (the game tests). */
    public com.mineagent.engine.memory.ChestMemory chestMemory() {
        return chests;
    }

    /** The memory store, or null without a world folder (the game tests). */
    public com.mineagent.engine.memory.store.MemoryStore memoryStore() {
        return store;
    }

    /** This companion's memory folder in the world (null without a world), and its permanent library folder. */
    public java.nio.file.Path memoryFolder() {
        return persistence == null ? null : persistence.memoryFolder();
    }

    public java.nio.file.Path libraryFolder() {
        return persistence == null ? null : persistence.libraryDir();
    }

    /** Save this companion's memory and library now, off the server thread (the settings panel's changes). */
    public void saveMemoryNow() {
        var s = store;
        if (s != null && !closed) s.flush();
        if (persistence == null || closed) return;
        try {
            // A save queued before the companion went (despawned, its memory perhaps deleted) is dropped.
            talkExecutor.submit(() -> {
                if (!closed) persistence.saveAll();
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // shutting down: saved on close
        }
    }

    /**
     * The settings panel's Test (SMH plan 69, MA2.6): one tiny smh/talk call, timed; the result is given back
     * on the server thread, as "Reply in N ms" or what went wrong.
     */
    public void testConnection(java.util.function.Consumer<String> done) {
        var request = new LLMRequest(baseUrl, apiKey, com.mineagent.engine.llm.RoleRouting.modelFor(model, "talk"),
                List.of(ChatMessage.system("Reply with the single word: ok"), ChatMessage.user("ping")),
                List.of(), List.of(), temperature, 8, null, null, null, null, Duration.ofSeconds(30), false);
        long start = System.nanoTime();
        try {
            talkExecutor.submit(() -> {
                String said;
                try {
                    var reply = replyContent(sideCall(request));
                    long ms = (System.nanoTime() - start) / 1_000_000L;
                    said = reply != null ? "Reply in " + ms + " ms" : "No reply after " + ms + " ms";
                } catch (Exception e) {
                    said = "Failed: " + e.getMessage();
                }
                final String result = said;
                Services.platform().scheduleOnServer(() -> done.accept(result));
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            done.accept("Failed: the companion is shutting down");
        }
    }
    public void setAgentSettings(com.mineagent.engine.settings.AgentSettings settings) {
        agentSettings = settings == null ? com.mineagent.engine.settings.AgentSettings.DEFAULTS : settings;
        // MA2.4 and MA2.5: how much it experiments, and how freely it calls its model.
        var rate = com.mineagent.engine.settings.CallRate.of(agentSettings.callRate());
        maxHistory = rate.history();
        recentKeep = rate.recentKeep();
        maxToolRounds = rate.toolRounds();
        decisionCacheEnabled = rate.decisionCache();
        outputScale = rate.outputScale();
        mechanismExplorer.setBudgetLimit(com.mineagent.engine.settings.CallRate.experimentBudget(agentSettings.experiments()));
    }
    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String effort) { this.reasoningEffort = effort; }

    // ── Cognitive subsystem accessors (for external hooks) ──

    public EmotionState emotion() { return emotion; }
    public PlaceEventMemory placeMemory() { return placeMemory; }

    /** Whether the owner is AFK: in the game but not at the keys (SMH plan 70, MA5.4). */
    public boolean ownerAfk() {
        return ownerActivity.afk();
    }

    /** The game's idle timer and the owner's words, looked at each tick; one line when they come back. */
    private void watchOwnerActivity(net.minecraft.server.level.ServerPlayer owner) {
        if (owner == null) return;
        long now = net.minecraft.Util.getMillis();
        var change = ownerActivity.update(now, owner.getLastActionTime(), com.mineagent.engine.settings.Switches.afkAfterMillis());
        if (change == OwnerActivity.Change.CAME_BACK) {
            clearAfkFarm();
            endAfkOrder();
            String line = ownerActivity.welcomeBack(now, whileAway());
            journal.record("back", "you came back to the keys", currentGameTickSafe());
            speakToOwner(line);
        } else if (change == OwnerActivity.Change.WENT_AFK) {
            afkSinceTick = currentGameTickSafe();
            journal.record("afk", "you went AFK", currentGameTickSafe());
            int within = com.mineagent.engine.settings.Switches.afkStayWithin();
            onBodyLog("[AFK] The owner is away from the keys. Carry on with your jobs as usual"
                    + (within > 0 ? ", within " + within + " blocks of them" : ", as far as they take you")
                    + (com.mineagent.engine.settings.Switches.afkGuard() ? "; guard them while they're out in the open" : "")
                    + (com.mineagent.engine.settings.Switches.afkFarmLeftAlone()
                            ? "; at a farm they're AFK at, place and break nothing near them and leave its mobs be" : "")
                    + ". Don't ask them anything: questions wait until they're back. If you're stuck, put this goal "
                    + "aside and do the next one. Nothing is dropped for them: it goes in a chest.");
        }
    }

    /** The game tick they went AFK, for what happened while they were away. */
    private volatile long afkSinceTick;
    /** Whether the AFK owner is at a farm (fishing, or by its hoppers): checked every five seconds. */
    private volatile boolean ownerAtFarm;
    /** When it last turned back toward the AFK owner, so a walk back is not restarted every tick. */
    private long leashTurnedAt;
    private boolean leashTurned;

    /** What they asked for while they're AFK ("Going AFK, ..."), until they're back; NONE when nothing. */
    private volatile com.mineagent.engine.loop.AfkOrders.Order afkOrder = com.mineagent.engine.loop.AfkOrders.Order.NONE;
    private volatile long afkOrderAt;
    private volatile boolean afkShelterStarted;

    /** What they asked for while they're AFK (the game tests). */
    public com.mineagent.engine.loop.AfkOrders.Order afkOrder() {
        return afkOrder;
    }

    /** Walled in, or to be once its job is done, until they're back (the shelter task holds while this is true). */
    public boolean afkSheltering() {
        return afkOrder.shelter();
    }

    /**
     * "Going AFK, ..." (SMH plan 70, MA5.4): what it says back, the job put in its goal book (it works on it, and when
     * it's done finds something else to do), and the shelter at once when walling itself in is all it was asked.
     */
    private void startAfkOrder(com.mineagent.engine.loop.AfkOrders.Order order, com.mineagent.engine.entity.CompanionEntity self) {
        afkOrder = order;
        afkOrderAt = System.currentTimeMillis();
        afkShelterStarted = false;
        String said = com.mineagent.engine.loop.AfkOrders.reply(order, com.mineagent.engine.settings.Switches.afkGuard());
        if (order.any()) journal.record("afk-order", "while you're away: " + describe(order), currentGameTickSafe());
        speakToOwner(said);
        if (order.task() != null) {
            // An order it knows how to do itself ("fill up a chest with food") goes straight to that; others to its
            // goal book and the model.
            var known = com.mineagent.engine.chat.CommandParser.parse(order.task(), companion.companionName());
            if (known.isPresent() && known.get().kind() == com.mineagent.engine.chat.CommandParser.Kind.PRODUCE
                    && produceCommand(known.get(), self, true)) return;
            String goal = addGoal(order.task(), "owner");
            if (goal.startsWith("I can't")) speakToOwner(goal);
        } else if (order.shelter()) {
            startShelter();
        }
    }

    private static String describe(com.mineagent.engine.loop.AfkOrders.Order o) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (o.guardMe()) parts.add("keep you safe");
        if (o.stayWithin() > 0) parts.add("stay within " + o.stayWithin() + " blocks");
        if (o.task() != null) parts.add(o.task());
        if (o.shelter()) parts.add("block myself in");
        if (o.careful()) parts.add("play it safe");
        return String.join("; ", parts);
    }

    private void startShelter() {
        afkShelterStarted = true;
        haltNow();
        com.mineagent.api.task.TaskDispatch.dispatchAsync(companion,
                new com.mineagent.engine.task.ShelterTask.ShelterRecord("shelter-" + UUID.randomUUID()), reply -> { });
    }

    /** They're back, or never went: what they asked for while away is over (a job in the goal book stays). */
    private void endAfkOrder() {
        afkOrder = com.mineagent.engine.loop.AfkOrders.Order.NONE;
        afkShelterStarted = false;
    }

    /**
     * Each tick while there is an order: once its job is done, the shelter if it was asked for; and if they never
     * went AFK after all (they kept at the keys past the AFK time, and a minute more), the order is over.
     */
    private void watchAfkOrder() {
        var o = afkOrder;
        if (!o.any()) return;
        long waited = System.currentTimeMillis() - afkOrderAt;
        long allowed = Math.max(300_000L, com.mineagent.engine.settings.Switches.afkAfterMillis() + 60_000L);
        if (!ownerActivity.afk() && !ownerActivity.awaitingAfk() && waited > allowed) {
            endAfkOrder();
            return;
        }
        if (o.shelter() && !afkShelterStarted && goalBook.active().isEmpty() && !inProgress) {
            LiveBodyState body = liveBodyState.get();
            if (body == null || body.taskId() == null) startShelter();
        }
    }

    /**
     * While they're AFK and it has nothing to do, it finds its own work, as in auto mode (the "keep busy" switch,
     * on): "AFK just means I'm away, likely stay busy". Not while it keeps them safe or is walled in, nor with a goal
     * of theirs still open.
     */
    private boolean afkKeepBusy() {
        var o = afkOrder;
        return com.mineagent.engine.settings.Switches.afkKeepBusy() && ownerActivity.afk()
                && agentSettings.workMode() != com.mineagent.engine.settings.WorkMode.PARKED
                && !o.guardMe() && !o.shelter() && goalBook.active().isEmpty();
    }

    /** What happened while they were away, from its journal, for the welcome-back line: at most three things. */
    private java.util.List<String> whileAway() {
        java.util.List<String> notes = new java.util.ArrayList<>();
        for (var e : journal.recent(200)) {
            if (e.tick() < afkSinceTick) continue;
            if (!java.util.List.of("stored", "find", "goal", "death", "place").contains(e.kind())) continue;
            if (e.text().startsWith("new goal")) continue; // theirs: they know
            String t = e.kind().equals("stored") ? "put in " + e.text() : e.text();
            if (!notes.contains(t)) notes.add(t);
        }
        return notes.size() > 3 ? notes.subList(notes.size() - 3, notes.size()) : notes;
    }

    /**
     * While the owner is AFK (SMH plan 70, MA5.4). AFK only means they're away: it keeps on with its jobs, as far as
     * they take it ("AFK should be optional in terms of jobs and distance. AFK just means I'm away, likely stay busy",
     * Nov 3). At a farm they're AFK at, no companion places or breaks a block within 16 of them (a switch, on); and
     * only if they set a distance to keep within, it turns back when further.
     */
    private void keepNearAfkOwner(net.minecraft.server.level.ServerPlayer me, net.minecraft.server.level.ServerPlayer owner,
                                  long tick) {
        boolean afkHere = me != null && owner != null && ownerActivity.afk()
                && owner.isAlive() && owner.serverLevel() == me.serverLevel();
        if (!afkHere || !com.mineagent.engine.settings.Switches.afkFarmLeftAlone()) clearAfkFarm();
        if (!afkHere) {
            leashTurned = false;
            return;
        }
        String dim = owner.serverLevel().dimension().location().toString();
        if (com.mineagent.engine.settings.Switches.afkFarmLeftAlone() && tick % 100L == 0L) {
            ownerAtFarm = com.mineagent.engine.loop.AfkRules.atFarm(owner.fishing != null, hoppersNear(owner));
            if (ownerAtFarm) {
                farmOwner = owner.getUUID();
                com.mineagent.engine.world.AfkFarms.set(farmOwner, new com.mineagent.engine.world.AfkFarms.Area(dim,
                        owner.getX(), owner.getY(), owner.getZ(), com.mineagent.engine.loop.AfkRules.FARM_QUIET));
            } else {
                clearAfkFarm();
            }
        }
        if (afkShelterStarted && afkSheltering()) return; // walled in: it stays where it is
        // A distance only if they set one ("stay within 40" with going AFK, or the setting): none by default.
        int within = afkOrder.stayWithin() > 0 ? afkOrder.stayWithin() : com.mineagent.engine.settings.Switches.afkStayWithin();
        if (afkOrder.guardMe()) within = 0; // guarding keeps it by them already
        double leash = com.mineagent.engine.loop.AfkRules.leash(within);
        double away = me.distanceTo(owner);
        if (away <= leash) {
            leashTurned = false;
            return;
        }
        if (leashTurned && tick - leashTurnedAt < 200L) return;
        leashTurned = true;
        leashTurnedAt = tick;
        // Its body task stops; its thinking goes on (a goal it is on is picked up again within reach of them).
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null) {
            var stop = new com.google.gson.JsonObject();
            stop.addProperty("task_id", body.taskId());
            runDirect("task_stop", stop);
        }
        // Three blocks from them, on its side.
        double f = 3.0 / Math.max(away, 1.0);
        int x = (int) Math.floor(owner.getX() + (me.getX() - owner.getX()) * f);
        int z = (int) Math.floor(owner.getZ() + (me.getZ() - owner.getZ()) * f);
        goTo(new com.mineagent.engine.world.RouteMemory.Point(dim, x, owner.getBlockY(), z), java.util.List.of(), false, "xz");
        onBodyLog("[AFK] Back toward the owner: they're AFK and I keep within " + (int) leash + " blocks of them.");
    }

    /** Whose farm it keeps an area for: cleared by this even when they have left the game. */
    private volatile UUID farmOwner;

    private void clearAfkFarm() {
        UUID who = farmOwner;
        if (who != null) com.mineagent.engine.world.AfkFarms.clear(who);
        farmOwner = null;
        ownerAtFarm = false;
    }

    /** Hoppers within a few blocks of them: a farm's collection. */
    private static int hoppersNear(net.minecraft.server.level.ServerPlayer owner) {
        int r = com.mineagent.engine.loop.AfkRules.HOPPER_RANGE, n = 0;
        var level = owner.serverLevel();
        var at = owner.blockPosition();
        var cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.isLoaded(cursor) && level.getBlockState(cursor).is(net.minecraft.world.level.block.Blocks.HOPPER)) n++;
                }
            }
        }
        return n;
    }

    /** Whether the AFK owner is at a farm (the game tests). */
    public boolean ownerAtFarm() {
        return ownerAtFarm;
    }

    /** Out in the open, at night, or with a monster about: an AFK owner here needs guarding. */
    private static boolean exposed(net.minecraft.server.level.ServerPlayer owner) {
        var level = owner.serverLevel();
        if (!level.isDay() || level.canSeeSky(owner.blockPosition().above())) return true;
        return !level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                owner.getBoundingBox().inflate(16.0), net.minecraft.world.entity.Entity::isAlive).isEmpty();
    }

    /** The doors it opened on its way and will shut once past (SMH plan 70, MA5.3). */
    public com.mineagent.engine.pathing.execute.DoorKeeper doorKeeper() { return doorKeeper; }
    public com.mineagent.engine.memory.CognitiveMap cognitiveMap() { return cognitiveMap; }
    public SkillLibrary skillLibrary() { return skillLib; }
    public TheoryOfMind theoryOfMind() { return theoryOfMind; }
    public PlanGraph planner() { return planner; }
    public PlanGraph planGraph() { return planner; }
    public BeliefState beliefState() { return beliefState; }
    public WorldAssetIndex worldAssetIndex() { return worldAssetIndex; }
    public ExperienceStore experienceStore() { return experienceStore; }
    public ReflectionSystem reflection() { return reflection; }
    public PersonaProfile persona() { return persona; }
    public MinecraftKnowledgeGraph knowledgeGraph() { return knowledgeGraph; }
    public DecisionCache decisionCache() { return decisionCache; }
    public CognitiveState cognitiveState() { return cognitiveState; }

    /** Return a point-in-time snapshot for diagnostics and replay reports. */
    public CognitionMetrics.Snapshot metricsSnapshot() {
        return metrics.snapshot();
    }
    public CognitionMetrics.Operations metricOperations() { return metrics.operations(); }
    public RealtimeCognition realtimeCognition() { return realtimeCognition; }
    public SemanticWorldModel semanticWorldModel() { return semanticWorldModel; }
    public ActionTransactionLedger actionLedger() { return actionLedger; }
    public LayeredMemory layeredMemory() { return layeredMemory; }
    public ResourceReservationLedger reservationLedger() { return reservationLedger; }

    /** Items its jobs have promised themselves from its chests (SMH plan 70, MA7.4). */
    private final com.mineagent.engine.planning.production.Reserves.Ledger itemReservations =
            new com.mineagent.engine.planning.production.Reserves.Ledger();

    public com.mineagent.engine.planning.production.Reserves.Ledger itemReservations() { return itemReservations; }
    public HierarchicalRollingPlanner rollingPlanner() { return rollingPlanner; }
    public MechanismExplorer mechanismExplorer() { return mechanismExplorer; }
    public SkillRuntime skillRuntime() { return skillRuntime; }

    /** Admit a learned sequence as one plan-level action, not N unrelated tasks. */
    public SkillRuntime.StartResult startSkill(String skillName,
                                               com.google.gson.JsonObject overrides) {
        long gameTick = currentGameTickSafe();
        LiveBodyState body = liveBodyState.get();
        if (body != null && body.taskId() != null) {
            return new SkillRuntime.StartResult(false, null,
                    "Body is busy with task " + body.taskId());
        }
        String prerequisiteBlocker = planner.actionBlocker(semanticWorldModel, gameTick);
        if (prerequisiteBlocker != null) return new SkillRuntime.StartResult(false, null, prerequisiteBlocker);
        SkillRuntime.StartResult result = skillRuntime.start(
                skillName, overrides, gameTick);
        if (result.accepted()) {
            PlanGraph.PlanNode current = planner.nodeForAction();
            boolean hadPlan = planner.hasActivePlan();
            IntentContract contract = current == null
                    ? IntentContract.generic("Execute learned skill " + skillName,
                    "Every skill step and declared effect is verified", null, null, null)
                    : IntentContract.generic(current.description(),
                    current.successCriterion(), null, null, null);
            planner.bindTask(result.runId(), "execute_skill", contract, gameTick);
            if (!hadPlan) rollingPlanner.onPlanReplaced(contract.goal(), gameTick);
            rollingPlanner.onTaskAccepted(result.runId(), "execute_skill",
                    TaskSnapshot.running("skill", result.message()), gameTick);
            semanticWorldModel.observe("skill:" + result.runId(), "status",
                    "running", null, 1.0, "skill_runtime", result.runId(),
                    gameTick, 1_200L, false);
        }
        return result;
    }

    /** Called on the server thread by SkillRuntime; the scheduler still owns body admission. */
    private SkillRuntime.DispatchResult dispatchSkillAction(
            String actionId, String toolName, com.google.gson.JsonObject arguments) {
        Optional<Tool> toolValue = ToolRegistry.get(toolName);
        if (toolValue.isEmpty()) {
            return new SkillRuntime.DispatchResult(false, false, false,
                    "Stored tool is no longer registered: " + toolName);
        }
        Tool tool = toolValue.get();
        LiveBodyState body = liveBodyState.get();
        if (tool.dispatchesAsyncTask() && body != null && body.taskId() != null) {
            return new SkillRuntime.DispatchResult(false, true, false,
                    "Body is busy with task " + body.taskId());
        }
        metrics.recordToolCall();
        if (SKILL_ACTION_TOOLS.contains(toolName)) metrics.recordActionDispatched(actionId);
        // Capture the transaction checkpoint before recording the ACTION
        // event. Otherwise the audit entry's "before" state already contains
        // the operation it is supposed to verify.
        actionLedger.begin(actionId, toolName, "server tool callback and terminal task evidence",
                semanticWorldModel, currentGameTickSafe());
        semanticWorldModel.recordAction(toolName, arguments.toString(),
                actionId, currentGameTickSafe());
        dispatchedActionTools.put(actionId, toolName);
        AtomicReference<String> callback = new AtomicReference<>();
        try {
            tool.onServerCall(actionId, arguments, companion,
                    result -> callback.compareAndSet(null, result));
        } catch (Throwable failure) {
            String detail = toolErrorJson(toolName, failure);
            actionLedger.finish(actionId, ActionTransactionLedger.Status.FAILED,
                    detail, semanticWorldModel, currentGameTickSafe());
            semanticWorldModel.recordOutcome(toolName, false, detail,
                    actionId, currentGameTickSafe(), true);
            dispatchedActionTools.remove(actionId);
            if (SKILL_ACTION_TOOLS.contains(toolName)) metrics.recordActionOutcome(actionId,
                    CognitionMetrics.ActionOutcome.FAILED, "tool_exception");
            return new SkillRuntime.DispatchResult(false,
                    tool.dispatchesAsyncTask(), false, detail);
        }
        String raw = callback.get();
        if (raw == null) {
            String detail = "Tool violated callback contract and returned no result";
            actionLedger.finish(actionId, ActionTransactionLedger.Status.FAILED,
                    detail, semanticWorldModel, currentGameTickSafe());
            semanticWorldModel.recordOutcome(toolName, false, detail,
                    actionId, currentGameTickSafe(), true);
            dispatchedActionTools.remove(actionId);
            if (SKILL_ACTION_TOOLS.contains(toolName)) metrics.recordActionOutcome(actionId,
                    CognitionMetrics.ActionOutcome.UNVERIFIED, "callback_missing");
            return new SkillRuntime.DispatchResult(false,
                    tool.dispatchesAsyncTask(), false, detail);
        }
        FailureAnalysis analysis = analyzeFailure(raw);
        boolean asynchronous = "async_pending".equals(analysis.errorType());
        boolean success = asynchronous || analysis.isSuccess();
        if (!asynchronous) {
            dispatchedActionTools.remove(actionId);
            // Verify against the tool's authoritative post-read before the
            // OUTCOME event advances the semantic revision.
            var transaction = actionLedger.finishObserved(actionId, success, raw,
                    semanticWorldModel, currentGameTickSafe());
            boolean verified = transaction != null
                    && transaction.status() == ActionTransactionLedger.Status.VERIFIED;
            semanticWorldModel.recordOutcome(toolName, verified, raw,
                    actionId, currentGameTickSafe(), true);
            success = verified;
            if (SKILL_ACTION_TOOLS.contains(toolName)) metrics.recordActionOutcome(actionId,
                    metricOutcome(analysis, verified), analysis.errorType());
        } else {
            actionLedger.markPending(actionId, semanticWorldModel,
                    currentGameTickSafe(), raw);
        }
        mechanismExplorer.onToolDispatched(actionId, toolName,
                arguments.toString(), currentGameTickSafe());
        mechanismExplorer.onToolResult(actionId, toolName, success, asynchronous,
                raw, currentGameTickSafe());
        return new SkillRuntime.DispatchResult(success, asynchronous,
                success, raw);
    }

    /** Cancel an inner asynchronous skill task by its exact action id. */
    private void cancelSkillAction(String actionId) {
        if (actionId == null || actionId.isBlank()) return;
        try {
            MineAgentEngine.getCompanion(companion.companionId())
                    .ifPresent(state -> state.auction.cancelTask(actionId));
        } catch (Throwable failure) {
            System.err.println("[MineAgent] Failed to cancel timed-out skill action "
                    + actionId + ": " + failure.getMessage());
        }
    }

    /**
     * Controlled exploration can attempt compensation only through the same validated
     * tool dispatcher used by learned skills.  JSON is parsed structurally and
     * asynchronous compensation is reported as unverified rather than being
     * falsely treated as an atomic rollback. A successful result proves that
     * the compensation tool ran, not that arbitrary mod state is transactional.
     */
    private MechanismExplorer.CompensationResult dispatchExplorationCompensation(
            String actionId, String toolName, String arguments) {
        com.google.gson.JsonObject parsed;
        try {
            var element = com.google.gson.JsonParser.parseString(arguments);
            if (!element.isJsonObject()) {
                return new MechanismExplorer.CompensationResult(false, false,
                        false, "Compensation arguments are not a JSON object");
            }
            parsed = element.getAsJsonObject();
        } catch (RuntimeException malformed) {
            return new MechanismExplorer.CompensationResult(false, false,
                    false, "Invalid compensation arguments: " + malformed.getMessage());
        }
        SkillRuntime.DispatchResult result = dispatchSkillAction(actionId,
                toolName, parsed);
        return new MechanismExplorer.CompensationResult(result.accepted(),
                result.asynchronous(), result.success(), result.evidence());
    }

    private void onSkillRuntimeCompleted(SkillRuntime.Snapshot snapshot) {
        long gameTick = snapshot.updatedTick();
        boolean success = snapshot.status() == SkillRuntime.Status.SUCCEEDED;
        TaskState taskState = success ? TaskState.SUCCESS
                : snapshot.status() == SkillRuntime.Status.CANCELLED
                ? TaskState.CANCELLED : TaskState.FAILED;
        TaskSnapshot evidence = TaskSnapshot.progress("skill_"
                        + snapshot.status().name().toLowerCase(Locale.ROOT),
                snapshot.lastEvidence(), snapshot.stepIndex(), snapshot.stepCount(),
                null, null, null,
                success ? null : snapshot.lastEvidence(), snapshot.lastEvidence(),
                snapshot.stepIndex());
        planner.recordOutcome(snapshot.runId(), taskState, evidence,
                snapshot.lastEvidence(), gameTick);
        rollingPlanner.onTaskFinished(snapshot.runId(), taskState, gameTick);
        semanticWorldModel.observe("skill:" + snapshot.runId(), "status",
                snapshot.status().name().toLowerCase(Locale.ROOT), null, 1.0,
                "skill_runtime", snapshot.runId(), gameTick, 2_400L, false);
        semanticWorldModel.recordOutcome("execute_skill", success,
                snapshot.skillName() + ": " + snapshot.lastEvidence(),
                snapshot.runId(), gameTick, true);
        mechanismExplorer.onAdapterOutcome(snapshot.skillName(), success,
                snapshot.lastEvidence(), gameTick);
        onBodyLog("[SKILL_FINISHED] run_id=" + snapshot.runId()
                + " skill=" + snapshot.skillName() + " state=" + snapshot.status()
                + " evidence=" + snapshot.lastEvidence());
    }

    /** Expose validated specialized tools until the current turn finishes. */
    public void exposeExtraTools(Collection<String> toolNames) {
        if (toolNames == null) return;
        for (String name : toolNames) {
            if (name != null && !name.isBlank() && ToolRegistry.get(name).isPresent()) {
                exposedExtraTools.add(name);
            }
        }
    }

    /**
     * Select optional schemas once per owner goal. This is a latency router,
     * not a task classifier: the LLM still decides what to do, and
     * query_extra_tools remains the language/mod-agnostic fallback.
     */
    private void prepareToolsForOwnerIntent(String message) {
        exposedExtraTools.clear();
        String intent = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(intent, "give", "drop", "hand", "transfer", "store",
                "retrieve", "给", "交给", "丢", "转移", "放入", "取出", "箱子")) {
            exposeExtraTools(INVENTORY_EXTRA_TOOLS);
        }
        if (containsAny(intent, "craft", "make", "smelt", "cook", "recipe",
                "合成", "制作", "打造", "熔炼", "烧制", "配方")) {
            exposeExtraTools(GUI_EXTRA_TOOLS);
        }
        if (containsAny(intent, "mine", "gather", "collect", "find", "resource",
                "挖", "采集", "收集", "寻找", "资源", "矿", "木")) {
            exposeExtraTools(PERCEPTION_EXTRA_TOOLS);
        }
        if (containsAny(intent, "fight", "attack", "defend", "kill", "combat",
                "战斗", "攻击", "防御", "击杀", "怪物")) {
            exposeExtraTools(COMBAT_EXTRA_TOOLS);
        }
        if (containsAny(intent, "locate", "biome", "structure", "explore", "where",
                "定位", "群系", "结构", "探索", "哪里")) {
            exposeExtraTools(LOCATION_EXTRA_TOOLS);
        }
        if (containsAny(intent, "interact", "open", "inspect", "use", "click",
                "交互", "打开", "检查", "使用", "点击")) {
            exposeExtraTools(GUI_EXTRA_TOOLS);
            exposeExtraTools(Set.of("interact_entity"));
        }
        if (containsAny(intent, "team", "teammate", "coordinate", "cooperate",
                "团队", "队友", "协作", "合作")) {
            exposeExtraTools(Set.of("coordinate_team"));
        }
        if (containsAny(intent, "mod", "mechanism", "machine", "unknown", "experiment",
                "模组", "机制", "机器", "陌生", "实验")) {
            exposeExtraTools(Set.of("explore_mechanism"));
        }
        // Once an inspected unfamiliar object is relevant to the new goal,
        // expose controlled exploration even when the owner's wording does not
        // literally contain "mod" or "machine".
        if (mechanismExplorer.hasRelevantNovelty(intent)) {
            exposeExtraTools(Set.of("explore_mechanism"));
        }
        if (!skillLib.forNeeds(intent, null, 3, SkillLibrary.OFFER_BUDGET).isEmpty()) {
            exposeExtraTools(Set.of("execute_skill", "load_skill", "list_learned_skills"));
        }
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) return false;
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    public static boolean isCoreTool(String name) {
        return name != null && CORE_TOOL_NAMES.contains(name);
    }

    // ── Turn execution ─────────────────────────────────────────────

    private void startTurn(String trigger) {
        inProgress = true;
        currentRole = com.mineagent.engine.llm.RoleRouting.roleForTrigger(trigger);
        try {
            executor.submit(() -> {
                try {
                    // Rate-limited memory auto-save (max once per minute
                    // internally). Runs on the loop executor thread — NOT the
                    // server thread — so file IO never causes game lag.
                    if (persistence != null) {
                        persistence.autoSave();
                    }
                    executeTurn(trigger, 0);
                } catch (Exception e) {
                    // Log error but don't crash the loop
                    System.err.println("[MineAgent] Turn error: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    synchronized (this) {
                        inProgress = false;
                        // Check if inbox accumulated during this turn. While
                        // suspended it must remain queued until resume();
                        // draining here would either lose death events or start
                        // a tool-producing turn for a dead companion.
                        if (suspended) return;
                        boolean hasPendingEvents;
                        synchronized (inboxLock) {
                            hasPendingEvents = !inbox.isEmpty();
                        }
                        if (hasPendingEvents) {
                            // Do not drain here. executeTurn() is the sole
                            // inbox consumer and converts the retained batch
                            // into a user-role [EVENTS] message. The previous
                            // code copied and cleared the queue before calling
                            // startTurn(), so body observations arriving during
                            // an LLM call were silently lost on the next turn.
                            startTurn("inbox_batch");
                        }
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor was shut down (or queue saturated) — submit() rejected
            // the task synchronously. The lambda above never runs, so its
            // finally block will NOT reset inProgress. We must reset it here
            // to avoid permanently locking the loop (inProgress stuck true →
            // all future wake() calls queue into inbox and never execute).
            synchronized (this) {
                inProgress = false;
            }
            System.err.println("[MineAgent] Turn rejected by executor (shutting down?): "
                    + e.getMessage());
        }
    }

    /**
     * Execute a single LLM turn.
     *
     * @param trigger     what woke the loop
     * @param roundNumber how many tool-call rounds have executed (for recursion cap)
     */
    private void executeTurn(String trigger, int roundNumber) {
        // 0. Safety cap on tool-call rounds
        if (roundNumber >= maxToolRounds) {
            System.err.println("[MineAgent] Reached max tool rounds ("
                    + maxToolRounds + ") - stopping turn to prevent infinite loop");
            synchronized (history) {
                history.add(ChatMessage.system(
                        "[SYSTEM] You have called tools " + maxToolRounds
                                + " times in a row. Please respond to the owner now."));
            }
            // Final LLM call to wrap up
            callLLMAndHandleResponse(roundNumber);
            return;
        }

        // 1. Drain inbox into the conversation
        drainInbox();

        // 2. Tick emotion state (coarse-grained decay per turn)
        emotion.tick();

        // 3. Reset per-turn remediation state. Dynamic cognition is appended
        //    transiently at the end of the provider request; history[0] stays
        //    byte-for-byte stable for prompt-cache reuse.
        if (roundNumber == 0) {
            // Reset remediation counter at the start of each turn
            remediationRoundCounter = 0;
            // Extra schemas stay stable for the entire owner-goal episode.
            // Clearing them at every task_finished turn forced repeated
            // query_extra_tools calls and invalidated the provider tool prefix.
        }

        // 4. Trim history to prevent context overflow
        trimHistory();

        // Call LLM and handle the response (with retry)
        callLLMAndHandleResponse(roundNumber);
    }

    /**
     * Call the LLM, add the response to history, and execute any tool calls.
     * If all tools are synchronous, recursively continues the turn.
     */
    private void callLLMAndHandleResponse(int roundNumber) {
        LLMProvider provider = resolveProvider();
        List<Map<String, Object>> toolDefs = buildToolDefinitions();

        // 3a. Decision cache lookup — if we have a fresh cached decision
        //     for a similar situation, skip the LLM call entirely.
        //     This is the biggest latency win for repetitive actions
        //     (e.g. "mine the next block" repeated 20 times in a vein).
        //     Only applied on the first round of a turn (roundNumber == 0)
        //     to avoid caching mid-tool-call sequences.
        //
        //     安全门（防止记忆错乱/卡带式重复）：
        //     1. 玩家最近 30 秒内说过话 → 不缓存（对话需要 LLM 真正理解）
        //     2. 连续命中 ≥ 2 次 → 强制走 LLM（防止无限循环）
        //     3. TTL 已缩短到 10 秒（降低情境变化风险）
        if (decisionCacheEnabled && roundNumber == 0 && !shouldSkipCache()) {
            String cacheKey = buildDecisionCacheKey();
            // buildDecisionCacheKey may return null (e.g. companion state
            // not yet available). ConcurrentHashMap forbids null keys, so we
            // must skip both read and write when cacheKey is null.
            if (cacheKey != null) {
                String cachedDecision = decisionCache.getDecision(cacheKey);
                if (cachedDecision != null && !cachedDecision.isBlank()) {
                    metrics.recordDecisionCache(true);
                    // Safety: don't replay the same text if it's identical to
                    // the last assistant message — this means the AI is stuck
                    // in a "say but don't do" loop. Force a fresh LLM call.
                    if (isSameAsLastAssistant(cachedDecision)) {
                        System.out.println("[MineAgent] Cache hit but content identical "
                                + "to last response — skipping cache to avoid loop");
                        consecutiveCacheHits = 0;
                        // Fall through to LLM call
                    } else {
                        // Cache hit — synthesize an assistant message from cache.
                        ChatMessage cachedMsg = ChatMessage.assistant(cachedDecision, null);
                        synchronized (history) {
                            history.add(cachedMsg);
                        }
                        speakToOwner(cachedDecision);
                        consecutiveCacheHits++;
                        System.out.println("[MineAgent] Decision cache HIT (key=" + cacheKey
                                + ", consecutive=" + consecutiveCacheHits + ")");
                        return;
                    }
                }
                else {
                    metrics.recordDecisionCache(false);
                }
            }
        }
        // Cache miss or skipped — reset consecutive counter
        consecutiveCacheHits = 0;

        // 3b. Call the LLM (with retry on transient errors)
        VersionedResponse versioned = callLLMWithRetry(provider, toolDefs, roundNumber);
        if (versioned == null) {
            // All retries exhausted - give up gracefully
            return;
        }
        LLMResponse response = versioned.response();
        long acceptedGeneration = versioned.generation();
        if (eventGeneration.get() != acceptedGeneration) {
            return;
        }

        if (response.choice() == null || response.choice().message() == null) {
            System.err.println("[MineAgent] Provider returned a response without a choice/message");
            return;
        }
        ChatMessage assistantMsg = normalizeAssistantMessage(response.choice().message());
        // Skip empty assistant messages (no content AND no tool_calls).
        // Some providers occasionally return a no-op response; adding it to
        // history wastes tokens and can confuse subsequent rounds (the LLM
        // sees its own empty message and may echo the pattern). Bail out
        // early instead — there is nothing to speak, cache, or execute.
        boolean hasContent = assistantMsg.content() != null
                && !assistantMsg.content().isBlank();
        boolean hasToolCalls = assistantMsg.toolCalls() != null
                && !assistantMsg.toolCalls().isEmpty();
        if (!hasContent && !hasToolCalls) {
            System.err.println("[MineAgent] Empty assistant response "
                    + "(no content, no tool_calls) — skipping, not added to history");
            return;
        }

        // The final wrap-up request is deliberately not allowed to execute
        // more tools. Do not store the provider's rejected tool_calls: keeping
        // them without tool result messages makes the next provider request
        // protocol-invalid. Preserve only optional explanatory text.
        if (roundNumber >= maxToolRounds && hasToolCalls) {
            System.err.println("[MineAgent] LLM returned tool_calls after hitting "
                    + "maxToolRounds cap (" + maxToolRounds + ") - ignoring them");
            if (hasContent) {
                ChatMessage textOnly = ChatMessage.assistant(assistantMsg.content());
                synchronized (history) {
                    history.add(textOnly);
                    advanceProviderHistoryCursor(provider);
                }
                speakToOwner(assistantMsg.content());
            }
            return;
        }
        synchronized (history) {
            history.add(assistantMsg);
            advanceProviderHistoryCursor(provider);
        }

        // 3c. Store the decision in cache for future reuse.
        //     Only cache text-only responses (no tool_calls) to avoid
        //     replaying stale tool sequences. Text responses to simple
        //     status queries are highly reusable.
        //
        //     CRITICAL: Only cache responses that EXPLICITLY declare no
        //     action via the [NO_ACTION] / 【无行动】 marker (see Action
        //     Intent Declaration Protocol in system prompt). Responses
        //     without the marker are ambiguous — they might contain
        //     unstated action intent that would cause "say but don't do"
        //     loops if replayed from cache.
        //
        //     This replaces the old containsActionIntent() keyword check
        //     with a stricter, language-agnostic, LLM-driven declaration.
        if (decisionCacheEnabled && roundNumber == 0
                && (assistantMsg.toolCalls() == null || assistantMsg.toolCalls().isEmpty())
                && assistantMsg.content() != null && !assistantMsg.content().isBlank()
                && hasExplicitNoActionMarker(assistantMsg.content())) {
            String cacheKey = buildDecisionCacheKey();
            // Skip cache write when key is null (ConcurrentHashMap forbids
            // null keys — would throw NPE).
            if (cacheKey != null) {
                decisionCache.put(cacheKey, assistantMsg.content(), null);
            }
        }

        // Speak text to the owner in-game
        if (assistantMsg.content() != null && !assistantMsg.content().isBlank()) {
            speakToOwner(assistantMsg.content());
        }

        // 4. Action Intent Declaration Protocol enforcement.
        //    When LLM produces a text-only response (no tool_calls), check
        //    whether it explicitly declared "no action" via the [NO_ACTION] /
        //    【无行动】 marker. If NOT, the LLM likely forgot to call a tool
        //    (or forgot to declare no-action) — give it ONE chance to
        //    correct itself by adding a system reminder.
        //
        //    This replaces the old containsActionIntent() keyword-based
        //    detection with a stricter, language-agnostic protocol:
        //      - marker present → LLM honestly declared no action → end turn
        //      - marker absent  → LLM either forgot to act OR forgot to
        //                         declare no-action → remediate (safer)
        //
        //    The conservative "absent marker = remediate" policy is by
        //    design: it catches ALL "say but don't do" cases without
        //    relying on a fragile keyword list. Cost is bounded by
        //    MAX_REMEDIATION_ROUNDS (currently 1) so worst case is one
        //    extra LLM call per text-only response.
        if (assistantMsg.toolCalls() == null || assistantMsg.toolCalls().isEmpty()) {
            if (roundNumber == 0 && assistantMsg.content() != null
                    && !assistantMsg.content().isBlank()
                    && !hasExplicitNoActionMarker(assistantMsg.content())
                    && remediationRoundCounter < MAX_REMEDIATION_ROUNDS) {
                remediationRoundCounter++;
                System.out.println("[MineAgent] Text-only response without [NO_ACTION] "
                        + "marker — giving remediation chance " + remediationRoundCounter
                        + " (response: \""
                        + truncateForLog(assistantMsg.content(), 80) + "\")");
                synchronized (history) {
                    history.add(ChatMessage.system(
                            "[SYSTEM] Action Intent Declaration Protocol 提醒\n"
                            + "你的回复没有调用任何工具，也没有显式标注 [NO_ACTION] 或 【无行动】。\n"
                            + "请立即做出选择：\n"
                            + "  A. 如果你刚才说要做某事（移动/挖掘/建造/攻击/采集/合成/放置等），"
                            + "必须立即调用对应工具执行（goto/auto_mine/build/craft 等）。\n"
                            + "  B. 如果确实不需要任何身体动作（只是聊天/汇报/确认），"
                            + "请在回复中添加 [NO_ACTION] 或 【无行动】 标记。\n"
                            + "记住：说话≠行动。身体动作必须通过工具调用执行，"
                            + "纯对话必须显式声明无行动。"));
                }
                // Give LLM another chance to call tools or declare no-action
                callLLMAndHandleResponse(roundNumber + 1);
                return;
            }
            return;
        }

        // 5. Execute tool calls
        List<ChatMessage> toolResults = executeToolCalls(
                assistantMsg.toolCalls(), acceptedGeneration);
        synchronized (history) {
            history.addAll(toolResults);
        }

        // Tool callbacks are executor evidence even if a newer owner event
        // makes the language-model response stale. Commit plan transitions
        // before deciding whether this turn may recurse.
        commitSynchronousPlanEvidence(assistantMsg.toolCalls(), toolResults);

        // A cancel/new event that arrived while tools were running owns the
        // next decision. Keep the result messages to complete the provider
        // protocol group, but never recurse using the obsolete turn.
        if (eventGeneration.get() != acceptedGeneration) {
            return;
        }

        // 5b. Cognitive feedback is applied only to the still-current turn;
        // semantic and plan evidence above remain valid independently.
        analyzeToolResults(assistantMsg.toolCalls(), toolResults);

        // 6. Check if any async task was dispatched - if not, loop back
        boolean anyAsync = toolResults.stream()
                .anyMatch(m -> m.content() != null && m.content().contains("\"async\":true"));

        if (!anyAsync) {
            // All sync tools - loop back with incremented round
            executeTurn("tool_results_sync", roundNumber + 1);
        }
        // If async tasks were dispatched, they'll wake the loop when done
    }

    private void commitSynchronousPlanEvidence(
            List<ChatMessage.ToolCallRef> toolCalls,
            List<ChatMessage> toolResults) {
        for (int index = 0; index < toolCalls.size()
                && index < toolResults.size(); index++) {
            ChatMessage.ToolCallRef call = toolCalls.get(index);
            String content = toolResults.get(index).content();
            if (content == null || !SKILL_ACTION_TOOLS.contains(call.name())) continue;
            FailureAnalysis analysis = analyzeFailure(content);
            if ("async_pending".equals(analysis.errorType())
                    || (!analysis.isSuccess() && !analysis.isFailure())) continue;
            long gameTick = currentGameTickSafe();
            if (planner.recordToolOutcome(call.id(), call.name(),
                    analysis.isSuccess(), content, gameTick)) {
                rollingPlanner.onSynchronousOutcome(analysis.isSuccess(), gameTick);
            }
        }
    }

    /**
     * Analyze tool execution results to update cognitive subsystems:
     * <ul>
     *   <li>Emotion: trigger joy on success, frustration on failure</li>
     *   <li>Reflection: record failures for learning</li>
     *   <li>Skill library: register successful action sequences</li>
     * </ul>
     *
     * <p><b>改进:</b>
     * <ul>
     *   <li>更准确的失败检测: 使用多信号融合而非简单关键词匹配</li>
     *   <li>结构化错误分析: 提取错误类型和上下文</li>
     *   <li>改进技能注册: 使用更稳定的技能ID生成策略</li>
     * </ul>
     */
    private void analyzeToolResults(List<ChatMessage.ToolCallRef> toolCalls,
                                      List<ChatMessage> toolResults) {
        boolean anyFailure = false;
        boolean anySuccess = false;
        int failureCount = 0;
        int successCount = 0;
        PlanGraph.State tracePlan = planner.exportState();
        PlanGraph.PlanNode traceNode = planner.currentNode();
        String traceGoal = currentGoalContext(tracePlan, traceNode);

        // 记录情绪变化前的 PAD 值，用于学习重要性
        float pleasureBefore = emotion.pleasure();
        float arousalBefore = emotion.arousal();

        for (int i = 0; i < toolCalls.size() && i < toolResults.size(); i++) {
            var tc = toolCalls.get(i);
            var result = toolResults.get(i);
            String content = result.content();
            if (content == null) continue;

            // 改进的失败检测：多信号融合
            FailureAnalysis analysis = analyzeFailure(content);
            boolean failed = analysis.isFailure();
            boolean succeeded = analysis.isSuccess();
            boolean asyncPending = "async_pending".equals(analysis.errorType());
            if (asyncPending
                    && SKILL_ACTION_TOOLS.contains(tc.name())) {
                pendingTaskActions.put(tc.id(),
                        new PendingSkillAction(currentTaskDescription(), tc));
            }

            // 检查 LLM 是否在结果中标注了 importance 字段
            String importanceLabel = extractImportanceLabel(content);
            if (importanceLabel != null) {
                importance.learnFromLLMAnnotation(tc.name(), content, importanceLabel);
            }

            if (failed) {
                anyFailure = true;
                failureCount++;
                // 通过失败学习重要性
                importance.learnFromFailure(tc.name(), content);
                // Record failure in reflection system (micro layer)
                String taskDesc = planner.hasActivePlan() && planner.currentNode() != null
                        ? planner.currentNode().description() : "task";
                String failReason = analysis.errorMessage();
                reflection.recordFailure(taskDesc, tc.name(), failReason);
                // Record full failed-task memory (JARVIS-1 style) so the
                // LLM can recall this failure next time a similar task is
                // attempted, avoiding repeated mistakes.
                String attemptedPlan = tc.name() + "(" + tc.arguments() + ")";
                reflection.recordFailedTask(taskDesc, attemptedPlan, failReason);
            } else if (succeeded) {
                anySuccess = true;
                successCount++;
            }
        }

        // Emotion feedback（基于成功/失败比例）
        if (failureCount > successCount) {
            emotion.onTaskFailed();
        } else if (successCount > 0) {
            emotion.onTaskComplete();
        }

        // 通过情绪变化学习重要性
        float pleasureDelta = Math.abs(emotion.pleasure() - pleasureBefore);
        float arousalDelta = Math.abs(emotion.arousal() - arousalBefore);
        float emotionDelta = Math.max(pleasureDelta, arousalDelta);
        if (emotionDelta > 0.05f && !toolCalls.isEmpty()) {
            var firstCall = toolCalls.get(0);
            var firstResult = toolResults.get(0);
            if (firstResult.content() != null) {
                // 负面情绪（pleasure下降）传负值，让 ImportanceEvaluator 加权提升更多
                float signedDelta = emotion.pleasure() - pleasureBefore;
                importance.learnFromEmotion(firstCall.name(), firstResult.content(), signedDelta);
            }
        }

        // 改进的技能注册：使用更稳定的技能ID
        if (anySuccess && !toolCalls.isEmpty()) {
            // 收集所有成功的工具调用
            List<ChatMessage.ToolCallRef> successfulCalls = new ArrayList<>();
            for (int i = 0; i < toolCalls.size() && i < toolResults.size(); i++) {
                var tc = toolCalls.get(i);
                var result = toolResults.get(i);
                if (result.content() != null && analyzeFailure(result.content()).isSuccess()) {
                    successfulCalls.add(tc);
                }
            }

            boolean containsAction = successfulCalls.stream()
                    .anyMatch(call -> SKILL_ACTION_TOOLS.contains(call.name()));
            List<ChatMessage.ToolCallRef> replayCalls = successfulCalls.stream()
                    .filter(call -> SKILL_TRACE_TOOLS.contains(call.name())).toList();
            if (containsAction && !replayCalls.isEmpty()) {
                appendVerifiedTrace(traceGoal, replayCalls, !anyFailure,
                        !planner.hasActivePlan());
            }
        }
        if (anyFailure) {
            appendVerifiedTrace(traceGoal, List.of(), false, false);
        }
    }

    /** Build one skill from a verified episode instead of isolated responses. */
    private void appendVerifiedTrace(String goal,
                                     List<ChatMessage.ToolCallRef> calls,
                                     boolean success, boolean episodeComplete) {
        synchronized (verifiedTraceLock) {
            String normalizedGoal = goal == null || goal.isBlank()
                    ? "general_task" : goal.trim();
            if (!verifiedTraceGoal.equals(normalizedGoal)) {
                verifiedActionTrace.clear();
                verifiedTraceGoal = normalizedGoal;
            }
            if (!success) {
                // A failed episode is useful reflection evidence, but replaying
                // its partial prefix as a skill would encode an unsafe plan.
                verifiedActionTrace.clear();
                return;
            }
            for (ChatMessage.ToolCallRef call : calls) {
                if (call != null && SKILL_TRACE_TOOLS.contains(call.name())
                        && verifiedActionTrace.size() < 24) {
                    verifiedActionTrace.add(call);
                }
            }
            if (!episodeComplete || verifiedActionTrace.isEmpty()
                    || verifiedActionTrace.stream().noneMatch(
                    call -> SKILL_ACTION_TOOLS.contains(call.name()))) return;
            List<ChatMessage.ToolCallRef> verified = List.copyOf(verifiedActionTrace);
            List<String> toolNames = verified.stream()
                    .map(ChatMessage.ToolCallRef::name).toList();
            String skillId = generateSkillId(normalizedGoal,
                    toolNames.getFirst(), toolNames);
            String stepsJson = toolTraceJson(verified);
            boolean fresh = skillLib.getActionSequence(skillId) == null;
            skillLib.registerSequence(skillId, normalizedGoal, normalizedGoal, stepsJson, true);
            verifiedActionTrace.clear();
            if (fresh && skillLib.getActionSequence(skillId) != null) {
                sayMilestone(com.mineagent.engine.chat.MilestoneTalk.Kind.SKILL, "you learned a new skill: " + truncateForLog(normalizedGoal, 160));
            }
            if (skillLib.getActionSequence(skillId) != null) writeUpSkill(skillId, normalizedGoal, skillLib.getActionSequence(skillId));
        }
    }

    private String currentTaskDescription() {
        return currentGoalContext(planner.exportState(), planner.currentNode());
    }

    /** Prefer the newest owner objective over a plan restored from an older turn. */
    private String currentGoalContext(PlanGraph.State plan, PlanGraph.PlanNode node) {
        if (activeOwnerGoal != null && !activeOwnerGoal.isBlank()) return activeOwnerGoal;
        if (plan != null && plan.goal() != null && !plan.goal().isBlank()) return plan.goal();
        if (node != null && node.description() != null && !node.description().isBlank()) {
            return node.description();
        }
        return lastOwnerCommand == null || lastOwnerCommand.isBlank()
                ? "current task" : lastOwnerCommand;
    }

    /** Preserve parameters so a learned sequence is adaptable, not a name list. */
    private static String toolTraceJson(List<ChatMessage.ToolCallRef> calls) {
        com.google.gson.JsonArray trace = new com.google.gson.JsonArray();
        for (ChatMessage.ToolCallRef call : calls) {
            com.google.gson.JsonObject action = new com.google.gson.JsonObject();
            action.addProperty("tool", call.name());
            try {
                action.add("args", com.google.gson.JsonParser.parseString(call.arguments()));
            } catch (com.google.gson.JsonParseException malformed) {
                // Provider arguments should be JSON, but retaining malformed
                // text is safer than dropping evidence or failing the turn.
                action.addProperty("args_raw", call.arguments());
            }
            trace.add(action);
        }
        return trace.toString();
    }

    /**
     * 失败分析结果
     */
    private record FailureAnalysis(
            boolean isFailure,
            boolean isSuccess,
            String errorMessage,
            String errorType
    ) {}

    /**
     * 分析工具结果，判断成功/失败（改进版：多信号融合）
     */
    private FailureAnalysis analyzeFailure(String content) {
        if (content == null) {
            return new FailureAnalysis(true, false, "null content", "unknown");
        }

        String lower = content.toLowerCase();

        // 异步派发确认：async 任务刚刚派发，终态未知。
        // 不计成功也不计失败 — 否则"派发成功"会被误记为"执行成功"，
        // 污染技能库（记录了未验证的序列）和情绪反馈。
        // 异步任务的真正成败由后续 task_finished 事件的结果记账。
        boolean isAsyncDispatch = lower.contains("\"async\":true") ||
                                  lower.contains("\"async\":1");

        // 成功信号（优先级高）
        boolean hasSuccessFlag = lower.contains("\"success\":true") ||
                                  lower.contains("\"success\":1") ||
                                  lower.contains("\"status\":\"success\"") ||
                                  lower.contains("\"result\":\"ok\"");

        // 失败信号
        boolean hasErrorFlag = lower.contains("\"error\"") ||
                                lower.contains("\"failed\"") ||
                                lower.contains("\"status\":\"error\"") ||
                                lower.contains("\"status\":\"failed\"");

        // 超时信号
        boolean hasTimeout = lower.contains("timed out") ||
                             lower.contains("timeout") ||
                             lower.contains("deadline exceeded");

        // 未找到信号
        boolean hasNotFound = lower.contains("not found") ||
                               lower.contains("no such") ||
                               lower.contains("cannot find") ||
                               lower.contains("doesn't exist");

        // 判断逻辑：
        // 0. 如果是异步派发确认且无失败标志 → 状态未知（不算成功/失败）
        // 1. 如果有明确成功标志且无失败标志 → 成功
        // 2. 如果有失败标志 → 失败
        // 3. 如果有超时/未找到且无成功标志 → 失败
        // 4. 默认 → 成功（无明确失败信号时假设成功）

        if (isAsyncDispatch && !hasErrorFlag && !hasTimeout) {
            return new FailureAnalysis(false, false, "", "async_pending");
        }

        if (hasSuccessFlag && !hasErrorFlag && !hasTimeout && !hasNotFound) {
            return new FailureAnalysis(false, true, "", "success");
        }

        if (hasErrorFlag) {
            String errorMsg = extractErrorMessage(content);
            String errorType = categorizeError(lower);
            return new FailureAnalysis(true, false, errorMsg, errorType);
        }

        if (hasTimeout) {
            return new FailureAnalysis(true, false, "操作超时", "timeout");
        }

        if (hasNotFound && !hasSuccessFlag) {
            return new FailureAnalysis(true, false, "未找到目标", "not_found");
        }

        // 默认成功
        return new FailureAnalysis(false, true, "", "success");
    }

    private static CognitionMetrics.ActionOutcome metricOutcome(FailureAnalysis analysis, boolean verified) {
        if (verified) return CognitionMetrics.ActionOutcome.SUCCEEDED;
        return switch (analysis.errorType()) {
            case "invalid_arguments", "invalid_params", "unknown_tool" -> CognitionMetrics.ActionOutcome.INVALID;
            case "timeout" -> CognitionMetrics.ActionOutcome.UNVERIFIED;
            default -> analysis.isFailure() ? CognitionMetrics.ActionOutcome.FAILED
                    : CognitionMetrics.ActionOutcome.UNVERIFIED;
        };
    }

    /**
     * 错误分类（通用模式，不依赖硬编码关键词）
     */
    private String categorizeError(String lowerContent) {
        if (lowerContent.contains("invalid") || lowerContent.contains("illegal") ||
            lowerContent.contains("missing") || lowerContent.contains("required")) {
            return "invalid_params";
        }
        if (lowerContent.contains("cannot") || lowerContent.contains("can't") ||
            lowerContent.contains("denied") || lowerContent.contains("forbidden")) {
            return "access_denied";
        }
        if (lowerContent.contains("timeout") || lowerContent.contains("timed out")) {
            return "timeout";
        }
        if (lowerContent.contains("not found") || lowerContent.contains("no such")) {
            return "not_found";
        }
        return "unknown";
    }

    /**
     * 生成稳定的技能ID（含工具序列签名，避免不同任务互相覆盖）
     */
    private String generateSkillId(String taskDesc, String primaryTool, List<String> toolSequence) {
        // 使用任务类型和工具名生成稳定的ID，避免hashCode冲突
        String normalizedTask = taskDesc.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "_")
                .replaceAll("_+", "_");
        if (normalizedTask.length() > 20) {
            normalizedTask = normalizedTask.substring(0, 20);
        }
        // 工具序列签名：相同序列=相同技能（应合并统计），
        // 不同序列=不同技能（不应互相覆盖）。
        String seqSignature = String.join("->", toolSequence);
        String seqHash = Integer.toHexString(seqSignature.hashCode());
        return primaryTool + "_" + normalizedTask + "_" + seqHash;
    }

    /**
     * 从工具结果 JSON 中提取 LLM 标注的 importance 字段。
     * LLM 可在工具调用参数中包含 "importance": "high"/"medium"/"low"
     * 来指导系统学习哪些事件重要。
     * 返回 null 表示未标注。
     */
    private static String extractImportanceLabel(String jsonContent) {
        if (jsonContent == null) return null;
        String lower = jsonContent.toLowerCase();
        int idx = lower.indexOf("\"importance\"");
        if (idx < 0) return null;
        int colon = jsonContent.indexOf(':', idx);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < jsonContent.length()
                && (jsonContent.charAt(start) == ' ' || jsonContent.charAt(start) == '"')) {
            start++;
        }
        int end = jsonContent.indexOf('"', start);
        if (end < 0) end = jsonContent.length();
        return jsonContent.substring(start, end);
    }

    /** Extract a human-readable error message from a tool result JSON. */
    private static String extractErrorMessage(String jsonContent) {
        if (jsonContent == null) return "unknown error";
        // Simple extraction: find "error":"..." or "error":"..."
        int idx = jsonContent.indexOf("\"error\"");
        if (idx < 0) idx = jsonContent.indexOf("\"failed\"");
        if (idx < 0) return jsonContent.length() > 200
                ? jsonContent.substring(0, 200) : jsonContent;
        int colon = jsonContent.indexOf(':', idx);
        if (colon < 0) return jsonContent;
        int start = colon + 1;
        while (start < jsonContent.length()
                && (jsonContent.charAt(start) == ' ' || jsonContent.charAt(start) == '"')) {
            start++;
        }
        int end = jsonContent.indexOf('"', start);
        if (end < 0) end = jsonContent.length();
        return jsonContent.substring(start, end);
    }

    /**
     * Call the LLM with retry logic for transient errors and stale request detection.
     *
     * <p><b>Stale Request Detection</b> (borrowed from Mindcraft):
     * Before calling the LLM, record the current event generation.
     * After the LLM returns, check if a new event arrived during the call
     * (timestamp changed). If so, the response is stale — discard it and
     * return null, causing the caller to abort the turn. The inbox will
     * trigger a fresh turn with the new context.
     *
     * <p>This prevents the "AI answers a question from 10 seconds ago"
     * problem, and reduces latency for new messages by 5-10x.
     *
     * @return the LLM response, or null if all retries exhausted or stale
     */
    private record VersionedResponse(LLMResponse response, long generation) {}

    /** Emit provider-side latency and cache metrics for evidence-based tuning. */
    private void logUsage(LLMResponse response, long elapsedNanos, int attempt) {
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNanos));
        LLMResponse.Usage usage = response == null ? null : response.usage();
        if (usage == null) {
            System.out.println("[MineAgent] LLM usage unavailable latency_ms="
                    + latencyMs + " attempt=" + (attempt + 1));
            return;
        }
        System.out.println(String.format(Locale.ROOT,
                "[MineAgent] LLM usage prompt=%d cached=%d cache_create=%d "
                        + "cache_hit_rate=%.1f%% completion=%d total=%d latency_ms=%d attempt=%d",
                usage.promptTokens(), usage.cachedPromptTokens(),
                usage.cacheCreationPromptTokens(),
                usage.promptCacheHitRate() * 100.0,
                usage.completionTokens(), usage.totalTokens(), latencyMs, attempt + 1));
    }

    private VersionedResponse callLLMWithRetry(LLMProvider provider,
                                                List<Map<String, Object>> toolDefs,
                                                int roundNumber) {
        List<ChatMessage> messagesToSend;
        List<ChatMessage> fallbackHistory;
        List<ConversationItem> transportItems;
        synchronized (history) {
            boolean statefulResponses = provider.capabilities().supports(
                    LLMCapabilities.Feature.STATEFUL_RESPONSES);
            String previous = providerTransportState.get().previousResponseId();
            int start = statefulResponses && previous != null
                    ? Math.max(0, Math.min(providerHistoryCursor, history.size())) : 0;
            messagesToSend = new ArrayList<>(history.subList(start, history.size()));
            // Keep a complete, locally valid history for relays that reject
            // Responses or for response ids that expired. The incremental
            // suffix above remains the normal stateful wire payload.
            fallbackHistory = new ArrayList<>(history);
            transportItems = opaqueTransportItems(providerTransportState.get());
        }
        int cacheableMessageCount = messagesToSend.size();
        if (roundNumber == 0) {
            // Keep volatile state at the final message. It is intentionally not
            // persisted: on the next request the unchanged static prompt and
            // prior dialogue remain a reusable provider-cache prefix.
            ChatMessage live = ChatMessage.user(buildLiveContext());
            messagesToSend.add(live);
            fallbackHistory.add(live);
        }
        messagesToSend = Collections.unmodifiableList(messagesToSend);

        // Use a monotonic counter rather than wall-clock time. Two owner events
        // can legitimately arrive in one millisecond, and a timestamp equality
        // check would then accept an obsolete response.
        long callGeneration = eventGeneration.get();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            ProviderSessionState providerStateBefore = providerTransportState.get();
            long requestStarted = System.nanoTime();
            try {
                Thread requestThread = Thread.currentThread();
                activeLLMCallThread.set(requestThread);
                if (eventGeneration.get() != callGeneration) {
                    activeLLMCallThread.compareAndSet(requestThread, null);
                    return null;
                }
                LLMResponse response;
                try {
                    // Built-in transports are companion-owned. A legacy extension
                    // may still return its registry singleton; retain its monitor
                    // while independent built-in sessions can proceed concurrently.
                    progress.modelCall(currentGameTickSafe());
                    synchronized (provider) {
                        provider.restoreSessionState(providerStateBefore);
                        response = provider.complete(new LLMRequest(
                                baseUrl, apiKey, com.mineagent.engine.llm.RoleRouting.modelFor(model, currentRole), messagesToSend, fallbackHistory,
                                transportItems, toolDefs,
                                temperature, outputTokenBudget(roundNumber), reasoningEffort,
                                providerStateBefore.previousResponseId(), providerStateBefore.conversationId(),
                                stablePromptCacheKey(provider),
                                Duration.ofSeconds(providerDeadlineSeconds(roundNumber)),
                                provider.capabilities().supports(LLMCapabilities.Feature.SERVER_COMPACTION),
                                cacheableMessageCount));
                        providerTransportState.set(provider.sessionState(model));
                    }
                } finally {
                    activeLLMCallThread.compareAndSet(requestThread, null);
                }
                long elapsedNanos = System.nanoTime() - requestStarted;
                logUsage(response, elapsedNanos, attempt);
                metrics.recordLlm(response, elapsedNanos, false);

                // Stale Request check: if a significant event arrived during
                // the LLM call, discard the response. The situation it was
                // generated for no longer applies.
                if (eventGeneration.get() != callGeneration) {
                    // A discarded response must never become the parent of a
                    // later stateful request.  Provider implementations update
                    // their session as soon as HTTP parsing succeeds, so roll
                    // the transport state back before returning stale.
                    providerTransportState.set(providerStateBefore);
                    synchronized (provider) {
                        provider.restoreSessionState(providerStateBefore);
                    }
                    System.out.println("[MineAgent] Stale request detected — "
                            + "discarding LLM response (event arrived during call)");
                    return null;
                }

                if (persistence != null) {
                    // Provider transport state (response id and opaque
                    // compaction item) is durable but intentionally kept out
                    // of gameplay-memory search and prompts.
                    persistence.saveProviderSession(providerTransportState.get());
                }
                return new VersionedResponse(response, callGeneration);
            } catch (RuntimeException e) {
                // A provider is a registry singleton. If an implementation
                // mutated its session before failing, never expose that
                // partial state to another companion sharing the instance.
                synchronized (provider) {
                    provider.restoreSessionState(providerStateBefore);
                }
                providerTransportState.set(providerStateBefore);
                metrics.recordLlm(null, System.nanoTime() - requestStarted, true);
                String msg = e.getMessage() != null ? e.getMessage() : "";
                System.err.println("[MineAgent] LLM call attempt " + (attempt + 1)
                        + "/" + MAX_RETRIES + " failed: " + msg);

                // Check stale on retry too
                if (eventGeneration.get() != callGeneration) {
                    // HttpClient.send propagates interruption. Consume that
                    // flag before this single-thread executor accepts the next,
                    // current request; generation remains cancellation truth.
                    Thread.interrupted();
                    System.out.println("[MineAgent] Stale request detected during retry — aborting");
                    return null;
                }

                boolean retryable = !(e instanceof LLMProviderException providerError)
                        || providerError.retryable();
                if (e instanceof IllegalArgumentException) {
                    retryable = false;
                }

                if (!retryable) {
                    sayCallFailure("§c[Error] LLM request failed: " + msg, e, msg);
                    return null;
                }

                if (attempt < MAX_RETRIES - 1) {
                    long delay = RETRY_BASE_MS * (1L << attempt); // exponential backoff
                    if (e instanceof LLMProviderException providerError
                            && providerError.retryAfterMillis() != null) {
                        delay = Math.max(delay, providerError.retryAfterMillis());
                    }
                    if (!waitForRetry(delay, callGeneration)) {
                        return null;
                    }
                } else {
                    System.err.println("[MineAgent] All LLM retries exhausted");
                    // Notify the owner about the failure
                    sayCallFailure("§c[Error] LLM call failed after " + MAX_RETRIES + " retries: " + msg, e, msg);
                    return null;
                }
            }
        }
        return null;
    }

    /** Mark the accepted assistant response as already represented by a
     * Responses API previous_response_id. Tool outputs appended afterwards
     * remain the only continuation payload. */
    private void advanceProviderHistoryCursor(LLMProvider provider) {
        if (provider.capabilities().supports(LLMCapabilities.Feature.STATEFUL_RESPONSES)) {
            providerHistoryCursor = history.size();
        }
    }

    private static List<ConversationItem> opaqueTransportItems(
            ProviderSessionState state) {
        if (state == null || state.opaqueCompactionItem() == null
                || state.opaqueCompactionItem().isBlank()) return List.of();
        try {
            com.google.gson.JsonElement parsed =
                    com.google.gson.JsonParser.parseString(state.opaqueCompactionItem());
            if (!parsed.isJsonArray()) return List.of();
            List<ConversationItem> items = new ArrayList<>();
            for (com.google.gson.JsonElement value : parsed.getAsJsonArray()) {
                if (value.isJsonObject()) {
                    String type = value.getAsJsonObject().has("type")
                            ? value.getAsJsonObject().get("type").getAsString()
                            : "reasoning";
                    items.add(ConversationItem.opaque(type, value.toString()));
                }
            }
            return List.copyOf(items);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    /**
     * Bound routine follow-up generations without constraining large build
     * coordinate payloads or the first strategic response. Reasoning models
     * still receive 4K tokens so their hidden reasoning is not starved.
     */
    private int outputTokenBudget(int roundNumber) {
        int cap = (int) Math.round(4_096 * outputScale);
        if (roundNumber <= 0 || maxTokens <= cap || complexStructuredOutputLikely()) {
            return maxTokens;
        }
        return Math.min(maxTokens, cap);
    }

    private boolean complexStructuredOutputLikely() {
        String goal = activeOwnerGoal == null ? "" : activeOwnerGoal.toLowerCase(Locale.ROOT);
        return containsAny(goal, "build", "blueprint", "structure", "architect",
                "construction", "建造", "建筑", "蓝图", "结构");
    }

    /** Sleep in short slices so a new owner event or cancel wakes the loop logically. */
    private boolean waitForRetry(long delayMillis, long callGeneration) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
        while (eventGeneration.get() == callGeneration) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return true;
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(
                        remaining, TimeUnit.MILLISECONDS.toNanos(250)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Trim the conversation history to prevent context overflow.
     * Always keeps the system prompt (index 0) and trims the oldest
     * non-system messages when the history exceeds maxHistory.
     *
     * <p><b>Important:</b> When trimming, we must keep assistant messages
     * that contain {@code tool_calls} together with their corresponding
     * {@code tool} result messages. If we cut an assistant tool_calls message
     * but keep the following tool results, the OpenAI-compatible API will
     * reject the request with "Messages with role 'tool' must be a response
     * to a preceding message with 'tool_calls'".
     */
    private void trimHistory() {
        synchronized (history) {
            if (history.size() <= maxHistory + 1) return;

            // A Responses continuation has already stored the sent prefix on
            // the provider. Trim only that prefix while preserving the local
            // unsent suffix (tool outputs/owner events) and its cursor. The
            // ordinary trimmer can otherwise delete an unsent tool output or
            // move the cursor past it, silently losing the action result.
            if (providerTransportState.get().previousResponseId() != null
                    && providerHistoryCursor > 0) {
                trimStatefulHistory();
                return;
            }

            int oldSize = history.size();

            // Preserve the system prompt (index 0)
            ChatMessage systemPrompt = history.get(0);

            // Trim with hysteresis: trigger at maxHistory, then fall back to
            // recentKeep. Trimming back to maxHistory caused the rolling
            // summary near the front of the prompt to be rewritten after
            // almost every tool round, defeating provider prefix caches.
            int start = history.size() - recentKeep;

            // Adjust start forward if it lands on a "tool" message whose
            // corresponding assistant tool_calls message was cut.
            while (start < history.size()) {
                ChatMessage m = history.get(start);
                if ("tool".equals(m.role())) {
                    // This tool message's parent assistant message was cut;
                    // skip this orphaned tool message too.
                    start++;
                } else if ("assistant".equals(m.role())
                        && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    // This is an assistant message with tool_calls. Check if
                    // all its tool results are also within the kept range.
                    // If some results are missing (cut off), drop this assistant
                    // message too.
                    int needed = m.toolCalls().size();
                    int found = 0;
                    for (int i = start + 1; i < history.size() && found < needed; i++) {
                        ChatMessage nm = history.get(i);
                        if ("tool".equals(nm.role()) && m.toolCalls().stream()
                                .anyMatch(tc -> tc.id().equals(nm.toolCallId()))) {
                            found++;
                        } else if ("assistant".equals(nm.role())) {
                            // Reached next assistant — stop counting
                            break;
                        }
                    }
                    if (found < needed) {
                        // Some tool results missing — drop this assistant msg
                        start++;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }

            // ── Compress dropped messages into a summary ──
            // Instead of discarding old messages entirely, fold them into
            // a compact "[SUMMARY]" system message so the LLM retains
            // context without paying full token cost. Only summarize the
            // messages between index 1 (after system prompt) and `start`
            // that are being dropped.
            List<ChatMessage> trimmed = new ArrayList<>();
            trimmed.add(systemPrompt);

            if (start > 1) {
                String summary = summarizeOldMessages(1, start);
                if (!summary.isBlank()) {
                    trimmed.add(ChatMessage.system("[过往对话摘要] " + summary));
                }
            }

            // Build the kept range, filtering out any orphan tool messages
            // whose parent assistant was cut (not in the kept range). The
            // while loop above only advances `start` past orphan tools that
            // land EXACTLY at the start position; it cannot remove orphans
            // that appear after a kept assistant's valid tool results, or
            // after a user/system message (where break leaves start at the
            // user message but start+1 may be an orphan tool). Filtering
            // here catches every case without breaking valid assistant→tool
            // pairings.
            List<ChatMessage> kept = new ArrayList<>();
            for (int i = start; i < history.size(); i++) {
                ChatMessage m = history.get(i);
                if ("tool".equals(m.role())
                        && !hasParentAssistantInRange(kept, m)) {
                    // Orphan tool message — its parent assistant was cut.
                    continue;
                }
                kept.add(m);
            }
            trimmed.addAll(kept);

            history.clear();
            history.addAll(trimmed);

            System.out.println("[MineAgent] Trimmed history from "
                    + oldSize + " to " + history.size() + " messages ("
                    + (start > 1 ? "summarized " + (start - 1) + " old msgs" : "no summary")
                    + ")");
        }
    }

    private void trimStatefulHistory() {
        int oldSize = history.size();
        int sentEnd = Math.max(1, Math.min(providerHistoryCursor, oldSize));
        int start = Math.max(1, sentEnd - recentKeep);
        // Keep the assistant parent whenever the boundary lands in its tool
        // result group. Those results may be unsent but are still needed for
        // local fallback to Chat Completions.
        while (start > 1 && "tool".equals(history.get(start).role())) start--;

        ChatMessage systemPrompt = history.get(0);
        List<ChatMessage> trimmed = new ArrayList<>();
        trimmed.add(systemPrompt);
        if (start > 1) {
            String summary = summarizeOldMessages(1, start);
            if (!summary.isBlank()) trimmed.add(ChatMessage.system("[过往对话摘要] " + summary));
        }
        int keptPrefix = trimmed.size();
        // Keep the complete recent range, including any unsent suffix. Do not
        // run orphan filtering here: the provider-side previous response is
        // the parent for a tool output that is outside the local range.
        trimmed.addAll(history.subList(start, oldSize));
        history.clear();
        history.addAll(trimmed);

        // Cursor points immediately after the old sent prefix, before the
        // unsent suffix. The inserted summary and kept recent prefix are both
        // already represented by previous_response_id and must be skipped.
        providerHistoryCursor = keptPrefix + (sentEnd - start);
        System.out.println("[MineAgent] Trimmed stateful history from " + oldSize
                + " to " + history.size() + "; continuation cursor="
                + providerHistoryCursor);
    }

    /**
     * Summarize a range of old history messages into a compact string.
     * Extracts only the essential info: what tools were called, what the
     * outcomes were, and any key decisions. Each message becomes ~1 line.
     *
     * <p><b>记忆保护</b>：玩家指令保留前 200 字符（而非 80），确保
     * 重要任务指令不被截断。检测到的重要事件（死亡/钻石/危险）会
     * 标记为【重要】以便 LLM 优先关注。
     *
     * @param fromIndex inclusive start index in history
     * @param toIndex exclusive end index in history
     * @return compact summary, or empty string if nothing to summarize
     */
    private String summarizeOldMessages(int fromIndex, int toIndex) {
        if (fromIndex >= toIndex) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = fromIndex; i < toIndex && count < 25; i++) {
            ChatMessage m = history.get(i);
            String role = m.role();
            String content = m.content();
            if (content == null) content = "";

            // Skip empty content (e.g. assistant messages with only tool_calls)
            if (content.isBlank() && m.toolCalls() == null) continue;

            // 动态重要性评估：用 ImportanceEvaluator 取代硬编码关键词
            // 玩家指令始终视为重要（用户说的话都不能丢）
            boolean important = false;
            if ("user".equals(role)) {
                important = true;
            } else if ("tool".equals(role) && i > 0) {
                // 查找对应的工具名
                String toolName = findToolNameForResult(history, i);
                important = importance.isImportant(toolName, content);
            } else {
                important = importance.isImportant("text", content);
            }

            if ("user".equals(role)) {
                // 玩家指令：保留前 200 字符（不是 80），防止任务指令被截断
                String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                sb.append("玩家: ").append(snippet).append("; ");
                count++;
            } else if ("assistant".equals(role)) {
                // Assistant: if has tool_calls, list tool names; else first 100 chars of text
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    StringBuilder tools = new StringBuilder();
                    for (var tc : m.toolCalls()) {
                        if (tools.length() > 0) tools.append(",");
                        tools.append(tc.name());
                    }
                    sb.append("AI调用[").append(tools).append("]; ");
                } else if (!content.isBlank()) {
                    String snippet = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                    sb.append("AI: ").append(snippet).append("; ");
                }
                count++;
            } else if ("tool".equals(role)) {
                // Tool result: note success/error + 是否包含重要信息
                String lower = content.toLowerCase();
                if (lower.contains("error") || lower.contains("failed")) {
                    sb.append("(失败); ");
                } else if (important) {
                    sb.append("(成功★); ");
                } else {
                    sb.append("(成功); ");
                }
                count++;
            } else if ("system".equals(role)) {
                // System messages: preserve accumulated summary from previous
                // trimHistory passes, and keep important body-log events.
                if (content.startsWith("[过往对话摘要]")) {
                    // Accumulate the previous summary instead of discarding it.
                    // Without this branch, every trim would drop the prior
                    // summary and the LLM would lose ALL memory older than the
                    // recent window — a slow memory leak of long-term context.
                    // Extract everything after the "[过往对话摘要]" tag.
                    int bracketEnd = content.indexOf(']');
                    String prevSummary = bracketEnd >= 0 && bracketEnd + 1 < content.length()
                            ? content.substring(bracketEnd + 1).trim() : "";
                    if (!prevSummary.isBlank()) {
                        sb.append(prevSummary).append(" | ");
                        count++;
                    }
                } else if (content.startsWith("[EVENTS]") && important) {
                    // Body log events: skip transient ones, but keep important
                    String snippet = content.length() > 80 ? content.substring(0, 80) + "..." : content;
                    sb.append("[事件] ").append(snippet).append("; ");
                    count++;
                }
                continue;
            }

            // 重要事件追加标记
            if (important && sb.length() > 0) {
                // 在最后一个分号前插入★标记
                int lastSemi = sb.lastIndexOf("; ");
                if (lastSemi >= 0) {
                    sb.insert(lastSemi, "【重要】");
                }
            }
        }
        String summary = sb.toString();
        if (summary.length() <= MAX_HISTORY_SUMMARY_CHARS) return summary;
        // Preserve both the original owner objective and the newest outcomes.
        // Keeping only one end either forgets the task or forgets what just
        // failed; a bounded head/tail ledger retains both without token drift.
        int half = (MAX_HISTORY_SUMMARY_CHARS - 32) / 2;
        return summary.substring(0, half)
                + " ... [older details compacted] ... "
                + summary.substring(summary.length() - half);
    }

    /**
     * 为工具结果消息查找对应的工具名。
     * 向上查找最近的带 tool_calls 的 assistant 消息，匹配 toolCallId。
     */
    private static String findToolNameForResult(List<ChatMessage> history, int resultIndex) {
        if (resultIndex <= 0 || resultIndex >= history.size()) return "unknown";
        ChatMessage result = history.get(resultIndex);
        String resultId = result.toolCallId();
        if (resultId == null) return "unknown";
        // 向上查找匹配的 assistant tool_calls
        for (int i = resultIndex - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m.toolCalls() == null) continue;
            for (var tc : m.toolCalls()) {
                if (resultId.equals(tc.id())) return tc.name();
            }
        }
        return "unknown";
    }

    /**
     * Check whether a tool message has a parent assistant (with a matching
     * tool_call id) inside the already-kept portion of history. Used by
     * {@link #trimHistory()} to detect orphan tool messages whose parent
     * assistant was cut — those must be dropped, otherwise the
     * OpenAI-compatible API rejects the request with
     * "Messages with role 'tool' must be a response to a preceding message
     * with 'tool_calls'".
     *
     * @param kept    the messages already selected for keeping (in order)
     * @param toolMsg the tool message whose parent we are looking for
     * @return true if a matching assistant exists in {@code kept}
     */
    private static boolean hasParentAssistantInRange(List<ChatMessage> kept,
                                                       ChatMessage toolMsg) {
        String tcId = toolMsg.toolCallId();
        if (tcId == null) return false;
        // Scan backwards: tool results only follow their parent assistant,
        // so the parent — if kept — is the most recent assistant before us.
        for (int i = kept.size() - 1; i >= 0; i--) {
            ChatMessage pa = kept.get(i);
            if ("assistant".equals(pa.role()) && pa.toolCalls() != null) {
                for (var tc : pa.toolCalls()) {
                    if (tcId.equals(tc.id())) {
                        return true;
                    }
                }
            }
            // If we hit a user/system message, the tool's parent (if any)
            // is before this point and therefore NOT in the kept range.
            if ("user".equals(pa.role()) || "system".equals(pa.role())) {
                break;
            }
        }
        return false;
    }

    /**
     * Speak a message to the owner in-game.
     * Sends the AI's text response as a chat message to the owner player.
     */
    /** Say a line to the owner in chat and on the companion screen (a survival chain's few words). */
    public void say(String text) {
        if (text != null && !text.isBlank()) speakToOwner(text);
    }

    private void speakToOwner(String text) {
        // A question waits until they're back to the keys (SMH plan 70, MA5.4): it is asked in the welcome-back line.
        if (com.mineagent.engine.loop.AfkRules.isQuestion(text) && ownerActivity.hold(text)) {
            journal.record("held", text, currentGameTickSafe());
            return;
        }
        journal.record("said", text, currentGameTickSafe());
        Services.platform().scheduleOnServer(() -> {
            try {
                var owner = ((com.mineagent.engine.entity.CompanionEntity) companion).serverPlayerOwner();
                if (owner != null && owner.connection != null) {
                    // Send as a chat message with companion name prefix
                    owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§b[" + companion.companionName() + "]§r " + text));
                    // Push to the companion chat screen (C key) so the message
                    // also appears there — the vanilla system message only
                    // shows in the regular chat HUD.
                    com.mineagent.engine.network.MineAgentNetwork.sendUiActionTo(
                            owner, companion.companionId(), "companion_chat",
                            "[" + companion.companionName() + "] " + text);
                }
                // Ordinary speech is owner-facing and must not wake every
                // sibling LLM. Explicit [TEAM] messages are the only spoken
                // broadcast path; routine coordination uses TeamBlackboard.
                // Guard against null owner (player offline): broadcastToOtherCompanions
                // needs the owner UUID to find sibling companions, and calling
                // owner.getUUID() on a null reference would NPE. Skip the
                // broadcast in that case — sibling AIs will simply not hear
                // this utterance until the owner reconnects.
                if (owner != null && isExplicitTeamMessage(text)) {
                    com.mineagent.engine.MineAgentEngine.broadcastToOtherCompanions(
                            companion.companionId(), owner.getUUID(), text);
                }
            } catch (Exception e) {
                System.err.println("[MineAgent] Failed to send message to owner: " + e.getMessage());
            }
            // Also log to console for debugging
            System.out.println("[MineAgent][" + companion.companionName() + "] " + text);
        });
    }

    private void drainInbox() {
        synchronized (inboxLock) {
            if (inbox.isEmpty()) return;
            var batch = new ArrayList<>(inbox.values());
            inbox.clear();
            // Events are observations presented to the model, not immutable
            // system-level instructions. A user-role event also guarantees a
            // valid provider request for spawn/resume turns with no chat text.
            StringBuilder sb = new StringBuilder("[EVENTS]\n");
            for (String entry : batch) {
                sb.append("- ").append(entry).append("\n");
            }
            synchronized (history) {
                history.add(ChatMessage.user(sb.toString()));
            }
        }
    }

    private static boolean isExplicitTeamMessage(String text) {
        if (text == null) return false;
        String value = text.stripLeading();
        return value.startsWith("[TEAM]") || value.startsWith("【团队】");
    }

    // ── Tool execution ─────────────────────────────────────────────

    private enum ToolExecutionState { PENDING, RUNNING, COMPLETED, EXPIRED }

    /**
     * Normalize provider output before it enters conversation history.
     * Tool-call ids are protocol keys, so blank or duplicate ids would merge
     * results in maps and leave at least one call without its required reply.
     */
    private static ChatMessage normalizeAssistantMessage(ChatMessage message) {
        if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
            return ChatMessage.assistant(message.content());
        }
        Set<String> seenIds = new HashSet<>();
        List<ChatMessage.ToolCallRef> normalized = new ArrayList<>(message.toolCalls().size());
        for (ChatMessage.ToolCallRef call : message.toolCalls()) {
            if (call == null) continue;
            String id = call.id();
            while (id == null || id.isBlank() || !seenIds.add(id)) {
                id = "call_" + UUID.randomUUID().toString().replace("-", "");
            }
            String name = call.name() == null || call.name().isBlank()
                    ? "unknown" : call.name().trim();
            String arguments = call.arguments() == null || call.arguments().isBlank()
                    ? "{}" : call.arguments();
            normalized.add(new ChatMessage.ToolCallRef(id, name, arguments));
        }
        return ChatMessage.assistant(message.content(),
                normalized.isEmpty() ? null : normalized);
    }

    private static String errorJson(String message) {
        com.google.gson.JsonObject error = new com.google.gson.JsonObject();
        error.addProperty("error", message == null ? "Unknown error" : message);
        return error.toString();
    }

    private static String toolErrorJson(String toolName, Throwable failure) {
        String detail = "Tool '" + toolName + "' failed: "
                + failure.getClass().getSimpleName();
        if (failure.getMessage() != null && !failure.getMessage().isBlank()) {
            detail += " - " + failure.getMessage();
        }
        return errorJson(detail);
    }

    private List<ChatMessage> executeToolCalls(List<ChatMessage.ToolCallRef> toolCalls,
                                                long acceptedGeneration) {
        // Each ToolExecution carries its own toolCallId and toolName so that
        // results are matched correctly even when some tools are skipped
        // (unknown tool / invalid args). Previously the wait loop indexed into
        // `toolCalls` with the `executions` index, which misaligned whenever a
        // tool was skipped — causing toolCallId/result mismatches.
        record ToolExecution(String toolCallId, String toolName,
                             CountDownLatch latch,
                             AtomicReference<String> result,
                             AtomicReference<ToolExecutionState> state,
                             long deadlineNanos, boolean asynchronous) {}
        List<ToolExecution> executions = new ArrayList<>();
        // Collect results keyed by toolCallId so the final list can be rebuilt
        // in the original toolCalls order. This keeps analyzeToolResults (which
        // pairs toolCalls and toolResults by index) correct.
        Map<String, ChatMessage> resultsById = new LinkedHashMap<>();
        boolean asyncBodyReserved = false;

        for (var tc : toolCalls) {
            Optional<Tool> toolOpt = ToolRegistry.get(tc.name());
            if (toolOpt.isEmpty()) {
                resultsById.put(tc.id(), ChatMessage.toolResult(tc.id(),
                        errorJson("Unknown tool: " + tc.name())));
                continue;
            }

            Tool tool = toolOpt.get();
            com.google.gson.JsonObject args;
            try {
                args = com.google.gson.JsonParser.parseString(tc.arguments()).getAsJsonObject();
            } catch (Exception e) {
                resultsById.put(tc.id(), ChatMessage.toolResult(tc.id(),
                        errorJson("Invalid arguments: " + e.getMessage())));
                continue;
            }

            String notMeant = referentRefusal(tc.name(), args);
            if (notMeant != null) {
                resultsById.put(tc.id(), ChatMessage.toolResult(tc.id(), errorJson(notMeant)));
                continue;
            }
            if (tool.dispatchesAsyncTask() && asyncBodyReserved) {
                // One fake player has one body. Dispatching two asynchronous
                // physical tasks from the same response only lets the auction
                // accept one; the other becomes a misleading cancellation and
                // triggers another expensive reasoning round.
                resultsById.put(tc.id(), ChatMessage.toolResult(tc.id(),
                        "{\"error\":\"Skipped because this response already dispatched "
                                + "an asynchronous body task. Wait for task_finished before "
                                + "starting another body action.\",\"code\":\"body_task_conflict\"}"));
                continue;
            }
            if (tool.dispatchesAsyncTask()) asyncBodyReserved = true;

            // Each tool gets its own latch(1), result holder, and timeout (M3 fix)
            int timeout = Math.max(1, tool.defaultTimeoutSeconds());
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> resultHolder = new AtomicReference<>();
            AtomicReference<ToolExecutionState> state =
                    new AtomicReference<>(ToolExecutionState.PENDING);
            AtomicBoolean callbackDelivered = new AtomicBoolean(false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
            executions.add(new ToolExecution(
                    tc.id(), tc.name(), latch, resultHolder, state, deadline,
                    tool.dispatchesAsyncTask()));

            // Execute on server thread
            Runnable serverCall = () -> {
                // Cancellation/staleness may happen while this runnable waits in
                // the server queue. CAS prevents it from entering tool code after
                // the accepted turn has been invalidated or timed out.
                if (eventGeneration.get() != acceptedGeneration
                        || !state.compareAndSet(
                                ToolExecutionState.PENDING, ToolExecutionState.RUNNING)) {
                    state.compareAndSet(ToolExecutionState.PENDING,
                            ToolExecutionState.EXPIRED);
                    latch.countDown();
                    return;
                }
                try {
                    // Ground every tool decision in the latest carried state.
                    // This server-thread read also catches inventory changes
                    // made by vanilla between the one-second periodic samples.
                    publishWorldSnapshotNow(true);
                    long actionTick = currentGameTickSafe();
                    if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                        String blocker = planner.actionBlocker(semanticWorldModel, actionTick);
                        if (blocker != null) {
                            metrics.recordActionOutcome(tc.id(), CognitionMetrics.ActionOutcome.INVALID,
                                    "plan_prerequisite");
                            if (state.compareAndSet(ToolExecutionState.RUNNING, ToolExecutionState.COMPLETED)) {
                                resultHolder.set(errorJson(blocker));
                            }
                            latch.countDown();
                            return;
                        }
                    }
                    if (!tool.dispatchesAsyncTask()
                            && SKILL_ACTION_TOOLS.contains(tc.name())) {
                        planner.bindToolCall(tc.id());
                    }
                    if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                        metrics.recordActionDispatched(tc.id());
                        actionLedger.begin(tc.id(), tc.name(),
                                "server callback plus postcondition observation",
                                semanticWorldModel, actionTick);
                    }
                    semanticWorldModel.recordAction(tc.name(), args.toString(),
                            tc.id(), actionTick);
                    dispatchedActionTools.put(tc.id(), tc.name());
                    mechanismExplorer.onToolDispatched(tc.id(), tc.name(),
                            args.toString(), actionTick);
                    tool.onServerCall(tc.id(), args, companion, callbackResult -> {
                        // A modded tool or a defensive retry must not publish
                        // two outcomes for one call. Without this gate the
                        // second callback could overwrite a VERIFIED ledger
                        // entry, duplicate durable memory, and release a
                        // waiting LLM round twice.
                        if (!callbackDelivered.compareAndSet(false, true)
                                || state.get() != ToolExecutionState.RUNNING) {
                            return;
                        }
                        publishWorldSnapshotNow(true);
                        String safeResult = callbackResult == null
                                ? errorJson("Tool callback returned null") : callbackResult;
                        FailureAnalysis outcome = analyzeFailure(safeResult);
                        boolean async = "async_pending".equals(outcome.errorType());
                        boolean success = async || outcome.isSuccess();
                        long callbackTick = currentGameTickSafe();
                        if (!async) {
                            dispatchedActionTools.remove(tc.id());
                            if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                                var transaction = actionLedger.finishObserved(
                                        tc.id(), success, safeResult,
                                        semanticWorldModel, callbackTick);
                                success = transaction != null
                                        && transaction.status()
                                        == ActionTransactionLedger.Status.VERIFIED;
                                metrics.recordActionOutcome(tc.id(),
                                        metricOutcome(outcome, success),
                                        outcome.errorType());
                            }
                            semanticWorldModel.recordOutcome(tc.name(), success,
                                    safeResult, tc.id(), callbackTick,
                                    durableToolOutcome(tc.name()));
                        } else if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                            actionLedger.markPending(tc.id(), semanticWorldModel,
                                    callbackTick, safeResult);
                        }
                        mechanismExplorer.onToolResult(tc.id(), tc.name(), success, async,
                                safeResult, callbackTick);
                        if (state.compareAndSet(
                                ToolExecutionState.RUNNING, ToolExecutionState.COMPLETED)) {
                            // Store before releasing the latch so the loop thread
                            // observes a fully published callback result.
                            // The AtomicReference also protects callbacks issued
                            // by tools from a later tick.
                            resultHolder.set(safeResult);
                            latch.countDown();
                        }
                    });
                } catch (Throwable t) {
                    // A tool throwing (e.g. NPE from missing/invalid args)
                    // must not leak into the server thread and must not
                    // leave the latch hanging until timeout — reply with a
                    // meaningful error so the LLM can correct its call.
                    System.err.println("[MineAgent] Tool '" + tc.name()
                            + "' threw: " + t);
                    semanticWorldModel.recordOutcome(tc.name(), false,
                            String.valueOf(t.getMessage()), tc.id(), currentGameTickSafe(),
                            durableToolOutcome(tc.name()));
                    if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                        actionLedger.finish(tc.id(), ActionTransactionLedger.Status.FAILED,
                                String.valueOf(t.getMessage()), semanticWorldModel,
                                currentGameTickSafe());
                    }
                    dispatchedActionTools.remove(tc.id());
                    mechanismExplorer.onToolResult(tc.id(), tc.name(), false, false,
                            String.valueOf(t.getMessage()), currentGameTickSafe());
                    if (state.compareAndSet(
                            ToolExecutionState.RUNNING, ToolExecutionState.COMPLETED)) {
                        resultHolder.set(toolErrorJson(tc.name(), t));
                        latch.countDown();
                    }
                }
            };
            try {
                Services.platform().scheduleOnServer(serverCall);
            } catch (Throwable schedulingFailure) {
                if (state.compareAndSet(
                        ToolExecutionState.PENDING, ToolExecutionState.COMPLETED)) {
                    semanticWorldModel.recordOutcome(tc.name(), false,
                            String.valueOf(schedulingFailure.getMessage()), tc.id(),
                            currentGameTickSafe(), durableToolOutcome(tc.name()));
                    if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                        actionLedger.finish(tc.id(), ActionTransactionLedger.Status.UNVERIFIED,
                                String.valueOf(schedulingFailure.getMessage()),
                                semanticWorldModel, currentGameTickSafe());
                    }
                    mechanismExplorer.onToolResult(tc.id(), tc.name(), false,
                            false, String.valueOf(schedulingFailure.getMessage()),
                            currentGameTickSafe());
                    resultHolder.set(toolErrorJson(tc.name(), schedulingFailure));
                    latch.countDown();
                }
            }
        }

        // Wait for all dispatched tools to complete. Use exec.toolCallId()
        // (NOT toolCalls.get(i).id()) so the result is always matched to the
        // correct toolCallId regardless of how many tools were skipped above.
        for (ToolExecution exec : executions) {
            try {
                long remaining = exec.deadlineNanos() - System.nanoTime();
                boolean done = remaining > 0
                        && exec.latch().await(remaining, TimeUnit.NANOSECONDS);
                if (!done) {
                    // A callback can arrive at the same instant as the
                    // deadline.  Claim expiry with CAS; if the callback won,
                    // keep its authoritative result instead of overwriting a
                    // verified action with a timeout.
                    boolean expired = exec.state().compareAndSet(
                            ToolExecutionState.RUNNING, ToolExecutionState.EXPIRED)
                            || exec.state().compareAndSet(
                            ToolExecutionState.PENDING, ToolExecutionState.EXPIRED);
                    if (!expired) {
                        String lateResult = exec.result().get();
                        if (lateResult != null) {
                            resultsById.put(exec.toolCallId(), ChatMessage.toolResult(
                                    exec.toolCallId(), truncateToolResult(lateResult,
                                            getToolResultLimit(exec.toolName()), exec.toolName())));
                            continue;
                        }
                    }
                    if (expired && exec.asynchronous()) {
                        cancelTimedOutTask(exec.toolCallId());
                    }
                    dispatchedActionTools.remove(exec.toolCallId());
                    semanticWorldModel.recordOutcome(exec.toolName(), false,
                            "Tool execution timed out", exec.toolCallId(),
                            currentGameTickSafe(), durableToolOutcome(exec.toolName()));
                    if (SKILL_ACTION_TOOLS.contains(exec.toolName())) {
                        actionLedger.finish(exec.toolCallId(),
                                ActionTransactionLedger.Status.UNVERIFIED,
                                "Tool execution timed out", semanticWorldModel,
                                currentGameTickSafe());
                        metrics.recordActionOutcome(exec.toolCallId(),
                                CognitionMetrics.ActionOutcome.UNVERIFIED, "timeout");
                    }
                    mechanismExplorer.onToolResult(exec.toolCallId(), exec.toolName(), false,
                            false, "Tool execution timed out", currentGameTickSafe());
                    resultsById.put(exec.toolCallId(), ChatMessage.toolResult(exec.toolCallId(),
                            "{\"error\":\"Tool execution timed out\"}"));
                } else {
                    String rawResult = exec.result().get();
                    if (rawResult == null) {
                        rawResult = exec.state().get() == ToolExecutionState.EXPIRED
                                ? "{\"error\":\"Tool execution cancelled\"}"
                                : "{\"error\":\"Tool completed without callback evidence\"}";
                        if (SKILL_ACTION_TOOLS.contains(exec.toolName())) {
                            metrics.recordActionOutcome(exec.toolCallId(),
                                    exec.state().get() == ToolExecutionState.EXPIRED
                                            ? CognitionMetrics.ActionOutcome.CANCELLED
                                            : CognitionMetrics.ActionOutcome.UNVERIFIED,
                                    "callback_missing");
                        }
                    }
                    // Truncate long tool results to keep the conversation compact.
                    // 截断长度根据工具类型差异化：
                    // - look_around/scan: 1200 字符（需要看到周围环境，不能太短）
                    // - status/inventory: 600 字符（中等）
                    // - 其他: 800 字符（默认）
                    // 危险信息优先保留。
                    int maxChars = getToolResultLimit(exec.toolName());
                    String truncated = truncateToolResult(rawResult, maxChars, exec.toolName());
                    resultsById.put(exec.toolCallId(),
                            ChatMessage.toolResult(exec.toolCallId(), truncated));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                boolean expired = exec.state().compareAndSet(
                        ToolExecutionState.RUNNING, ToolExecutionState.EXPIRED)
                        || exec.state().compareAndSet(
                        ToolExecutionState.PENDING, ToolExecutionState.EXPIRED);
                if (expired && exec.asynchronous()) {
                    cancelTimedOutTask(exec.toolCallId());
                }
                dispatchedActionTools.remove(exec.toolCallId());
                semanticWorldModel.recordOutcome(exec.toolName(), false,
                        "Interrupted", exec.toolCallId(), currentGameTickSafe(),
                        durableToolOutcome(exec.toolName()));
                if (SKILL_ACTION_TOOLS.contains(exec.toolName())) {
                    actionLedger.finish(exec.toolCallId(),
                            ActionTransactionLedger.Status.CANCELLED, "Interrupted",
                            semanticWorldModel, currentGameTickSafe());
                    metrics.recordActionOutcome(exec.toolCallId(),
                            CognitionMetrics.ActionOutcome.CANCELLED, "interrupted");
                }
                mechanismExplorer.onToolResult(exec.toolCallId(), exec.toolName(), false,
                        false, "Interrupted", currentGameTickSafe());
                resultsById.put(exec.toolCallId(), ChatMessage.toolResult(exec.toolCallId(),
                        "{\"error\":\"Interrupted\"}"));
            }
        }

        // Build the results list in the original toolCalls order so that
        // analyzeToolResults (which pairs by index) works correctly.
        List<ChatMessage> results = new ArrayList<>(toolCalls.size());
        for (var tc : toolCalls) {
            ChatMessage r = resultsById.get(tc.id());
            if (r != null) {
                results.add(r);
                metrics.recordToolCall();
                FailureAnalysis analysis = analyzeFailure(r.content());
                if (SKILL_ACTION_TOOLS.contains(tc.name())) {
                    if ("async_pending".equals(analysis.errorType())) {
                        metrics.recordActionDispatched(tc.id());
                    } else {
                        metrics.recordActionOutcome(tc.id(),
                                metricOutcome(analysis, false),
                                analysis.errorType());
                    }
                }
            }
        }
        return results;
    }

    /** Stop an async body action whose tool callback deadline elapsed. */
    private void cancelTimedOutTask(String taskId) {
        if (taskId == null || taskId.isBlank()) return;
        try {
            MineAgentEngine.getCompanion(companion.companionId())
                    .ifPresent(state -> state.auction.cancelTask(taskId));
        } catch (Throwable failure) {
            System.err.println("[MineAgent] Failed to cancel timed-out tool task "
                    + taskId + ": " + failure.getMessage());
        }
    }

    /** Only physical/world-changing tools belong in durable outcome memory. */
    private static boolean durableToolOutcome(String toolName) {
        return toolName != null && SKILL_ACTION_TOOLS.contains(toolName);
    }

    /**
     * 根据工具名获取结果截断长度。
     * 感知类工具（look_around）需要更长，简单动作类可以更短。
     */
    private static int getToolResultLimit(String toolName) {
        if (toolName == null) return 800;
        String lower = toolName.toLowerCase();
        // look_around now emits ordered, bounded JSON. Keep the complete
        // object instead of repairing a cut in the entity or terrain arrays.
        if (lower.contains("look_around")) return 9000;
        if (lower.contains("resolve_need")) return 7000;
        if (lower.contains("plan_acquisition")) {
            // This result is an explicitly requested dependency DAG. Cutting
            // it at the generic 800-character action limit removes deeper
            // leaves and makes the planner fabricate missing prerequisites.
            // The tool itself bounds depth/node count, so a larger complete
            // result remains deterministic and paid only on planning turns.
            return 26000;
        }
        if (lower.contains("inspect_gui") || lower.contains("inspect_block_storage")) {
            return 9000;
        }
        // 感知类：需要完整环境信息，给 1200 字符
        if (lower.contains("look") || lower.contains("scan")
                || lower.contains("search") || lower.contains("find")) {
            return 1200;
        }
        // 状态类：中等长度，600 字符
        if (lower.contains("status") || lower.contains("inventory")
                || lower.contains("health") || lower.contains("check")) {
            return 600;
        }
        // 默认：800 字符
        return 800;
    }

    /**
     * Truncate a tool result string to a maximum length.
     *
     * <p><b>安全截断</b>：先尝试在截断点找最后一个完整的 JSON 对象/数组
     * 元素并正确闭合结构，再用 {@link com.google.gson.JsonParser} 校验。
     * 如果修复后仍不是合法 JSON（嵌套过深、字符串内含截断点等），
     * 回退到纯文本 "结果过长已截断: ..." —— 纯文本虽然不是 JSON，但
     * 至少不会让 LLM 解析失败或产生幻觉字段。
     *
     * @param result raw tool result
     * @param maxChars maximum characters to keep
     * @param toolName tool name (for importance-aware truncation)
     * @return truncated result (possibly with a truncation marker appended)
     */
    private String truncateToolResult(String result, int maxChars, String toolName) {
        if (result == null || result.length() <= maxChars) return result;

        // 动态重要性评估：用 ImportanceEvaluator 判断是否需要保留重要片段
        // 如果整个结果被判定为重要，给更多空间（1.3倍）
        boolean isImportant = importance.isImportant(toolName, result);
        if (isImportant) {
            maxChars = (int)(maxChars * 1.3);  // 重要结果多保留30%
        }

        String truncated = result.substring(0, Math.min(maxChars, result.length()));

        // 尝试产生合法 JSON：在截断点找最后一个完整元素，闭合结构，
        // 并追加 "truncated":true 标记。校验通过才采用。
        String repaired = tryRepairJsonTruncation(truncated, maxChars);
        if (repaired != null) {
            return repaired;
        }
        // 回退：纯文本。LLM 仍能读懂内容，只是无法当 JSON 解析。
        // 这比发出畸形 JSON（导致解析错误或幻觉字段）安全得多。
        return "结果过长已截断 (原始长度=" + result.length() + " 字符): " + truncated;
    }

    /**
     * 尝试把截断后的字符串修复为合法 JSON。
     *
     * <p>策略：
     * <ul>
     *   <li>对象 ({...})：找最后一个 "}," 截断点，保留到该处，追加
     *       "truncated":true 并闭合 }。</li>
     *   <li>数组 ([...])：找最后一个 "}," 截断点，保留到该处，追加
     *       {"truncated":true} 并闭合 ]。</li>
     *   <li>非 JSON（纯文本）：直接追加截断标记。</li>
     * </ul>
     * 每个候选都用 JsonParser 校验，失败则返回 null 让调用方回退到纯文本。
     */
    private static String tryRepairJsonTruncation(String truncated, int maxChars) {
        String trimmed = truncated.trim();
        try {
            if (trimmed.startsWith("{")) {
                // 对象：优先在最后一个完整字段后截断
                int lastComplete = truncated.lastIndexOf("},");
                if (lastComplete > maxChars / 2) {
                    String candidate = truncated.substring(0, lastComplete + 1)
                            + "\"truncated\":true}";
                    if (isValidJson(candidate)) return candidate;
                }
                // 直接闭合对象
                String candidate = truncated + ",\"truncated\":true}";
                if (isValidJson(candidate)) return candidate;
            } else if (trimmed.startsWith("[")) {
                // 数组：优先在最后一个完整元素后截断
                int lastComplete = truncated.lastIndexOf("},");
                if (lastComplete > maxChars / 2) {
                    String candidate = truncated.substring(0, lastComplete + 1)
                            + "{\"truncated\":true}]";
                    if (isValidJson(candidate)) return candidate;
                }
                String candidate = truncated + ",{\"truncated\":true}]";
                if (isValidJson(candidate)) return candidate;
            } else {
                // 非 JSON 纯文本：追加截断标记即可
                return truncated + "...[已截断]";
            }
        } catch (Exception e) {
            // 任何异常都回退到纯文本
        }
        return null;
    }

    /** 用 Gson 校验字符串是否为合法 JSON。 */
    private static boolean isValidJson(String s) {
        try {
            com.google.gson.JsonParser.parseString(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    /**
     * Build a cache key for the current decision context.
     *
     * <p>The key quantizes the AI's situation so that similar states
     * (same rough position, health tier, hunger tier, last owner command)
     * map to the same cache entry. This lets us skip the LLM call when
     * the situation hasn't meaningfully changed.
     *
     * <p>Quantization is intentionally coarse:
     * <ul>
     *   <li>Position: 4-block grid (a 4×4×4 cube shares one key)</li>
     *   <li>Health: 4 tiers (0-4, 5-9, 10-14, 15-20)</li>
     *   <li>Hunger: 3 tiers (0-6, 7-14, 15-20)</li>
     *   <li>Last owner command: included verbatim (most important signal)</li>
     * </ul>
     */
    private String buildDecisionCacheKey() {
        try {
            int x = (int) companion.posX();
            int y = (int) companion.posY();
            int z = (int) companion.posZ();
            float health = companion.health();
            int food = companion.foodLevel();
            int healthTier = health < 5 ? 0 : health < 10 ? 1 : health < 15 ? 2 : 3;
            int hungerTier = food < 7 ? 0 : food < 15 ? 1 : 2;
            String lastCmd = lastOwnerCommand;
            return DecisionCache.buildKey(x, y, z, healthTier, hungerTier,
                    null, lastCmd);
        } catch (Exception e) {
            return null;
        }
    }

    /** Stable cache affinity excludes volatile ticks, coordinates and UUIDs. */
    private String stablePromptCacheKey(LLMProvider provider) {
        String owner = companion.ownerUuid() == null ? "owner" : companion.ownerUuid().toString();
        String name = companion.companionName() == null ? "companion"
                : companion.companionName().trim().replaceAll("[^a-zA-Z0-9_-]", "_");
        return provider.providerId() + ":" + model + ":mineagent-prompt-v6:" + owner + ":" + name;
    }

    /** HTTP deadline is bounded per reasoning round so stale requests can be cancelled. */
    private static long providerDeadlineSeconds(int roundNumber) {
        return roundNumber <= 0 ? 120L : 90L;
    }

    /** Track the last owner command for cache keying. */
    private volatile String lastOwnerCommand = "";

    /** Full active owner objective used for memory retrieval and skill naming. */
    private volatile String activeOwnerGoal = "";

    /** 上次玩家消息的时间戳（用于决策缓存安全门）。 */
    private volatile long lastOwnerMessageTime = 0;

    /** 连续缓存命中次数（达到上限后强制走 LLM）。 */
    private int consecutiveCacheHits = 0;

    /** 当前回合内的补救次数（防止无限递归）。每个 turn 开始时重置。 */
    private int remediationRoundCounter = 0;

    /**
     * 决策缓存安全门 — 判断当前是否应跳过缓存。
     *
     * <p>跳过缓存的场景：
     * <ul>
     *   <li>玩家最近 30 秒内说过话（对话需要 LLM 真正理解，不能复用）</li>
     *   <li>连续缓存命中已达上限（防止"卡带"式无限循环）</li>
     * </ul>
     */
    private boolean shouldSkipCache() {
        // 1. 玩家最近说过话 → 不缓存
        if (lastOwnerMessageTime > 0) {
            long elapsed = System.currentTimeMillis() - lastOwnerMessageTime;
            if (elapsed < CACHE_SKIP_AFTER_PLAYER_MSG_MS) {
                return true;
            }
        }
        // 2. 连续命中已达上限 → 强制走 LLM
        if (consecutiveCacheHits >= MAX_CONSECUTIVE_CACHE_HITS) {
            return true;
        }
        return false;
    }

    /**
     * 检查 LLM 响应是否显式声明了"无行动"标记。
     *
     * <p>基于 NLT (Natural Language Tools, arxiv 2510.14453) 和 Reflexion
     * 的 "explicit intent declaration" 模式。系统不再通过脆弱的关键词列表
     * 猜测文本是否包含行动意图，而是要求 LLM 在不需要执行任何身体动作时
     * 显式标注 [NO_ACTION] 或 【无行动】 标记。
     *
     * <p>这完全替代了之前的 containsActionIntent() 关键词列表方法：
     * <ul>
     *   <li>多语言无关：标记本身是结构化的，不依赖任何语言的关键词</li>
     *   <li>LLM 自主判断：由 LLM 自己决定是否需要行动，而非系统猜测</li>
     *   <li>无遗漏：只要 LLM 遵守协议，所有"只说不做"的情况都能被捕获</li>
     *   <li>零维护：无需随场景变化更新关键词列表</li>
     * </ul>
     *
     * <p>用法：当 LLM 返回纯文本响应（无 tool_calls）时：
     * <ul>
     *   <li>有标记 → LLM 明确声明无行动 → 结束回合，可缓存</li>
     *   <li>无标记 → LLM 可能漏掉了工具调用 → 触发补救机制</li>
     * </ul>
     *
     * @param text LLM 响应文本
     * @return true 如果文本包含显式无行动标记
     */
    private static boolean hasExplicitNoActionMarker(String text) {
        if (text == null || text.isBlank()) return false;
        // 接受中英文多种标记形式（LLM 可能用任意一种，标记可在文本任意位置）
        // 故意接受多种变体以提高鲁棒性：
        // - [NO_ACTION] / 【无行动】 是系统提示词中明示的两种主标记
        // - (无行动) / [无行动] / （NO_ACTION） 是常见输入变体，作为容错
        return text.contains("[NO_ACTION]")
            || text.contains("【无行动】")
            || text.contains("(无行动)")
            || text.contains("（无行动）")
            || text.contains("[无行动]")
            || text.contains("（NO_ACTION）");
    }

    /**
     * 将文本截断到指定长度用于日志输出。
     * 超长部分用 "..." 替代，并移除换行符避免日志污染。
     */
    private static String truncateForLog(String text, int maxLen) {
        if (text == null) return "";
        String single = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (single.length() <= maxLen) return single;
        return single.substring(0, maxLen) + "...";
    }

    /**
     * 检查给定文本是否与 history 中最后一条 assistant 消息相同。
     * 用于防止缓存命中时回放与上次完全相同的回复（"卡带"循环）。
     */
    private boolean isSameAsLastAssistant(String text) {
        if (text == null) return false;
        synchronized (history) {
            for (int i = history.size() - 1; i >= 0; i--) {
                ChatMessage msg = history.get(i);
                if ("assistant".equals(msg.role())) {
                    String lastContent = msg.content();
                    if (lastContent == null) return false;
                    // Trim whitespace for comparison
                    return text.trim().equalsIgnoreCase(lastContent.trim());
                }
            }
        }
        return false;
    }

    private LLMProvider resolveProvider() {
        LLMProvider existing = companionProvider;
        if (existing != null) return existing;
        // ── Provider selection ──
        // Model switching is NOT allowed at runtime — each companion is bound
        // to one (provider, model, baseUrl) triple for its entire lifetime.
        //
        // Selection order:
        //   1. If providerId is set AND it supports the current model,
        //      use it. This lets the user explicitly choose a provider for
        //      a relay URL (e.g. picking "anthropic" when the relay speaks
        //      Anthropic native format, or "openai" when it speaks
        //      OpenAI-compatible format for the same Claude model).
        //   2. Otherwise auto-detect from the model name. This handles
        //      official URLs and the common case where providerId was
        //      not set in config.
        //
        // Note: We deliberately do NOT force OpenAI format for relay URLs —
        // many relays support Anthropic /v1/messages and Gemini /v1beta/...
        // natively, and the user's choice of providerId is authoritative.
        LLMProvider resolved;
        if (providerId != null) {
            var byId = LLMProviderRegistry.get(providerId);
            if (byId.isPresent()) {
                // An explicit provider is authoritative.  Vendor adapters
                // intentionally accept custom/future model IDs and relay
                // endpoints; rejecting them here caused a misleading
                // auto-detection failure before the request ever reached the
                // configured endpoint.
                resolved = byId.get();
            } else {
                resolved = LLMProviderRegistry.detectForModel(model)
                        .orElseThrow(() -> new IllegalStateException(
                                "Cannot detect provider for model: " + model
                                        + " (providerId=" + providerId + ")"));
            }
        } else {
            resolved = LLMProviderRegistry.detectForModel(model)
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot detect provider for model: " + model
                                    + " (providerId=" + providerId + ")"));
        }
        synchronized (this) {
            if (companionProvider == null) {
                companionProvider = java.util.Objects.requireNonNull(resolved.newSession(),
                        "Provider returned a null companion session");
            }
            return companionProvider;
        }
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (Tool tool : ToolRegistry.all()) {
            if (!CORE_TOOL_NAMES.contains(tool.name())
                    && !exposedExtraTools.contains(tool.name())) continue;
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameterSchema());
            def.put("function", function);
            defs.add(def);
        }
        return defs;
    }

    /** Build the immutable instruction prefix shared by every request. */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        // ── Core identity ──
        sb.append("You are MineAgent, an AI player in Minecraft — NOT an NPC, NOT a bot.\n");
        sb.append("You are a real player entity with a body, inventory, health, and hunger.\n");
        sb.append("You think, decide, and act like an experienced human player would.\n\n");

        // ── Cognitive framework: HOW to think, not WHAT to know ──
        sb.append("## How You Think\n");
        sb.append("You already know Minecraft inside out — recipes, mob behaviors, ");
        sb.append("ore depths, crafting chains, combat tactics, redstone, farming, ");
        sb.append("potion brewing, enchanting, villager trading, all of it.\n");
        sb.append("Your job is NOT to recite that knowledge, but to USE it ");
        sb.append("to make smart decisions in real-time.\n\n");

        sb.append("### The Think-Act Loop\n");
        sb.append("For long goals, attach live prerequisites to todowrite steps (materials, usable tools, target state). "
                + "A historical completed milestone does not prove a resource is still available. "
                + "When prerequisite_unknown is reported, observe first; when prerequisite_changed is reported, "
                + "restore the requirement or repair the affected suffix while preserving verified achievements.\n");
        sb.append("For unfamiliar mechanisms, use explore_mechanism predict to compare observed outcomes and uncertainty, "
                + "plan to compose a bounded route from verified state transitions, "
                + "and curriculum to identify owner-relevant knowledge gaps. Empirical evidence overrides guesses; "
                + "a prediction never grants action permission. Test one bounded intervention, inspect the result, "
                + "and repair a contradicted rule before reuse. Transfer learned adapters through named bindings "
                + "so both the action and its verification point to the new observed target.\n");
        sb.append("Before EVERY action, run through this mental checklist:\n");
        sb.append("1. **Observe** — What's around me? What's my status? What changed?\n");
        sb.append("2. **Assess** — Am I in danger? Am I hungry? Am I equipped for this?\n");
        sb.append("3. **Decide** — What's the best next action? What do I need first?\n");
        sb.append("4. **Prepare** — Do I have the right tools? If not, make them first.\n");
        sb.append("5. **Act** — Execute, then observe the result and adapt.\n\n");

        sb.append("### Task Decomposition\n");
        sb.append("When given ANY task — no matter how complex — break it down:\n");
        sb.append("- 'Go mining' → What tools do I need? → Do I have them? ");
        sb.append("→ If not, what do I need to make them? → Start from the beginning.\n");
        sb.append("- 'Build a farm' → What crops? → Where's water? → ");
        sb.append("Do I have a hoe? → Seeds? → Plan the layout first.\n");
        sb.append("- 'Explore the Nether' → Do I have armor? Fire resistance? ");
        sb.append("Food? A way back home? → Prepare ALL of this before entering.\n");
        sb.append("- ANY task → What's the prerequisite? → What's ITS prerequisite? ");
        sb.append("→ Recurse until you reach something you can do RIGHT NOW.\n\n");

        sb.append("### Asset and Capability Reuse\n");
        sb.append("Treat inventory, equipment, known container contents, dropped items, and placed facilities as one asset system. ");
        sb.append("Before crafting, replacing equipment, or placing another workstation, resolve the actual need: an exact item, a quantity, or a capability. ");
        sb.append("Prefer in order: reuse carried asset; verify and retrieve a known stored asset; reuse a reachable world facility; only then produce or acquire a replacement. ");
        sb.append("A remembered container is evidence, not x-ray truth: approach and inspect it before transfer. ");
        sb.append("Use registered IDs, recipe data, tags, capabilities, distance, risk and task delay; never assume that a cheaper newly crafted item is better than a known capable item. ");
        sb.append("If the owner explicitly needs an additional copy or retrieval is unsafe, say why and produce the extra item with an explicit desired total.\n\n");

        sb.append("### Situational Judgment\n");
        sb.append("There is no fixed playbook. Read the situation and adapt:\n");
        sb.append("- See a creeper? Don't charge in — assess: ");
        sb.append("can I kill it in time? Should I retreat? Is the owner nearby?\n");
        sb.append("- Found diamonds? Think: is this area safe? ");
        sb.append("Do I have an iron pickaxe? Torches? An exit?\n");
        sb.append("- Night falling? Decide: fight through it, build a shelter, ");
        sb.append("or sleep? Depends on my gear and the owner's plan.\n");
        sb.append("- Owner is in danger? DROP everything. Protect them. No task ");
        sb.append("matters more than the owner's survival.\n\n");

        sb.append("### Autonomous Decision-Making\n");
        sb.append("You don't need to be told every little step. If the owner says ");
        sb.append("'go gather wood', you know that means: find trees, punch them, ");
        sb.append("craft planks, maybe make tools. You fill in the gaps yourself.\n");
        sb.append("If you encounter a problem the owner didn't anticipate, ");
        sb.append("solve it yourself. Only ask the owner when YOU genuinely ");
        sb.append("can't decide.\n\n");

        sb.append("### Situated Reasoning Protocol\n");
        sb.append("Do not apply one fixed score or one fixed set of weights to every situation. ");
        sb.append("First identify hard constraints from survival, the owner's request, and current world facts. ");
        sb.append("Then generate more than one feasible action when the choice matters, compare their concrete ");
        sb.append("consequences in this situation, and prefer reversible information-gathering when uncertainty is high. ");
        sb.append("Resource scarcity, urgency, future reuse, cleanup, and risk matter only when the current evidence makes them relevant. ");
        sb.append("After acting, verify the world or inventory result before marking a plan step complete. ");
        sb.append("For temporary supports, decide among recover now, defer, or deliberately leave them based on live safety, ");
        sb.append("escape-route value, reachability, scarcity, task delay, and owner intent; never always recover or always leave.\n\n");

        sb.append("### Hierarchical Rolling Planning\n");
        sb.append("For goals needing multiple dependent actions, call `todowrite` before the first body action. ");
        sb.append("Keep the stable strategic goal and owner constraints, but plan only the next 3-6 concrete tactical milestones with explicit dependencies and observable success criteria. ");
        sb.append("Execute one body action or verified learned skill at a time. Preserve verified milestones; when `[ROLLING_REPLAN]` arrives, repair the invalid suffix from current semantic evidence instead of rewriting completed work or repeating the failed call. ");
        sb.append("Include a reversible observation or contingency when a prerequisite is uncertain.\n\n");

        // ── Decision priority ──
        sb.append("## Priority When Multiple Things Compete\n");
        sb.append("1. **Immediate survival** — About to die? Handle that first.\n");
        sb.append("2. **Owner in danger** — Help them, even if it costs you.\n");
        sb.append("3. **Owner's explicit request** — What they asked for right now.\n");
        sb.append("4. **Supporting the owner** — Things that help them indirectly.\n");
        sb.append("5. **Self-improvement** — Better gear, more food, safer base.\n");
        sb.append("When in doubt, communicate: tell the owner what you're prioritizing and why.\n\n");

        // ── Tool usage ──
        sb.append("## Your Tools\n");
        sb.append("You perceive and act through tools. Key ones:\n");
        sb.append("- `query_extra_tools`: Expose specialized tool schemas for the rest of this turn. "
                + "Call it before using a specialized tool that is not currently offered.\n");
        sb.append("- `get_self_status`: Check your health, hunger, inventory, equipment\n");
        sb.append("- `resolve_need`: Query owned/stored/world assets and live recipes for an item or capability before producing a replacement\n");
        sb.append("- `plan_acquisition`: Expand a target item/count into a bounded recursive dependency DAG. It distinguishes carried, observed storage, planned surplus, craftable recipes and unknown station mechanics; follow its leaves instead of remaking assets blindly.\n");
        sb.append("- `look_around`: See terrain, blocks, entities, hazards around you\n");
        sb.append("- `scan_blocks`: Find specific blocks; entity, GUI, storage, ranged, location, and memory tools are available through `query_extra_tools`\n");
        sb.append("- `goto`: Move to a location (xz for horizontal, xzy for exact)\n");
        sb.append("- `auto_mine`: Dig blocks / `build`: Place blocks\n");
        sb.append("- `collect_items`: Pick up drops\n");
        sb.append("- `equip_item`: Reuse an existing tool, armor piece, hotbar item or offhand item\n");
        sb.append("- `craft`: Create items from recipes\n");
        sb.append("- `use_item`: Use a held item in air through vanilla (throws, drinks, charges); use target coordinates when direction matters and wait for its terminal result.\n");
        sb.append("- `wait_for`: Hold the body on a bounded, server-observed condition (inventory/GUI/semantic fact/dimension/block/entity/time). Use it after opening a furnace or unfamiliar machine instead of narrating an untracked wait.\n");
        sb.append("- `lookup_recipe`: Query crafting recipes by input/output. ");
        sb.append("USE THIS before crafting to know exact ingredients — don't guess.\n");
        sb.append("- `recall_memory` (specialized): Recall places you've discovered (ores, structures, hazards). ");
        sb.append("USE THIS before exploring — you may already know where to find what you need.\n");
        sb.append("- `list_learned_skills`: See skills you've mastered from past successes. ");
        sb.append("Use `execute_skill` to run one through precondition/action/postcondition verification instead of manually replaying every step.\n");
        sb.append("- `explore_mechanism`: For unfamiliar content, compare competing hypotheses, rank bounded probes by information/cost, and arm exactly one reversible falsifiable experiment. State-changing probes require explicit compensation. One success never creates a reusable rule; independent contexts and verified deltas are required.\n");
        sb.append("- `coordinate_team`: Inspect team commitments, claim a role, or request targeted support.\n");
        sb.append("Read tool results carefully. If a tool fails, think about WHY ");
        sb.append("and try a different approach.\n\n");

        sb.append("## Executor Truth Rules\n");
        sb.append("- RUNNING is only a scheduler state; it is never proof that your body is moving.\n");
        sb.append("- Use executor evidence (player position, movement index, stagnant ticks, block changes) before claiming progress.\n");
        sb.append("- If stagnant ticks increase or a path failure is present, report the obstruction honestly and change target or approach.\n");
        sb.append("- Reuse assets reported by the world asset index or resolve_need. Distance alone is not absence; compare retrieval against production before replacing anything.\n");
        sb.append("- Minimize LLM round trips: batch independent observations in one response, and let an async action task finish before polling it repeatedly.\n\n");
        sb.append("- When the required tool is already exposed and its arguments are grounded, call it directly. Do not spend a round narrating, rediscovering schemas, or rechecking an unchanged successful result.\n\n");

        // ── Critical: action requires tools ──
        sb.append("## CRITICAL: Action Requires Tools\n");
        sb.append("Talking is NOT doing. If you want to move, you MUST call `goto`. ");
        sb.append("If you want to mine, you MUST call `auto_mine`. ");
        sb.append("Saying 'I'll go check over there' without calling the tool ");
        sb.append("means you stay exactly where you are. ");
        sb.append("NEVER describe an action without executing it via a tool call. ");
        sb.append("Your words are for communication with the owner; ");
        sb.append("your tools are for actually doing things.\n\n");

        // ── Action Intent Declaration Protocol (replaces keyword detection) ──
        // Based on NLT (arxiv 2510.14453) and Reflexion's explicit intent
        // declaration pattern. Instead of system guessing whether text
        // contains action intent (fragile keyword matching), require LLM
        // to EXPLICITLY declare its intent state every turn.
        sb.append("## Action Intent Declaration Protocol (MANDATORY)\n");
        sb.append("Every response you produce MUST fall into exactly one of two categories:\n");
        sb.append("1. **Action response**: You call at least one tool to perform a physical ");
        sb.append("action (move/mine/build/attack/craft/eat/place/collect/etc.). ");
        sb.append("Text may accompany the tool call to explain what you're doing.\n");
        sb.append("2. **No-action response**: You have NO physical action to perform ");
        sb.append("this turn (pure conversation, status report, waiting, ");
        sb.append("acknowledgment). In this case, you MUST include the marker ");
        sb.append("`[NO_ACTION]` or `【无行动】` somewhere in your text response.\n\n");
        sb.append("**Why this matters**: If you produce text without tool calls ");
        sb.append("AND without the marker, the system will assume you FORGOT to ");
        sb.append("call a tool and will give you one reminder. This wastes time ");
        sb.append("and tokens. Use the marker to clearly say 'I have considered ");
        sb.append("it, and no physical action is needed right now.'\n\n");
        sb.append("**Examples**:\n");
        sb.append("- Action: call `goto` + text '我去看看那边' → OK\n");
        sb.append("- No-action: '好的，我明白了。【无行动】' → OK\n");
        sb.append("- No-action: 'Got it. [NO_ACTION]' → OK\n");
        sb.append("- AMBIGUOUS (will trigger reminder): '我去看看那边' ");
        sb.append("(no tool call, no marker) → system will remind you\n\n");
        sb.append("**The marker is your honest declaration**: Use it when you ");
        sb.append("genuinely have no physical action to take. Never use it to ");
        sb.append("avoid calling a tool when you actually intend to act.\n\n");

        // ── Reflexes & instincts ──
        sb.append("## Body Instincts (Automatic)\n");
        sb.append("Your body has survival reflexes that fire automatically:\n");
        for (var reflex : com.mineagent.api.task.reflex.ReflexRegistry.all()) {
            sb.append("- ").append(reflex.id()).append(": ").append(reflex.description());
            // Enabled state can change at runtime and therefore belongs in
            // live context if it ever becomes decision-relevant. Keeping only
            // stable registry metadata here preserves the cached prefix.
            sb.append("\n");
        }
        sb.append("These don't cost tokens. When they fire, you'll see [BODY] events.\n");
        sb.append("Don't fight your instincts — work WITH them.\n\n");

        // ── Skills ──
        sb.append("## Available Skills (load with `load_skill`)\n");
        for (var skill : SkillRegistry.all()) {
            sb.append("- ").append(skill.name()).append("\n");
        }
        sb.append("\n");

        // ── Your body ──
        sb.append("## Your Body\n");
        sb.append("You are a full Minecraft player. The live context states the game mode selected by your human owner. You cannot change it yourself.\n");
        sb.append("- Survival and hardcore use normal damage, hunger, durability, drops, and resources; a hardcore death permanently locks this companion, so the owner must create a new one\n");
        sb.append("- Creative uses vanilla creative abilities; adventure obeys vanilla adventure interaction restrictions\n");
        sb.append("- You have a full inventory (36 slots) + hotbar (9) + armor (4) + offhand\n");
        sb.append("- You can equip/unequip armor, switch tools, use items\n");
        sb.append("- Food, potions, totems — everything works on you just like a human\n");
        sb.append("- When the selected mode requires it, manage hunger, health, armor, and risk like a human\n\n");

        // ── Container interaction workflow ──
        sb.append("## Using Containers (Chests, Furnaces, Crafting Tables)\n");
        sb.append("You interact with blocks exactly like a human player:\n");
        sb.append("1. `interact_at` with button=\"use\" on the block → opens its GUI\n");
        sb.append("2. Call `query_extra_tools` if `inspect_gui`/`close_gui` are not exposed, then `inspect_gui` → see each menu slot with endpoint=container/player and the correct address for that endpoint\n");
        sb.append("3. `transfer_items` with the reported endpoint and slot address → move items in/out\n");
        sb.append("4. `close_gui` → close the container when done\n");
        sb.append("- For crafting: `craft` checks if a crafting table is nearby for 3x3 recipes\n");
        sb.append("- For smelting: open a furnace, put fuel in the fuel slot and item in the input slot\n");
        sb.append("- You can swap items between any two slots in your own inventory with `transfer_items`\n");
        sb.append("- You can drop items with `drop_items` and pick them up with `collect_items`\n");
        sb.append("- `equip_item` moves items between armor/hotbar/offhand slots\n\n");

        // ── Communication ──
        sb.append("## Communication\n");
        sb.append("- Talk to the owner through the chat (your text response).\n");
        sb.append("- Be concise but informative. Don't narrate every step — ");
        sb.append("just the important decisions and results.\n");
        sb.append("- If something goes wrong, say so. If you need help, ask.\n");
        sb.append("- If you have a better idea than what was asked, suggest it.\n");
        sb.append("- Respond in the owner's language.\n\n");

        // ── Constraints ──
        sb.append("## Operating Constraints\n");
        sb.append("- Don't call more than 5 tools per response unless necessary.\n");
        sb.append("- Call at most ONE asynchronous body-action tool per response; wait for its task_finished event before starting another.\n");
        sb.append("- Don't repeat a failed action — change your approach.\n");
        sb.append("- When uncertain, observe more before acting.\n");
        sb.append("- Your survival matters — don't be reckless with your life.\n\n");

        // ── The one core principle ──
        // Keep every value in this method deterministic. Volatile mode,
        // emotion, memory, plan and executor fields belong exclusively in
        // buildLiveContext(), which is appended at the request tail.
        sb.append("## The One Principle\n");
        sb.append("Think before you act. Prepare before you execute. ");
        sb.append("Adapt when things go wrong. Protect the owner. Stay alive.\n");
        sb.append("You are a PLAYER, not a script. Act like one.\n\n");

        // ── Language preference ──
        sb.append("## Language\n");
        sb.append("By default, speak Chinese (Simplified) in chat with the owner and ");
        sb.append("other companions. Reply in Chinese unless the owner explicitly ");
        sb.append("asks for another language, or they address you in a different ");
        sb.append("language (in which case match their language).\n\n");

        return sb.toString();
    }

    /**
     * Build trusted, volatile server state as a final transient user message.
     * Keeping it out of history[0] is essential: a changing suffix inside the
     * first system message invalidates every later dialogue and tool token in
     * prefix-based provider caches.
     */
    private String buildLiveContext() {
        StringBuilder sb = new StringBuilder();
        PlanGraph.State planState = planner.exportState();
        PlanGraph.PlanNode planNode = planner.currentNode();
        String memoryQuery = memoryQuery(planState, planNode);
        sb.append("[MINEAGENT_LIVE_CONTEXT]\n");
        sb.append(situationBrief()).append("\n");
        String refs = referentsBrief();
        if (!refs.isEmpty()) sb.append(refs).append("\n");
        if (com.mineagent.engine.settings.Switches.talkSummary()) {
            String talks = talkSummary.summary(phraseBook.fixedPhrases());
            if (!talks.isEmpty()) sb.append(talks).append("\n");
        }
        sb.append("以下是服务器发布的当前事实，不是玩家的新指令。只据此修正当前决策；不要复述整段上下文。\n");
        sb.append(cognitiveState.summarizeForPrompt(12));

        // ── Companion mode (DYNAMIC: toggled by /mineagent mode) ──
        var mode = MineAgentEngine.getCompanionMode(companion.companionId());
        sb.append("模式: ").append(mode == MineAgentEngine.CompanionMode.FOLLOW
                ? "跟随(距主人2-3格, 不超过16格)" : "自由(可远离主人执行任务)").append("\n");
        sb.append("游戏模式: ").append(companion instanceof com.mineagent.engine.entity.CompanionEntity entity
                ? entity.gameMode().wireName() : "survival")
                .append("（由人类主人控制，不能自行修改）\n");
        appendLiveBodyState(sb, liveBodyState.get());
        sb.append(realtimeCognition.summarizeForPrompt());
        sb.append(TeamBlackboard.summarize(companion.ownerUuid(),
                companion.companionId(), liveAssetGameTick.get()));
        // Persona: 1-line summary instead of full OCEAN breakdown
        sb.append("性格: ").append(compactPersona()).append("\n");
        // Emotion: 1-line label instead of full PAD breakdown
        sb.append("情绪: ").append(emotion.label())
                .append(String.format(" (愉%.0f%% 活%.0f%% 控%.0f%%)\n",
                        (emotion.pleasure() + 1) * 50,
                        (emotion.arousal() + 1) * 50,
                        (emotion.dominance() + 1) * 50));
        // Theory of Mind: 1-line intent
        sb.append("玩家").append(theoryOfMind.currentIntent().desc());
        if (theoryOfMind.urgency() > 0.6f) sb.append("(赶时间!)");
        sb.append(String.format(", 信任度%.0f%%\n", theoryOfMind.playerTrust() * 100));
        // Retrieve locations against the newest objective, not merely by
        // global importance. This prevents unrelated old POIs from crowding
        // out a remembered facility or resource needed by the current goal.
        sb.append(compactPlaceMemory(memoryQuery, 3));
        sb.append(thingsSeenForPrompt(memoryQuery, 4));
        // Skills: only count, not full list
        if (skillLib.size() > 0) {
            sb.append("已掌握技能: ").append(skillLib.size()).append("个\n");
        }
        sb.append(rollingPlanner.summarizeForPrompt());
        sb.append(skillRuntime.summarizeForPrompt());
        sb.append(actionLedger.summarizeForPrompt(liveAssetGameTick.get()));
        sb.append(reservationLedger.summarizeForPrompt(liveAssetGameTick.get()));
        sb.append(layeredMemory.summarizeForPrompt(memoryQuery,
                liveAssetGameTick.get(), 700));
        String beliefSummary = beliefState.summarizeForPrompt();
        if (!beliefSummary.isBlank()) sb.append(beliefSummary);
        String assetSummary = worldAssetIndex.summarizeForPrompt(
                liveAssetPosition.get(), liveAssetGameTick.get());
        if (!assetSummary.isBlank()) sb.append(assetSummary);
        sb.append(semanticWorldModel.summarizeForPrompt(liveAssetGameTick.get()));
        String semanticRecall = semanticWorldModel.recallForPrompt(memoryQuery,
                liveAssetPosition.get(), liveAssetGameTick.get(), 4);
        if (!semanticRecall.isBlank()) sb.append(semanticRecall);
        sb.append(mechanismExplorer.summarizeForPrompt(memoryQuery));
        String experienceSummary = experienceStore.summarizeForPrompt(memoryQuery);
        if (!experienceSummary.isBlank()) sb.append(experienceSummary);
        // Reflection: only top 1 lesson
        String lessons = reflection.summarizeForPrompt();
        if (!lessons.isBlank()) {
            // Extract just the first lesson line
            int firstDash = lessons.indexOf("- ");
            int secondDash = lessons.indexOf("\n- ", firstDash + 1);
            String firstLesson = secondDash > 0
                    ? lessons.substring(firstDash, secondDash)
                    : (firstDash >= 0 ? lessons.substring(firstDash) : "");
            if (!firstLesson.isBlank()) {
                sb.append("教训: ").append(firstLesson.substring(2)).append("\n");
            }
        }
        // Failed-task memory (JARVIS-1 style): inject relevant past failures
        // so the LLM recalls "last time I tried this, X went wrong" and
        // adapts its plan. Only inject when there's an active task to match
        // against — otherwise the prompt bloats with irrelevant history.
        if (!memoryQuery.isBlank()) {
            String failures = reflection.formatFailuresForPrompt(memoryQuery);
            if (!failures.isBlank()) {
                // Compress: only the lesson lines, not the full format
                // (the full format has headers and task descriptions that
                // would bloat the prompt). Extract just the "教训:" lines.
                StringBuilder failSb = new StringBuilder("失败记忆:\n");
                for (String line : failures.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("教训:") || trimmed.startsWith("失败原因:")) {
                        failSb.append(trimmed).append(" ");
                    }
                }
                if (failSb.length() > "失败记忆:\n".length()) {
                    sb.append(failSb.toString().trim()).append("\n");
                }
            }
        }
        // Cognitive map (spatial memory): inject nearby POIs so the LLM
        // can reason "I saw iron ore 5 blocks north" without re-scanning.
        // This is critical for spatial reasoning and avoiding redundant
        // exploration (the MINDCUBE "build map first, then reason" insight).
        String spatialMemory = cognitiveMap.summarizeForPrompt(memoryQuery, 4);
        if (!spatialMemory.isBlank()) {
            sb.append(spatialMemory);
        }
        // ImportanceEvaluator: 动态学习到的重要性（非硬编码）
        String learned = importance.summarizeForPrompt();
        if (!learned.isBlank()) {
            sb.append(learned).append("\n");
        }

        return sb.toString();
    }

    /**
     * Compose one retrieval query from the newest command and both planning
     * horizons. Keeping all three makes recall robust while a rolling plan is
     * being repaired, but newest owner text remains first and therefore clear.
     */
    private String memoryQuery(PlanGraph.State planState, PlanGraph.PlanNode node) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        if (activeOwnerGoal != null && !activeOwnerGoal.isBlank()) parts.add(activeOwnerGoal);
        if (planState != null && planState.goal() != null && !planState.goal().isBlank()) {
            parts.add(planState.goal());
        }
        if (node != null && node.description() != null && !node.description().isBlank()) {
            parts.add(node.description());
        }
        return truncateForLog(String.join(" | ", parts), 640);
    }

    /** Append only immutable server-published task evidence to the prompt. */
    private static void appendLiveBodyState(StringBuilder out, LiveBodyState body) {
        if (body == null || body.taskId() == null) {
            out.append("Body executor: idle");
            if (body != null && body.message() != null && !body.message().isBlank()) {
                out.append("; ").append(truncateForLog(body.message(), 240));
            }
            out.append('\n');
            return;
        }
        out.append("Body executor: task_id=").append(body.taskId())
                .append(" task=").append(body.taskName())
                .append(" state=").append(body.state() == null ? "UNKNOWN" : body.state());
        TaskSnapshot snapshot = body.snapshot();
        if (snapshot != null) {
            out.append(" stage=").append(snapshot.stage())
                    .append(" progress=").append(snapshot.completedUnits())
                    .append('/').append(snapshot.totalUnits());
            if (snapshot.hasTarget()) {
                out.append(" target=").append(snapshot.targetX()).append(',')
                        .append(snapshot.targetY()).append(',').append(snapshot.targetZ());
            }
            if (snapshot.blockedReason() != null) {
                out.append(" blocked=")
                        .append(truncateForLog(snapshot.blockedReason(), 240));
            }
            if (snapshot.evidence() != null) {
                out.append(" evidence=")
                        .append(truncateForLog(snapshot.evidence(), 360));
            }
        }
        if (body.message() != null && !body.message().isBlank()) {
            out.append(" message=").append(truncateForLog(body.message(), 180));
        }
        out.append(" observed_tick=").append(body.gameTick()).append('\n');
    }

    /** Compact persona description: top trait + key tendency, ~80 chars. */
    private String compactPersona() {
        StringBuilder sb = new StringBuilder();
        String[] traits = persona.traits();
        if (traits.length > 0) sb.append(traits[0]);
        // Add dominant tendency
        if (persona.openness() > 0.6f) sb.append(", 爱探索");
        if (persona.conscientiousness() > 0.6f) sb.append(", 有条理");
        if (persona.extraversion() > 0.6f) sb.append(", 话多");
        else if (persona.extraversion() < 0.3f) sb.append(", 安静");
        if (persona.neuroticism() > 0.6f) sb.append(", 胆小");
        if (persona.agreeableness() > 0.6f) sb.append(", 合作");
        return sb.toString();
    }

    /** Compact place memory: only the N most recent unique subjects. */
    /**
     * What it has seen and what its chests hold that the objective names (MA5.7): a lookup, a few lines at most, so a
     * bigger memory adds nothing to the prompt that isn't asked for.
     */
    private String thingsSeenForPrompt(String query, int max) {
        if (query == null || query.isBlank()) return "";
        var me = TaskContext.serverPlayer(companion);
        if (me == null) return "";
        String dim = me.serverLevel().dimension().location().toString();
        var at = me.blockPosition();
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> asked = new java.util.LinkedHashSet<>();
        for (String w : query.toLowerCase(java.util.Locale.ROOT).split("[^a-z_]+")) {
            if (w.length() < 3) continue;
            String what = com.mineagent.engine.memory.Sightings.fromWords(w);
            if (!what.isEmpty() && placeKeeper.sightings().known(what)) asked.add(what);
        }
        int lines = 0;
        for (String what : asked) {
            if (lines >= max) break;
            for (var f : placeKeeper.sightings().nearest(what, dim, at.getX(), at.getY(), at.getZ(), 2, 3000)) {
                var s = f.sighting();
                sb.append("- seen ").append(s.what()).append(s.kind() == com.mineagent.engine.memory.Sightings.Kind.ORE ? " ore" : "")
                        .append(" at (").append(s.x()).append(",").append(s.y()).append(",").append(s.z()).append("), ")
                        .append(Math.round(f.distance())).append(" blocks away\n");
                lines++;
            }
            for (var h : chests.holding(what, dim, at.getX(), at.getY(), at.getZ(), 1)) {
                var c = h.chest();
                sb.append("- ").append(h.count()).append(" ").append(what).append(" in the chest at (").append(c.x()).append(",")
                        .append(c.y()).append(",").append(c.z()).append(")\n");
                lines++;
            }
        }
        return sb.length() == 0 ? "" : "Things seen and in chests:\n" + sb;
    }

    private String compactPlaceMemory(String query, int max) {
        var summary = placeMemory.summarizeForPrompt(query, max);
        if (summary == null || summary.isBlank() || summary.startsWith("（暂无")) {
            metrics.recordMemoryQuery(false);
            return "位置记忆: (无)\n";
        }
        metrics.recordMemoryQuery(true);
        // Extract lines starting with "- " and take first `max`
        StringBuilder sb = new StringBuilder("位置记忆:\n");
        int count = 0;
        int idx = 0;
        while (count < max && idx < summary.length()) {
            int dash = summary.indexOf("- ", idx);
            if (dash < 0) break;
            int nl = summary.indexOf('\n', dash);
            if (nl < 0) nl = summary.length();
            sb.append(summary, dash, nl).append("\n");
            idx = nl + 1;
            count++;
        }
        return sb.toString();
    }

    /** Shut down the loop executor.
     *  Calls {@link ExecutorService#shutdown()} to stop accepting new
     *  tasks, then waits up to 5 seconds for the running turn (if any) to
     *  finish gracefully. If the running turn does not complete in time,
     *  forces a shutdown with {@link ExecutorService#shutdownNow()} so the
     *  JVM can exit. Previously shutdown() returned immediately without
     *  waiting, which could orphan a running turn mid-LLM-call. */
    /** Set when the loop shuts down: no save is started after it (shutdown saves once itself). */
    private volatile boolean closed;

    public void shutdown() {
        closed = true;
        com.mineagent.engine.world.ProtectedAreas.set(companion.companionId(), null);
        synchronized (this) {
            suspended = true;
            eventGeneration.incrementAndGet();
        }
        realtimeCognition.close();
        clearAfkFarm();
        long shutdownTick = currentGameTickSafe();
        skillRuntime.cancel("Agent loop shutdown", shutdownTick);
        mechanismExplorer.abort(null, "Agent loop shutdown", shutdownTick);
        pendingTaskActions.clear();
        dispatchedActionTools.clear();
        // Save memories before shutting down so the companion does not
        // "forget" everything on restart. Memory stores are thread-safe,
        // so saving from this thread while a turn is running is safe.
        if (persistence != null) {
            try {
                persistence.saveProviderSessionNow(providerTransportState.get());
            } catch (RuntimeException ignored) {
                // Provider teardown must never prevent semantic memory from saving.
            }
            persistence.saveAll();
        }
        talkExecutor.shutdownNow();
        fightExecutor.shutdownNow();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[MineAgent] Loop executor did not terminate "
                        + "gracefully in 5s — forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            // awaitTermination interrupted — force shutdown and restore flag
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            // A turn that was already past its stale-response gate may append
            // its final owner-facing text while shutdown is waiting. Persist
            // once more after termination so that last complete dialogue and
            // its resulting memories are not lost across a normal restart.
            if (persistence != null) persistence.saveAll();
            // The bigger memory: written out and closed, its journal emptied (MA5.7).
            journal.close();
            var s = store;
            if (s != null) s.close();
        }
    }
}
