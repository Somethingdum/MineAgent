// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/versionedfields/Blocks.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.versionedfields;

import net.minecraft.world.level.block.Block;

/**
 * A helper class implementing blocks that are not yet supported in certain versions
 * Using these in non-supported versions might lead to strange bugs and/or crashes...
 * Please see {@link VersionedFieldHelper#isSupported(Object)}
 */
public abstract class Blocks extends net.minecraft.world.level.block.Blocks {

    public static final Block UNSUPPORTED = VersionedFieldHelper.createUnsafeUnsupportedBlock();



}
