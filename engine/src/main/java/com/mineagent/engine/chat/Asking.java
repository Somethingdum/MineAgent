package com.mineagent.engine.chat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Asking its owner instead of guessing (SMH plan 70, the owner: "it can ask questions if it needs to. That's preferable
 * over it doing random bullshit"): one question at a time, answered yes or no in chat. Unanswered within its time, it
 * takes the answer that lets the job go on (the owner: "it assumes the most convenient answer if not responded to"),
 * says so, and keeps that for a game day; an answer the owner gives is kept with the world. Pure.
 */
public final class Asking {
    /** How long an assumed answer stands before it asks again: a game day. */
    public static final long ASSUMED_FOR = 24_000L;

    /** A question waiting: what it is about, its words, the answer taken if none comes, when asked, how long it waits. */
    public record Question(String key, String text, boolean convenient, long askedAt, long waits) {}

    /** How a question was settled: told by the owner, or taken as the convenient answer for want of one. */
    public record Settled(String key, boolean yes, boolean assumed) {}

    private Question pending;
    private final Map<String, Boolean> told = new LinkedHashMap<>();
    private final Map<String, long[]> assumed = new LinkedHashMap<>(); // key -> {answer 0/1, tick}

    static final Set<String> YES = Set.of("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "go ahead", "do it", "y", "fine", "go for it",
            "yes please", "sure thing", "of course", "please do", "aye", "affirmative", "yes you can", "you may", "you can");
    static final Set<String> NO = Set.of("no", "nope", "nah", "don't", "dont", "do not", "n", "no thanks", "please don't", "no way",
            "never", "negative", "leave it", "don't do it", "not now", "no you can't", "you can't", "you may not");

    /** "yes", "nope", "go ahead": the answer in a reply, or null when the reply is not a yes or a no. */
    public static Boolean yesNo(String said) {
        if (said == null) return null;
        String s = said.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?,]+$", "").replaceAll("\\s+", " ");
        if (YES.contains(s)) return Boolean.TRUE;
        if (NO.contains(s)) return Boolean.FALSE;
        return null;
    }

    /** The answer it goes by for this, if it has one: what it was told, else one it assumed within the day. */
    public synchronized Optional<Boolean> answer(String key, long now) {
        Boolean t = told.get(key);
        if (t != null) return Optional.of(t);
        long[] a = assumed.get(key);
        if (a != null && now - a[1] < ASSUMED_FOR) return Optional.of(a[0] == 1);
        return Optional.empty();
    }

    /**
     * Ask, unless it already has an answer or a question is waiting. True when this is a new question (to be said);
     * false otherwise (the one waiting may be this one).
     */
    public synchronized boolean ask(String key, String text, boolean convenient, long now, long waits) {
        if (answer(key, now).isPresent() || pending != null) return false;
        pending = new Question(key, text, convenient, now, Math.max(20, waits));
        return true;
    }

    public synchronized Optional<Question> pending() {
        return Optional.ofNullable(pending);
    }

    /** A reply from the owner: the waiting question settled by it, when it is a yes or a no. */
    public synchronized Optional<Settled> hear(String said) {
        if (pending == null) return Optional.empty();
        Boolean a = yesNo(said);
        if (a == null) return Optional.empty();
        Question q = pending;
        pending = null;
        told.put(q.key(), a);
        assumed.remove(q.key());
        return Optional.of(new Settled(q.key(), a, false));
    }

    /** Time passing: a question waited out is settled with its convenient answer. */
    public synchronized Optional<Settled> tick(long now) {
        if (pending == null || now - pending.askedAt() < pending.waits()) return Optional.empty();
        Question q = pending;
        pending = null;
        assumed.put(q.key(), new long[] {q.convenient() ? 1 : 0, now});
        return Optional.of(new Settled(q.key(), q.convenient(), true));
    }

    /** The owner says it again ("you can kill golems", "leave the golems"): kept as told. */
    public synchronized void tell(String key, boolean yes) {
        told.put(key, yes);
        assumed.remove(key);
        if (pending != null && pending.key().equals(key)) pending = null;
    }

    public synchronized Map<String, Boolean> exportTold() {
        return new LinkedHashMap<>(told);
    }

    public synchronized void importTold(Map<String, Boolean> m) {
        told.clear();
        if (m != null) m.forEach((k, v) -> {
            if (k != null && v != null && told.size() < 256) told.put(k, v);
        });
    }
}
