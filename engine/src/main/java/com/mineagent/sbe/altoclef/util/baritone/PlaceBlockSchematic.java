// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/util/baritone/PlaceBlockSchematic.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.util.baritone;

import com.mineagent.sbe.automatone.api.schematic.AbstractSchematic;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public class PlaceBlockSchematic extends AbstractSchematic {
   private static final int RANGE = 1;
   private final Block[] blockToPlace;
   private final boolean skipIfAlreadyThere;
   private final boolean done;
   private BlockState targetPlace;

   public PlaceBlockSchematic(Block[] blocksToPlace, boolean skipIfAlreadyThere) {
      super(1, 1, 1);
      this.blockToPlace = blocksToPlace;
      this.done = false;
      this.targetPlace = null;
      this.skipIfAlreadyThere = skipIfAlreadyThere;
   }

   public PlaceBlockSchematic(Block[] blocksToPlace) {
      this(blocksToPlace, true);
   }

   public PlaceBlockSchematic(Block blockToPlace) {
      this(new Block[]{blockToPlace});
   }

   public boolean foundSpot() {
      return this.targetPlace != null;
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState blockState, List<BlockState> list) {
      if (x == 0 && y == 0 && z == 0) {
         if (this.skipIfAlreadyThere && this.blockIsTarget(blockState.getBlock())) {
            this.targetPlace = blockState;
         }

         boolean isDone = this.targetPlace != null;
         if (isDone) {
            return this.targetPlace;
         } else {
            if (!list.isEmpty()) {
               for (BlockState possible : list) {
                  if (possible != null && this.blockIsTarget(possible.getBlock())) {
                     this.targetPlace = possible;
                     return possible;
                  }
               }
            }

            return blockState;
         }
      } else {
         return blockState;
      }
   }

   private boolean blockIsTarget(Block block) {
      if (this.blockToPlace != null) {
         for (Block check : this.blockToPlace) {
            if (check == block) {
               return true;
            }
         }
      }

      return false;
   }
}
