package io.github.gaming32.javayield.transform;

import static io.github.gaming32.javayield.api.Yield.yieldAll;
import static io.github.gaming32.javayield.api.Yield.yield_;

public class OtherTest {
    public Iterable<Integer> generatorTest(int param) {
        {
            Integer myVar = param;
            while (myVar > 0) {
                yield_(myVar = myVar * 3);
            }
        }
        yieldAll("hello".chars().iterator());
        return null;
    }
}
