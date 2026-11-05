// Ported from SecondBrainEngine (github.com/sailex428/SecondBrainEngine, LGPL-3.0; its AltoClef parts MIT,
// Copyright (c) 2020 Adris Jautakas; its Automatone and Baritone parts LGPL-3.0) at 1712b17e:
// src/main/java/me/sailex/automatone/Automatone.java, as 1.21.1 in Mojang's names (third_party/secondbrainengine).
// Changes for the companion are marked SMH (.github/port-sbe/port.py).
/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mineagent.sbe.automatone;

import com.mineagent.sbe.automatone.command.defaults.DefaultCommands;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@KeepName
public final class Automatone { // SMH: not a mod of its own; the engine calls onInitialize once
    public static final Logger LOGGER = LogManager.getLogger("Automatone");
    public static final String MOD_ID = "automatone";

    public static final TagKey<Item> EMPTY_BUCKETS = TagKey.create(Registries.ITEM, id("empty_buckets"));
    public static final TagKey<Item> WATER_BUCKETS = TagKey.create(Registries.ITEM, id("water_buckets"));
    private static final ThreadPoolExecutor threadPool;

    static {
        AtomicInteger threadCounter = new AtomicInteger(0);
        threadPool = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                r -> new Thread(r, "Automatone Worker " + threadCounter.incrementAndGet()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ThreadPoolExecutor getExecutor() {
        return threadPool;
    }

    public static void onInitialize() {
        DefaultCommands.registerAll();
    }
}
