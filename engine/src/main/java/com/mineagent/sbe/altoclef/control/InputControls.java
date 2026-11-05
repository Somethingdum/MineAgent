// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/control/InputControls.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.control;

import com.mineagent.sbe.altoclef.AltoClefController;
import com.mineagent.sbe.automatone.api.utils.Rotation;
import com.mineagent.sbe.automatone.api.utils.input.Input;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class InputControls {
   AltoClefController controller;
   private final Queue<Input> toUnpress = new ArrayDeque<>();
   private final Set<Input> waitForRelease = new HashSet<>();

   public InputControls(AltoClefController controller) {
      this.controller = controller;
   }

   public void tryPress(Input input) {
      if (!this.waitForRelease.contains(input)) {
         this.controller.getBaritone().getInputOverrideHandler().setInputForceState(input, true);
         this.toUnpress.add(input);
         this.waitForRelease.add(input);
      }
   }

   public void hold(Input input) {
      this.controller.getBaritone().getInputOverrideHandler().setInputForceState(input, true);
   }

   public void release(Input input) {
      this.controller.getBaritone().getInputOverrideHandler().setInputForceState(input, false);
   }

   public boolean isHeldDown(Input input) {
      return this.controller.getBaritone().getInputOverrideHandler().isInputForcedDown(input);
   }

   public void forceLook(float yaw, float pitch) {
      this.controller.getBaritone().getLookBehavior().updateTarget(new Rotation(yaw, pitch), true);
   }

   public void onTickPre() {
      while (!this.toUnpress.isEmpty()) {
         this.controller.getBaritone().getInputOverrideHandler().setInputForceState(this.toUnpress.remove(), false);
      }
   }

   public void onTickPost() {
      this.waitForRelease.clear();
   }
}
