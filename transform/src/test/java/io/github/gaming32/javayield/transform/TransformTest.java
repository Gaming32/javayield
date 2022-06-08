package io.github.gaming32.javayield.transform;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

public class TransformTest {
    @Test
    public void transformationTest() throws Exception {
        Class<?> testClass = testSingle("OtherTest", false); {
            final Method testMethod = testClass.getDeclaredMethod("generatorTest", int.class);
            @SuppressWarnings("unchecked")
            final Iterable<Integer> testIterable = (Iterable<Integer>)testMethod.invoke(testClass.newInstance(), 1);
            for (final int testI : testIterable) {
                System.out.println(testI);
            }
        }

        testClass = testSingle("JavayieldIjTest", true);
        testClass = testSingle("ResultTest", true);
    }

    private Class<?> testSingle(String className, boolean mainClass) throws Exception {
        byte[] testFile = Files.readAllBytes(
            Paths.get(
                TransformTest.class.getResource(className + ".class").toURI()
            )
        );
        byte[] transformed = YieldTransformer.transformClass(testFile);
        if (transformed == null) {
            System.out.println("**Not transformed**");
            return null;
        } else {
            // try (OutputStream os = new FileOutputStream("io/github/gaming32/javayield/OtherTest.class")) {
            //     os.write(transformed);
            // }
            ClassReader reader = new ClassReader(transformed);
            CheckClassAdapter.verify(reader, true, new PrintWriter(System.out));
            // reader.accept(new TraceClassVisitor(new PrintWriter(System.out)), ClassReader.SKIP_FRAMES);

            Class<?> result = new ClassLoader() {
                public Class<?> loadClassFromBytecode(String name, byte[] bytecode) {
                    return super.defineClass(name, bytecode, 0, bytecode.length);
                }
            }.loadClassFromBytecode("io.github.gaming32.javayield.transform." + className, transformed);
            if (mainClass) {
                final Method mainMethod = result.getDeclaredMethod("main", String[].class);
                mainMethod.invoke(null, new Object[] { new String[0] });
            }
            return result;
        }
    }
}
