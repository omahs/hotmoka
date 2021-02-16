package io.takamaka.code.verification.internal.checksOnMethods;

import org.apache.bcel.Const;
import org.apache.bcel.generic.MethodGen;

import io.takamaka.code.verification.internal.CheckOnMethods;
import io.takamaka.code.verification.internal.VerifiedClassImpl;
import io.takamaka.code.verification.issues.InconsistentSelfChargedError;
import io.takamaka.code.verification.issues.SelfChargedNotAllowedError;

/**
 * A checks that {@code @@SelfCharged} is applied to instance public methods of contracts only.
 */
public class SelfChargedCodeIsInstancePublicMethodOfContractCheck extends CheckOnMethods {

	public SelfChargedCodeIsInstancePublicMethodOfContractCheck(VerifiedClassImpl.Verification builder, MethodGen method) {
		super(builder, method);

		if (annotations.isSelfCharged(className, methodName, methodArgs, methodReturnType))
			if (!allowSelfCharged)
				issue(new SelfChargedNotAllowedError(inferSourceFile(), methodName));
			else if (!isInPublicInstanceMethodOfContract())
				issue(new InconsistentSelfChargedError(inferSourceFile(), methodName));
	}

	private boolean isInPublicInstanceMethodOfContract() {
		return method.isPublic() && !method.isStatic() && !Const.CONSTRUCTOR_NAME.equals(method.getName()) && classLoader.isContract(className);
	}
}