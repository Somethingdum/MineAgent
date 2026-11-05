// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/Debug.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). SMH: the companion has no chat window of its own, so AltoClef's
// messages go to the server log, marked with the companion's name, and only when its work is traced
// (-Dmineagent.traceWork=true); warnings and errors always. What it says to its owner is the engine's to decide.
package com.mineagent.engine.alto;

public class Debug {

    private static final int DEBUG_LOG_LEVEL = 0;
    private static final int WARN_LOG_LEVEL = 1;
    private static final int ERROR_LOG_LEVEL = 2;

    private static boolean tracing() {
        return Boolean.getBoolean("mineagent.traceWork");
    }

    private static String who() {
        AltoClef host = AltoClef.current();
        return host != null ? "[MineAgent] alto(" + host.getPlayer().getGameProfile().getName() + "): " : "[MineAgent] alto: ";
    }

    public static void logInternal(String message) {
        if (canLog(DEBUG_LOG_LEVEL) && Boolean.getBoolean("mineagent.traceAlto")) {
            System.out.println(who() + message);
        }
    }

    public static void logInternal(String format, Object... args) {
        if (canLog(DEBUG_LOG_LEVEL) && Boolean.getBoolean("mineagent.traceAlto")) {
            logInternal(String.format(format, args));
        }
    }

    public static void logMessage(String message, boolean prefix) {
        if (tracing()) {
            System.out.println(who() + message);
        }
    }

    public static void logMessage(String message) {
        logMessage(message, true);
    }

    public static void logMessage(String format, Object... args) {
        logMessage(String.format(format, args));
    }

    public static void logWarning(String message) {
        if (canLog(WARN_LOG_LEVEL)) {
            System.out.println(who() + "WARNING: " + message);
        }
    }

    public static void logWarning(String format, Object... args) {
        logWarning(String.format(format, args));
    }

    public static void logError(String message) {
        String stacktrace = getStack(2);
        if (canLog(ERROR_LOG_LEVEL)) {
            System.err.println(who() + message);
            System.err.println("at:");
            System.err.println(stacktrace);
        }
    }

    public static void logError(String message, Throwable t) {
        logError(message + ": " + t);
    }

    public static void logError(String format, Object... args) {
        logError(String.format(format, args));
    }

    public static void logStack() {
        logInternal("STACKTRACE: \n" + getStack(2));
    }

    private static String getStack(int toSkip) {
        StringBuilder stacktrace = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (toSkip-- <= 0) {
                stacktrace.append(ste.toString()).append("\n");
            }
        }
        return stacktrace.toString();
    }

    private static boolean canLog(int level) {
        AltoClef host = AltoClef.current();
        if (host == null || host.getModSettings() == null) return true;

        String enabledLogLevel = host.getModSettings().getLogLevel();

        return switch (enabledLogLevel) {
            case "NONE" -> false;
            case "ALL" -> true;
            case "NORMAL" -> level == WARN_LOG_LEVEL || level == ERROR_LOG_LEVEL;
            case "WARN" -> level == WARN_LOG_LEVEL;
            case "ERROR" -> level == ERROR_LOG_LEVEL;
            default ->
                    // invalid log level, switch to default (NORMAL)
                    level != DEBUG_LOG_LEVEL;
        };
    }
}
