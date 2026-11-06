package com.mineagent.engine.alto;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** One AltoClef per companion (SMH plan 71, §6.4, invariant I2): a thread works for one host at a time, or none. */
class HostScopeTest {
    @Test void outsideAnyScopeItFailsLoudlyAndNamesTheThread() {
        HostScope<String> scope = new HostScope<>("AltoClef");
        IllegalStateException e = assertThrows(IllegalStateException.class, scope::get);
        assertTrue(e.getMessage().contains(Thread.currentThread().getName()), e.getMessage());
        assertNull(scope.peek());
    }

    @Test void insideAScopeItIsThatHostAndScopesNest() {
        HostScope<String> scope = new HostScope<>("AltoClef");
        String outer = scope.swap("first companion");
        assertNull(outer);
        assertEquals("first companion", scope.get());
        String inner = scope.swap("second companion");
        assertEquals("first companion", inner);
        assertEquals("second companion", scope.get());
        scope.restore(inner);
        assertEquals("first companion", scope.get());
        scope.restore(outer);
        assertThrows(IllegalStateException.class, scope::get);
    }

    @Test void anotherThreadNeverSeesThisThreadsHost() throws InterruptedException {
        HostScope<String> scope = new HostScope<>("AltoClef");
        String before = scope.swap("this companion");
        AtomicReference<Throwable> seen = new AtomicReference<>();
        Thread pathWorker = new Thread(() -> {
            try {
                scope.get();
            } catch (Throwable t) {
                seen.set(t);
            }
        }, "Automatone Worker 1");
        pathWorker.start();
        pathWorker.join();
        scope.restore(before);
        assertInstanceOf(IllegalStateException.class, seen.get());
        assertTrue(seen.get().getMessage().contains("Automatone Worker 1"));
    }
}
