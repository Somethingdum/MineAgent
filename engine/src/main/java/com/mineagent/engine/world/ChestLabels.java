package com.mineagent.engine.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The labels on a chest, as its owner's sorting system puts them (SMH plan 70): a sign on its face, a sign standing on
 * it, an item frame on it (the item in it is what the chest is for). Both halves of a double chest. Read as purposes
 * ({@link ChestSigns}); a frame names its item exactly.
 */
public final class ChestLabels {
    private ChestLabels() {}

    private static volatile Names names;

    private record Names(Set<String> things) {}

    /** Whether words name an item, a sort of item by its last word ("wool"), or a material ("iron"). */
    public static boolean isThing(String words) {
        Names n = names;
        if (n == null) {
            Set<String> t = new HashSet<>();
            for (var item : BuiltInRegistries.ITEM) {
                String path = BuiltInRegistries.ITEM.getKey(item).getPath().replace('_', ' ');
                t.add(path);
                String last = path.substring(path.lastIndexOf(' ') + 1);
                t.add(ChestPurposes.singular(last));
                if (path.startsWith("raw ")) t.add(path.substring(4));
                int sp = path.indexOf(' ');
                if (sp > 0 && ChestPurposes.MATERIAL.contains(last)) t.add(path.substring(0, sp));
            }
            t.remove("");
            n = names = new Names(Set.copyOf(t));
        }
        return n.things().contains(words) || n.things().contains(ChestPurposes.singular(words));
    }

    /** The purposes the chest's labels give, in the order found (none: no labels, or none that say anything). */
    public static List<String> read(ServerLevel level, BlockPos chest) {
        List<BlockPos> halves = new ArrayList<>(List.of(chest));
        var st = level.getBlockState(chest);
        if (st.getBlock() instanceof ChestBlock && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            halves.add(chest.relative(ChestBlock.getConnectedDirection(st)));
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (BlockPos c : halves) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos s = c.relative(d);
                var ss = level.getBlockState(s);
                if (ss.getBlock() instanceof WallSignBlock && ss.getValue(WallSignBlock.FACING) == d) out.addAll(sign(level, s));
            }
            if (level.getBlockState(c.above()).getBlock() instanceof StandingSignBlock) out.addAll(sign(level, c.above()));
            for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, new AABB(c).inflate(1.0))) {
                if (!f.getPos().relative(f.getDirection().getOpposite()).equals(c) || f.getItem().isEmpty()) continue;
                out.add(BuiltInRegistries.ITEM.getKey(f.getItem().getItem()).getPath().replace('_', ' '));
            }
        }
        return List.copyOf(out);
    }

    private static List<String> sign(ServerLevel level, BlockPos at) {
        if (!(level.getBlockEntity(at) instanceof SignBlockEntity be)) return List.of();
        List<String> lines = new ArrayList<>();
        for (var c : be.getFrontText().getMessages(false)) lines.add(c.getString());
        List<String> out = new ArrayList<>(ChestSigns.purposes(lines, ChestLabels::isThing));
        lines.clear();
        for (var c : be.getBackText().getMessages(false)) lines.add(c.getString());
        out.addAll(ChestSigns.purposes(lines, ChestLabels::isThing));
        return out;
    }
}
