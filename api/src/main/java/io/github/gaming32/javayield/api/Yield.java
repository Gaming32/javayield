package io.github.gaming32.javayield.api;

import java.util.Iterator;

public final class Yield {
    private Yield() {
    }

    private static Error didntInstrument(String method) {
        throw new Error("Called Yield." + method + " without instrumentation");
    }

    public static <T> void yield_(T t) {
        throw didntInstrument("yield");
    }

    public static <T> void yieldAll(Iterable<T> it) {
        throw didntInstrument("yieldAll");
    }

    public static <T> void yieldAll(Iterator<T> it) {
        throw didntInstrument("yieldAll");
    }
}
