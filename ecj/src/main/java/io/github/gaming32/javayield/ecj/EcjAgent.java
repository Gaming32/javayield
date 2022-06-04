package io.github.gaming32.javayield.ecj;

import java.io.File;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.jar.JarFile;

public class EcjAgent {
    public static void premain(String args, Instrumentation inst) {
        generalMain(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        generalMain(args, inst);
    }

    private static void generalMain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            private Set<ClassLoader> equinoxHandled = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                // if (className.equals("org/eclipse/jdt/internal/compiler/util/Util")) {
                //     handleEquinox(loader);
                //     try {
                //         return Transformers.transformUtil(classfileBuffer);
                //     } catch (Throwable t) {
                //         printError(t);
                //     }
                // }
                if (className.equals("org/eclipse/jdt/internal/core/builder/AbstractImageBuilder")) {
                    handleEquinox(loader);
                    try {
                        return Transformers.transformAbstractImageBuilder(classfileBuffer);
                    } catch (Throwable t) {
                        printError(t);
                    }
                }
                return null;
            }

            @SuppressWarnings("unchecked")
            private void handleEquinox(ClassLoader loader) {
                if (loader.getClass().getName().equals("org.eclipse.osgi.internal.loader.EquinoxClassLoader")) {
                    // Eclipse makes us do this oof
                    if (equinoxHandled.contains(loader)) return;
                    try {
                        final Method getBundleLoader = loader.getClass().getDeclaredMethod("getBundleLoader");
                        final Object bundle = getBundleLoader.invoke(loader);

                        final Field containerField = bundle.getClass().getDeclaredField("container");
                        containerField.setAccessible(true);
                        final Object container = containerField.get(bundle);

                        final Field bootDelegationField = container.getClass().getDeclaredField("bootDelegation");
                        bootDelegationField.setAccessible(true);
                        final Set<String> bootDelegation = (Set<String>)bootDelegationField.get(container);

                        bootDelegation.add("io.github.gaming32.javayield.ecj"); // Make us accessible
                        equinoxHandled.add(loader);

                        // loader.loadClass("io.github.gaming32.javayield.ecj.AgentUtils");
                    } catch (Throwable t) {
                        printError(t);
                    }
                }
            }
        }, true);
        try {
            inst.appendToBootstrapClassLoaderSearch(
                new JarFile(new File(EcjAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()))
            );
        } catch (Throwable t) {
            printError(t);
        }
    }

    private static void printError(Throwable t) {
        t.printStackTrace();
        try (PrintWriter pw = new PrintWriter("ERROR-" + System.currentTimeMillis() + ".log")) {
            t.printStackTrace(pw);
        } catch (Throwable t2) {
            t2.printStackTrace();
        }
    }
}
