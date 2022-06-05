package io.github.gaming32.javayield.transform;

import static io.github.gaming32.javayield.api.Yield.yield_;

public class JavayieldIjTest {
    public static Iterable<String> testGenerator(String original) {
        yield_(original);
        while (original.length() < 100) {
            yield_(original += original);
        }
        return null;
    }

    public static void main(String[] args) {
        for (String s : testGenerator("hello ")) {
            System.out.println(s);
        }
    }
}
