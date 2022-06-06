package io.github.gaming32.javayield.transform;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.ListIterator;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class YieldTransformer {
    private static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");
    private static final Type SUPPLIER_OBJECT_TYPE = Type.getObjectType("java/util/function/Supplier");
    private static final Type SUPPLIER_METHOD_TYPE = Type.getMethodType(OBJECT_TYPE);
    private static final Type ITERATOR_ARRAY_TYPE = Type.getType("[Ljava/util/Iterator;");

    private YieldTransformer() {
    }

    public static byte[] transformClass(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        ClassNode result = transformClass(classNode);
        if (result == null) {
            return null;
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        result.accept(writer);
        return writer.toByteArray();
    }

    public static ClassNode transformClass(ClassNode classNode) {
        boolean transformed = false;
        final int nMethods = classNode.methods.size();
        for (int i = 0; i < nMethods; i++) {
            transformed |= maybeTransformMethod(classNode, classNode.methods.get(i));
        }
        return transformed ? classNode : null;
    }

    public static boolean maybeTransformMethod(ClassNode classNode, MethodNode method) {
        if (!mightBeGenerator(method)) return false;
        checkAllowedToBeGenerator(method);
        transformMethod(classNode, method);
        return true;
    }

    private static void transformMethod(ClassNode classNode, MethodNode method) {
        final int lineNumber = getLineNumber(method.instructions.iterator());
        final int argOffset;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            argOffset = 1;
        } else {
            argOffset = 0;
        }
        final Type[] argTypes = Type.getArgumentTypes(method.desc);
        final boolean[] effectivelyFinal = effectivelyFinalArgs(method, argOffset, argTypes);
        Type[] varTypes = calculateAndExpandLvt(classNode, method, effectivelyFinal, argTypes);
        final int yieldCountInfo = expandYieldAll(method);
        final boolean hasYieldAll = (yieldCountInfo & 1) != 0;
        if (hasYieldAll) {
            varTypes = Arrays.copyOf(varTypes, varTypes.length + 1);
            varTypes[varTypes.length - 1] = ITERATOR_ARRAY_TYPE;
        }
        final Type internalGeneratorType = Type.getMethodType(OBJECT_TYPE, varTypes);
        final MethodNode newMethod = (MethodNode)classNode.visitMethod(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            method.name + "$0",
            internalGeneratorType.getDescriptor(),
            null,
            method.exceptions.toArray(new String[method.exceptions.size()])
        );
        method.instructions.accept(newMethod);
        method.instructions.clear();
        final Label start = new Label();
        method.visitLabel(start);
        for (int i = 0; i < method.localVariables.size(); i++) {
            final String name = method.localVariables.get(i).name;
            final String desc = varTypes[i].getDescriptor();
            newMethod.visitLocalVariable(name, desc, null, start, start, i);
            newMethod.visitParameter(name, Opcodes.ACC_FINAL);
        }
        newMethod.visitLocalVariable("$state", "[I", null, start, start, varTypes.length - (hasYieldAll ? 2 : 1));
        newMethod.visitParameter("$state", Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC);
        if (hasYieldAll) {
            newMethod.visitLocalVariable("$yieldAll", "[Ljava/util/Iterator;", null, start, start, varTypes.length - 1);
            newMethod.visitParameter("$yieldAll", Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC);
        }
        transformMethod0(newMethod, varTypes, yieldCountInfo);
        method.visitCode();
        if (lineNumber != -1) {
            method.visitLineNumber(lineNumber, start);
        }
        for (int i = 0; i < argOffset; i++) {
            method.visitVarInsn(Opcodes.ALOAD, i);
        }
        for (int i = 0; i < effectivelyFinal.length; i++) {
            if (effectivelyFinal[i]) {
                method.visitVarInsn(argTypes[i].getOpcode(Opcodes.ILOAD), argOffset + i);
            } else {
                final String descriptor = argTypes[i].getDescriptor();
                iconst(method, 1);
                if (descriptor.length() == 1) {
                    method.visitIntInsn(Opcodes.NEWARRAY, getPrimitiveIdentifer(descriptor));
                } else {
                    method.visitTypeInsn(Opcodes.ANEWARRAY, argTypes[i].getInternalName());
                }
                method.visitInsn(Opcodes.DUP);
                iconst(method, 0);
                method.visitVarInsn(argTypes[i].getOpcode(Opcodes.ILOAD), argOffset + i);
                method.visitInsn(argTypes[i].getOpcode(Opcodes.IASTORE));
            }
        }
        final int varTypesEndOffset = hasYieldAll ? 2 : 1;
        for (int i = effectivelyFinal.length + argOffset; i < varTypes.length - varTypesEndOffset; i++) {
            final String descriptor = argTypes[i].getDescriptor();
            iconst(method, 1);
            if (descriptor.length() == 1) {
                method.visitIntInsn(Opcodes.NEWARRAY, getPrimitiveIdentifer(descriptor));
            } else {
                method.visitTypeInsn(Opcodes.ANEWARRAY, varTypes[i].getInternalName());
            }
        }
        iconst(method, 1);
        method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
        if (hasYieldAll) {
            iconst(method, 1);
            method.visitTypeInsn(Opcodes.ANEWARRAY, "java/util/Iterator");
        }
        method.visitInvokeDynamicInsn(
            "get",
            Type.getMethodDescriptor(SUPPLIER_OBJECT_TYPE, varTypes),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
            ),
            new Object[] {
                SUPPLIER_METHOD_TYPE,
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    classNode.name,
                    method.name + "$0",
                    internalGeneratorType.getDescriptor(),
                    (classNode.access & Opcodes.ACC_INTERFACE) != 0
                ),
                SUPPLIER_METHOD_TYPE
            }
        );
        if (Type.getReturnType(method.desc).getInternalName().equals("java/lang/Iterable")) {
            method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "io/github/gaming32/javayield/runtime/GeneratorIterator",
                "$createIterableGenerator",
                "(Ljava/util/function/Supplier;)Ljava/lang/Iterable;",
                false
            );
        } else {
            method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "io/github/gaming32/javayield/runtime/GeneratorIterator",
                "$createIteratorGenerator",
                "(Ljava/util/function/Supplier;)Ljava/lang/Iterator;",
                false
            );
        }
        method.visitInsn(Opcodes.ARETURN);
        method.visitEnd();
    }

    private static int getPrimitiveIdentifer(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'Z': return Opcodes.T_BOOLEAN;
            case 'B': return Opcodes.T_BYTE;
            case 'S': return Opcodes.T_SHORT;
            case 'C': return Opcodes.T_CHAR;
            case 'I': return Opcodes.T_INT;
            case 'F': return Opcodes.T_FLOAT;
            case 'J': return Opcodes.T_LONG;
            case 'D': return Opcodes.T_DOUBLE;
            default:
                throw new AssertionError();
        }
    }

    /** Expand all yieldAll calls to their equivalent yield_ calls and return the number of yield_ calls */
    private static int expandYieldAll(MethodNode method) {
        int yieldCount = 0;
        boolean hasYieldAll = false;
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();
        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
            final MethodInsnNode methodInsn = (MethodInsnNode)insn;
            if (!methodInsn.owner.equals("io/github/gaming32/javayield/api/Yield")) continue;
            if (methodInsn.name.equals("yield_")) {
                yieldCount++;
                continue;
            }
            if (methodInsn.name.equals("yieldAll")) {
                hasYieldAll = true;
                yieldCount++;
                it.remove();
                if (methodInsn.desc.equals("(Ljava/lang/Iterable;)V")) {
                    it.add(new MethodInsnNode(
                        Opcodes.INVOKEINTERFACE,
                        "java/lang/Iterable",
                        "iterator",
                        "()Ljava/util/Iterator;"
                    ));
                }
                final LabelNode start = new LabelNode();
                final LabelNode end = new LabelNode();
                it.add(start);
                it.add(new InsnNode(Opcodes.DUP));
                it.add(new MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    "java/util/Iterator",
                    "hasNext",
                    "()Z"
                ));
                it.add(new JumpInsnNode(Opcodes.IFEQ, end));
                it.add(new InsnNode(Opcodes.DUP));
                it.add(new MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    "java/util/Iterator",
                    "next",
                    "()Ljava/lang/Object;"
                ));
                it.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "io/github/gaming32/javayield/api/Yield",
                    "yieldAllProxy",
                    "()V"
                ));
                it.add(new JumpInsnNode(Opcodes.GOTO, start));
                it.add(end);
                it.add(new InsnNode(Opcodes.POP));
            }
        }
        return (yieldCount << 1) | (hasYieldAll ? 1 : 0);
    }

    private static void transformMethod0(MethodNode method, Type[] varTypes, int yieldCountInfo) {
        final boolean hasYieldAll = (yieldCountInfo & 1) != 0;
        final int yieldCount = yieldCountInfo >> 1;
        final int stateVarIndex = varTypes.length - (hasYieldAll ? 2 : 1);
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();
        final LabelNode[] stateLabels = new LabelNode[yieldCount + 2];
        for (int i = 0; i < stateLabels.length; i++) {
            stateLabels[i] = new LabelNode();
        }
        it.add(new VarInsnNode(Opcodes.ALOAD, stateVarIndex));
        it.add(iconst(0));
        it.add(new InsnNode(Opcodes.IALOAD));
        it.add(new TableSwitchInsnNode(0, yieldCount + 1, stateLabels[yieldCount + 1], stateLabels));
        it.add(stateLabels[0]);
        int currentYield = 1;
        boolean returnVisited = false;
        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (insn.getOpcode() == Opcodes.ARETURN) {
                it.remove(); // ARETURN
                it.previous();
                it.remove(); // ACONST_NULL
                if (!returnVisited) {
                    it.add(stateLabels[yieldCount + 1]);
                    it.add(new VarInsnNode(Opcodes.ALOAD, stateVarIndex));
                    it.add(iconst(0));
                    it.add(iconst(yieldCount + 1));
                    it.add(new InsnNode(Opcodes.IASTORE));
                    it.add(new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "io/github/gaming32/javayield/runtime/GeneratorIterator",
                        "$COMPLETE",
                        "Ljava/lang/Object;"
                    ));
                    it.add(new InsnNode(Opcodes.ARETURN));
                } else {
                    it.add(new JumpInsnNode(Opcodes.GOTO, stateLabels[yieldCount + 1]));
                }
                continue;
            }
            if (insn.getOpcode() != Opcodes.INVOKESTATIC) continue;
            final MethodInsnNode methodInsn = (MethodInsnNode)insn;
            boolean isYieldAll = false;
            if (
                methodInsn.owner.equals("io/github/gaming32/javayield/api/Yield") &&
                (methodInsn.name.equals("yield_") || (isYieldAll = methodInsn.name.equals("yieldAllProxy")))
            ) {
                it.remove();
                final int state = currentYield++;
                if (isYieldAll) {
                    // Store the Iterator for future use
                    it.previous();
                    it.add(new VarInsnNode(Opcodes.ALOAD, varTypes.length - 1));
                    it.add(new InsnNode(Opcodes.SWAP));
                    it.add(iconst(0));
                    it.add(new InsnNode(Opcodes.SWAP));
                    it.add(new InsnNode(Opcodes.AASTORE));
                    it.next();
                }
                it.add(new VarInsnNode(Opcodes.ALOAD, stateVarIndex));
                it.add(iconst(0));
                it.add(iconst(state));
                it.add(new InsnNode(Opcodes.IASTORE));
                it.add(new InsnNode(Opcodes.ARETURN));
                it.add(stateLabels[state]);
                if (isYieldAll) {
                    // Recover the iterator
                    it.add(new VarInsnNode(Opcodes.ALOAD, varTypes.length - 1));
                    it.add(iconst(0));
                    it.add(new InsnNode(Opcodes.AALOAD));
                }
            }
        }
    }

    private static boolean[] effectivelyFinalArgs(MethodNode method, int argOffset, Type[] argTypes) {
        final boolean[] effectivelyFinal = new boolean[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            effectivelyFinal[i] = isArgEffectivelyFinal(method, i + argOffset);
        }
        return effectivelyFinal;
    }

    private static Type[] calculateAndExpandLvt(ClassNode classNode, MethodNode method, boolean[] effectivelyFinal, Type[] argTypes) {
        if (method.localVariables == null) {
            throw new IllegalArgumentException("Yield transformer depends on LVT");
        }
        // final Map<AbstractInsnNode, Integer> stackHeights = calculateStackHeights(method);
        final Type[] varTypes = new Type[method.localVariables.size() + 1];
        int i = 0;
        final int argOffset;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            varTypes[i++] = Type.getType("L" + classNode.name + ";");
            argOffset = 1;
        } else {
            argOffset = 0;
        }
        for (final Type argType : argTypes) {
            if (effectivelyFinal[i - argOffset]) {
                // Final parameter doesn't need an array to mimic pass by reference, since it can't be changed
                varTypes[i++] = argType;
            } else {
                rewriteVariable(method, i, argType);
                varTypes[i++] = Type.getType("[" + argType.getDescriptor());
            }
        }
        while (i < method.localVariables.size()) {
            varTypes[i++] = Type.getType("[" + rewriteVariable(method, i, null).getDescriptor());
        }
        varTypes[i++] = Type.getType("[I");
        return varTypes;
    }

    private static Type rewriteVariable(MethodNode method, int varIndex, Type varType) {
        LocalVariableNode varNode = method.localVariables.get(varIndex);
        if (varType == null) {
            varType = Type.getType(varNode.desc);
        }
        ListIterator<AbstractInsnNode> it = method.instructions.iterator(method.instructions.indexOf(varNode.start));
        while (it.hasNext()) {
            AbstractInsnNode insn = it.next();
            if (insn == varNode.end) {
                break;
            }
            if (insn instanceof IincInsnNode) {
                IincInsnNode iincInsn = (IincInsnNode)insn;
                if (iincInsn.var == varIndex) {
                    // final int oldStack = stackHeights.get(insn);
                    it.remove();
                    AbstractInsnNode newInsn = new VarInsnNode(Opcodes.ALOAD, varIndex);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 1);
                    newInsn = iconst(0);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 2);
                    newInsn = new InsnNode(Opcodes.DUP2);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 4);
                    newInsn = new InsnNode(varType.getOpcode(Opcodes.IALOAD));
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 3);
                    newInsn = iconst(iincInsn.incr);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 4);
                    newInsn = new InsnNode(Opcodes.IADD);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 3);
                    newInsn = new InsnNode(varType.getOpcode(Opcodes.IASTORE));
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack);
                }
                continue;
            }
            if (!(insn instanceof VarInsnNode)) {
                continue;
            }
            VarInsnNode varInsn = (VarInsnNode)insn;
            if (varInsn.var == varIndex) {
                // final int oldStack = stackHeights.get(insn);
                it.remove();
                AbstractInsnNode newInsn;
                if (varInsn.getOpcode() >= Opcodes.ILOAD && varInsn.getOpcode() <= Opcodes.ALOAD) {
                    newInsn = new VarInsnNode(Opcodes.ALOAD, varIndex);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 1);
                    newInsn = iconst(0);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 2);
                    newInsn = new InsnNode(varType.getOpcode(Opcodes.IALOAD));
                } else {
                    newInsn = new VarInsnNode(Opcodes.ALOAD, varIndex);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 1);
                    newInsn = new InsnNode(Opcodes.SWAP);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 1);
                    newInsn = iconst(0);
                    it.add(newInsn);
                    // stackHeights.put(newInsn, oldStack + 2);
                    newInsn = new InsnNode(Opcodes.SWAP);
                    it.add(newInsn);
                    newInsn = new InsnNode(varType.getOpcode(Opcodes.IASTORE));
                }
                it.add(newInsn);
                // stackHeights.put(newInsn, oldStack);
            }
        }
        return varType;
    }

    private static boolean isArgEffectivelyFinal(MethodNode method, int argIndex) {
        if (method.parameters != null && (method.parameters.get(argIndex).access & Opcodes.ACC_FINAL) != 0) {
            return true;
        }
        for (final AbstractInsnNode insn : method.instructions) {
            if (insn instanceof VarInsnNode) {
                final VarInsnNode varInsn = (VarInsnNode)insn;
                if (varInsn.var == argIndex) {
                    final int opcode = varInsn.getOpcode();
                    if (opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE) {
                        return false;
                    }
                }
            } else if (insn instanceof IincInsnNode) {
                IincInsnNode iincInsn = (IincInsnNode)insn;
                if (iincInsn.var == argIndex) return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unused")
    private static Map<AbstractInsnNode, Integer> calculateStackHeights(MethodNode method) {
        final Map<AbstractInsnNode, Integer> result = new IdentityHashMap<>();
        int i = 0;
        for (final AbstractInsnNode insn : method.instructions) {
            final int opcode;
            switch (opcode = insn.getOpcode()) {
                case Opcodes.ACONST_NULL:
                case Opcodes.ICONST_M1:
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                case Opcodes.FCONST_0:
                case Opcodes.FCONST_1:
                case Opcodes.FCONST_2:
                case Opcodes.BIPUSH:
                case Opcodes.SIPUSH:
                case Opcodes.LDC:
                case Opcodes.ILOAD:
                case Opcodes.FLOAD:
                case Opcodes.ALOAD:
                case Opcodes.DUP:
                case Opcodes.DUP_X1:
                case Opcodes.DUP_X2:
                case Opcodes.I2L:
                case Opcodes.I2D:
                case Opcodes.F2L:
                case Opcodes.F2D:
                case Opcodes.JSR:
                case Opcodes.NEW:
                    i++;
                    break;
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                case Opcodes.DCONST_0:
                case Opcodes.DCONST_1:
                case Opcodes.LLOAD:
                case Opcodes.DLOAD:
                case Opcodes.DUP2:
                case Opcodes.DUP2_X1:
                case Opcodes.DUP2_X2:
                    i += 2;
                    break;
                case Opcodes.IALOAD:
                case Opcodes.FALOAD:
                case Opcodes.AALOAD:
                case Opcodes.BALOAD:
                case Opcodes.CALOAD:
                case Opcodes.SALOAD:
                case Opcodes.ISTORE:
                case Opcodes.FSTORE:
                case Opcodes.ASTORE:
                case Opcodes.POP:
                case Opcodes.IADD:
                case Opcodes.FADD:
                case Opcodes.ISUB:
                case Opcodes.FSUB:
                case Opcodes.IMUL:
                case Opcodes.FMUL:
                case Opcodes.IDIV:
                case Opcodes.FDIV:
                case Opcodes.IREM:
                case Opcodes.FREM:
                case Opcodes.ISHL:
                case Opcodes.ISHR:
                case Opcodes.IUSHR:
                case Opcodes.IAND:
                case Opcodes.IOR:
                case Opcodes.IXOR:
                case Opcodes.L2I:
                case Opcodes.L2F:
                case Opcodes.D2I:
                case Opcodes.D2F:
                case Opcodes.LCMP:
                case Opcodes.DCMPL:
                case Opcodes.DCMPG:
                case Opcodes.IFEQ:
                case Opcodes.IFNE:
                case Opcodes.IFLT:
                case Opcodes.IFGE:
                case Opcodes.IFGT:
                case Opcodes.IFLE:
                case Opcodes.TABLESWITCH:
                case Opcodes.LOOKUPSWITCH:
                case Opcodes.MONITORENTER:
                case Opcodes.MONITOREXIT:
                case Opcodes.IFNULL:
                case Opcodes.IFNONNULL:
                    i--;
                    break;
                case Opcodes.LSTORE:
                case Opcodes.DSTORE:
                case Opcodes.POP2:
                case Opcodes.LADD:
                case Opcodes.DADD:
                case Opcodes.LSUB:
                case Opcodes.DSUB:
                case Opcodes.LMUL:
                case Opcodes.DMUL:
                case Opcodes.LDIV:
                case Opcodes.DDIV:
                case Opcodes.LREM:
                case Opcodes.DREM:
                case Opcodes.LSHL:
                case Opcodes.LSHR:
                case Opcodes.LUSHR:
                case Opcodes.LAND:
                case Opcodes.LOR:
                case Opcodes.LXOR:
                case Opcodes.IF_ICMPEQ:
                case Opcodes.IF_ICMPNE:
                case Opcodes.IF_ICMPLT:
                case Opcodes.IF_ICMPGE:
                case Opcodes.IF_ICMPGT:
                case Opcodes.IF_ICMPLE:
                case Opcodes.IF_ACMPEQ:
                case Opcodes.IF_ACMPNE:
                    i -= 2;
                    break;
                case Opcodes.IASTORE:
                case Opcodes.FASTORE:
                case Opcodes.AASTORE:
                case Opcodes.BASTORE:
                case Opcodes.CASTORE:
                case Opcodes.SASTORE:
                    i -= 3;
                    break;
                case Opcodes.LASTORE:
                case Opcodes.DASTORE:
                    i -= 4;
                    break;
                case Opcodes.IRETURN:
                case Opcodes.LRETURN:
                case Opcodes.FRETURN:
                case Opcodes.DRETURN:
                case Opcodes.ARETURN:
                case Opcodes.RETURN:
                    i = 0;
                    break;
                case Opcodes.ATHROW:
                    i = 1;
                    break;
                // Special case opcodes
                case Opcodes.PUTFIELD:
                    i--;
                case Opcodes.PUTSTATIC: {
                    final FieldInsnNode varInsn = (FieldInsnNode)insn;
                    i -= Type.getType(varInsn.desc).getSize();
                    break;
                }
                case Opcodes.GETFIELD:
                    i--;
                case Opcodes.GETSTATIC: {
                    final FieldInsnNode varInsn = (FieldInsnNode)insn;
                    i += Type.getType(varInsn.desc).getSize();
                    break;
                }
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKESPECIAL:
                case Opcodes.INVOKESTATIC:
                case Opcodes.INVOKEINTERFACE: {
                    final MethodInsnNode methodInsn = (MethodInsnNode)insn;
                    final int argAndReturnSizes = Type.getArgumentsAndReturnSizes(methodInsn.desc);
                    int argSizes = argAndReturnSizes >> 2;
                    if (opcode == Opcodes.INVOKESTATIC) argSizes--;
                    i -= argSizes - (argAndReturnSizes & 3);
                    break;
                }
                case Opcodes.INVOKEDYNAMIC: {
                    final InvokeDynamicInsnNode indyInsn = (InvokeDynamicInsnNode)insn;
                    final int argAndReturnSizes = Type.getArgumentsAndReturnSizes(indyInsn.desc);
                    i -= (argAndReturnSizes >> 2) - 1 - (argAndReturnSizes & 3);
                    break;
                }
                case Opcodes.MULTIANEWARRAY: {
                    final MultiANewArrayInsnNode manaInsn = (MultiANewArrayInsnNode)insn;
                    i -= manaInsn.dims - 1;
                    break;
                }
            }
            result.put(insn, i);
        }
        return result;
    }

    private static AbstractInsnNode iconst(int i) {
        if (i >= -1 && i <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + i);
        } else if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, i);
        } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, i);
        } else {
            return new LdcInsnNode(i);
        }
    }

    public static void iconst(MethodVisitor mv, int i) {
        if (i >= -1 && i <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + i);
        } else if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, i);
        } else if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, i);
        } else {
            mv.visitLdcInsn(i);
        }
    }

    private static int getLineNumber(ListIterator<AbstractInsnNode> it) {
        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (insn instanceof LineNumberNode) {
                return ((LineNumberNode)insn).line;
            }
        }
        return -1;
    }

    private static boolean mightBeGenerator(MethodNode method) {
        if (method.invisibleAnnotations != null) {
            for (AnnotationNode annotation : method.invisibleAnnotations) {
                if (annotation.desc.equals("Lio/github/gaming32/javayield/api/Generator;")) {
                    return true;
                }
            }
        }
        for (AbstractInsnNode insn : method.instructions) {
            if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                MethodInsnNode methodInsn = (MethodInsnNode)insn;
                if (methodInsn.owner.equals("io/github/gaming32/javayield/api/Yield") && !methodInsn.itf) {
                    if (
                        methodInsn.name.equals("yield_") &&
                        methodInsn.desc.equals("(Ljava/lang/Object;)V")
                    ) {
                        return true;
                    }
                    if (
                        methodInsn.name.equals("yieldAll") &&
                        (
                            methodInsn.desc.equals("(Ljava/lang/Iterable;)V") ||
                            methodInsn.desc.equals("(Ljava/util/Iterator;)V")
                        )
                    ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void checkAllowedToBeGenerator(MethodNode method) {
        String returnType = method.desc.substring(method.desc.indexOf(')') + 1);
        if (
            !returnType.equals("Ljava/lang/Iterable;") &&
            !returnType.equals("Ljava/util/Iterator;")
        ) {
            throw new IllegalArgumentException("Generator must return either java.lang.Iterable or java.util.Iterator");
        }
        ListIterator<AbstractInsnNode> it = method.instructions.iterator();
        while (it.hasNext()) {
            AbstractInsnNode insn = it.next();
            if (insn.getOpcode() == Opcodes.ARETURN) {
                it.previous();
                if (it.previous().getOpcode() != Opcodes.ACONST_NULL) {
                    throw new IllegalArgumentException("All returns in generator must be null returns");
                }
                it.next();
                it.next();
            }
        }
    }
}
