package com.mineagent.engine.chat;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The owner's plain commands, understood without a model (after the Andy Omega plan's Lexer, §8; SMH plan
 * 69). "Follow me", "stop", "come here", "auto", "status", "drop 32 cobblestone" are acted on at once, on the
 * server thread, with a short spoken answer, so nobody waits seconds for a model to hear "stop". Only whole,
 * short commands match; anything else, or an item it cannot name, goes to the model as before. A leading
 * "don't", "wait no" or "actually" is a stop, never the verb after it.
 */
public final class CommandParser {
    public enum Kind { STOP, STAY, FOLLOW, COME, MODE, STATUS, CANCEL, EAT, DROP, GUARD_HERE, GUARD_PLAYER, GUARD_OFF,
        GOAL_ADD, RESUME, AFK, BACK, PRODUCE, HOME_HERE, REMEMBER_PLACE, GO_PLACE, WHERE_PLACE, WAY_BACK, WHERE_SEEN, WHERE_PUT,
        WHAT_HAPPENED, FIND_PLACE, MINE_ORE, WHATS_NEXT, WHY, NEEDS, GIVE, FOOD_BOTH, BUILD, LIGHT, PRIME, HUNT_PLAYER, PROGRESS, PROTECT, UNPROTECT, PORTAL, ENCHANT, TRADE, BREW, BREED, PATROL, FISH, REPAIR, GRIND, BARTER, CHEST_PURPOSE, PUT_AWAY, TIDY, CHESTS_OK, CHESTS_NO, FIND_STRONGHOLD, APPLY_BOOK, PROTECTION, GOLEMS, ROLL_LIBRARIAN, BEST_ENCHANTS, GEAR_REPORT, BOSS_PREP, ON_ITS_OWN, SPEEDRUN }

    /**
     * {@code value}: the mode for MODE, the item id for DROP, the player for GUARD_PLAYER, the goal's words for
     * GOAL_ADD, the place's name for REMEMBER_PLACE, GO_PLACE ("home" for home) and WHERE_PLACE, "out" or "back" for
     * WAY_BACK. {@code count}: for DROP;
     * for GUARD_HERE, the radius asked for (null: the default).
     */
    public record Command(Kind kind, String value, Integer count) {}

    private static final Pattern NAME_PREFIX = Pattern.compile("^\\[[^\\]]{1,40}\\]:\\s*");
    private static final Pattern NEGATION = Pattern.compile("^(don'?t|do not|wait no|actually no|no stop)\\b.*");
    private static final Pattern STOP = Pattern.compile("^(stop|halt|freeze|cease|stand still|hold on|stop now|stop that|stop it|stop everything)$");
    private static final Pattern STAY = Pattern.compile("^(stay|stay here|wait|wait here|stay put)$");
    private static final Pattern FOLLOW = Pattern.compile("^(follow|follow me|come with me|stick with me|tail me|keep up)$");
    private static final Pattern COME = Pattern.compile("^(come|come here|come back|come to me|here|over here|get over here)$");
    private static final Pattern MODE = Pattern.compile(
            "^(?:(?:go|switch to|switch|use|be|enter|mode)\\s+)?(servant|auto|escort|park|parked)(?:\\s+mode)?$");
    private static final Pattern OWN = Pattern.compile("^(on your own|do your own thing|go do your thing|be free)$");
    private static final Pattern ESCORT = Pattern.compile("^(protect me|guard me|defend me|keep me safe|escort me|cover me|watch over me)$");
    /** Going AFK, or back (SMH plan 70, MA5.4). */
    private static final Pattern AFK = Pattern.compile("^(afk|brb|bbl|be right back|going afk|gonna go afk|gonna be afk|i'?m afk|i'?m going afk|back in a (?:bit|sec|second|min|minute|few)|gtg for a (?:bit|sec|second|min|minute))$");
    /**
     * "Going AFK, fill up a chest with food while I'm out": AFK, and what to do meanwhile, any length (MA5.4). A comma,
     * colon, dash or "and" after the AFK words is needed, so "afk farm is broken" is not taken for going AFK.
     */
    private static final Pattern AFK_WITH = Pattern.compile("^(?:(?:i'?m |i am )?(?:going |gonna go |gonna be |going to be |about to go )?afk"
            + "|brb|be right back|(?:i'?m )?stepping (?:away|out))(?:\\s*[,:;\u2013\u2014-]+\\s*|\\s+(?:and|so)\\s+)(.{2,300})$");
    /**
     * An order to make something (SMH plan 70, production): "fill this chest with stone bricks", "I want an entire
     * chest of stone bricks", "put 2 stacks of torches in this chest", "make me 64 torches", "I need 3 furnaces". The
     * value is the item in their words, "chest|" first when it goes in the chest they mean; the count is how many, a
     * negative number of stacks, or 0 for as many as fill the chest.
     */
    private static final String HOW_MANY = "(a couple of stacks of|a stack of|\\d{1,2} stacks of|\\d{1,4}|an?|one)";
    /** The ores it mines on its own, as people name them. */
    private static final String ORE_WORDS = "(diamonds?|iron|gold|coal|redstone|lapis(?: lazuli)?|emeralds?|copper|quartz|netherite|ancient debris|ores?)";
    /** "mine diamonds", "go mine some iron", "mine 10 iron ore", "dig for gold", "mine iron here", "mine until I say stop". */
    private static final Pattern MINE_ORE = Pattern.compile("^(?:go )?(?:mine|dig|strip mine|branch mine)(?: for)?(?: me)?(?: some)? (?:"
            + "(\\d{1,3}) )?" + ORE_WORDS + "(?: ore)?( here| at this height| on this level)?(?: until i (?:say|tell you to) stop| till i say stop| for me)?$");
    /** "get me 3 diamonds", "go get me some iron". */
    private static final Pattern GET_ORE = Pattern.compile("^(?:go )?(?:get|fetch) me (?:(\\d{1,3}) |some )?" + ORE_WORDS + "(?: ore)?$");
    /** "go mining", "mine until I say stop", "go strip mining". */
    private static final Pattern MINING = Pattern.compile("^(?:go (?:strip |branch )?mining|mine until i (?:say|tell you to) stop|start mining|keep mining)$");
    /** The words a goal keeps an order in, so it means the same chest after a restart. */
    private static final Pattern FILL_AT = Pattern.compile("^fill the chest at (-?\\d+), (-?\\d+), (-?\\d+) with (.{2,60})$");
    private static final Pattern PUT_AT = Pattern.compile("^put (\\d{1,4}) (.{2,60}?) in the chest at (-?\\d+), (-?\\d+), (-?\\d+)$");
    /** "Give me 8 food", "hand me 16 torches": handed over (MA8.5). */
    private static final Pattern GIVE = Pattern.compile("^(?:give|hand|bring|pass|toss) me " + HOW_MANY + " (.{2,40}?)(?: please)?$");
    /** "Build a hut", "build us a shelter here": the hut blueprint (MA9.1). */
    /**
     * "Kill Steve", "hunt down Alex", "take out Notch" (MA10.7): the player by name, as the game spells names (3 to 16
     * letters, digits or underscores). Whether it is a player at all, and one it may fight, the agent loop decides.
     */
    private static final Pattern HUNT_PLAYER = Pattern.compile("^(?:go )?(?:kill|hunt(?: down)?|take out|murder|find and kill|go after)"
            + "(?: the player)? ([a-z0-9_]{3,16})$");
    private static final Pattern BUILD = Pattern.compile("^build (?:me |us )?(?:a |an )?(?:small |little )?(dirt shelter|dirt hut|wood house|wooden house|wood cabin"
            + "|hut|house|shelter|base|home|cabin)(?: here| for us)?$");
    /** "Light up the area", "light this place up", "put torches around": no dark spot left (MA9.4). */
    private static final Pattern LIGHT = Pattern.compile("^(?:light (?:up )?(?:the |this )?(?:area|place|base|here|it)(?: up)?|light it up|light this place up"
            + "|put (?:some )?torches (?:around|down|up)(?: here)?|torch (?:the |this )?(?:area|place) up)$");
    /** "Prime the world", "set us up": a starting point for both (MA9.3). */
    private static final Pattern PRIME = Pattern.compile("^(?:prime (?:the |this |a |our )?world|set us up|get us started|give us a (?:good )?start)$");
    /** "Get food for both of us": food for two, half handed over. */
    private static final Pattern FOOD_BOTH = Pattern.compile("^(?:get|make|find|cook) (?:some )?food for (?:both of us|the two of us|us both|us|me and you|you and me)$");
    /** What next, why, what it needs (MA6.5): answered from its goals and its task, no model. */
    private static final Pattern WHATS_NEXT = Pattern.compile("^(?:what'?s next|what next|what are you doing next|what will you do next|what'?s after (?:that|this)|then what)$");
    private static final Pattern WHY = Pattern.compile("^(?:why|why are you doing (?:that|this)|what'?s that for|what for|why that)$");
    private static final Pattern NEEDS = Pattern.compile("^(?:what do you need|do you need anything|what are you missing|need anything)$");
    private static final Pattern FILL = Pattern.compile("^fill (?:up )?(?:(this|that|the|my|our|a|an|another|one) )?(?:chest|barrel)(?: up)? (?:with|full of) (.{2,60})$");
    private static final Pattern FILL_IT = Pattern.compile("^fill (?:it|this|that)(?: up)? (?:with|full of) (.{2,60})$");
    private static final Pattern CHEST_OF = Pattern.compile("^(?:i (?:want|need)|make me|get me|give me|i'?d like)\\s+(?:an? )?(?:(?:entire|full|whole) )?chest(?:ful)? of (.{2,60})$");
    private static final Pattern PUT_IN = Pattern.compile("^(?:put|make|craft|get)(?: me)? " + HOW_MANY + " (.{2,60}?) (?:in|into) (?:this|that|the|my|our) chest$");
    private static final Pattern MAKE = Pattern.compile("^(?:make|craft|get|fetch|gather|collect|go get|go and get)(?: me| us)? " + HOW_MANY + " (.{2,60})$");
    private static final Pattern NEED = Pattern.compile("^i (?:need|want) " + HOW_MANY + " (.{2,60})$");
    private static final Pattern BACK = Pattern.compile("^(back|i'?m back|back now|i'?m here now|here now)$");
    // Places and the way back (SMH plan 70, MA5.6).
    /** A place's name, with the article it was said with ("the mine"): route memory matches with or without it. */
    private static final String NAME = "((?:the |my |our )?[a-z0-9][a-z0-9 '_-]{0,30})";
    private static final Pattern HOME_HERE = Pattern.compile("^(?:this is (?:my |our |the )?(?:home|base)|this is where (?:i|we) live"
            + "|set (?:my |our |the )?(?:home|base)(?: here)?|make (?:this|here) (?:my |our |the )?(?:home|base)"
            + "|(?:home|base) is here|here is (?:home|base)|(?:we|i) live here)$");
    private static final Pattern REMEMBER_PLACE = Pattern.compile("^(?:remember (?:this place|this spot|this location|this|here)(?: as)?"
            + "|call (?:this|here)(?: place| spot)?|name (?:this|here)(?: place| spot)?|mark (?:this|here)(?: place| spot)? as"
            + "|this (?:place|spot) is(?: called)?) " + NAME + "$");
    private static final Pattern GO_HOME = Pattern.compile("^(?:let'?s |lets )?(?:go|head|walk|run|return|come|take me|lead me|bring me"
            + "|take us|lead us)(?: back)?(?: to)? (?:home|(?:my |our |the )?base)$");
    private static final Pattern GO_PLACE = Pattern.compile("^(?:let'?s |lets )?(?:go|head|walk|run|return|take me|lead me|bring me"
            + "|take us|lead us)(?: back)? to " + NAME + "$");
    private static final Pattern WHERE_PLACE = Pattern.compile("^(?:where'?s|wheres|where is|where was|how far is|how far to"
            + "|how far away is) " + NAME + "$");
    private static final Pattern WAY_OUT = Pattern.compile("^(?:get (?:us|me) out(?: of here| of this cave| of the cave)?"
            + "|lead (?:me|us|the way) out(?: of here)?|take (?:me|us) out(?: of here)?|get out of (?:here|this cave|the cave)"
            + "|find (?:the|a) way out|way out)$");
    private static final Pattern WAY_BACK = Pattern.compile("^(?:go back the way (?:we|you|i) came|take (?:me|us) back the way (?:we|you) came"
            + "|back the way (?:we|you) came|retrace (?:our|your) steps|lead (?:me|us) back|take (?:me|us) back|lead the way back"
            + "|find the way back)$");
    // Asked from memory (SMH plan 70, MA5.7 and MA5.9).
    private static final Pattern WHERE_SEEN = Pattern.compile("^(?:where did you see|where have you seen|have you seen(?: any)?"
            + "|seen any|did you see(?: any)?|where can i find|where could i find) (.{2,40})$");
    private static final Pattern WHERE_PUT = Pattern.compile("^(?:where did you put|where'?d you put|where did we put"
            + "|where have you put|where did you store|where'?s the chest with|which chest has) (.{2,40})$");
    private static final Pattern WHAT_HAPPENED = Pattern.compile("^(?:what did (?:we|you) do|what happened|what have you been"
            + " (?:doing|up to)|what did you get up to)(?: (today|yesterday|while i was (?:away|gone|out)|since i left))?$");
    private static final Pattern FIND_PLACE = Pattern.compile("^(?:find|look for|search for|go find|go look for|scout for"
            + "|explore for|go and find|hunt for)(?: me| us)? (?:a |an |the |some |another )?([a-z][a-z ]{2,24})$");
    private static final Pattern EXPLORE = Pattern.compile("^(?:explore|go exploring|scout around|explore around|explore a bit)$");
    private static final Pattern WHAT_MISSED = Pattern.compile("^(?:what did i miss|catch me up|recap|fill me in)$");
    /** "Find the stronghold", "throw an eye of ender" (MA11.5): eyes thrown and their lines crossed. */
    private static final Pattern STRONGHOLD = Pattern.compile("^(?:find|locate|look for|where is|where's|triangulate)(?: me| us)? (?:the |a )?(?:stronghold|end portal)$");
    /** "Leave my chests alone", "use my chests" (MA10.5): whether it may take from its owner's chests. */
    private static final Pattern CHESTS_NO = Pattern.compile("^(?:leave|stay out of|keep out of|keep away from|don't (?:use|touch|take from|open)|do not (?:use|touch|take from|open))"
            + " (?:my|our|the) (?:chests|storage)(?: alone)?$");
    private static final Pattern CHESTS_OK = Pattern.compile("^(?:(?:you (?:can|may) )?(?:use|take from|take what you need from|help yourself to)"
            + " (?:my|our|the) (?:chests|storage|stuff|things|items)|you have access to (?:my|our) (?:chests|storage|stuff|things|items)"
            + "|help yourself|take what you need|what'?s mine is yours)$");
    /** "This chest is for tools", "this is the food chest", "use this chest for spruce logs" (MA9.2). */
    private static final Pattern CHEST_PURPOSE = Pattern.compile("^(?:(?:this|that) (?:chest|barrel) is (?:for |the |my |our )?(.{2,40}?)(?: chest| barrel)?"
            + "|(?:this|that) is (?:the|my|a|our) (.{2,30}?) (?:chest|barrel)|use (?:this|that) (?:chest|barrel) for (.{2,40}))$");
    /** "Put your things away", "empty your pack into the chests" (MA9.2): by what each chest is for. */
    private static final Pattern PUT_AWAY = Pattern.compile("^(?:(?:put|store) (?:all )?(?:your|the) (?:things|stuff|items|loot|inventory)(?: away| in the chests)"
            + "|put (?:your |all your )?(?:things|stuff|items|loot) (?:in|into) the chests|empty your (?:pack|inventory|pockets)(?: into the chests)?"
            + "|(?:sort|stash|put away) your (?:things|stuff|items|loot|inventory))$");
    /** "Tidy the chests" (MA9.2): the overflow chests sorted into the chests with a purpose. */
    private static final Pattern TIDY = Pattern.compile("^(?:tidy|sort|organi[sz]e)(?: up| out)? (?:the |our |my )?(?:chests|storage|storage room)$");
    /** "Repair your pickaxe", "fix my sword", "mend your gear" (MA11.2): an anvil, and what mends it. */
    private static final Pattern REPAIR = Pattern.compile("^(?:repair|fix|mend)(?: up)? (?:your |my |the |some |a )?(gear|stuff|things|tools|armou?r|[a-z ]{3,20}?)(?: at the anvil| on the anvil)?$");
    /** "Put mending on your pickaxe", "apply the mending book to my sword" (MA11.2): the book and the piece in an anvil. */
    private static final Pattern APPLY_BOOK = Pattern.compile("^(?:put|apply|add)(?: the| a| your| my)? ([a-z ]{3,30}?)(?: book)? (?:on|onto|to) (?:your |my |the )?([a-z ]{3,20})$");
    /** "Disenchant your bow", "grind the enchantments off my sword" (MA11.2): a grindstone. */
    private static final Pattern GRIND = Pattern.compile("^(?:disenchant|grind(?: the enchantments? off)?|take the enchantments? off|strip the enchantments? (?:off|from)"
            + "|remove the enchantments? (?:from|off)) (?:your |my |the |a )?([a-z ]{3,20}?)(?: at the grindstone| on the grindstone)?$");
    /** "Barter 8 gold with the piglins", "barter with the piglins" (MA11.1): gold thrown to piglins, what comes back. */
    private static final Pattern BARTER = Pattern.compile("^(?:barter|trade|swap)(?: (\\d{1,2}|some|all(?: (?:your|my|the|of your))?)(?: of (?:your|my|the))?)?(?: gold(?: ingots?)?)?"
            + "(?: with (?:the |some )?piglins?)?$");
    /** "Enchant your pickaxe", "enchant your gear" (MA11.2): the table, its shelves, and the best enchantment it can pay for. */
    private static final Pattern ENCHANT = Pattern.compile("^enchant (?:your |my |the |some |a )?(gear|stuff|things|tools|[a-z ]{3,20}?)$");
    /** "Trade for emeralds", "sell your wheat for emeralds", "buy bread from the villager" (MA11.3). */
    private static final Pattern TRADE = Pattern.compile("^(?:trade (?:with (?:the |a )?villagers? )?for|get (?:me |us )?some|buy(?: some)?|sell (?:your |the |some )?[a-z ]{3,20}? for)"
            + " (emeralds?|bread|[a-z ]{3,20}?)(?: from (?:the |a )?villagers?)?$");
    /** "Go fishing", "catch me 3 fish" (MA8.5): the rod cast at open water near it. */
    private static final Pattern FISH = Pattern.compile("^(?:go fishing|fish for a bit|catch (?:me |us )?" + HOW_MANY + " fish(?:es)?)$");
    /** "Add a patrol point here", "patrol here"; "clear the patrol" (MA10.1): points a guard walks between. */
    private static final Pattern PATROL_ADD = Pattern.compile("^(?:add (?:a )?patrol point(?: here)?|patrol point here|patrol here too|and patrol here)$");
    private static final Pattern PATROL_CLEAR = Pattern.compile("^(?:clear|forget|drop|stop) (?:the |your )?patrol(?: points| route)?$");
    /** "Breed the cows", "breed some sheep", "breed the animals" (MA8.5): two grown ones fed by hand. */
    private static final Pattern BREED = Pattern.compile("^breed (?:the |some |our |my |two |a pair of |more )?([a-z][a-z ]{2,19}?)$");
    /** "Brew a potion of fire resistance", "brew healing potions" (MA11.4). */
    private static final Pattern BREW = Pattern.compile("^(?:brew|make)(?: me| us)? (?:a |some |3 |three )?(?:potions? of )?(fire resistance|healing|strength|swiftness|speed|slow falling|regeneration|night vision|water breathing|leaping)(?: potions?)?$");
    /** "Build a nether portal", "make a portal" (MA11.1): the frame from the obsidian it carries, lit. */
    private static final Pattern PORTAL = Pattern.compile("^(?:build|make|put up|set up|light)(?: me| us)? (?:a |the )?(?:nether )?portal(?: here| for us)?$");
    /** "Don't touch my garden", "leave the farm alone" (MA10.2): a place no companion breaks or builds in. */
    /** "Don't protect me", "I can look after myself" (off); "you can protect me again" (on): its owner's fights, SMH plan 70. */
    private static final Pattern PROTECTION_OFF = Pattern.compile("^(?:(?:you )?(?:don'?t|do not) (?:need to |have to )?(?:protect|defend|look after|save|help) me"
            + "|no (?:need to )?protect(?:ion|ing)?(?: me)?|i can (?:look after|take care of|handle|defend|protect) myself"
            + "|let me fight (?:my own battles|alone|on my own)|(?:don'?t|do not) fight for me|stay out of my fights)$");
    private static final Pattern PROTECTION_ON = Pattern.compile("^(?:(?:you can|please) (?:protect|defend|look after) me(?: again)?|protect me again"
            + "|look out for me(?: again)?|have my back(?: again)?)$");
    /**
     * "Get me a mending villager", "roll a librarian for unbreaking", "find us a librarian selling efficiency": a
     * librarian rolled for that book (mending when none is named).
     */
    private static final Pattern ROLL = Pattern.compile("^(?:(?:get|find|make) (?:me |us )?an? ([a-z ]{3,24}?) (?:villager|librarian)"
            + "|(?:re)?roll (?:a |the |me a |us a )?librarian(?: for ([a-z ]{3,24}?))?"
            + "|(?:get|find|make) (?:me |us )?an? librarian (?:selling|with|for) ([a-z ]{3,24}?)(?: books?)?)$");
    /** "Make your sword god tier", "best enchants on your pickaxe", "max out your boots": the books on in the best order. */
    private static final Pattern BEST_ENCHANTS = Pattern.compile("^(?:make (?:your|my|the) ([a-z ]{3,20}?) god[- ]?tier|god[- ]?tier (?:your|my|the) ([a-z ]{3,20}?)"
            + "|(?:put )?(?:the )?best enchant(?:ment)?s on (?:your|my|the) ([a-z ]{3,20}?)|max out (?:your|my|the) ([a-z ]{3,20}?)(?: enchants| enchantments)?"
            + "|optimi[sz]e (?:your|my|the) ([a-z ]{3,20}?)(?:'s)?(?: enchants| enchantments)?)$");
    /** "Go survive on your own", "go get OP" (on); "stop surviving on your own" (off; "come here" also brings it back). */
    private static final Pattern ON_OWN = Pattern.compile("^(?:go )?(?:survive|live) (?:on your own|by yourself|alone)|go get op(?: as hell)?|fend for yourself"
            + "|go be self[- ]sufficient|go make your own base$");
    private static final Pattern ON_OWN_OFF = Pattern.compile("^(?:stop (?:surviving|living) (?:on your own|by yourself|alone)|you can stop surviving|you can come back now)$");
    /** "Speedrun", "let's speedrun" (on); "no more speedrunning", "stop speedrunning" (off). */
    private static final Pattern SPEEDRUN = Pattern.compile("^(?:let'?s )?speed ?run(?: mode)?(?: the game)?$");
    private static final Pattern SPEEDRUN_OFF = Pattern.compile("^(?:no more speed ?running|stop speed ?running|no speed ?run|speed ?run off)$");
    private static final Pattern BEAT_GAME = Pattern.compile("^(?:go )?(?:beat|finish|win) the game$");
    /** "Get ready for the dragon", "prepare for the wither", "gear up for the dragon fight": the kit for it made. */
    private static final Pattern BOSS_PREP = Pattern.compile("^(?:get (?:ready|prepared)|prepare|prep|gear up|stock up) for (?:the )?((?:ender )?dragon|wither|end)(?: fight| boss)?$");
    /** "What do you need for god gear", "how's your gear": what each piece lacks of its best. */
    private static final Pattern GEAR_REPORT = Pattern.compile("^(?:what do you need for god (?:gear|tier)|how'?s your gear|what does your gear need"
            + "|what are you missing for god gear|gear report)$");
    /** "You can kill golems" / "leave the golems alone": the question it would ask, answered before it asks. */
    private static final Pattern GOLEMS_OK = Pattern.compile("^(?:you can|feel free to|go ahead and) (?:kill|hunt|farm) (?:the )?(?:iron )?golems?(?: for iron)?$");
    private static final Pattern GOLEMS_NO = Pattern.compile("^(?:(?:don'?t|do not|never) (?:kill|hurt|hunt|attack) (?:the |any )?(?:iron )?golems?"
            + "|leave (?:the )?(?:iron )?golems? alone)$");
    private static final Pattern PROTECT = Pattern.compile("^(?:(?:don'?t|do not|never) (?:touch|break|mess with|dig up|mine in|grief|build in|change)"
            + " (?:my|the|this|that|our) ([a-z][a-z ]{2,23}?)|leave (?:my|the|this|that|our) ([a-z][a-z ]{2,23}?) alone)(?: here| please)?$");
    /** "You can touch the garden now", "stop protecting my farm" (MA10.2): that place free again. */
    private static final Pattern UNPROTECT = Pattern.compile("^(?:(?:you can|it'?s ok(?:ay)? to|feel free to) (?:touch|break|mine|use|build in|change)"
            + " (?:my|the|this|that|our) ([a-z][a-z ]{2,23}?)|(?:stop protecting|unprotect|forget about) (?:my|the|this|that|our) ([a-z][a-z ]{2,23}?))(?: now| again)?$");
    /** "Progress", "session report", "stats" (MA10.3): the progress record in a line. */
    private static final Pattern PROGRESS = Pattern.compile("^(?:progress|progress report|session report|stats|your stats|how much have you (?:done|got done)"
            + "|what have you (?:got done|achieved)(?: this session)?)$");
    private static final Pattern STATUS = Pattern.compile("^(status|sitrep|report|what are you doing|what r u doing|what're you doing|how'?s it going)$");
    private static final Pattern CANCEL = Pattern.compile("^(cancel|abort|nevermind|never mind|forget it|cancel that|cancel the goal|drop it)$");
    private static final Pattern EAT = Pattern.compile("^(eat|eat something|eat food|have a snack|have something to eat)$");
    private static final Pattern GUARD_HERE = Pattern.compile("^(?:guard|protect|defend|watch over)\\s+(?:here|this|it|(that)|(that) [a-z]{3,16}|"
            + "this (?:spot|place|area|chest|door|base|house|farm|room|gate|stuff|pen|field)|"
            + "the (?:base|house|farm|chest|door|gate|area|village|pen|field))"
            + "(?:\\s+(?:radius|within|for|in a radius of)\\s+(\\d{1,2})(?:\\s+blocks?)?)?$");
    private static final Pattern GUARD_OFF = Pattern.compile("^(stop guarding|unguard|stand down|off duty|stop protecting(?: [a-z0-9_]+)?|you can stop guarding)$");
    private static final Pattern GUARD_PLAYER = Pattern.compile("^(?:guard|protect|defend|watch over)\\s+([a-z0-9_]{3,16})$");
    /** "Goal: build a castle", "new goal: get 64 logs": into the goal book (MA3.1). Any length, up to 300 characters. */
    private static final Pattern GOAL = Pattern.compile("^(?:new goal|long goal|long term goal|add goal|add a goal|goal|your goal is|your new goal is)\\s*[:,-]\\s*(.{3,300})$");
    private static final Pattern RESUME = Pattern.compile("^(resume|carry on|continue|keep going|go on|back to work|get back to work|continue the goal|resume the goal|resume your goal)$");
    private static final Pattern DROP = Pattern.compile("^(?:drop|throw|toss|chuck)\\s+(?:(\\d{1,4}|a stack of|half a stack of|all(?: your| the)?|all of your)\\s+)?([a-z][a-z _]{1,40})$");

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("cobble", "cobblestone"), Map.entry("stone", "cobblestone"), Map.entry("dirt", "dirt"),
            Map.entry("logs", "oak_log"), Map.entry("wood", "oak_log"), Map.entry("planks", "oak_planks"),
            Map.entry("sticks", "stick"), Map.entry("torches", "torch"), Map.entry("diamonds", "diamond"),
            Map.entry("iron", "iron_ingot"), Map.entry("gold", "gold_ingot"), Map.entry("coal", "coal"),
            Map.entry("diamond pick", "diamond_pickaxe"), Map.entry("iron pick", "iron_pickaxe"),
            Map.entry("stone pick", "stone_pickaxe"), Map.entry("arrows", "arrow"), Map.entry("seeds", "wheat_seeds"));

    private CommandParser() {}

    /** The command in a chat line (with or without its "[name]: " prefix), if it is one. */
    public static Optional<Command> parse(String line, String companionName) {
        String t = normalise(line, companionName);
        Matcher g = GOAL.matcher(t);
        if (g.matches()) return Optional.of(new Command(Kind.GOAL_ADD, g.group(1).strip(), null));
        Matcher afk = AFK_WITH.matcher(t);
        if (afk.matches()) return Optional.of(new Command(Kind.AFK, afk.group(1).strip(), null));
        Matcher at = FILL_AT.matcher(t);
        if (at.matches()) return Optional.of(new Command(Kind.PRODUCE, "chestat|" + at.group(1) + "," + at.group(2) + "," + at.group(3)
                + "|" + at.group(4).strip(), 0));
        at = PUT_AT.matcher(t);
        if (at.matches()) return Optional.of(new Command(Kind.PRODUCE, "chestat|" + at.group(3) + "," + at.group(4) + "," + at.group(5)
                + "|" + at.group(2).strip(), Integer.parseInt(at.group(1))));
        var roll = ROLL.matcher(t);
        if (roll.matches()) {
            String wanted = roll.group(1) != null ? roll.group(1) : roll.group(2) != null ? roll.group(2) : roll.group(3);
            wanted = wanted == null ? "mending" : wanted.strip().replaceAll(" (?:book|books)$", "");
            if (!wanted.matches("good|trading|great|new|librarian|book")) return Optional.of(new Command(Kind.ROLL_LIBRARIAN, wanted, null));
        }
        if (FOOD_BOTH.matcher(t).matches()) return Optional.of(new Command(Kind.FOOD_BOTH, null, null));
        var build = BUILD.matcher(t);
        if (build.matches()) {
            String what = build.group(1);
            String print = what.startsWith("dirt") ? "dirt_shelter" : what.startsWith("wood") ? "small_wood_house" : "hut";
            return Optional.of(new Command(Kind.BUILD, print, null));
        }
        if (PORTAL.matcher(t).matches()) return Optional.of(new Command(Kind.PORTAL, null, null));
        var fish = FISH.matcher(t);
        if (fish.matches()) return Optional.of(new Command(Kind.FISH, null, fish.group(1) == null ? 4 : howMany(fish.group(1))));
        if (PATROL_ADD.matcher(t).matches()) return Optional.of(new Command(Kind.PATROL, "add", null));
        if (PATROL_CLEAR.matcher(t).matches()) return Optional.of(new Command(Kind.PATROL, "clear", null));
        var breed = BREED.matcher(t);
        if (breed.matches()) return Optional.of(new Command(Kind.BREED, breed.group(1).strip(), null));
        var brew = BREW.matcher(t);
        if (brew.matches()) return Optional.of(new Command(Kind.BREW, brew.group(1).replace("speed", "swiftness").replace(' ', '_'), null));
        var purpose = CHEST_PURPOSE.matcher(t);
        if (purpose.matches()) {
            String w = purpose.group(1) != null ? purpose.group(1) : purpose.group(2) != null ? purpose.group(2) : purpose.group(3);
            if (w != null && !w.isBlank()) return Optional.of(new Command(Kind.CHEST_PURPOSE, w.strip(), null));
        }
        if (PUT_AWAY.matcher(t).matches()) return Optional.of(new Command(Kind.PUT_AWAY, null, null));
        if (TIDY.matcher(t).matches()) return Optional.of(new Command(Kind.TIDY, null, null));
        var apply = APPLY_BOOK.matcher(t);
        if (apply.matches() && !apply.group(1).matches(".*\\b(?:torch|torches|block|blocks|chest|planks?)\\b.*")) {
            return Optional.of(new Command(Kind.APPLY_BOOK, apply.group(1).strip().replaceAll("^the ", "") + "|" + apply.group(2).strip(), null));
        }
        var repair = REPAIR.matcher(t);
        if (repair.matches()) {
            String what = repair.group(1).strip();
            return Optional.of(new Command(Kind.REPAIR, java.util.Set.of("gear", "stuff", "things", "tools", "armor", "armour").contains(what) ? "gear" : what, null));
        }
        var grind = GRIND.matcher(t);
        if (grind.matches()) return Optional.of(new Command(Kind.GRIND, grind.group(1).strip(), null));
        var barter = BARTER.matcher(t);
        if (barter.matches() && (t.contains("gold") || t.contains("piglin"))) {
            String n = barter.group(1);
            return Optional.of(new Command(Kind.BARTER, null, n == null || !n.matches("\\d+") ? 64 : Integer.parseInt(n)));
        }
        var ench = ENCHANT.matcher(t);
        if (ench.matches()) {
            String what = ench.group(1).strip();
            return Optional.of(new Command(Kind.ENCHANT, java.util.Set.of("gear", "stuff", "things", "tools").contains(what) ? "gear" : what, null));
        }
        var hunt = HUNT_PLAYER.matcher(t);
        if (hunt.matches()) return Optional.of(new Command(Kind.HUNT_PLAYER, hunt.group(1), null));
        if (LIGHT.matcher(t).matches()) return Optional.of(new Command(Kind.LIGHT, null, 8));
        if (PRIME.matcher(t).matches()) return Optional.of(new Command(Kind.PRIME, null, null));
        Matcher give = GIVE.matcher(t);
        if (give.matches()) return Optional.of(new Command(Kind.GIVE, give.group(2).strip(), howMany(give.group(1))));
        if (WHATS_NEXT.matcher(t).matches()) return Optional.of(new Command(Kind.WHATS_NEXT, null, null));
        if (WHY.matcher(t).matches()) return Optional.of(new Command(Kind.WHY, null, null));
        if (NEEDS.matcher(t).matches()) return Optional.of(new Command(Kind.NEEDS, null, null));
        Matcher ore = MINE_ORE.matcher(t);
        if (!ore.matches()) ore = GET_ORE.matcher(t);
        if (ore.matches()) {
            boolean here = ore.groupCount() >= 3 && ore.group(3) != null;
            String what = ore.group(2) + (here ? "|here" : "");
            return Optional.of(new Command(Kind.MINE_ORE, what, ore.group(1) == null ? 0 : Integer.parseInt(ore.group(1))));
        }
        if (MINING.matcher(t).matches()) return Optional.of(new Command(Kind.MINE_ORE, "ore", 0));
        // "This chest": the one they mean. "A chest": that one if there is one by them, else one it puts down.
        Matcher make = FILL.matcher(t);
        if (make.matches()) {
            String art = make.group(1);
            boolean any = art == null || art.equals("a") || art.equals("an") || art.equals("another") || art.equals("one");
            return Optional.of(new Command(Kind.PRODUCE, (any ? "anychest|" : "chest|") + make.group(2).strip(), 0));
        }
        make = FILL_IT.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, "chest|" + make.group(1).strip(), 0));
        make = CHEST_OF.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, "anychest|" + make.group(1).strip(), 0));
        make = PUT_IN.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, "chest|" + make.group(2).strip(), howMany(make.group(1))));
        make = MAKE.matcher(t);
        if (!make.matches()) make = NEED.matcher(t);
        if (make.matches()) return Optional.of(new Command(Kind.PRODUCE, make.group(2).strip(), howMany(make.group(1))));
        if (t.isEmpty() || t.split(" ").length > 8) return Optional.empty();
        // "Don't touch my garden" is a standing order about a place, not "don't" as in stop.
        if (STRONGHOLD.matcher(t).matches()) return Optional.of(new Command(Kind.FIND_STRONGHOLD, null, null));
        if (CHESTS_NO.matcher(t).matches()) return Optional.of(new Command(Kind.CHESTS_NO, null, null));
        if (CHESTS_OK.matcher(t).matches()) return Optional.of(new Command(Kind.CHESTS_OK, null, null));
        var best = BEST_ENCHANTS.matcher(t);
        if (best.matches()) {
            String what = null;
            for (int i = 1; i <= best.groupCount() && what == null; i++) what = best.group(i);
            if (what != null && !what.matches("(?:max|out|gear|stats?|levels?)")) return Optional.of(new Command(Kind.BEST_ENCHANTS, what.strip(), null));
        }
        if (GEAR_REPORT.matcher(t).matches()) return Optional.of(new Command(Kind.GEAR_REPORT, null, null));
        if (ON_OWN.matcher(t).matches()) return Optional.of(new Command(Kind.ON_ITS_OWN, "on", null));
        if (ON_OWN_OFF.matcher(t).matches()) return Optional.of(new Command(Kind.ON_ITS_OWN, "off", null));
        if (SPEEDRUN.matcher(t).matches()) return Optional.of(new Command(Kind.SPEEDRUN, "on", null));
        if (SPEEDRUN_OFF.matcher(t).matches()) return Optional.of(new Command(Kind.SPEEDRUN, "off", null));
        if (BEAT_GAME.matcher(t).matches()) return Optional.of(new Command(Kind.BOSS_PREP, "beat the game: dragon", null));
        var boss = BOSS_PREP.matcher(t);
        if (boss.matches()) return Optional.of(new Command(Kind.BOSS_PREP, boss.group(1), null));
        if (PROTECTION_OFF.matcher(t).matches()) return Optional.of(new Command(Kind.PROTECTION, "off", null));
        if (PROTECTION_ON.matcher(t).matches()) return Optional.of(new Command(Kind.PROTECTION, "on", null));
        if (GOLEMS_OK.matcher(t).matches()) return Optional.of(new Command(Kind.GOLEMS, "on", null));
        if (GOLEMS_NO.matcher(t).matches()) return Optional.of(new Command(Kind.GOLEMS, "off", null));
        var keep = PROTECT.matcher(t);
        if (keep.matches()) return Optional.of(new Command(Kind.PROTECT, (keep.group(1) != null ? keep.group(1) : keep.group(2)).strip(), null));
        var free = UNPROTECT.matcher(t);
        if (free.matches()) return Optional.of(new Command(Kind.UNPROTECT, (free.group(1) != null ? free.group(1) : free.group(2)).strip(), null));
        if (NEGATION.matcher(t).matches()) return Optional.of(new Command(Kind.STOP, null, null));
        if (STOP.matcher(t).matches()) return Optional.of(new Command(Kind.STOP, null, null));
        if (STAY.matcher(t).matches()) return Optional.of(new Command(Kind.STAY, null, null));
        if (FOLLOW.matcher(t).matches()) return Optional.of(new Command(Kind.FOLLOW, null, null));
        if (COME.matcher(t).matches()) return Optional.of(new Command(Kind.COME, null, null));
        Matcher m = MODE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.MODE, m.group(1).startsWith("park") ? "parked" : m.group(1), null));
        if (OWN.matcher(t).matches()) return Optional.of(new Command(Kind.MODE, "auto", null));
        if (ESCORT.matcher(t).matches()) return Optional.of(new Command(Kind.MODE, "escort", null));
        if (GUARD_OFF.matcher(t).matches()) return Optional.of(new Command(Kind.GUARD_OFF, null, null));
        m = GUARD_HERE.matcher(t);
        if (m.matches()) {
            // "That" (or "that horse"): what they look at; else here.
            String that = m.group(1) != null || m.group(2) != null ? "that" : null;
            return Optional.of(new Command(Kind.GUARD_HERE, that, m.group(3) == null ? null : Integer.valueOf(m.group(3))));
        }
        m = GUARD_PLAYER.matcher(t);
        if (m.matches() && !m.group(1).equals("me") && !m.group(1).equals("us")) return Optional.of(new Command(Kind.GUARD_PLAYER, m.group(1), null));
        if (AFK.matcher(t).matches()) return Optional.of(new Command(Kind.AFK, null, null));
        if (BACK.matcher(t).matches()) return Optional.of(new Command(Kind.BACK, null, null));
        if (HOME_HERE.matcher(t).matches()) return Optional.of(new Command(Kind.HOME_HERE, null, null));
        if (WAY_OUT.matcher(t).matches()) return Optional.of(new Command(Kind.WAY_BACK, "out", null));
        if (WAY_BACK.matcher(t).matches()) return Optional.of(new Command(Kind.WAY_BACK, "back", null));
        if (GO_HOME.matcher(t).matches()) return Optional.of(new Command(Kind.GO_PLACE, "home", null));
        m = REMEMBER_PLACE.matcher(t);
        if (m.matches()) {
            String name = m.group(1).strip();
            String bare = name.replaceFirst("^(?:the|my|our) ", "");
            if (bare.equals("home") || bare.equals("base")) return Optional.of(new Command(Kind.HOME_HERE, null, null));
            return Optional.of(new Command(Kind.REMEMBER_PLACE, name, null));
        }
        m = GO_PLACE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.GO_PLACE, m.group(1).strip(), null));
        m = WHERE_SEEN.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.WHERE_SEEN, m.group(1).strip(), null));
        m = WHERE_PUT.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.WHERE_PUT, m.group(1).strip(), null));
        m = WHAT_HAPPENED.matcher(t);
        if (m.matches()) {
            String when = m.group(1) == null ? "today" : m.group(1).startsWith("yesterday") ? "yesterday"
                    : m.group(1).equals("today") ? "today" : "away";
            return Optional.of(new Command(Kind.WHAT_HAPPENED, when, null));
        }
        if (WHAT_MISSED.matcher(t).matches()) return Optional.of(new Command(Kind.WHAT_HAPPENED, "away", null));
        if (EXPLORE.matcher(t).matches()) return Optional.of(new Command(Kind.FIND_PLACE, "", null));
        m = FIND_PLACE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.FIND_PLACE, m.group(1).strip(), null));
        m = WHERE_PLACE.matcher(t);
        if (m.matches()) return Optional.of(new Command(Kind.WHERE_PLACE, m.group(1).strip(), null));
        if (STATUS.matcher(t).matches()) return Optional.of(new Command(Kind.STATUS, null, null));
        if (PROGRESS.matcher(t).matches()) return Optional.of(new Command(Kind.PROGRESS, null, null));
        var trade = TRADE.matcher(t);
        if (trade.matches() && (t.startsWith("trade") || t.startsWith("sell") || t.contains("villager") || trade.group(1).startsWith("emerald"))) {
            return Optional.of(new Command(Kind.TRADE, trade.group(1).startsWith("emerald") ? "emerald" : trade.group(1).strip(), null));
        }
        if (RESUME.matcher(t).matches()) return Optional.of(new Command(Kind.RESUME, null, null));
        if (CANCEL.matcher(t).matches()) return Optional.of(new Command(Kind.CANCEL, null, null));
        if (EAT.matcher(t).matches()) return Optional.of(new Command(Kind.EAT, null, null));
        m = DROP.matcher(t);
        if (m.matches()) {
            String q = m.group(1);
            Integer count = q == null ? Integer.valueOf(1) : q.startsWith("a stack") ? Integer.valueOf(64)
                    : q.startsWith("half") ? Integer.valueOf(32) : q.startsWith("all") ? null : Integer.valueOf(q);
            String item = itemId(m.group(2));
            // "All" of something is the model's to work out (the drop tool takes 1 to 64).
            if (item != null && count != null) return Optional.of(new Command(Kind.DROP, item, count));
        }
        return Optional.empty();
    }

    /** An item named in plain words as an item id (minecraft namespace), or null if it is not a plain name. */
    public static String itemId(String words) {
        if (words == null) return null;
        String w = words.strip().replaceAll("\\s+", " ");
        if (ALIASES.containsKey(w)) return ALIASES.get(w);
        String id = w.replace(' ', '_');
        if (!id.matches("[a-z][a-z_]{1,40}")) return null;
        // "two torches" → torch: a plain plural, when the singular is the usual item name.
        if (id.endsWith("es") && (id.endsWith("ches") || id.endsWith("shes"))) id = id.substring(0, id.length() - 2);
        else if (id.endsWith("s") && !id.endsWith("ss") && !id.endsWith("_planks") && !id.endsWith("_leaves")) id = id.substring(0, id.length() - 1);
        return id;
    }

    /** "64" is 64, "a" one, "a stack of" one stack (as -1: a stack's size is the item's), "3 stacks of" -3. */
    static int howMany(String w) {
        if (w.matches("\\d+")) return Integer.parseInt(w);
        if (w.equals("a") || w.equals("an") || w.equals("one")) return 1;
        if (w.equals("a stack of")) return -1;
        if (w.equals("a couple of stacks of")) return -2;
        return -Integer.parseInt(w.substring(0, w.indexOf(' ')));
    }

    public static String normalise(String line, String companionName) {
        if (line == null) return "";
        String t = NAME_PREFIX.matcher(line.strip()).replaceFirst("").toLowerCase(Locale.ROOT);
        String name = companionName == null ? "" : companionName.toLowerCase(Locale.ROOT).strip();
        if (!name.isEmpty()) {
            t = t.replaceFirst("^(?:hey |ok |okay |yo )?@?" + Pattern.quote(name) + "[,:!]?\\s+", "");
            t = t.replaceFirst("[,\\s]+@?" + Pattern.quote(name) + "[.!?]*$", "");
        }
        t = t.replaceAll("[.!?]+$", "").strip();
        t = t.replaceFirst("^(?:please |pls |can you |could you |would you |will you )+", "");
        t = t.replaceFirst("(?:,? please| pls)$", "");
        t = t.replaceAll("\\btwo\\b", "2").replaceAll("\\bthree\\b", "3").replaceAll("\\bfour\\b", "4")
                .replaceAll("\\bfive\\b", "5").replaceAll("\\bten\\b", "10").replaceAll("^(drop|throw|toss|chuck) (a|an|one) (?!stack)", "$1 1 ");
        return t.replaceAll("\\s+", " ").strip();
    }
}
