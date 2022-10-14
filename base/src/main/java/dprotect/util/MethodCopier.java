package dprotect.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import proguard.classfile.*;
import proguard.classfile.attribute.CodeAttribute;
import proguard.classfile.attribute.visitor.*;
import proguard.classfile.editor.*;
import proguard.classfile.instruction.Instruction;
import proguard.classfile.visitor.*;

import proguard.classfile.attribute.*;
import proguard.classfile.constant.*;
import proguard.classfile.instruction.visitor.*;
import proguard.classfile.constant.visitor.*;
import proguard.classfile.instruction.*;

import proguard.classfile.constant.Constant;


public class MethodCopier
implements   InstructionVisitor,
             MemberVisitor,
             AttributeVisitor,
             ConstantVisitor
{
    private static final Logger logger = LogManager.getLogger(MethodCopier.class);
    private final CodeAttributeEditor codeAttributeEditor = new CodeAttributeEditor();

    private CodeAttributeComposer composer;
    private ConstantPoolEditor constantPoolEditor;

    private MethodCopier(CodeAttributeComposer composer, ConstantPoolEditor constantPoolEditor)
    {
        this.composer           = composer;
        this.constantPoolEditor = constantPoolEditor;
    }


    // Implementations for MemberVisitor.
    @Override
    public void visitAnyMember(Clazz clazz, Member member) {}


    @Override
    public void visitProgramMethod(ProgramClass programClass, ProgramMethod programMethod)
    {
        programMethod.attributesAccept(programClass, this);
    }

    // Implementations for AttributeVisitor.
    @Override
    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}

    @Override
    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
        codeAttributeEditor.reset(codeAttribute.u4codeLength);
        codeAttribute.instructionsAccept(clazz, method, this);
        codeAttribute.accept(clazz, method, codeAttributeEditor);
    }


    // Implementations for InstructionVisitor.
    @Override
    public void visitAnyInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, Instruction instruction)
    {
        //logger.info("{}: {}", offset, instruction.toString());
        this.composer.appendInstruction(offset, instruction);
    }

    public void visitBranchInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, BranchInstruction branchInstruction)
    {
        //logger.info("[branch] {} {}", offset, branchInstruction.toString());
        this.composer.appendInstruction(offset, branchInstruction);
    }

    public void visitConstantInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, ConstantInstruction constantInstruction)
    {
        //logger.info("[constant] {} {}", offset, constantInstruction.toString());
        int constantIndex = constantInstruction.constantIndex;
        byte opcode = constantInstruction.opcode;
        switch (opcode) {
            case Instruction.OP_NEW:
                {
                    ProgramClass programClass = (ProgramClass) clazz;
                    ClassConstant ref = (ClassConstant)programClass.getConstant(constantIndex);
                    int idx = this.constantPoolEditor.addClassConstant(ref.getName(clazz), null);
                    this.composer.appendInstruction(offset, new ConstantInstruction(opcode, idx));
                    break;
                }

            case Instruction.OP_INVOKESPECIAL:
            case Instruction.OP_INVOKEVIRTUAL:
            case Instruction.OP_INVOKEDYNAMIC:
            case Instruction.OP_INVOKESTATIC:
                {
                    ProgramClass programClass = (ProgramClass) clazz;
                    MethodrefConstant ref = (MethodrefConstant)programClass.getConstant(constantIndex);
                    int idx = this.constantPoolEditor.addMethodrefConstant(
                            ref.getClassName(clazz),
                            ref.getName(clazz),
                            ref.getType(clazz),
                            null, null);
                    this.composer.appendInstruction(offset, new ConstantInstruction(opcode, idx));
                    break;
                }

            case Instruction.OP_LDC:
            case Instruction.OP_LDC_W:
                {
                    ProgramClass programClass = (ProgramClass) clazz;
                    Constant cst = programClass.getConstant(constantIndex);
                    int newIdx = 0;
                    switch (cst.getTag())
                    {
                        case Constant.STRING:
                            {
                                newIdx = this.constantPoolEditor.addStringConstant(programClass.getStringString(constantIndex));
                                break;
                            }
                        case Constant.FLOAT:
                            {
                                newIdx = this.constantPoolEditor.addFloatConstant(((FloatConstant)cst).getValue());
                                break;
                            }

                        case Constant.LONG:
                            {
                                newIdx = this.constantPoolEditor.addLongConstant(((LongConstant)cst).getValue());
                                break;
                            }

                        case Constant.DOUBLE:
                            {
                                newIdx = this.constantPoolEditor.addDoubleConstant(((DoubleConstant)cst).getValue());
                                break;
                            }
                        case Constant.UTF8:
                            {
                                newIdx = this.constantPoolEditor.addUtf8Constant(((Utf8Constant)cst).getString());
                                break;
                            }
                        case Constant.INTEGER:
                            {
                                newIdx = this.constantPoolEditor.addIntegerConstant(((IntegerConstant)cst).getValue());
                                break;
                            }
                        default:
                            {
                                logger.error("{} is not supported. Please consider openning an issue", cst.toString());
                                // TODO(romain): Better error handling
                                System.exit(1);
                            }

                    }
                    this.composer.appendInstruction(offset, new ConstantInstruction(opcode, newIdx));
                    break;
                }

        }
    }

    public static void copy(ProgramClass target, ProgramMethod targetMethod, Clazz srcClass, Method srcMethod) {
        ConstantPoolEditor poolEditor = new ConstantPoolEditor(target);
        CodeAttributeComposer composer = new CodeAttributeComposer();
        composer.beginCodeFragment(1000);
        srcMethod.accept(srcClass, new MethodCopier(composer, poolEditor));
        composer.endCodeFragment();
        composer.addCodeAttribute(target, targetMethod);
    }
}
