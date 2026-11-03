package com.mineagent.engine.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The game side of route memory (SMH plan 70, MA5.6): where the companion walks is kept as a trail, every trip between
 * known places becomes a route, and a structure the world generated (a village, a mineshaft, a fortress) is
 * remembered the moment it walks into one. Only the structure it stands in is read, from the game's own structure
 * data, as a player sees where they are; nothing is located from afar. Places and routes are saved with its memory.
 */
public final class PlaceKeeper {
    /** How often its position is added to the trail, in ticks. */
    static final int RECORD_EVERY = 10;
    /** How often it looks at what structure it stands in, in ticks. */
    static final int LOOK_EVERY = 100;
    /** Ground this far above its head means it is underground (or under a roof): the trail keeps every corner. */
    static final int COVER = 2;

    private final RouteMemory memory = new RouteMemory();
    private long lastLook = Long.MIN_VALUE;

    public RouteMemory memory() {
        return memory;
    }

    /** Where a player stands, as route memory keeps it. */
    public static RouteMemory.Point point(ServerPlayer sp) {
        BlockPos at = sp.blockPosition();
        return new RouteMemory.Point(sp.serverLevel().dimension().location().toString(), at.getX(), at.getY(), at.getZ());
    }

    /** Ground (or a roof) above it, so it can't see the way in from where it stands. */
    public static boolean underground(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) return false;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) > feet.getY() + COVER;
    }

    /**
     * Each tick: the trail, and every few seconds what structure it stands in. A place found for the first time is
     * returned, to tell its owner about.
     */
    public Optional<RouteMemory.Place> tick(ServerPlayer sp, long gameTick) {
        if (sp == null || !sp.isAlive() || sp.isSpectator()) return Optional.empty();
        if (gameTick % RECORD_EVERY == 0) {
            memory.walked(point(sp), underground(sp.serverLevel(), sp.blockPosition()), gameTick);
        }
        if (gameTick - lastLook < LOOK_EVERY) return Optional.empty();
        lastLook = gameTick;
        try {
            return lookAround(sp, gameTick);
        } catch (RuntimeException error) {
            System.err.println("[MineAgent] Place check failed: " + error.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RouteMemory.Place> lookAround(ServerPlayer sp, long gameTick) {
        ServerLevel level = sp.serverLevel();
        BlockPos at = sp.blockPosition();
        var manager = level.structureManager();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure structure : manager.getAllStructuresAt(at).keySet()) {
            StructureStart start = manager.getStructureWithPieceAt(at, structure);
            if (!start.isValid()) continue;
            var key = registry.getKey(structure);
            String kind = key == null ? null : kindOf(key.getPath());
            if (kind == null) continue;
            RouteMemory.Point here = point(sp);
            BoundingBox box = start.getBoundingBox().inflatedBy(8);
            // The same structure walked into again (a village is wider than the 64 blocks found() allows).
            for (RouteMemory.Place known : memory.places()) {
                if (known.kind().equals(kind) && known.at().dim().equals(here.dim())
                        && box.isInside(known.at().x(), known.at().y(), known.at().z())) {
                    memory.found(kind, known.at(), gameTick);
                    return Optional.empty();
                }
            }
            int before = memory.places().size();
            RouteMemory.Place place = memory.found(kind, here, gameTick);
            return memory.places().size() > before ? Optional.of(place) : Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * The word for a structure, from its id ({@code village_plains} is a village). Null for the ones a player can't
     * see they are in (buried treasure).
     */
    static String kindOf(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.toLowerCase(Locale.ROOT);
        if (p.startsWith("village")) return "village";
        if (p.startsWith("mineshaft")) return "mineshaft";
        if (p.startsWith("shipwreck")) return "shipwreck";
        if (p.startsWith("ruined_portal")) return "ruined portal";
        if (p.startsWith("ocean_ruin")) return "ocean ruin";
        return switch (p) {
            case "buried_treasure" -> null;
            case "pillager_outpost" -> "outpost";
            case "mansion" -> "mansion";
            case "monument" -> "ocean monument";
            case "fortress" -> "fortress";
            case "bastion_remnant" -> "bastion";
            case "desert_pyramid" -> "desert temple";
            case "jungle_pyramid" -> "jungle temple";
            case "swamp_hut" -> "witch hut";
            default -> p.replace('_', ' ');
        };
    }

    /** "340 blocks north-east, at 120, 64, -300." */
    public static String whereFrom(RouteMemory.Point from, RouteMemory.Point to) {
        String at = "at " + to.x() + ", " + to.y() + ", " + to.z();
        if (from == null || !from.dim().equals(to.dim())) return at + ", in " + dimensionName(to.dim()) + ".";
        double dx = to.x() - from.x(), dz = to.z() - from.z();
        long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance <= 4) return "right here, " + at + ".";
        return distance + " blocks " + compass(dx, dz) + ", " + at + ".";
    }

    /** Eight compass points; north is -z in the game. */
    static String compass(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0) degrees += 360.0;
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.round(degrees / 45.0) % 8];
    }

    public static String dimensionName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "the Overworld";
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            default -> dim;
        };
    }

    /**
     * The waypoints for a trip to {@code place}: the known routes from the place it stands at, if any, else none (the
     * pathfinder plans the whole way).
     */
    public List<RouteMemory.Point> wayTo(RouteMemory.Point here, RouteMemory.Place place) {
        var from = memory.placeAt(here);
        if (from.isEmpty()) return List.of();
        return memory.way(from.get().id(), place.id());
    }
}
