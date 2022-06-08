package io.github.gaming32.javayield.runtime;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public final class GeneratorIterator<E, R> implements Iterator<E> {
    final Supplier<Object> fn;
    private boolean valueReady;
    private Object next;

    public GeneratorIterator(Supplier<Object> fn) {
        this.fn = fn;
    }

    @Override
    public boolean hasNext() {
        if (!valueReady) {
            if (next instanceof CompletedGenerator) {
                return false;
            }
            next = fn.get();
            valueReady = !(next instanceof CompletedGenerator);
        }
        return valueReady;
    }

    @Override
    public E next() {
        if (!valueReady) {
            if (next instanceof CompletedGenerator) {
                throw new NoSuchElementException();
            }
            next = fn.get();
            if (next instanceof CompletedGenerator) {
                throw new NoSuchElementException();
            }
        }
        @SuppressWarnings("unchecked")
        E value = (E)next;
        valueReady = false;
        next = null;
        return value;
    }

    @SuppressWarnings("unchecked")
    public R getResult() {
        if (!(next instanceof CompletedGenerator)) {
            throw new IllegalStateException("Generator not complete!");
        }
        return ((CompletedGenerator<R>)next).result;
    }
}
