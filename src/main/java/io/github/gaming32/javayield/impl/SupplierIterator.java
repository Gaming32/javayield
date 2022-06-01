package io.github.gaming32.javayield.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public final class SupplierIterator<E> implements Iterator<E> {
    public static final Object $COMPLETE = new Object();

    private final Supplier<E> fn;
    private boolean valueReady;
    private E next;

    public SupplierIterator(Supplier<E> fn) {
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
            if (next == $COMPLETE) {
                throw new NoSuchElementException();
            }
            next = fn.get();
        }
        E value = next;
        valueReady = false;
        next = null;
        return value;
    }

    public static <E> Iterable<E> $createGenerator(Supplier<E> gen) {
        return new IterableFromIterator<>(new SupplierIterator<>(gen));
    }
}
