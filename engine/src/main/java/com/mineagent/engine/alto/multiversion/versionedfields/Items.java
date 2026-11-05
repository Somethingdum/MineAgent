// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/multiversion/versionedfields/Items.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.multiversion.versionedfields;


import net.minecraft.world.item.Item;

/**
 * A helper class implementing items that are not yet supported in certain versions
 * Using these in non-supported versions might lead to strange bugs and/or crashes...
 * Please see {@link VersionedFieldHelper#isSupported(Object)}
 */
public class Items extends net.minecraft.world.item.Items {


    public static final Item UNSUPPORTED = VersionedFieldHelper.createUnsafeUnsupportedItem();



}
