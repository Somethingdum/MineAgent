// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/speedrun/BeatMinecraftConfig.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.speedrun;

public class BeatMinecraftConfig {
    public int targetEyes = 14; // how many eyes of ender to collect
    public int minimumEyes = 12; // the MINIMUM amount of eyes of ender to have, assuming we don't have our stronghold portal opened yet
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
