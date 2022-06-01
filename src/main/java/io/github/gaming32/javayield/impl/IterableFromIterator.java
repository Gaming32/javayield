package io.github.gaming32.javayield.impl;

import java.util.Iterator;

public final class IterableFromIterator<E> implements Iterable<E> {
    private final Iterator<E> iterator;

    public IterableFromIterator(Iterator<E> iterator) {
        this.iterator = iterator;
    }

    @Override
    public Iterator<E> iterator() {
        return iterator;
    }
}
