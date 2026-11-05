// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/tasks/squashed/TypeSquasher.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.tasks.squashed;

import com.mineagent.engine.alto.tasks.ResourceTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TypeSquasher<T extends ResourceTask> {

    private final List<T> _tasks = new ArrayList<>();

    void add(T task) {
        _tasks.add(task);
    }

    public List<ResourceTask> getSquashed() {
        if (_tasks.isEmpty()) {
            // We're empty, don't run any logic.
            return Collections.emptyList();
        }
        return getSquashed(_tasks);
    }

    protected abstract List<ResourceTask> getSquashed(List<T> tasks);
}
