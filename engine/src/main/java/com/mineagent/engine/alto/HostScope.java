package com.mineagent.engine.alto;

/**
 * Which companion's AltoClef this thread is working for (SMH plan 71, §6.4, step 1.3). AltoClef calls
 * {@code AltoClef.getInstance()} as if there were one player; here there is one host per companion, so each thread
 * that does AltoClef's work for a companion (its tick, its construction, a call into it from the routine, the loop or
 * a scene, an event for it) opens that companion's scope first, and {@link #get()} outside any scope fails loudly
 * instead of handing back another companion's host or nothing. A path search on Automatone's own threads never opens
 * one: what runs there is given its companion's objects directly. Written for this mod, after the old port's static
 * {@code current} (alto/AltoClef, which it replaces) and Java's own {@link ThreadLocal}.
 */
public final class HostScope<T> {
    private final ThreadLocal<T> open = new ThreadLocal<>();
    private final String what;

    public HostScope(String what) {
        this.what = what;
    }

    /** This thread's host from now on; returns the one it had, for {@link #restore}. */
    public T swap(T host) {
        T before = open.get();
        if (host == null) open.remove();
        else open.set(host);
        return before;
    }

    /** Puts back the host {@link #swap} replaced (null: none). */
    public void restore(T before) {
        if (before == null) open.remove();
        else open.set(before);
    }

    /** This thread's host; throws when this thread is in no companion's scope. */
    public T get() {
        T host = open.get();
        if (host == null) {
            throw new IllegalStateException(what + " asked for outside any companion's scope, on thread \""
                    + Thread.currentThread().getName() + "\"");
        }
        return host;
    }

    /** This thread's host, or null when it is in no scope (for code that checks). */
    public T peek() {
        return open.get();
    }
}
