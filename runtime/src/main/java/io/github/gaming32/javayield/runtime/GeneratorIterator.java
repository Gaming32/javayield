package io.github.gaming32.javayield.runtime;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

public final class GeneratorIterator<E, S, R> implements Iterator<E> {
    final Function<Object, S> fn;
    private S sent;
    private boolean valueReady;
    private Object next;

    public GeneratorIterator(Function<Object, S> fn) {
        this.fn = fn;
    }

    @Override
    public boolean hasNext() {
        if (!valueReady) {
            if (next instanceof CompletedGenerator) {
                return false;
            }
            next = fn.apply(sent);
            sent = null;
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
            next = fn.apply(sent);
            sent = null;
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

    public GeneratorIterator<E, S, R> send(S sent) {
        this.sent = sent;
        return this;
    }

    @SuppressWarnings("unchecked")
    public R getResult() {
        if (!(next instanceof CompletedGenerator)) {
            throw new IllegalStateException("Generator not complete!");
        }
        return ((CompletedGenerator<R>)next).result;
    }
}
