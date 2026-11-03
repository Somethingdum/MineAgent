package com.mineagent.engine.llm;

import java.util.Locale;

/**
 * What to tell the owner when a call to SMH fails, in plain words (SMH plan 69; the user, 2026-11-03: "Just drag and
 * drop to the folder. And start SMH."): SMH not running, no model in memory, a key it refused, its game endpoint off.
 * Null for anything else, which keeps the provider's own message. Pure.
 */
public final class SmhHint {
    private SmhHint() {}

    /** Whether a base URL is SMH on this PC (http://127.0.0.1:47821/apps, or localhost). */
    public static boolean local(String baseUrl) {
        if (baseUrl == null) return false;
        String b = baseUrl.toLowerCase(Locale.ROOT);
        return b.startsWith("http://127.0.0.1") || b.startsWith("http://localhost") || b.startsWith("http://[::1]");
    }

    /** Whether a base URL is an SMH game endpoint at all (it ends in /apps). */
    public static boolean smh(String baseUrl) {
        if (baseUrl == null) return false;
        String b = baseUrl.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return b.endsWith("/apps") || b.endsWith("/apps/v1") || b.contains("/apps/v1/");
    }

    public static String forFailure(String baseUrl, Integer status, String message, Throwable cause) {
        if (!smh(baseUrl)) return null;
        String m = message == null ? "" : message;
        if (status == null && refused(m, cause)) {
            return local(baseUrl)
                    ? "I can't reach SMH on this PC. Start SMH and I'll carry on."
                    : "I can't reach SMH at " + baseUrl + ". Is that PC on, with SMH running?";
        }
        if (status == null) return null;
        return switch (status) {
            case 503 -> m.contains("no_model") ? "SMH has no model in memory yet. Load one and I'll carry on." : null;
            case 401 -> local(baseUrl)
                    ? "SMH didn't take my key. On this PC no key is needed: clear the API key in my connection settings."
                    : "SMH didn't take my key. Make one on SMH's Minecraft card and put it in my connection settings.";
            case 403 -> "SMH doesn't take game calls from this address. Use its Tailscale address, or play on the PC SMH runs on.";
            case 404 -> "SMH's game endpoint is switched off (SMH's Switches tab).";
            default -> null;
        };
    }

    private static boolean refused(String message, Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.nio.channels.UnresolvedAddressException) return true;
            if (t.getCause() == t) break;
        }
        return message.contains("ConnectException") || message.contains("Connection refused");
    }
}
