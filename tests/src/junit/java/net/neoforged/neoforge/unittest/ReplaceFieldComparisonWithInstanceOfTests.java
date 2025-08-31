/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.unittest;

import static org.assertj.core.api.Assertions.assertThat;

import cpw.mods.modlauncher.api.ITransformer;
import java.lang.reflect.Method;
import java.util.List;
import net.neoforged.neoforge.coremods.ReplaceFieldComparisonWithInstanceOf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ReplaceFieldComparisonWithInstanceOfTests {
    private ReplaceFieldComparisonWithInstanceOf newTransformer(String owner, String name, String replacement) {
        return new ReplaceFieldComparisonWithInstanceOf(owner, name, replacement, List.<ITransformer.Target<MethodNode>>of());
    }

    private Method getPrivateMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws Exception {
        Method m = clazz.getDeclaredMethod(name, parameterTypes);
        m.setAccessible(true);
        return m;
    }

    @Test
    @DisplayName("transform: replaces IF_ACMPEQ + matching GETSTATIC with INSTANCEOF + IFNE")
    void transform_replacesEqComparisonWithInstanceOfAndIfNe() {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "java/lang/String");

        MethodNode mn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "m", "()V", null, null);
        InsnList insns = mn.instructions;
        FieldInsnNode field = new FieldInsnNode(Opcodes.GETSTATIC, "owner/Cls", "FIELD", "Ljava/lang/Object;");
        LabelNode label = new LabelNode();
        JumpInsnNode jump = new JumpInsnNode(Opcodes.IF_ACMPEQ, label);
        insns.add(field);
        insns.add(jump);
        insns.add(label);

        tx.transform(mn, null);

        // Verify field access replaced by INSTANCEOF with expected class name
        assertThat(insns.getFirst()).isInstanceOf(TypeInsnNode.class);
        TypeInsnNode type = (TypeInsnNode) insns.getFirst();
        assertThat(type.getOpcode()).isEqualTo(Opcodes.INSTANCEOF);
        assertThat(type.desc).isEqualTo("java/lang/String");

        // Verify jump opcode flipped to IFNE and label preserved
        assertThat(insns.get(1)).isInstanceOf(JumpInsnNode.class);
        JumpInsnNode newJump = (JumpInsnNode) insns.get(1);
        assertThat(newJump.getOpcode()).isEqualTo(Opcodes.IFNE);
        assertThat(newJump.label).isSameAs(label);
    }

    @Test
    @DisplayName("transform: replaces IF_ACMPNE + matching GETFIELD with INSTANCEOF + IFEQ")
    void transform_replacesNeComparisonWithInstanceOfAndIfEq() {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");

        MethodNode mn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "m2", "()V", null, null);
        InsnList insns = mn.instructions;
        FieldInsnNode field = new FieldInsnNode(Opcodes.GETFIELD, "owner/Cls", "FIELD", "Ljava/lang/Object;");
        LabelNode label = new LabelNode();
        JumpInsnNode jump = new JumpInsnNode(Opcodes.IF_ACMPNE, label);
        insns.add(field);
        insns.add(jump);
        insns.add(label);

        tx.transform(mn, null);

        assertThat(insns.getFirst()).isInstanceOf(TypeInsnNode.class);
        TypeInsnNode type = (TypeInsnNode) insns.getFirst();
        assertThat(type.desc).isEqualTo("pkg/Replacement");

        JumpInsnNode newJump = (JumpInsnNode) insns.get(1);
        assertThat(newJump.getOpcode()).isEqualTo(Opcodes.IFEQ);
        assertThat(newJump.label).isSameAs(label);
    }

    @Test
    @DisplayName("transform: leaves instructions unchanged when jump opcode is not a reference compare")
    void transform_leavesNonTargetJumpUntouched() {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");

        MethodNode mn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "m3", "()V", null, null);
        InsnList insns = mn.instructions;
        FieldInsnNode field = new FieldInsnNode(Opcodes.GETSTATIC, "owner/Cls", "FIELD", "Ljava/lang/Object;");
        LabelNode label = new LabelNode();
        // Use IFNULL which is not targeted by the transformer
        JumpInsnNode jump = new JumpInsnNode(Opcodes.IFNULL, label);
        insns.add(field);
        insns.add(jump);
        insns.add(label);

        tx.transform(mn, null);

        assertThat(insns.getFirst()).isInstanceOf(FieldInsnNode.class);
        assertThat(((FieldInsnNode) insns.getFirst()).owner).isEqualTo("owner/Cls");
        assertThat(insns.get(1)).isInstanceOf(JumpInsnNode.class);
        assertThat(((JumpInsnNode) insns.get(1)).getOpcode()).isEqualTo(Opcodes.IFNULL);
    }

    @Test
    @DisplayName("transform: leaves instructions unchanged when field access does not match owner/name")
    void transform_leavesNonMatchingFieldUntouched() {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");

        MethodNode mn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "m4", "()V", null, null);
        InsnList insns = mn.instructions;
        // Wrong owner and name
        FieldInsnNode field = new FieldInsnNode(Opcodes.GETSTATIC, "other/Owner", "OTHER", "Ljava/lang/Object;");
        LabelNode label = new LabelNode();
        JumpInsnNode jump = new JumpInsnNode(Opcodes.IF_ACMPEQ, label);
        insns.add(field);
        insns.add(jump);
        insns.add(label);

        tx.transform(mn, null);

        assertThat(insns.getFirst()).isInstanceOf(FieldInsnNode.class);
        assertThat(((FieldInsnNode) insns.getFirst()).owner).isEqualTo("other/Owner");
        assertThat(((FieldInsnNode) insns.getFirst()).name).isEqualTo("OTHER");
        assertThat(((JumpInsnNode) insns.get(1)).getOpcode()).isEqualTo(Opcodes.IF_ACMPEQ);
    }

    @Test
    @DisplayName("transform: does not transform when previous instruction is not a field access")
    void transform_doesNotTransformWhenPrevIsNotField() {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");

        MethodNode mn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "m5", "()V", null, null);
        InsnList insns = mn.instructions;
        LabelNode target = new LabelNode();
        // Put a label (non-field) before the jump
        insns.add(new LabelNode());
        JumpInsnNode jump = new JumpInsnNode(Opcodes.IF_ACMPEQ, target);
        insns.add(jump);
        insns.add(target);

        tx.transform(mn, null);

        // Ensure no transformation happened: first instruction is still a Label, then the original IF_ACMPEQ jump
        assertThat(insns.getFirst()).isInstanceOf(LabelNode.class);
        assertThat(insns.get(1)).isInstanceOf(JumpInsnNode.class);
        assertThat(((JumpInsnNode) insns.get(1)).getOpcode()).isEqualTo(Opcodes.IF_ACMPEQ);
    }

    @Test
    @DisplayName("isTargetJumpOpcode: true only for IF_ACMPEQ and IF_ACMPNE")
    void isTargetJumpOpcode_behaviour() throws Exception {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");
        Method m = getPrivateMethod(ReplaceFieldComparisonWithInstanceOf.class, "isTargetJumpOpcode", int.class);

        assertThat((boolean) m.invoke(tx, Opcodes.IF_ACMPEQ)).isTrue();
        assertThat((boolean) m.invoke(tx, Opcodes.IF_ACMPNE)).isTrue();
        assertThat((boolean) m.invoke(tx, Opcodes.IFEQ)).isFalse();
        assertThat((boolean) m.invoke(tx, Opcodes.IFNULL)).isFalse();
        assertThat((boolean) m.invoke(tx, Opcodes.GOTO)).isFalse();
    }

    @Test
    @DisplayName("isTargetFieldAccess: matches GETSTATIC/GETFIELD with correct owner and name")
    void isTargetFieldAccess_behaviour() throws Exception {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");
        Method m = getPrivateMethod(ReplaceFieldComparisonWithInstanceOf.class, "isTargetFieldAccess", FieldInsnNode.class);

        FieldInsnNode getStaticMatch = new FieldInsnNode(Opcodes.GETSTATIC, "owner/Cls", "FIELD", "Ljava/lang/Object;");
        FieldInsnNode getFieldMatch = new FieldInsnNode(Opcodes.GETFIELD, "owner/Cls", "FIELD", "Ljava/lang/Object;");
        FieldInsnNode wrongOwner = new FieldInsnNode(Opcodes.GETSTATIC, "other/Owner", "FIELD", "Ljava/lang/Object;");
        FieldInsnNode wrongName = new FieldInsnNode(Opcodes.GETSTATIC, "owner/Cls", "OTHER", "Ljava/lang/Object;");
        FieldInsnNode putStatic = new FieldInsnNode(Opcodes.PUTSTATIC, "owner/Cls", "FIELD", "Ljava/lang/Object;");

        assertThat((boolean) m.invoke(tx, getStaticMatch)).isTrue();
        assertThat((boolean) m.invoke(tx, getFieldMatch)).isTrue();
        assertThat((boolean) m.invoke(tx, wrongOwner)).isFalse();
        assertThat((boolean) m.invoke(tx, wrongName)).isFalse();
        assertThat((boolean) m.invoke(tx, putStatic)).isFalse();
    }

    @Test
    @DisplayName("applyTransformation: replaces nodes in-place with INSTANCEOF and flipped jump")
    void applyTransformation_replacesNodes() throws Exception {
        ReplaceFieldComparisonWithInstanceOf tx = newTransformer("owner/Cls", "FIELD", "pkg/Replacement");
        Method m = getPrivateMethod(ReplaceFieldComparisonWithInstanceOf.class, "applyTransformation", MethodNode.class, JumpInsnNode.class, FieldInsnNode.class);

        MethodNode mn = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "m6", "()V", null, null);
        InsnList insns = mn.instructions;
        FieldInsnNode field = new FieldInsnNode(Opcodes.GETSTATIC, "owner/Cls", "FIELD", "Ljava/lang/Object;");
        LabelNode label = new LabelNode();
        JumpInsnNode jump = new JumpInsnNode(Opcodes.IF_ACMPEQ, label);
        insns.add(field);
        insns.add(jump);
        insns.add(label);

        m.invoke(tx, mn, jump, field);

        assertThat(insns.getFirst()).isInstanceOf(TypeInsnNode.class);
        assertThat(((TypeInsnNode) insns.getFirst()).desc).isEqualTo("pkg/Replacement");
        assertThat(insns.get(1)).isInstanceOf(JumpInsnNode.class);
        assertThat(((JumpInsnNode) insns.get(1)).getOpcode()).isEqualTo(Opcodes.IFNE);
        assertThat(((JumpInsnNode) insns.get(1)).label).isSameAs(label);
    }
}
