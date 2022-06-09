package io.github.gaming32.javayield.transform;

import static io.github.gaming32.javayield.api.Yield.sent;
import static io.github.gaming32.javayield.api.Yield.yield_;

import io.github.gaming32.javayield.runtime.GeneratorIterator;

public class SendTest {
    public static GeneratorIterator<String, String, ?> testGenerator() {
        while (sent() != null) {
            yield_((String)sent() + (String)sent());
        }
        return null;
    }

    public static void main(String[] args) {
        GeneratorIterator<String, String, ?> it = testGenerator();
        it.send("123");
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }
}
