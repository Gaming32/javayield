package io.github.gaming32.javayield.runtime;

final class CompletedGenerator<R> {
    R result;

    CompletedGenerator(R result) {
        this.result = result;
    }
}
