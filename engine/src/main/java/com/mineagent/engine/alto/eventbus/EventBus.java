// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/EventBus.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.Tuple;

/**
 * A static class to solve dependency issues. Lets us send and receive events globally, decoupling our codebase.
 * <p>
 * Technically `ConfigHelper` does something like this, but here is a more general case.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class EventBus {

    // SMH: one bus per companion (its AltoClef host), so one companion's container or block events never reach
    // another's trackers; the static calls below use the host that is ticking or being built, else this global one.
    private static final EventBus GLOBAL = new EventBus();

    private final HashMap<Class, List<Subscription>> topics = new HashMap<>();
    private final List<Tuple<Class, Subscription>> toAdd = new ArrayList<>();
    private boolean lock;

    private static EventBus bus() {
        com.mineagent.engine.alto.AltoClef host = com.mineagent.engine.alto.AltoClef.current();
        return host != null ? host.getEventBus() : GLOBAL;
    }

    public static <T> void publish(T event) {
        bus().publishHere(event);
    }

    public static <T> Subscription<T> subscribe(Class<T> type, Consumer<T> consumeEvent) {
        return bus().subscribeHere(type, consumeEvent);
    }

    public static <T> void unsubscribe(Subscription<T> subscription) {
        if (subscription != null)
            subscription.delete();
    }

    public <T> void publishHere(T event) {
        Class type = event.getClass();
        // Add all subscriptions we need to add
        for (Tuple<Class, Subscription> toAdd : toAdd) {
            subscribeInternal(toAdd.getA(), toAdd.getB());
        }
        toAdd.clear();
        if (topics.containsKey(type)) {
            List<Subscription> subscribers = topics.get(type);
            // Subscriptions can be deleted while they're called
            List<Subscription> toDelete = new ArrayList<>();
            // Go through our subscription list. We shouldn't modify the list while we're iterating it.
            lock = true;
            for (Subscription subRaw : subscribers) {
                Subscription<T> sub;
                try {
                    sub = (Subscription<T>) subRaw;
                    if (sub.shouldDelete()) {
                        toDelete.add(sub);
                    } else {
                        sub.accept(event);
                    }
                } catch (ClassCastException e) {
                    System.err.println("TRIED PUBLISHING MISMAPPED EVENT: " + event);
                    e.printStackTrace();
                }
            }
            // Delete all subscriptions
            subscribers.removeAll(toDelete); // SMH: AltoClef collected these but never removed them.
            lock = false;
        }
    }

    private <T> void subscribeInternal(Class<T> type, Subscription<T> sub) {
        if (!topics.containsKey(type)) {
            topics.put(type, new ArrayList<>());
        }
        topics.get(type).add(sub);
    }

    public <T> Subscription<T> subscribeHere(Class<T> type, Consumer<T> consumeEvent) {
        Subscription<T> sub = new Subscription<>(consumeEvent);
        if (lock) {
            toAdd.add(new Tuple<>(type, sub));
        } else {
            subscribeInternal(type, sub);
        }
        return sub;
    }
}
