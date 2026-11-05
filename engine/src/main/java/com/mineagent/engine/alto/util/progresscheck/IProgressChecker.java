// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/util/progresscheck/IProgressChecker.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.util.progresscheck;

/**
 * Used to determine when a task/command is not making any progress over a threshold period of time.
 */

public interface IProgressChecker<T> {
    void setProgress(T progress);

    boolean failed();

    void reset();
}
