package io.github.gaming32.javayield.runtime;

import java.util.function.Function;

public final class RawGenerators {
    public static final CompletedGenerator<?> COMPLETE = new CompletedGenerator<>(null);

    private RawGenerators() {
    }

    public static <E, S, R> GeneratorIterator<E, S, R> createIteratorGenerator(Function<Object, S> gen) {
        return new GeneratorIterator<>(gen);
    }

    public static <E> Iterable<E> createIterableGenerator(Function<Object, ?> gen) {
        return new IterableFromIterator<>(new GeneratorIterator<>(gen));
    }

    public static <E, S, R> Function<Object, S> unwrap(GeneratorIterator<E, S, R> it) {
        return it.fn;
    }

    @SuppressWarnings("unchecked")
    public static <R> CompletedGenerator<R> complete(R result) {
        return result == null ? (CompletedGenerator<R>)COMPLETE : new CompletedGenerator<>(result);
    }

    public static boolean isComplete(Object state) {
        return state instanceof CompletedGenerator;
    }

    @SuppressWarnings("unchecked")
    public static <R> R getCompletedResult(Object state) {
        return ((CompletedGenerator<R>)state).result;
    }
}
