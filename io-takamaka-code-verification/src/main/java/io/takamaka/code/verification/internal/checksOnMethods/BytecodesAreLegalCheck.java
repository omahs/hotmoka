package io.takamaka.code.verification.internal.checksOnMethods;

import org.apache.bcel.Const;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.JsrInstruction;
import org.apache.bcel.generic.MONITORENTER;
import org.apache.bcel.generic.MONITOREXIT;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.PUTSTATIC;
import org.apache.bcel.generic.RET;
import org.apache.bcel.generic.StoreInstruction;

import io.takamaka.code.verification.internal.CheckOnMethods;
import io.takamaka.code.verification.internal.VerifiedClassImpl;
import io.takamaka.code.verification.issues.IllegalJsrInstructionError;
import io.takamaka.code.verification.issues.IllegalPutstaticInstructionError;
import io.takamaka.code.verification.issues.IllegalRetInstructionError;
import io.takamaka.code.verification.issues.IllegalSynchronizationError;
import io.takamaka.code.verification.issues.IllegalUpdateOfLocal0Error;

/**
 * A check that the method has no unusual bytecodes, such as {@code jsr}, {@code ret}
 * or updates of local 0 in instance methods. Such bytecodes are allowed in
 * Java bytecode, although they are never generated by modern compilers. Takamaka forbids them
 * since they make code verification more difficult.
 */
public class BytecodesAreLegalCheck extends CheckOnMethods {

	public BytecodesAreLegalCheck(VerifiedClassImpl.Verification builder, MethodGen method) {
		super(builder, method);

		instructions().forEach(this::checkIfItIsIllegal);
	}

	private void checkIfItIsIllegal(InstructionHandle ih) {
		Instruction ins = ih.getInstruction();

		if (ins instanceof PUTSTATIC) {
			// static field updates are allowed inside the synthetic methods or static initializer,
			// for instance in an enumeration
			if (!method.isSynthetic() && !Const.STATIC_INITIALIZER_NAME.equals(methodName))
				issue(new IllegalPutstaticInstructionError(inferSourceFile(), methodName, lineOf(ih)));
		}
		else if (ins instanceof JsrInstruction)
			issue(new IllegalJsrInstructionError(inferSourceFile(), methodName, lineOf(ih)));
		else if (ins instanceof RET)
			issue(new IllegalRetInstructionError(inferSourceFile(), methodName, lineOf(ih)));
		else if (!method.isStatic() && ins instanceof StoreInstruction && ((StoreInstruction) ins).getIndex() == 0)
			issue(new IllegalUpdateOfLocal0Error(inferSourceFile(), methodName, lineOf(ih)));					
		else if (ins instanceof MONITORENTER || ins instanceof MONITOREXIT)
			issue(new IllegalSynchronizationError(inferSourceFile(), methodName, lineOf(ih)));
	}
}