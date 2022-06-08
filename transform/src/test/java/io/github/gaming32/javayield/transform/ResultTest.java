package io.github.gaming32.javayield.transform;

import static io.github.gaming32.javayield.api.Yield.yield_;
import static io.github.gaming32.javayield.api.Yield.result;

import io.github.gaming32.javayield.runtime.GeneratorIterator;

public class ResultTest {
    public static GeneratorIterator<String, Integer> testGenerator(String original) {
        yield_(original);
        while (original.length() < 23) {
            yield_(original += original);
        }
        return result(original.length());
    }

    public static void main(String[] args) {
        GeneratorIterator<String, Integer> it = testGenerator("world ");
        while (it.hasNext()) {
            System.out.println(it.next());
        }
        System.out.println(it.getResult());
    }
}
