package io.github.gaming32.javayield;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import io.github.gaming32.javayield.transform.YieldTransformer;

public class AppTest {
    public static void main(String[] args) throws IOException, URISyntaxException, NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException {
        byte[] testFile = Files.readAllBytes(
            Paths.get(
                AppTest.class.getResource("OtherTest.class").toURI()
            )
        );
        byte[] transformed = YieldTransformer.transformClass(testFile);
        if (transformed == null) {
            System.out.println("**Not transformed**");
        } else {
            try (OutputStream os = new FileOutputStream("io/github/gaming32/javayield/OtherTest.class")) {
                os.write(transformed);
            }
            ClassReader reader = new ClassReader(transformed);
            CheckClassAdapter.verify(reader, true, new PrintWriter(System.out));
            // reader.accept(new TraceClassVisitor(new PrintWriter(System.out)), 0);

            final Class<?> testClass = new ClassLoader() {
                public Class<?> loadClassFromBytecode(String name, byte[] bytecode) {
                    return super.defineClass(name, bytecode, 0, bytecode.length);
                }
            }.loadClassFromBytecode("io.github.gaming32.javayield.OtherTest", transformed);
            final Method testMethod = testClass.getDeclaredMethod("generatorTest", int.class);
            @SuppressWarnings("unchecked")
            final Iterable<Integer> testIterable = (Iterable<Integer>)testMethod.invoke(null, 1);
            for (final int testI : testIterable) {
                System.out.println(testI);
            }
        }
    }
}
