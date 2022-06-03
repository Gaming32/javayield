package io.github.gaming32.javayield.transform;

import static io.github.gaming32.javayield.api.Yield.yieldAll;
import static io.github.gaming32.javayield.api.Yield.yield_;

public class OtherTest {
    public static Iterable<Integer> generatorTest(int param) {
        while (param > 0) {
            yield_(param = param * 3);
        }
        yieldAll("hello".chars().iterator());
        return null;
    }
}
