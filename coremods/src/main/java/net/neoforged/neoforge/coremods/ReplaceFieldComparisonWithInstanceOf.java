/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.coremods;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces code such as {@code itemstack.getItem() == Items.CROSSBOW} with instanceof checks such
 * as {@code itemstack.getItem() instanceof CrossbowItem}.
 * This transformer targets a set of methods to replace the occurrence of a single field-comparison.
 */
public class ReplaceFieldComparisonWithInstanceOf implements ITransformer<MethodNode> {
    private static final Logger LOG = LoggerFactory.getLogger(ReplaceFieldComparisonWithInstanceOf.class);

    private final Set<Target<MethodNode>> targets;
    private final String fieldOwner;
    private final String fieldName;
    private final String replacementClassName;

    /**
     * @param fieldOwner           The class that owns {@code fieldName}
     * @param fieldName            The name of a field in {@code fieldOwner}
     * @param replacementClassName Reference comparisons against {@code fieldName} in {@code fieldOwner} are replaced
     *                             by instanceof checks against this class.
     * @param methodsToScan        The methods to scan
     */
    public ReplaceFieldComparisonWithInstanceOf(String fieldOwner,
            String fieldName,
            String replacementClassName,
            List<Target<MethodNode>> methodsToScan) {
        this.targets = Set.copyOf(methodsToScan);

        this.fieldOwner = fieldOwner;
        this.fieldName = fieldName;
        this.replacementClassName = replacementClassName;
    }

    @Override
    public TargetType<MethodNode> getTargetType() {
        return TargetType.METHOD;
    }

    @Override
    public Set<Target<MethodNode>> targets() {
        return targets;
    }

    @Override
    public MethodNode transform(MethodNode methodNode, ITransformerVotingContext votingContext) {
        int count = 0;
        for (AbstractInsnNode node = methodNode.instructions.getFirst(); node != null; node = node.getNext()) {
            // Guard Clause 1: The current node must be a reference comparison jump instruction.
            if (!(node instanceof JumpInsnNode jumpNode) || !isTargetJumpOpcode(jumpNode.getOpcode())) {
                continue;
            }

            // Guard Clause 2: The previous node must be a field access instruction that matches our target.
            AbstractInsnNode previousNode = node.getPrevious();
            if (!(previousNode instanceof FieldInsnNode fieldAccessNode) || !isTargetFieldAccess(fieldAccessNode)) {
                continue;
            }

            // If all guards have passed, apply the transformation.
            applyTransformation(methodNode, jumpNode, fieldAccessNode);
            count++;
        }

        if (count > 0) {
            LOG.trace("Transforming: {}. Replaced {} field comparison(s) with instanceof checks.", methodNode.name, count);
        }

        return methodNode;
    }

    /**
     * Checks if the jump instruction's opcode is one of the targeted reference comparisons.
     */
    private boolean isTargetJumpOpcode(int opcode) {
        return opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE;
    }

    /**
     * Checks if the field access instruction matches the exact field we want to replace.
     */
    private boolean isTargetFieldAccess(FieldInsnNode fieldAccessNode) {
        int opcode = fieldAccessNode.getOpcode();
        boolean isCorrectOpcode = opcode == Opcodes.GETSTATIC || opcode == Opcodes.GETFIELD;
        boolean isCorrectOwner = fieldAccessNode.owner.equals(this.fieldOwner);
        boolean isCorrectName = fieldAccessNode.name.equals(this.fieldName);
        return isCorrectOpcode && isCorrectOwner && isCorrectName;
    }

    /**
     * Applies the bytecode transformation, replacing the field comparison with an instanceof check.
     */
    private void applyTransformation(MethodNode methodNode, JumpInsnNode jumpNode, FieldInsnNode fieldAccessNode) {
        // Replace GETSTATIC/GETFIELD with INSTANCEOF
        methodNode.instructions.set(fieldAccessNode, new TypeInsnNode(Opcodes.INSTANCEOF, this.replacementClassName));
        // Convert the reference comparison (ACMPEQ/ACMPNE) to an integer/boolean comparison (NE/EQ)
        int newJumpOpcode = jumpNode.getOpcode() == Opcodes.IF_ACMPEQ ? Opcodes.IFNE : Opcodes.IFEQ;
        methodNode.instructions.set(jumpNode, new JumpInsnNode(newJumpOpcode, jumpNode.label));
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }
}
