package io.github.gaming32.javayield.javac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.Trees;

import io.github.gaming32.javayield.transform.YieldTransformer;
import sun.misc.Unsafe;

public class JavayieldJavacPlugin implements Plugin {
    private static final Unsafe UNSAFE;
    private static final StandardLocation MODULE_SOURCE_PATH = getEnumMemberOrNull(StandardLocation.class, "MODULE_SOURCE_PATH");
    private static final ElementKind MODULE = getEnumMemberOrNull(ElementKind.class, "MODULE");

    static {
        try {
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            UNSAFE = (Unsafe)unsafeField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    @Override
    public String getName() {
        return "javayield";
    }

    @Override
    public void init(JavacTask task, String... args) {

        final Trees trees = Trees.instance(task);
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() != TaskEvent.Kind.GENERATE) return;
                final JavaFileManager fileManager;
                final boolean multiModuleMode;
                try {
                    final JavaFileObject fileObject = e.getSourceFile();
                    final long fieldOffset = UNSAFE.objectFieldOffset(fileObject.getClass().getSuperclass().getDeclaredField("fileManager"));
                    fileManager = (JavaFileManager)UNSAFE.getObject(fileObject, fieldOffset);
                    multiModuleMode = MODULE_SOURCE_PATH != null && fileManager.hasLocation(MODULE_SOURCE_PATH);
                } catch (Exception e1) {
                    throw new RuntimeException(e1);
                }
                final TypeElement classElement = e.getTypeElement();
                final Location outLocn;
                final Name nameObject;
                if (classElement.getEnclosingElement().getKind() == MODULE) {
                    nameObject = classElement.getQualifiedName();
                } else {
                    try {
                        final long fieldOffset = UNSAFE.objectFieldOffset(classElement.getClass().getDeclaredField("flatname"));
                        nameObject = (Name)UNSAFE.getObject(classElement, fieldOffset);
                    } catch (Exception e1) {
                        throw new RuntimeException(e1);
                    }
                }
                final String name = nameObject.toString();
                if (multiModuleMode) {
                    final ModuleElement msym = classElement.getEnclosingElement().getKind() == MODULE
                        ? (ModuleElement)classElement.getEnclosingElement()
                        : (ModuleElement)getPackageElement(classElement).getEnclosingElement();
                    try {
                        outLocn = fileManager.getLocationForModule(StandardLocation.CLASS_OUTPUT, msym.getQualifiedName().toString());
                    } catch (IOException e1) {
                        throw new UncheckedIOException(e1);
                    }
                } else {
                    outLocn = StandardLocation.CLASS_OUTPUT;
                }
                final JavaFileObject outFile;
                try {
                    outFile = fileManager.getJavaFileForOutput(
                        outLocn,
                        name,
                        JavaFileObject.Kind.CLASS,
                        e.getSourceFile()
                    );
                } catch (IOException e1) {
                    throw new UncheckedIOException(e1);
                }
                final byte[] input;
                try (InputStream is = outFile.openInputStream()) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, n);
                    }
                    input = baos.toByteArray();
                } catch (Exception e1) {
                    trees.printMessage(
                        Diagnostic.Kind.ERROR, "Failed to read class file: " + e1,
                        e.getCompilationUnit(), e.getCompilationUnit()
                    );
                    return;
                }
                final byte[] output = YieldTransformer.transformClass(input);
                if (output != null) {
                    try (OutputStream os = outFile.openOutputStream()) {
                        os.write(output);
                    } catch (Exception e1) {
                        trees.printMessage(
                            Diagnostic.Kind.ERROR, "Failed to write class file: " + e1,
                            e.getCompilationUnit(), e.getCompilationUnit()
                        );
                        return;
                    }
                }
            }
        });
    }

    private static PackageElement getPackageElement(Element element) {
        while (element.getKind() != ElementKind.PACKAGE) {
            element = element.getEnclosingElement();
        }
        return (PackageElement)element;
    }

    private static <T extends Enum<T>> T getEnumMemberOrNull(Class<T> enumType, String name) {
        try {
            return Enum.valueOf(enumType, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
