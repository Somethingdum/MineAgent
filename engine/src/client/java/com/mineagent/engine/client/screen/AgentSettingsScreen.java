package com.mineagent.engine.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineagent.engine.client.MineAgentClientController;
import com.mineagent.engine.client.ui.MineAgentUiComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Agent settings panel (SMH plan 69, MA2.6; the user, 2026-11-02: "The mod should give an easy to
 * understand UI panel to change settings on the agent."). Five tabs in plain words: Connection, Who it obeys,
 * Personality, Learning, Call rate. It shows what the server says (settings_state) and changes things with the
 * owner's own commands (/mineagent settings, trust, untrust), which the server checks; nothing is decided here.
 */
public class AgentSettingsScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 24;
    private static final int LINE = 12;
    private static final String[] TABS = {"connection", "obeys", "personality", "learning", "goals", "combat", "callrate",
            "behaviour"};

    /** The behaviour switches as the panel names them (SMH plan 70, Switches). */
    private static final String[][] SWITCHES = {
            {"neverBreakBuilt", "Keep built blocks"}, {"rememberPlaced", "Remember placed"}, {"closeDoors", "Close doors"},
            {"afkGuardMe", "Guard me AFK"}, {"sleepWithPlayers", "Sleep with players"}, {"routeMemory", "Route memory"},
            {"sprint", "Sprint"}, {"saveWithWorld", "Save with world"}, {"swim", "Swim"}, {"waterDrops", "Water drops"},
            {"bucketDescents", "Bucket descents"}, {"avoidMobs", "Avoid mobs"}, {"longTrips", "Plan long trips"},
            {"afkFarmLeftAlone", "Leave my AFK farm"}, {"afkKeepBusy", "Keep busy when I'm AFK"},
            {"huntForOrders", "Hunt for orders"}, {"oreSightOnly", "Only ore it has seen"},
            {"readyCheck", "Ready before trips"}, {"sparePickaxe", "Spare pickaxe"}, {"escapeLavaFire", "Out of lava and fire"},
            {"sleepWhenAway", "Sleep nights when I'm away"}, {"liveOnOwn", "Look after itself when I'm away"},
            {"manners", "Manners"}, {"recoverDrops", "Fetch my things when I die"}, {"feedOwner", "Feed me when I'm low"},
            {"offerTools", "Offer tools"}, {"partner", "Take the other half of my job"}, {"guardPerimeter", "Guard the perimeter"},
            {"fightPlayers", "Fight players I name"}, {"huntDrops", "Bring a hunted player's drops"},
            {"liveTracking", "Hunt by live position"}, {"replantTrees", "Replant trees it fells"}, {"putOutFires", "Put out fires near it"}, {"pickBerries", "Pick berries"}, {"lavaFuel", "Lava buckets as fuel"}, {"keepPhrases", "Remember what my words mean"}, {"talkSummary", "Keep a summary of our talks"}, {"fillHoles", "Fill the holes it digs near home"}, {"huntMonsters", "Hunt monsters for what an order needs"}, {"gearGoal", "Better gear where it's dangerous"}, {"bowAtRange", "Bow at range"}, {"kitGoal", "Keep a shield and golden apples"}, {"readChestLabels", "Read chest signs"}};
    private static final int[] READY_FOOD = {2, 4, 8, 16, 32};
    private static final int[] READY_TORCHES = {4, 8, 16, 32, 64};
    private static final int[] AFK_MINUTES = {1, 3, 5, 10, 20};
    /** While I'm AFK, keep within: 0 is no limit, the default. */
    private static final int[] AFK_WITHIN = {0, 16, 32, 64, 128};
    private static final int[] THINGS_SEEN = {10_000, 50_000, 100_000, 200_000};
    private static final int[] JOURNAL_DAYS = {30, 90, 365, 3_650};
    private static final int[] CHESTS = {1_024, 4_096, 8_192, 16_384};
    private static final int[] GUARD_RADII = {8, 12, 16, 24};
    private static int guardRadiusChoice = 1;
    private static final int GOALS_SHOWN = 5;
    private static final int SKILLS_SHOWN = 4;

    private static int tab = 0;
    private MineAgentUiComponents.Rect panel;
    private int left;
    private int innerWidth;
    private int contentTop;
    private EditBox playerField;
    private EditBox personaField;
    private String personaDraft;
    private EditBox goalField;
    private String goalDraft;
    private String testResult = "";
    private final List<Line> lines = new ArrayList<>();

    private record Line(Component text, int x, int y, int color) {}

    public AgentSettingsScreen() {
        super(Component.translatable("screen.mineagent.agent.title"));
    }

    @Override
    protected void init() {
        super.init();
        lines.clear();
        panel = MineAgentUiComponents.centeredPanel(this.width, this.height, 440, 290);
        innerWidth = panel.width() - MineAgentUiComponents.PANEL_PADDING * 2;
        left = panel.x() + MineAgentUiComponents.PANEL_PADDING;
        int tabsY = panel.y() + MineAgentUiComponents.TITLE_BAND_HEIGHT + 8;
        int gap = 3;
        int tabWidth = (innerWidth - gap * (TABS.length - 1)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int which = i;
            String label = (i == tab ? "> " : "") + Component.translatable("screen.mineagent.agent.tab." + TABS[i]).getString();
            this.addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
                tab = which;
                rebuild();
            }).bounds(left + i * (tabWidth + gap), tabsY, tabWidth, BUTTON_HEIGHT).build());
        }
        contentTop = tabsY + BUTTON_HEIGHT + 10;
        JsonObject state = MineAgentClientController.agentSettingsState();
        if (state == null) {
            line(Component.translatable("screen.mineagent.agent.loading"), left, contentTop, MineAgentUiComponents.COLOR_SUBTITLE);
            command("mineagent settings");
        } else {
            switch (TABS[tab]) {
                case "connection" -> connection(state);
                case "obeys" -> obeys(state);
                case "personality" -> personality(state);
                case "learning" -> learning(state);
                case "goals" -> goals(state);
                case "combat" -> combat(state);
                case "behaviour" -> behaviour(state);
                default -> callRate(state);
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                ignored -> Minecraft.getInstance().setScreen(new MineAgentMainMenuScreen()))
                .bounds(left, panel.y() + panel.height() - MineAgentUiComponents.PANEL_PADDING - BUTTON_HEIGHT,
                        innerWidth, BUTTON_HEIGHT).build());
    }

    private void connection(JsonObject s) {
        int y = contentTop;
        line(Component.translatable("screen.mineagent.agent.model", str(s, "model")), left, y, MineAgentUiComponents.COLOR_TITLE);
        line(Component.translatable("screen.mineagent.agent.base_url", clip(str(s, "baseUrl"), 60)), left, y += LINE, MineAgentUiComponents.COLOR_TITLE);
        line(Component.translatable(s.has("keySet") && s.get("keySet").getAsBoolean()
                ? "screen.mineagent.agent.key_set" : "screen.mineagent.agent.key_unset"), left, y += LINE, MineAgentUiComponents.COLOR_TITLE);
        line(Component.translatable("screen.mineagent.agent.connection_note"), left, y += LINE + 4, MineAgentUiComponents.COLOR_SUBTITLE);
        y += LINE + 6;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.test"), ignored -> {
            testResult = Component.translatable("screen.mineagent.agent.testing").getString();
            command("mineagent settings test");
        }).bounds(left, y, 140, BUTTON_HEIGHT).build());
        if (!testResult.isEmpty()) line(Component.literal(clip(testResult, 60)), left + 150, y + 6, MineAgentUiComponents.COLOR_ACCENT);
    }

    private void obeys(JsonObject s) {
        int y = contentTop;
        line(Component.translatable("screen.mineagent.agent.obeys_note"), left, y, MineAgentUiComponents.COLOR_SUBTITLE);
        y += LINE + 4;
        JsonArray trusted = s.has("trusted") && s.get("trusted").isJsonArray() ? s.getAsJsonArray("trusted") : new JsonArray();
        if (trusted.isEmpty()) {
            line(Component.translatable("screen.mineagent.agent.only_you"), left, y + 6, MineAgentUiComponents.COLOR_TITLE);
            y += ROW;
        }
        for (int i = 0; i < Math.min(5, trusted.size()); i++) {
            String name = trusted.get(i).getAsString();
            line(Component.literal(clip(name, 36)), left, y + 6, MineAgentUiComponents.COLOR_TITLE);
            this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.remove"),
                    ignored -> command("mineagent untrust " + safeWord(name)))
                    .bounds(left + innerWidth - 80, y, 80, BUTTON_HEIGHT).build());
            y += ROW;
        }
        playerField = new EditBox(this.font, left, y + 4, innerWidth - 90, BUTTON_HEIGHT,
                Component.translatable("screen.mineagent.agent.player"));
        playerField.setMaxLength(16);
        playerField.setHint(Component.translatable("screen.mineagent.agent.player"));
        this.addRenderableWidget(playerField);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.add"), ignored -> {
            String name = safeWord(playerField.getValue());
            if (!name.isEmpty()) command("mineagent trust " + name);
        }).bounds(left + innerWidth - 80, y + 4, 80, BUTTON_HEIGHT).build());
    }

    private void personality(JsonObject s) {
        int y = contentTop;
        line(Component.translatable("screen.mineagent.agent.persona_note"), left, y, MineAgentUiComponents.COLOR_SUBTITLE);
        personaField = new EditBox(this.font, left, y + LINE + 4, innerWidth, BUTTON_HEIGHT,
                Component.translatable("screen.mineagent.agent.persona"));
        personaField.setMaxLength(600);
        personaField.setValue(personaDraft != null ? personaDraft : str(s, "persona"));
        personaField.setHint(Component.translatable("screen.mineagent.agent.persona_hint"));
        this.addRenderableWidget(personaField);
        int by = y + LINE + 4 + BUTTON_HEIGHT + 8;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.save"), ignored -> {
            String text = personaField.getValue().replaceAll("[\\r\\n]+", " ").strip();
            personaDraft = null;
            command("mineagent settings set persona " + (text.isEmpty() ? "none" : text));
        }).bounds(left, by, 100, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.clear"), ignored -> {
            personaDraft = null;
            command("mineagent settings set persona none");
        }).bounds(left + 106, by, 100, BUTTON_HEIGHT).build());
    }

    private void learning(JsonObject s) {
        int y = contentTop;
        String mode = str(s, "mode").isEmpty() ? "servant" : str(s, "mode");
        String nextMode = switch (mode) {
            case "servant" -> "auto";
            case "auto" -> "escort";
            case "escort" -> "parked";
            default -> "servant";
        };
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.mode",
                Component.translatable("screen.mineagent.agent.mode." + mode)),
                ignored -> command("mineagent settings set mode " + nextMode))
                .bounds(left, y, innerWidth, BUTTON_HEIGHT).build());
        line(Component.translatable("screen.mineagent.agent.mode." + mode + ".about"), left, y + BUTTON_HEIGHT + 4, MineAgentUiComponents.COLOR_SUBTITLE);
        y += BUTTON_HEIGHT + 4 + LINE + 6;
        boolean practice = !s.has("curriculum") || s.get("curriculum").getAsBoolean();
        this.addRenderableWidget(Button.builder(Component.translatable(practice
                ? "screen.mineagent.agent.practice_on" : "screen.mineagent.agent.practice_off"),
                ignored -> command("mineagent settings set curriculum " + (practice ? "off" : "on")))
                .bounds(left, y, (innerWidth - 6) / 2, BUTTON_HEIGHT).build());
        String level = str(s, "experiments");
        String next = level.equals("low") ? "normal" : level.equals("normal") ? "high" : "low";
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.experiments",
                Component.translatable("screen.mineagent.agent.level." + (level.isEmpty() ? "high" : level))),
                ignored -> command("mineagent settings set experiments " + next))
                .bounds(left + (innerWidth - 6) / 2 + 6, y, (innerWidth - 6) / 2, BUTTON_HEIGHT).build());
        y += ROW + 4;
        line(Component.translatable("screen.mineagent.agent.skills_note"), left, y, MineAgentUiComponents.COLOR_SUBTITLE);
        y += LINE + 2;
        JsonArray skills = s.has("skills") && s.get("skills").isJsonArray() ? s.getAsJsonArray("skills") : new JsonArray();
        if (skills.isEmpty()) line(Component.translatable("screen.mineagent.agent.no_skills"), left, y + 6, MineAgentUiComponents.COLOR_TITLE);
        for (int i = 0; i < Math.min(SKILLS_SHOWN, skills.size()); i++) {
            JsonObject k = skills.get(i).getAsJsonObject();
            String name = str(k, "name");
            line(Component.literal(clip(name, 28) + "  " + clip(str(k, "score"), 40)), left, y + 6, MineAgentUiComponents.COLOR_TITLE);
            this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.forget"),
                    ignored -> command("mineagent settings forget " + safeWord(name)))
                    .bounds(left + innerWidth - 70, y, 70, BUTTON_HEIGHT).build());
            y += ROW - 2;
        }
        if (skills.size() > SKILLS_SHOWN) {
            line(Component.translatable("screen.mineagent.agent.more_skills", skills.size() - SKILLS_SHOWN), left, y + 4, MineAgentUiComponents.COLOR_MUTED);
        }
    }

    /** The goal book (MA3.1): open goals with what can be done to each, and a box to add one. */
    private void goals(JsonObject s) {
        int y = contentTop;
        line(Component.translatable("screen.mineagent.agent.goals_note"), left, y, MineAgentUiComponents.COLOR_SUBTITLE);
        y += LINE + 4;
        JsonArray goals = s.has("goals") && s.get("goals").isJsonArray() ? s.getAsJsonArray("goals") : new JsonArray();
        int shown = 0;
        for (int i = 0; i < goals.size() && shown < GOALS_SHOWN; i++) {
            JsonObject g = goals.get(i).getAsJsonObject();
            String id = safeWord(str(g, "id"));
            String status = str(g, "status");
            boolean open = status.equals("active") || status.equals("paused");
            int color = status.equals("active") ? MineAgentUiComponents.COLOR_ACCENT
                    : open ? MineAgentUiComponents.COLOR_TITLE : MineAgentUiComponents.COLOR_MUTED;
            line(Component.literal(clip(str(g, "line"), open ? 52 : 70)), left, y + 6, color);
            if (open) {
                int bx = left + innerWidth - 150;
                if (status.equals("paused")) {
                    this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.goal_switch"),
                            ignored -> command("mineagent goal switch " + id)).bounds(bx, y, 48, BUTTON_HEIGHT).build());
                } else {
                    this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.goal_done"),
                            ignored -> command("mineagent goal done " + id)).bounds(bx, y, 48, BUTTON_HEIGHT).build());
                }
                this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.goal_drop"),
                        ignored -> command("mineagent goal drop " + id)).bounds(bx + 52, y, 48, BUTTON_HEIGHT).build());
            }
            y += ROW - 2;
            shown++;
        }
        if (goals.isEmpty()) {
            line(Component.translatable("screen.mineagent.agent.no_goals"), left, y + 6, MineAgentUiComponents.COLOR_TITLE);
            y += ROW;
        }
        goalField = new EditBox(this.font, left, y + 4, innerWidth - 90, BUTTON_HEIGHT,
                Component.translatable("screen.mineagent.agent.goal_hint"));
        goalField.setMaxLength(200);
        goalField.setHint(Component.translatable("screen.mineagent.agent.goal_hint"));
        if (goalDraft != null) goalField.setValue(goalDraft);
        this.addRenderableWidget(goalField);
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.add"), ignored -> {
            String text = goalField.getValue().replaceAll("[\\p{Cntrl}§]", " ").strip();
            if (text.isEmpty()) return;
            goalDraft = "";
            goalField.setValue("");
            command("mineagent goal add " + text);
        }).bounds(left + innerWidth - 80, y + 4, 80, BUTTON_HEIGHT).build());
    }

    /** Each switch on or off, and how much it remembers: set on the server with /mineagent setconfig, then shown again. */
    private void behaviour(JsonObject s) {
        JsonObject b = s.has("behaviour") && s.get("behaviour").isJsonObject() ? s.getAsJsonObject("behaviour") : new JsonObject();
        JsonObject m = s.has("memory") && s.get("memory").isJsonObject() ? s.getAsJsonObject("memory") : new JsonObject();
        int cols = 3;
        int gap = 4;
        int w = (innerWidth - gap * (cols - 1)) / cols;
        int y = contentTop;
        int i = 0;
        for (String[] sw : SWITCHES) {
            boolean on = !b.has(sw[0]) || b.get(sw[0]).getAsBoolean();
            int slot = i - behaviourPage * PER_PAGE;
            if (slot >= 0 && slot < PER_PAGE) {
                int x = left + (slot % cols) * (w + gap);
                this.addRenderableWidget(Button.builder(Component.literal(sw[1] + ": " + (on ? "on" : "off")), ignored -> {
                    command("mineagent setconfig behaviour." + sw[0] + " " + (on ? "off" : "on"));
                    command("mineagent settings");
                }).bounds(x, y + (slot / cols) * (BUTTON_HEIGHT + 3), w, BUTTON_HEIGHT).build());
            }
            i++;
        }
        i = cycle(b, "afkMinutes", "behaviour.afkMinutes", "AFK after", " min", AFK_MINUTES, i, cols, w, gap, y);
        i = cycle(b, "afkStayWithin", "behaviour.afkStayWithin", "AFK: stay within", " blocks", AFK_WITHIN, i, cols, w, gap, y);
        i = cycle(b, "readyFood", "behaviour.readyFood", "Trip food", "", READY_FOOD, i, cols, w, gap, y);
        i = cycle(b, "readyTorches", "behaviour.readyTorches", "Trip torches", "", READY_TORCHES, i, cols, w, gap, y);
        i = cycle(m, "thingsSeen", "memory.thingsSeen", "Things seen", "", THINGS_SEEN, i, cols, w, gap, y);
        i = cycle(m, "journalDays", "memory.journalDays", "Journal", " days", JOURNAL_DAYS, i, cols, w, gap, y);
        i = cycle(m, "chests", "memory.chests", "Chests", "", CHESTS, i, cols, w, gap, y);
        // More switches than fit: pages, and a button to turn them (SMH plan 70, Switches).
        int pages = (i + PER_PAGE - 1) / PER_PAGE;
        if (behaviourPage >= pages) behaviourPage = 0;
        if (pages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("Page " + (behaviourPage + 1) + " of " + pages + " >"), ignored -> {
                behaviourPage = (behaviourPage + 1) % pages;
                rebuild();
            }).bounds(left, y + (PER_PAGE / cols) * (BUTTON_HEIGHT + 3), w, BUTTON_HEIGHT).build());
        }
    }

    /** Which page of the behaviour switches is showing, and how many go on a page (7 rows of 3). */
    private static int behaviourPage = 0;
    private static final int PER_PAGE = 21;

    /** A setting stepped through a few values; the one after its present value is sent. */
    private int cycle(JsonObject from, String field, String key, String label, String unit, int[] values,
                      int i, int cols, int w, int gap, int y) {
        int now = from.has(field) ? from.get(field).getAsInt() : values[0];
        int next = values[0];
        for (int v : values) if (v > now) { next = v; break; }
        final int send = next;
        int slot = i - behaviourPage * PER_PAGE;
        if (slot < 0 || slot >= PER_PAGE) return i + 1;
        int x = left + (slot % cols) * (w + gap);
        String shown = now == 0 && values[0] == 0 ? "no limit" : String.format("%,d", now) + unit;
        this.addRenderableWidget(Button.builder(Component.literal(label + ": " + shown), ignored -> {
            command("mineagent setconfig " + key + " " + send);
            command("mineagent settings");
        }).bounds(x, y + (slot / cols) * (BUTTON_HEIGHT + 3), w, BUTTON_HEIGHT).build());
        return i + 1;
    }

    /** Guard here or stand down, whom it may attack, and how its fights have gone, as the game rated them. */
    private void combat(JsonObject s) {
        int y = contentTop;
        JsonObject c = s.has("combat") && s.get("combat").isJsonObject() ? s.getAsJsonObject("combat") : new JsonObject();
        String guarding = str(c, "guarding");
        line(guarding.isEmpty() ? Component.translatable("screen.mineagent.agent.not_guarding")
                : Component.translatable("screen.mineagent.agent.guarding", clip(guarding, 40), str(c, "guardRadius")),
                left, y, guarding.isEmpty() ? MineAgentUiComponents.COLOR_SUBTITLE : MineAgentUiComponents.COLOR_ACCENT);
        y += LINE + 4;
        int w = (innerWidth - 12) / 3;
        int radius = GUARD_RADII[Math.floorMod(guardRadiusChoice, GUARD_RADII.length)];
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.guard_here"),
                ignored -> command("mineagent guard here " + radius)).bounds(left, y, w, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.guard_radius", radius), ignored -> {
            guardRadiusChoice = Math.floorMod(guardRadiusChoice + 1, GUARD_RADII.length);
            rebuild();
        }).bounds(left + w + 6, y, w, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.stand_down"),
                ignored -> command("mineagent guard off")).bounds(left + 2 * (w + 6), y, w, BUTTON_HEIGHT).build());
        y += ROW + 2;
        JsonArray targets = c.has("targets") && c.get("targets").isJsonArray() ? c.getAsJsonArray("targets") : new JsonArray();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) names.add(targets.get(i).getAsString());
        line(names.isEmpty() ? Component.translatable("screen.mineagent.agent.no_targets")
                : Component.translatable("screen.mineagent.agent.targets", clip(String.join(", ", names), 44)),
                left, y + 6, MineAgentUiComponents.COLOR_TITLE);
        if (!names.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.translatable("screen.mineagent.agent.clear"),
                    ignored -> command("mineagent target clear")).bounds(left + innerWidth - 70, y, 70, BUTTON_HEIGHT).build());
        }
        y += ROW + 2;
        line(Component.translatable("screen.mineagent.agent.fights_note"), left, y, MineAgentUiComponents.COLOR_SUBTITLE);
        y += LINE + 2;
        JsonArray fights = c.has("fights") && c.get("fights").isJsonArray() ? c.getAsJsonArray("fights") : new JsonArray();
        if (fights.isEmpty()) line(Component.translatable("screen.mineagent.agent.no_fights"), left, y, MineAgentUiComponents.COLOR_MUTED);
        for (int i = 0; i < Math.min(4, fights.size()); i++) {
            line(Component.literal(clip(fights.get(i).getAsString(), 72)), left, y, MineAgentUiComponents.COLOR_TITLE);
            y += LINE;
        }
    }

    private void callRate(JsonObject s) {
        int y = contentTop;
        String now = str(s, "callRate");
        String[] levels = {"calm", "normal", "busy"};
        int w = (innerWidth - 12) / 3;
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            String label = (level.equals(now) ? "> " : "") + Component.translatable("screen.mineagent.agent.rate." + level).getString();
            this.addRenderableWidget(Button.builder(Component.literal(label),
                    ignored -> command("mineagent settings set callrate " + level))
                    .bounds(left + i * (w + 6), y, w, BUTTON_HEIGHT).build());
        }
        y += ROW + 6;
        for (String level : levels) {
            line(Component.translatable("screen.mineagent.agent.rate." + level + ".about"), left, y,
                    level.equals(now) ? MineAgentUiComponents.COLOR_ACCENT : MineAgentUiComponents.COLOR_SUBTITLE);
            y += LINE + 2;
        }
    }

    /** The server sent the settings again: drawn afresh, except while a personality is being typed. */
    public void refresh() {
        if (personaField != null && personaField.isFocused()) {
            personaDraft = personaField.getValue();
            return;
        }
        if (goalField != null && goalField.isFocused()) {
            goalDraft = goalField.getValue();
            return;
        }
        rebuild();
    }

    public void testResult(String result) {
        testResult = result == null ? "" : result;
        rebuild();
    }

    private void rebuild() {
        if (personaField != null) personaDraft = personaField.getValue();
        if (goalField != null) goalDraft = goalField.getValue();
        goalField = null;
        this.clearWidgets();
        this.init();
    }

    private void command(String command) {
        if (this.minecraft != null && this.minecraft.player != null) this.minecraft.player.connection.sendCommand(command);
    }

    private void line(Component text, int x, int y, int color) {
        lines.add(new Line(text, x, y, color));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        MineAgentUiComponents.drawPanel(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                this.title, true, this.width, this.height);
        for (Line l : lines) graphics.drawString(this.font, l.text(), l.x(), l.y(), l.color());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /** A player or skill name as one command word (letters, digits, _ and -). */
    private static String safeWord(String s) {
        return s == null ? "" : s.strip().replaceAll("[^A-Za-z0-9_\\-]", "");
    }
}
