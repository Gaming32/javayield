package io.github.gaming32.javayield.ecj;

import java.util.ListIterator;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class Transformers {
    public static byte[] transformUtil(byte[] classfileBuffer) {
        final ClassNode clazz = new ClassNode();
        new ClassReader(classfileBuffer).accept(clazz, 0);
        for (final MethodNode method : clazz.methods) {
            if (
                method.name.equals("writeToDisk") &&
                method.desc.equals("(ZLjava/lang/String;Ljava/lang/String;Lorg/eclipse/jdt/internal/compiler/ClassFile;)V")
            ) {
                final ListIterator<AbstractInsnNode> it = method.instructions.iterator();
                boolean writeReached = false;
                while (it.hasNext()) {
                    final AbstractInsnNode insn = it.next();
                    if (insn instanceof MethodInsnNode) {
                        final MethodInsnNode methodInsn = (MethodInsnNode)insn;
                        if (
                            methodInsn.owner.equals("java/io/BufferedOutputStream") &&
                            methodInsn.name.equals("write") &&
                            methodInsn.desc.equals("([BII)V")
                        ) {
                            if (!writeReached) {
                                it.previous();
                                it.add(new TypeInsnNode(Opcodes.NEW, "java/io/ByteArrayOutputStream"));
                                it.add(new InsnNode(Opcodes.DUP));
                                it.add(new MethodInsnNode(
                                    Opcodes.INVOKESPECIAL,
                                    "java/io/ByteArrayOutputStream",
                                    "<init>",
                                    "()V",
                                    false
                                ));
                                it.add(new VarInsnNode(Opcodes.ASTORE, 6));
                                writeReached = true;
                                it.next();
                            }
                            it.remove();
                            it.add(new VarInsnNode(Opcodes.ALOAD, 6));
                            it.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "io/github/gaming32/javayield/ecj/AgentUtils",
                                "writeToByteArrayOutputStream",
                                "(Ljava/io/FileOutputStream;[BIILjava/io/ByteArrayOutputStream;)V",
                                false
                            ));
                        } else if (
                            methodInsn.owner.equals("java/io/BufferedOutputStream") &&
                            methodInsn.name.equals("flush") &&
                            methodInsn.desc.equals("()V")
                        ) {
                            it.remove();
                            it.add(new VarInsnNode(Opcodes.ALOAD, 6));
                            it.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "io/github/gaming32/javayield/ecj/AgentUtils",
                                "transformAndSave",
                                "(Ljava/io/FileOutputStream;Ljava/io/ByteArrayOutputStream;)V",
                                false
                            ));
                        }
                    }
                }
                break;
            }
        }
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    public static byte[] transformAbstractImageBuilder(byte[] classfileBuffer) {
        final ClassNode clazz = new ClassNode();
        new ClassReader(classfileBuffer).accept(clazz, 0);
        for (final MethodNode method : clazz.methods) {
            if (
                method.name.equals("writeClassFileContents") &&
                method.desc.equals("(Lorg/eclipse/jdt/internal/compiler/ClassFile;Lorg/eclipse/core/resources/IFile;Ljava/lang/String;ZLorg/eclipse/jdt/internal/core/builder/SourceFile;)V")
            ) {
                final ListIterator<AbstractInsnNode> it = method.instructions.iterator();
                while (it.hasNext()) {
                    final AbstractInsnNode insn = it.next();
                    if (insn instanceof MethodInsnNode) {
                        final MethodInsnNode methodInsn = (MethodInsnNode)insn;
                        if (
                            methodInsn.owner.equals("org/eclipse/jdt/internal/compiler/ClassFile") &&
                            methodInsn.name.equals("getBytes") &&
                            methodInsn.desc.equals("()[B")
                        ) {
                            it.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "io/github/gaming32/javayield/ecj/AgentUtils",
                                "maybeTransform",
                                "([B)[B",
                                false
                            ));
                            break;
                        }
                    }
                }
                break;
            }
        }
        final ClassWriter writer = new ClassWriter(0);
        clazz.accept(writer);
        return writer.toByteArray();
    }
}
