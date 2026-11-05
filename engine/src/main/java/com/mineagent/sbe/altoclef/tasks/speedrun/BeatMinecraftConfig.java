// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/altoclef/tasks/speedrun/BeatMinecraftConfig.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
package com.mineagent.sbe.altoclef.tasks.speedrun;

public class BeatMinecraftConfig {
   public int targetEyes = 14;
   public int minimumEyes = 12;
   public boolean placeSpawnNearEndPortal = false;
   public boolean barterPearlsInsteadOfEndermanHunt;
   public boolean sleepThroughNight = false;
   public boolean rePickupCraftingTable = true;
   public int foodUnits = 220;
   public int requiredBeds = 10;
   public int minBuildMaterialCount = 5;
   public int buildMaterialCount = 64;
   public boolean rePickupSmoker = true;
   public boolean rePickupFurnace = true;
}
