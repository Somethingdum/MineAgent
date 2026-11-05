// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/helpers/ConfigHelper.java
// at commit 1f8e038, in Mojang's names (third_party/altoclef). SMH: no config files and no Jackson: every config is its
// defaults, handed to its reader as AltoClef hands a freshly made one when its file is missing.
package com.mineagent.engine.alto.util.helpers;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigHelper {

    public static void reloadAllConfigs() {
    }

    public static <T> void loadConfig(String path, Supplier<T> getDefault, Class<T> classToLoad, Consumer<T> onReload) {
        onReload.accept(getDefault.get());
    }

    public static <T> void saveConfig(String path, T config) {
    }
}
