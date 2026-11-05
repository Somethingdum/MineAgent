// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/slot/ClickSlotTask.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.slot;

import com.mineagent.sbe.altoclef.control.SlotHandler;
import com.mineagent.sbe.altoclef.tasksystem.Task;
import com.mineagent.sbe.altoclef.util.slots.Slot;
import net.minecraft.world.inventory.ClickType;

public class ClickSlotTask extends Task {
   private final Slot slot;
   private final int mouseButton;
   private final ClickType type;
   private boolean clicked = false;

   public ClickSlotTask(Slot slot, int mouseButton, ClickType type) {
      this.slot = slot;
      this.mouseButton = mouseButton;
      this.type = type;
   }

   public ClickSlotTask(Slot slot, ClickType type) {
      this(slot, 0, type);
   }

   public ClickSlotTask(Slot slot, int mouseButton) {
      this(slot, mouseButton, ClickType.PICKUP);
   }

   public ClickSlotTask(Slot slot) {
      this(slot, ClickType.PICKUP);
   }

   @Override
   protected void onStart() {
      this.clicked = false;
   }

   @Override
   protected Task onTick() {
      SlotHandler slotHandler = this.controller.getSlotHandler();
      if (slotHandler.canDoSlotAction()) {
         slotHandler.clickSlot(this.slot, this.mouseButton, this.type);
         slotHandler.registerSlotAction();
         this.clicked = true;
      }

      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task obj) {
      return !(obj instanceof ClickSlotTask task) ? false : task.mouseButton == this.mouseButton && task.type == this.type && task.slot.equals(this.slot);
   }

   @Override
   protected String toDebugString() {
      return "Clicking " + this.slot.toString();
   }

   @Override
   public boolean isFinished() {
      return this.clicked;
   }
}
