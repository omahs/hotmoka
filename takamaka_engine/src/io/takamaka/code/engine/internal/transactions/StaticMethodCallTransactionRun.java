package io.takamaka.code.engine.internal.transactions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.StaticMethodCallTransactionRequest;
import io.hotmoka.beans.responses.MethodCallTransactionExceptionResponse;
import io.hotmoka.beans.responses.MethodCallTransactionFailedResponse;
import io.hotmoka.beans.responses.MethodCallTransactionResponse;
import io.hotmoka.beans.responses.MethodCallTransactionSuccessfulResponse;
import io.hotmoka.beans.responses.VoidMethodCallTransactionSuccessfulResponse;
import io.hotmoka.beans.updates.UpdateOfBalance;
import io.hotmoka.nodes.Node;
import io.takamaka.code.constants.Constants;
import io.takamaka.code.engine.SideEffectsInViewMethodException;
import io.takamaka.code.engine.internal.EngineClassLoaderImpl;

public class StaticMethodCallTransactionRun extends MethodCallTransactionRun<StaticMethodCallTransactionRequest> {
	private final EngineClassLoaderImpl classLoader;

	/**
	 * The response computed at the end of the transaction.
	 */
	private final MethodCallTransactionResponse response;

	public StaticMethodCallTransactionRun(StaticMethodCallTransactionRequest request, TransactionReference current, Node node) throws TransactionException {
		super(request, current, node);

		try (EngineClassLoaderImpl classLoader = new EngineClassLoaderImpl(request.classpath, this)) {
			this.classLoader = classLoader;
			this.deserializedCaller = deserializer.deserialize(request.caller);
			this.deserializedActuals = request.actuals().map(deserializer::deserialize).toArray(Object[]::new);
			checkIsExternallyOwned(deserializedCaller);
			// we sell all gas first: what remains will be paid back at the end;
			// if the caller has not enough to pay for the whole gas, the transaction won't be executed
			UpdateOfBalance balanceUpdateInCaseOfFailure = checkMinimalGas(request, deserializedCaller);
			chargeForCPU(node.getGasCostModel().cpuBaseTransactionCost());
			chargeForStorage(request);
			MethodCallTransactionResponse response = null;

			try {
				Method methodJVM = getMethod();

				if (!Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("cannot call an instance method");

				ensureWhiteListingOf(methodJVM, deserializedActuals);

				boolean isVoidMethod = methodJVM.getReturnType() == void.class;
				boolean isViewMethod = hasAnnotation(methodJVM, Constants.VIEW_NAME);

				try {
					result = methodJVM.invoke(null, deserializedActuals);
				}
				catch (InvocationTargetException e) {
					if (isCheckedForThrowsExceptions(e, methodJVM)) {
						chargeForStorage(new MethodCallTransactionExceptionResponse((Exception) e.getCause(), updates(), storageReferencesOfEvents(), gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage()));
						increaseBalance(deserializedCaller);
						response = new MethodCallTransactionExceptionResponse((Exception) e.getCause(), updates(), storageReferencesOfEvents(), gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage());
					}
					else
						throw e.getCause();
				}

				if (response == null) {
					if (isViewMethod && !onlyAffectedBalanceOfCaller())
						throw new SideEffectsInViewMethodException(method);

					if (isVoidMethod) {
						chargeForStorage(new VoidMethodCallTransactionSuccessfulResponse(updates(), storageReferencesOfEvents(), gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage()));
						increaseBalance(deserializedCaller);
						response = new VoidMethodCallTransactionSuccessfulResponse(updates(), storageReferencesOfEvents(), gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage());
					}
					else {
						chargeForStorage(new MethodCallTransactionSuccessfulResponse
							(serializer.serialize(result), updates(), storageReferencesOfEvents(), gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage()));
						increaseBalance(deserializedCaller);
						response = new MethodCallTransactionSuccessfulResponse
							(serializer.serialize(result), updates(), storageReferencesOfEvents(), gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage());
					}
				}
			}
			catch (Throwable t) {
				// we do not pay back the gas: the only update resulting from the transaction is one that withdraws all gas from the balance of the caller
				response = new MethodCallTransactionFailedResponse(wrapAsTransactionException(t), balanceUpdateInCaseOfFailure, gasConsumedForCPU(), gasConsumedForRAM(), gasConsumedForStorage(), gasConsumedForPenalty());
			}

			this.response = response;
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t);
		}
	}

	@Override
	public EngineClassLoaderImpl getClassLoader() {
		return classLoader;
	}

	@Override
	public MethodCallTransactionResponse getResponse() {
		return response;
	}
}