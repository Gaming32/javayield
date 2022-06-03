package io.github.gaming32.javayield.runtime;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public final class GeneratorIterator<E> implements Iterator<E> {
    public static final Object $COMPLETE = new Object();

    private final Supplier<Object> fn;
    private boolean valueReady;
    private Object next;

    public GeneratorIterator(Supplier<Object> fn) {
        this.fn = fn;
    }

    @Override
    public boolean hasNext() {
        if (!valueReady) {
            next = fn.get();
            valueReady = next != $COMPLETE;
        }
        return valueReady;
    }

    @Override
    public E next() {
        if (!valueReady) {
            next = fn.get();
            if (next == $COMPLETE) {
                throw new NoSuchElementException();
            }
        }
        @SuppressWarnings("unchecked")
        E value = (E)next;
        valueReady = false;
        next = null;
        return value;
    }

    public static <E> Iterator<E> $createIteratorGenerator(Supplier<Object> gen) {
        return new GeneratorIterator<>(gen);
    }

    public static <E> Iterable<E> $createIterableGenerator(Supplier<Object> gen) {
        return new IterableFromIterator<>(new GeneratorIterator<>(gen));
    }
}
