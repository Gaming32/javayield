package io.github.gaming32.javayield.api;

import java.util.Iterator;

import io.github.gaming32.javayield.runtime.GeneratorIterator;

public final class Yield {
    private Yield() {
    }

    private static Error didntInstrument(String method) {
        throw new Error("Called Yield." + method + " without instrumentation");
    }

    public static <T> void yield_(T t) {
        throw didntInstrument("yield(T)");
    }

    public static <T> void yieldAll(Iterable<T> it) {
        throw didntInstrument("yieldAll(Iterable<T>)");
    }

    public static <T> void yieldAll(Iterator<T> it) {
        throw didntInstrument("yieldAll(Iterator<T>)");
    }

    public static <E, R> GeneratorIterator<E, R> result(R result) {
        throw didntInstrument("result(R)");
    }
}
