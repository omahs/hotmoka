package takamaka.blockchain;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import takamaka.blockchain.request.AbstractJarStoreTransactionRequest;
import takamaka.blockchain.request.ConstructorCallTransactionRequest;
import takamaka.blockchain.request.GameteCreationTransactionRequest;
import takamaka.blockchain.request.JarStoreInitialTransactionRequest;
import takamaka.blockchain.request.JarStoreTransactionRequest;
import takamaka.blockchain.response.AbstractJarStoreTransactionResponse;
import takamaka.blockchain.response.ConstructorCallTransactionExceptionResponse;
import takamaka.blockchain.response.ConstructorCallTransactionFailedResponse;
import takamaka.blockchain.response.ConstructorCallTransactionResponse;
import takamaka.blockchain.response.ConstructorCallTransactionSuccessfulResponse;
import takamaka.blockchain.response.GameteCreationTransactionResponse;
import takamaka.blockchain.response.JarStoreInitialTransactionResponse;
import takamaka.blockchain.response.JarStoreTransactionFailedResponse;
import takamaka.blockchain.response.JarStoreTransactionResponse;
import takamaka.blockchain.response.JarStoreTransactionSuccessfulResponse;
import takamaka.blockchain.types.StorageType;
import takamaka.blockchain.values.BigIntegerValue;
import takamaka.blockchain.values.StorageReference;
import takamaka.blockchain.values.StorageValue;
import takamaka.lang.Event;
import takamaka.lang.InsufficientFundsError;
import takamaka.lang.OutOfGasError;
import takamaka.lang.Storage;
import takamaka.lang.Takamaka;
import takamaka.lang.ThrowsExceptions;
import takamaka.translator.Dummy;
import takamaka.translator.JarInstrumentation;
import takamaka.translator.Program;

/**
 * A generic implementation of a blockchain. Specific implementations can subclass this class
 * and just implement the abstract template methods. The rest of code should work instead
 * as a generic layer for all blockchain implementations.
 */
public abstract class AbstractBlockchain implements Blockchain {
	private static final String CONTRACT_NAME = "takamaka.lang.Contract";
	private static final String EXTERNALLY_OWNED_ACCOUNT_NAME = "takamaka.lang.ExternallyOwnedAccount";

	/**
	 * The maximal length of the name of a jar installed in this blockchain, including its suffix.
	 */
	public final static int MAX_JAR_NAME_LENGTH = 100;

	/**
	 * The events accumulated during the current transaction. This is reset at each transaction.
	 */
	private final List<Event> events = new ArrayList<>();

	/**
	 * A map from each storage reference to its deserialized object. This is needed in order to guarantee that
	 * repeated deserialization of the same storage reference yields the same object and can also
	 * work as an efficiency measure. This is reset at each transaction since each transaction uses
	 * a distinct class loader and each storage object keeps a reference to its class loader, as
	 * always in Java.
	 */
	private final Map<StorageReference, Storage> cache = new HashMap<>();

	/**
	 * The remaining amount of gas for the current transaction, not yet consumed.
	 */
	private BigInteger gas;
	/**
	 * A stack of available gas. When a sub-computation is started
	 * with a subset of the available gas, the latter is taken away from
	 * the current available gas and pushed on top of this stack.
	 */
	private final LinkedList<BigInteger> oldGas = new LinkedList<>();

	/**
	 * The class loader for the transaction currently being executed.
	 */
	private BlockchainClassLoader classLoader;	

	/**
	 * A comparator that puts updates in the order required for the parameter
	 * of the deserialization constructor of storage objects: fields of superclasses first;
	 * then follow the fields for the same class, ordered by name and then by the
	 * {@code toString()} of their type.
	 */
	private final Comparator<Update> updateComparator = new Comparator<Update>() {

		@Override
		public int compare(Update update1, Update update2) {
			FieldSignature field1 = update1.field;
			FieldSignature field2 = update2.field;

			try {
				String className1 = field1.definingClass.name;
				String className2 = field2.definingClass.name;

				if (className1.equals(className2)) {
					int diff = field1.name.compareTo(field2.name);
					if (diff != 0)
						return diff;
					else
						return field1.type.toString().compareTo(field2.type.toString());
				}

				Class<?> clazz1 = classLoader.loadClass(className1);
				Class<?> clazz2 = classLoader.loadClass(className2);
				if (clazz1.isAssignableFrom(clazz2)) // clazz1 superclass of clazz2
					return -1;
				else if (clazz2.isAssignableFrom(clazz1)) // clazz2 superclass of clazz1
					return 1;
				else
					throw new IllegalStateException("Updates are not on the same supeclass chain");
			}
			catch (ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
		}
	};

	// ABSTRACT TEMPLATE METHODS
	// Any implementation of a blockchain must implement the following and leave the rest unchanged

	/**
	 * Yields the reference to the transaction currently being executed.
	 * If no transaction is currently under execution, this is the reference to the
	 * next transaction that will be executed.
	 * 
	 * @return the reference
	 */
	public abstract TransactionReference getCurrentTransactionReference();

	/**
	 * Increases the internal reference to the transaction that will be executed next.
	 */
	protected abstract void stepToNextTransactionReference();

	/**
	 * Blockchain-specific implementation at the end of the successful creation of a gamete. Any blockchain implementation
	 * has here the opportunity to deal with the result of the creation.
	 * 
	 * @param takamakaBase the reference to the jar containing the basic Takamaka classes
	 * @param initialAmount the amount of coin provided to the gamete. This cannot be negative
	 * @param gamete the reference to the gamete that has been created
	 * @param updates the memory updates induced by the transaction. This includes at least
	 *                those that describe the new gamete
	 * @throws Exception if something goes wrong in this method. In that case, the gamete creation
	 *                   transaction will be aborted
	 */
	protected abstract void addGameteCreationTransactionInternal(Classpath takamakaBase, BigInteger initialAmount, StorageReference gamete, SortedSet<Update> updates) throws Exception;

	/**
	 * Blockchain-specific implementation at the end of the successful installation of a jar. Any blockchain implementation
	 * has here the opportunity to deal with the result of the installation.
	 * 
	 * @param caller the externally owned caller contract that pays for the transaction
	 * @param classpath the class path where the {@code caller} is interpreted
	 * @param jarName the name of the jar to install
	 * @param jar the jar to install
	 * @param instrumented the same {@code jar}, instrumented
	 * @param updates the updates induced by the execution of the transaction
	 * @param gas the maximal amount of gas that can be consumed by the transaction
	 * @param consumedGas the actual amount of gas that was consumed by the transaction
	 * @param dependencies the dependencies of the jar, already installed in this blockchain
	 * @throws Exception if something goes wrong. In that case, the transaction will be aborted
	 */
	protected abstract void addJarStoreTransactionInternal(StorageReference caller, Classpath classpath, String jarName, Path jar, Path instrumented, SortedSet<Update> updates, BigInteger gas, BigInteger consumedGas, Classpath... dependencies) throws Exception;

	/**
	 * Blockchain-specific implementation at the end of the successful execution of a constructor. Any blockchain implementation
	 * has here the opportunity to deal with the result of the construction.
	 *
	 * @param executor the thread that executed the constructor
	 * @throws Exception if anything goes wrong. In that case, the transaction will be aborted
	 */
	protected abstract void addConstructorCallTransactionInternal(CodeExecutor executor) throws Exception;

	/**
	 * Blockchain-specific implementation at the end of the successful execution of an instance method. Any blockchain implementation
	 * has here the opportunity to deal with the result of the execution.
	 *
	 * @param executor the thread that executed the method
	 * @throws Exception if anything goes wrong. In that case, the transaction will be aborted
	 */
	protected abstract void addInstanceMethodCallTransactionInternal(CodeExecutor executor) throws Exception;

	/**
	 * Blockchain-specific implementation at the end of the successful execution of a static method. Any blockchain implementation
	 * has here the opportunity to deal with the result of the execution.
	 *
	 * @param executor the thread that executed the method
	 * @throws Exception if anything goes wrong. In that case, the transaction will be aborted
	 */
	protected abstract void addStaticMethodCallTransactionInternal(CodeExecutor executor) throws Exception;

	/**
	 * Puts in the given set all the latest updates for the fields of eager type of the
	 * object at the given storage reference.
	 * 
	 * @param object the storage reference
	 * @param updates the set where the latest updates must be added
	 * @throws Exception if the operation fails
	 */
	protected abstract void collectEagerUpdatesFor(StorageReference object, Set<Update> updates) throws Exception;

	/**
	 * Yields the most recent update for the given field, of lazy type, of the object at given storage reference.
	 * Conceptually, this amounts to scanning backwards the blockchain, from its tip,
	 * looking for the latest update.
	 * 
	 * @param object the storage reference
	 * @param field the field whose update is being looked for
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	protected abstract Update getLastLazyUpdateFor(StorageReference object, FieldSignature field) throws Exception;

	/**
	 * Expands the given list with the dependent class paths, recursively.
	 * 
	 * @param classpath the class path whose dependencies must be added, recursively
	 * @param paths the list that gets expanded
	 * @throws Exception if the class paths cannot be found
	 */
	private void extractPathsRecursively(Classpath classpath, List<Path> paths) throws Exception {
		// if the class path is recursive, we consider its dependencies as well, recursively
		if (classpath.recursive) {
			TransactionRequest request = getRequestAt(classpath.transaction);
			if (!(request instanceof AbstractJarStoreTransactionRequest))
				throw new IllegalTransactionRequestException("classpath does not refer to a jar store transaction");

			Stream<Classpath> dependencies = ((AbstractJarStoreTransactionRequest) request).getDependencies();

			for (Classpath dependency: dependencies.toArray(Classpath[]::new))
				extractPathsRecursively(dependency, paths);
		}

		TransactionResponse response = getResponseAt(classpath.transaction);
		if (!(response instanceof AbstractJarStoreTransactionResponse))
			throw new IllegalTransactionRequestException("classpath does not refer to a successful jar store transaction");

		byte[] instrumentedJarBytes = ((AbstractJarStoreTransactionResponse) response).getInstrumentedJar();

		try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(instrumentedJarBytes))) {
			Path classpathElement = Files.createTempFile("classpath", "jar");
			paths.add(classpathElement);
			Files.copy(is, classpathElement, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// BLOCKCHAIN-AGNOSTIC IMPLEMENTATION

	/**
	 * Decreases the available gas by the given amount.
	 * 
	 * @param amount the amount of gas to consume
	 */
	public final void charge(BigInteger amount) {
		if (amount.signum() <= 0)
			throw new IllegalArgumentException("Gas can only decrease");
	
		if (gas.compareTo(amount) < 0)
			// we report how much gas is missing
			throw new OutOfGasError(amount.subtract(gas));
	
		gas = gas.subtract(amount);
	}

	/**
	 * Decreases the available gas by the given amount.
	 * 
	 * @param amount the amount of gas to consume
	 */
	public final void charge(long amount) {
		charge(BigInteger.valueOf(amount));
	}

	/**
	 * Decreases the available gas by the given amount.
	 * 
	 * @param amount the amount of gas to consume
	 */
	public final void charge(int amount) {
		charge(BigInteger.valueOf(amount));
	}

	/**
	 * Runs a given piece of code with a subset of the available gas.
	 * It first charges the given amount of gas. Then runs the code
	 * with the charged gas only. At its end, the remaining gas is added
	 * to the available gas to continue the computation.
	 * 
	 * @param amount the amount of gas provided to the code
	 * @param what the code to run
	 * @return the result of the execution of the code
	 * @throws OutOfGasError if there is not enough gas
	 * @throws Exception if the code runs into this exception
	 */
	public final <T> T withGas(BigInteger amount, Callable<T> what) throws Exception {
		charge(amount);
		oldGas.addFirst(gas);
		gas = amount;
	
		try {
			return what.call();
		}
		finally {
			gas = gas.add(oldGas.removeFirst());
		}
	}

	/**
	 * Adds an event to those occurred during the execution of the current transaction.
	 * 
	 * @param event the event
	 * @throws IllegalArgumentException if the event is {@code null}
	 */
	public final void event(Event event) {
		if (event == null)
			throw new IllegalArgumentException("Events cannot be null");

		events.add(event);
	}

	@Override
	public final JarStoreInitialTransactionResponse runJarStoreInitialTransaction(JarStoreInitialTransactionRequest request, TransactionReference where) throws TransactionException {
		return wrapInCaseOfException(() -> {
			//TODO: check if already initialized before where
			if (!request.getDependencies().allMatch(dependency -> dependency.transaction.isOlderThan(where)))
				throw new IllegalTransactionRequestException("A jar file can only depend on jars installed by older transactions");

			initTransaction(BigInteger.ZERO);

			// we transform the array of bytes into a real jar file
			Path original = Files.createTempFile("original", ".jar");
			Files.write(original, request.getJar());

			// we create a temporary file to hold the instrumented jar
			Path instrumented = Files.createTempFile("instrumented", ".jar");
			new JarInstrumentation(original, instrumented, mkProgram(original, request.getDependencies()));
			Files.delete(original);
			byte[] instrumentedBytes = Files.readAllBytes(instrumented);
			Files.delete(instrumented);
		
			return new JarStoreInitialTransactionResponse(instrumentedBytes);
		});
	}

	@Override
	public final TransactionReference addJarStoreInitialTransaction(JarStoreInitialTransactionRequest request) throws TransactionException {
		return wrapInCaseOfException(() -> {
			TransactionReference reference = getCurrentTransactionReference();
			expandBlockchainWith(request, runJarStoreInitialTransaction(request, reference));
			stepToNextTransactionReference();
			return reference;
		});
	}

	@Override
	public final GameteCreationTransactionResponse runGameteCreationTransaction(GameteCreationTransactionRequest request, TransactionReference where) throws TransactionException {
		return wrapInCaseOfException(() -> {
			//TODO: check if initialized

			if (request.initialAmount.signum() < 0)
				throw new IllegalTransactionRequestException("initialAmount must be non-negative");

			try (BlockchainClassLoader classLoader = this.classLoader = new BlockchainClassLoader(request.classpath)) {
				initTransaction(BigInteger.ZERO);
				// we create an initial gamete ExternallyOwnedContract and we fund it with the initial amount
				Class<?> gameteClass = classLoader.loadClass(EXTERNALLY_OWNED_ACCOUNT_NAME);
				Class<?> contractClass = classLoader.loadClass(CONTRACT_NAME);
				Storage gamete = (Storage) gameteClass.newInstance();
				// we set the balance field of the gamete
				Field balanceField = contractClass.getDeclaredField("balance");
				balanceField.setAccessible(true); // since the field is private
				balanceField.set(gamete, request.initialAmount);
				SortedSet<Update> updates = collectUpdates(null, null, null, gamete);
				StorageReference gameteRef = gamete.storageReference;

				return new GameteCreationTransactionResponse(updates, gameteRef);
			}
		});
	}

	public final StorageReference addGameteCreationTransaction(GameteCreationTransactionRequest request) throws TransactionException {
		return wrapInCaseOfException(() -> {
			GameteCreationTransactionResponse response = runGameteCreationTransaction(request, getCurrentTransactionReference());
			expandBlockchainWith(request, response);
			stepToNextTransactionReference();
			return response.gamete;
		});
	}

	@Override
	public final JarStoreTransactionResponse runJarStoreTransaction(JarStoreTransactionRequest request, TransactionReference where) throws TransactionException {
		return wrapInCaseOfException(() -> {
			//TODO: check if already initialized before where
			checkMinimalGas(request.gas);

			initTransaction(request.gas);
			Storage deserializedCaller = request.caller.deserialize(this);
			checkIsExternallyOwned(deserializedCaller);
			
			// we sell all gas first: what remains will be paid back at the end;
			// if the caller has not enough to pay for the whole gas, the transaction won't be executed
			decreaseBalance(deserializedCaller, request.gas);

			if (!request.getDependencies().allMatch(dependency -> dependency.transaction.isOlderThan(where)))
				throw new IllegalTransactionRequestException("A jar file can only depend on jars installed by older transactions");

			// before this line, an exception will abort the transaction and leave the blockchain unchanged;
			// after this line, the transaction will be added to the blockchain, possibly as a failed one

			try {
				charge(GasCosts.BASE_TRANSACTION_COST);
				charge(BigInteger.valueOf(request.getNumberOfDependencies()).multiply(GasCosts.GAS_PER_DEPENDENCY_OF_JAR));
				charge(BigInteger.valueOf((long) (((long) request.getJarSize()) * GasCosts.GAS_PER_BYTE_IN_JAR)));

				// we transform the array of bytes into a real jar file
				Path original = Files.createTempFile("original", "jar");
				Files.write(original, request.getJar());

				// we create a temporary file to hold the instrumented jar
				Path instrumented = Files.createTempFile("instrumented", "jar");
				new JarInstrumentation(original, instrumented, mkProgram(original, request.getDependencies()));
				Files.delete(original);
				byte[] instrumentedBytes = Files.readAllBytes(instrumented);
				Files.delete(instrumented);
				BigInteger consumedGas = request.gas.subtract(remainingGas());
				increaseBalance(deserializedCaller, remainingGas());
				SortedSet<Update> updates = collectUpdates(null, deserializedCaller, null, null);

				return new JarStoreTransactionSuccessfulResponse(instrumentedBytes, updates, consumedGas);
			}
			catch (Throwable t) {
				// we do not pay back the gas
				return new JarStoreTransactionFailedResponse(wrapAsTransactionException(t, "Failed transaction"), collectUpdates(null, deserializedCaller, null, null), request.gas);
			}
		});
	}

	@Override
	public final TransactionReference addJarStoreTransaction(JarStoreTransactionRequest request) throws TransactionException {
		return wrapInCaseOfException(() -> {
			TransactionReference reference = getCurrentTransactionReference();
			JarStoreTransactionResponse response = runJarStoreTransaction(request, reference);
			expandBlockchainWith(request, response);
			stepToNextTransactionReference();

			if (response instanceof JarStoreTransactionFailedResponse)
				throw ((JarStoreTransactionFailedResponse) response).cause;
			else
				return reference;
		});
	}

	@Override
	public final ConstructorCallTransactionResponse runConstructorCallTransaction(ConstructorCallTransactionRequest request, TransactionReference where) throws TransactionException {
		return wrapInCaseOfException(() -> {
			try (BlockchainClassLoader classLoader = this.classLoader = new BlockchainClassLoader(request.classpath)) {
				checkMinimalGas(request.gas);
				initTransaction(request.gas);
				Storage deserializedCaller = request.caller.deserialize(this);
				checkIsExternallyOwned(deserializedCaller);
				
				// we sell all gas first: what remains will be paid back at the end;
				// if the caller has not enough to pay for the whole gas, the transaction won't be executed
				BigInteger decreasedBalanceOfCaller = decreaseBalance(deserializedCaller, request.gas);

				// before this line, an exception will abort the transaction and leave the blockchain unchanged;
				// after this line, the transaction can be added to the blockchain, possibly as a failed one

				try {
					charge(GasCosts.BASE_TRANSACTION_COST);

					CodeExecutor executor = new ConstructorExecutor(request.classpath, request.constructor, request.caller, deserializedCaller, request.gas, request.getActuals().toArray(StorageValue[]::new));
					executor.start();
					executor.join();

					if (executor.exception instanceof InvocationTargetException) {
						increaseBalance(deserializedCaller, remainingGas());
						return new ConstructorCallTransactionExceptionResponse((Exception) executor.exception, executor.updates(), request.gas.subtract(remainingGas()));
					}

					if (executor.exception != null)
						throw executor.exception;

					increaseBalance(deserializedCaller, remainingGas());
					return new ConstructorCallTransactionSuccessfulResponse
						((StorageReference) StorageValue.serialize(executor.result), executor.updates(), request.gas.subtract(remainingGas()));
				}
				catch (Throwable t) {
					// we do not pay back the gas: the only update resulting from the transaction is one that withdraws all gas from the balance of the caller
					Set<Update> updates = new HashSet<>();
					updates.add(new Update(deserializedCaller.storageReference, FieldSignature.BALANCE_FIELD, new BigIntegerValue(decreasedBalanceOfCaller)));
					return new ConstructorCallTransactionFailedResponse(wrapAsTransactionException(t, "Failed transaction"), updates, request.gas);
				}
			}
		});
	}

	@Override
	public final StorageReference addConstructorCallTransaction(ConstructorCallTransactionRequest request) throws TransactionException, CodeExecutionException {
		return wrapInCaseOfException(() -> {
			TransactionReference reference = getCurrentTransactionReference();
			ConstructorCallTransactionResponse response = runConstructorCallTransaction(request, reference);
			expandBlockchainWith(request, response);
			stepToNextTransactionReference();

			if (response instanceof ConstructorCallTransactionFailedResponse)
				throw ((ConstructorCallTransactionFailedResponse) response).cause;
			else if (response instanceof ConstructorCallTransactionExceptionResponse)
				throw new CodeExecutionException("Constructor threw exception", ((ConstructorCallTransactionExceptionResponse) response).exception);
			else
				return ((ConstructorCallTransactionSuccessfulResponse) response).newObject;
		});
	}

	@Override
	public final StorageValue addInstanceMethodCallTransaction(StorageReference caller, BigInteger gas, Classpath classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionException, CodeExecutionException {
		return transaction(caller, gas, classpath, deserializedCaller -> new InstanceMethodExecutor(classpath, method, caller, deserializedCaller, gas, receiver, actuals));
	}

	@Override
	public final StorageValue addStaticMethodCallTransaction(StorageReference caller, BigInteger gas, Classpath classpath, MethodSignature method, StorageValue... actuals) throws TransactionException, CodeExecutionException {
		return transaction(caller, gas, classpath, deserializedCaller -> new StaticMethodExecutor(classpath, method, caller, deserializedCaller, gas, actuals));
	}

	// BLOCKCHAIN-AGNOSTIC IMPLEMENTATION
	
	private static void checkMinimalGas(BigInteger gas) throws IllegalTransactionRequestException {
		if (gas.compareTo(GasCosts.BASE_TRANSACTION_COST) < 0)
			throw new IllegalTransactionRequestException("Not enough gas to start the transaction");
	}

	private static <T> T wrapInCaseOfException(Callable<T> what) throws TransactionException {
		try {
			return what.call();
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	/**
	 * Expands the blockchain with a new topmost transaction.
	 * 
	 * @param request the request of the transaction
	 * @param response the response of the transaction
	 * @throws Exception if the expansion cannot be completed
	 */
	protected abstract void expandBlockchainWith(TransactionRequest request, TransactionResponse response) throws Exception;

	protected abstract TransactionRequest getRequestAt(TransactionReference reference) throws Exception;

	protected abstract TransactionResponse getResponseAt(TransactionReference reference) throws Exception;

	/**
	 * A class loader used to access the definition of the classes
	 * of Takamaka methods or constructors executed during a transaction.
	 */
	private class BlockchainClassLoader extends URLClassLoader implements AutoCloseable {

		/**
		 * The temporary files that hold the class path for a transaction.
		 */
		private final List<Path> classpathElements = new ArrayList<>();

		/**
		 * Builds the class loader for the given class path and its dependencies.
		 * 
		 * @param classpath the class path
		 * @throws IOException if a disk access error occurs
		 */
		private BlockchainClassLoader(Classpath classpath) throws Exception {
			// we initially build it without URLs
			super(new URL[0], classpath.getClass().getClassLoader());

			// then we add the URLs corresponding to the class path and its dependencies, recursively
			addURLs(classpath);
		}

		private void addURLs(Classpath classpath) throws Exception {
			// if the class path is recursive, we consider its dependencies as well, recursively
			if (classpath.recursive) {
				TransactionRequest request = getRequestAt(classpath.transaction);
				if (!(request instanceof AbstractJarStoreTransactionRequest))
					throw new IllegalTransactionRequestException("classpath does not refer to a jar store transaction");

				Stream<Classpath> dependencies = ((AbstractJarStoreTransactionRequest) request).getDependencies();
				for (Classpath dependency: dependencies.toArray(Classpath[]::new))
					addURLs(dependency);
			}

			TransactionResponse response = getResponseAt(classpath.transaction);
			if (!(response instanceof AbstractJarStoreTransactionResponse))
				throw new IllegalTransactionRequestException("classpath does not refer to a successful jar store transaction");

			byte[] instrumentedJarBytes = ((AbstractJarStoreTransactionResponse) response).getInstrumentedJar();

			try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(instrumentedJarBytes))) {
				Path classpathElement = Files.createTempFile("classpath", "jar");
				classpathElements.add(classpathElement);
				Files.copy(is, classpathElement, StandardCopyOption.REPLACE_EXISTING);

				// we add, for class loading, the jar containing the instrumented code
				addURL(classpathElement.toFile().toURI().toURL());
			}
		}

		@Override
		public void close() throws IOException {
			// we delete all paths elements that were used to build this class loader
			for (Path classpathElement: classpathElements)
				Files.delete(classpathElement);

			super.close();
		}
	}

	/**
	 * The thread that executes a constructor or method.
	 */
	private interface ExecutorProducer {
		CodeExecutor produce(Storage deserializedCaller) throws Exception;
	}

	/**
	 * Performs a constructor or method call transaction.
	 * 
	 * @param caller the externally owned caller contract that pays for the execution
	 * @param gas the maximal amount of gas that can be consumed by the transaction
	 * @param classpath the class path where the {@code caller} is interpreted
	 * @param executorProducer the thread that executes the constructor or method
	 * @return the result of the execution of the method or the object created by the constructor. For {@code void} methods, returns {@code null}
	 * @throws TransactionException if there is an internal problem for the execution
	 * @throws CodeExecutionException if the execution threw an exception (that is not an {@link java.lang.Error} and the constructor or method is
	 *                                annotated as {@link takamaka.lang.ThrowsExceptions}
	 */
	//TODO: to be removed at the end
	private StorageValue transaction(StorageReference caller, BigInteger gas, Classpath classpath, ExecutorProducer executorProducer) throws TransactionException, CodeExecutionException {
		try (BlockchainClassLoader classLoader = this.classLoader = new BlockchainClassLoader(classpath)) {
			initTransaction(gas);
			Storage deserializedCaller = caller.deserialize(this);
			checkIsExternallyOwned(deserializedCaller);
			decreaseBalance(deserializedCaller, gas);
			charge(GasCosts.BASE_TRANSACTION_COST);

			CodeExecutor executor = executorProducer.produce(deserializedCaller);
			executor.start();
			executor.join();

			if (executor.exception instanceof TransactionException)
				throw (TransactionException) executor.exception;

			StorageValue result = executor.exception == null ? StorageValue.serialize(executor.result) : null;
			increaseBalance(deserializedCaller, remainingGas());
			executor.addTransactionInternal();
			stepToNextTransactionReference();

			if (executor.exception != null)
				throw new CodeExecutionException("Code execution threw exception", executor.exception);
			else
				return result;
		}
		catch (CodeExecutionException e) {
			throw e; // do not wrap into a TransactionException
		}
		catch (Throwable t) {
			//TODO we should register the update to the balance of the caller
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	/**
	 * Deserializes the given storage reference from the blockchain. It first checks in a cache if the
	 * same reference has been already deserialized during the current transaction and in such a case yeilds
	 * the same object. Otherwise, it calls method {@link takamaka.blockchain.AbstractBlockchain#deserializeAnew(StorageReference)}
	 * and yields the resulting object.
	 * 
	 * @param reference the storage reference to deserialize
	 * @return the resulting storage object
	 */
	public final Storage deserialize(StorageReference reference) {
		return cache.computeIfAbsent(reference, this::deserializeAnew);
	}

	/**
	 * Deserializes the given storage reference from the blockchain.
	 * 
	 * @param reference the storage reference to deserialize
	 * @return the resulting storage object
	 */
	private Storage deserializeAnew(StorageReference reference) {
		try {
			SortedSet<Update> updates = new TreeSet<>(updateComparator);
			collectEagerUpdatesFor(reference, updates);
	
			Optional<Update> classTag = updates.stream()
					.filter(Update::isClassTag)
					.findAny();
	
			if (!classTag.isPresent())
				throw new IllegalStateException("No class tag found for " + reference);
	
			String className = classTag.get().field.definingClass.name;
			List<Class<?>> formals = new ArrayList<>();
			List<Object> actuals = new ArrayList<>();
			// the constructor for deserialization has a first parameter
			// that receives the storage reference of the object
			formals.add(StorageReference.class);
			actuals.add(reference);
	
			for (Update update: updates)
				if (!update.isClassTag()) {
					formals.add(update.field.type.toClass(this));
					actuals.add(update.value.deserialize(this));
				}
	
			Class<?> clazz = classLoader.loadClass(className);
			Constructor<?> constructor = clazz.getConstructor(formals.toArray(new Class<?>[formals.size()]));
			return (Storage) constructor.newInstance(actuals.toArray(new Object[actuals.size()]));
		}
		catch (Exception e) {
			throw new DeserializationError(e);
		}
	}

	/**
	 * Yields the latest value for the given field, of lazy type, of the given storage reference.
	 * Conceptually, this method goes backwards from the tip of the blockchain, looking for the latest
	 * update of the given field. This can be of course made more efficient. For instance, {@code final}
	 * fields can only be looked for in the transaction where the reference was created.
	 * 
	 * @param reference the storage reference
	 * @param field the field, of lazy type
	 * @return the value of the field
	 * @throws Exception if the look up fails
	 */
	public final Object deserializeLastLazyUpdateFor(StorageReference reference, FieldSignature field) throws Exception {
		return getLastLazyUpdateFor(reference, field).value.deserialize(this);
	}

	/**
	 * Yields the class with the given name for the current transaction.
	 * 
	 * @param name the name of the class
	 * @return the class, if any
	 * @throws ClassNotFoundException if the class cannot be found for the current transaction
	 */
	public final Class<?> loadClass(String name) throws ClassNotFoundException {
		return classLoader.loadClass(name);
	}

	/**
	 * Sells the given amount of gas to the given externally owned account.
	 * 
	 * @param eoa the reference to the externally owned account
	 * @param gas the gas to sell
	 * @return the balance of the contract after paying the given amount of gas
	 * @throws IllegalTransactionRequestException if the externally owned account does not have funds
	 *                                            for buying the given amount of gas
	 * @throws InsufficientFundsError if the account has not enough money to pay for the gas
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private static BigInteger decreaseBalance(Storage eoa, BigInteger gas)
			throws IllegalTransactionRequestException, ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
	
		BigInteger delta = GasCosts.toCoin(gas);
		Class<?> contractClass = eoa.getClass().getClassLoader().loadClass(CONTRACT_NAME);
		Field balanceField = contractClass.getDeclaredField("balance");
		balanceField.setAccessible(true); // since the field is private
		BigInteger previousBalance = (BigInteger) balanceField.get(eoa);
		if (previousBalance.compareTo(delta) < 0)
			throw new IllegalTransactionRequestException("Caller has not enough funds to buy " + gas + " units of gas");

		BigInteger result = previousBalance.subtract(delta);
		balanceField.set(eoa, result);
		return result;
	}

	/**
	 * Buys back the given amount of gas from the given externally owned account.
	 * 
	 * @param eoa the reference to the externally owned account
	 * @param gas the gas to buy back
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private static void increaseBalance(Storage eoa, BigInteger gas)
			throws ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
	
		BigInteger delta = GasCosts.toCoin(gas);
		Class<?> contractClass = eoa.getClass().getClassLoader().loadClass(CONTRACT_NAME);
		Field balanceField = contractClass.getDeclaredField("balance");
		balanceField.setAccessible(true); // since the field is private
		BigInteger previousBalance = (BigInteger) balanceField.get(eoa);
		balanceField.set(eoa, previousBalance.add(delta));
	}

	/**
	 * Checks if the given object is an externally owned account or subclass.
	 * 
	 * @param object the object to check
	 * @throws IllegalTransactionRequestException if the object is not an externally owned account
	 * @throws ClassNotFoundException if the {@link takamaka.lang.ExternallyOwnedAccount} class cannot be found
	 *                                in the class path of the transaction
	 */
	private void checkIsExternallyOwned(Storage object) throws ClassNotFoundException, IllegalTransactionRequestException {
		Class<?> eoaClass = classLoader.loadClass(EXTERNALLY_OWNED_ACCOUNT_NAME);
		if (!eoaClass.isAssignableFrom(object.getClass()))
			throw new IllegalTransactionRequestException("Only an externally owned contract can start a transaction");
	}

	/**
	 * Collects all updates reachable from the actuals or from the caller, receiver or result of a method call.
	 * 
	 * @param actuals the actuals; only {@code Storage} are relevant; this might be {@code null}
	 * @param caller the caller of an {@code @@Entry} method; this might be {@code null}
	 * @param receiver the receiver of the call; this might be {@code null}
	 * @param result the result; relevant only if {@code Storage}
	 * @return the ordered updates
	 */
	private static SortedSet<Update> collectUpdates(Object[] actuals, Storage caller, Storage receiver, Object result) {
		List<Storage> potentiallyAffectedObjects = new ArrayList<>();
		if (caller != null)
			potentiallyAffectedObjects.add(caller);
		if (receiver != null)
			potentiallyAffectedObjects.add(receiver);
		if (result instanceof Storage)
			potentiallyAffectedObjects.add((Storage) result);

		if (actuals != null)
			for (Object actual: actuals)
				if (actual instanceof Storage)
					potentiallyAffectedObjects.add((Storage) actual);

		Set<StorageReference> seen = new HashSet<>();
		SortedSet<Update> updates = new TreeSet<>();
		potentiallyAffectedObjects.forEach(storage -> storage.updates(updates, seen));

		return updates;
	}

	/**
	 * The thread that executes a constructor or method of a storage object. It creates the class loader
	 * from the class path and deserializes receiver and actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	public abstract class CodeExecutor extends Thread {

		/**
		 * The exception resulting from the execution of the method or constructor, if any.
		 * This is {@code null} if the execution completed without exception.
		 */
		//TODO: probably removed at the end
		protected Throwable exception;

		/**
		 * The resulting value for methods or the created object for constructors.
		 * This is {@code null} if the execution completed with an exception or
		 * if the method actually returned {@code null}.
		 */
		protected Object result;

		/**
		 * The class path that must be sued to find the definition of the classes.
		 */
		public final Classpath classpath;

		/**
		 * The caller, that pays for the execution.
		 */
		public final StorageReference caller;

		/**
		 * The deserialized caller.
		 */
		protected final Storage deserializedCaller;

		/**
		 * The gas provided to the call.
		 */
		public final BigInteger gas;

		/**
		 * The method or constructor that is being called.
		 */
		public final CodeSignature methodOrConstructor;
		
		/**
		 * The receiver of a method call. This is {@code null} for static methods and constructors.
		 */
		public final StorageReference receiver;

		/**
		 * The deserialized receiver of a method call. This is {@code null} for static methods and constructors.
		 */
		protected final Storage deserializedReceiver; // it might be null

		/**
		 * The actual arguments of the call.
		 */
		private final StorageValue[] actuals;

		/**
		 * The deserialized actual arguments of the call.
		 */
		protected final Object[] deserializedActuals;

		/**
		 * Builds the executor of a method or constructor.
		 * 
		 * @param classpath the class path that must be used to find the classes during the execution of the method or constructor
		 * @param classLoader the class loader that must be used to find the classes during the execution of the method or constructor
		 * @param caller the caller, that pays for the execution
		 * @param deseralizedCaller the deserialized caller
		 * @param gas the gas provided for the execution
		 * @param methodOrConstructor the method or constructor to call
		 * @param receiver the receiver of the call, if any. This is {@code null} for constructors and static methods
		 * @param actuals the actuals provided to the method or constructor
		 */
		private CodeExecutor(Classpath classpath, StorageReference caller, Storage deseralizedCaller, BigInteger gas, CodeSignature methodOrConstructor, StorageReference receiver, StorageValue... actuals) {
			this.classpath = classpath;
			this.caller = caller;
			this.deserializedCaller = deseralizedCaller;
			this.gas = gas;
			this.methodOrConstructor = methodOrConstructor;
			this.receiver = receiver;
			this.deserializedReceiver = receiver != null ? receiver.deserialize(AbstractBlockchain.this) : null;
			this.actuals = actuals;
			this.deserializedActuals = deserializeActuals();

			setContextClassLoader(new ClassLoader(classLoader.getParent()) {

				@Override
				public Class<?> loadClass(String name) throws ClassNotFoundException {
					return classLoader.loadClass(name);
				}
			});
		}

		/**
		 * Deserializes the actual arguments.
		 * 
		 * @return the deserialized actuals
		 */
		private Object[] deserializeActuals() {
			Object[] deserialized = new Object[actuals.length];
			for (int pos = 0; pos < actuals.length; pos++)
				deserialized[pos] = actuals[pos].deserialize(AbstractBlockchain.this);
			
			return deserialized;
		}

		/**
		 * Yields the actuals passed to the method or constructor.
		 * 
		 * @return the actuals
		 */
		public final StorageValue[] getActuals() {
			return actuals;
		}

		/**
		 * Yields the exception generated by the execution of the method or constructor.
		 * 
		 * @return the exception, if any. This is {@code null} if the execution ended without exception
		 */
		public final Throwable getException() {
			return exception;
		}

		/**
		 * Yields the serialized result of the method or constructor call.
		 *
		 * @return the serialized result. This is {@code null} if the execution ended into an exception.
		 *         If method returns {@code null} or is {@code void}, this is {@link takamaka.blockchain.values.NullValue#INSTANCE}.
		 */
		public final StorageValue getResult() {
			return exception == null ? StorageValue.serialize(result) : null;
		}

		/**
		 * Yields the updates resulting from the execution of the method or constructor.
		 * 
		 * @return the updates
		 */
		public final SortedSet<Update> updates() {
			return collectUpdates(deserializedActuals, deserializedCaller, deserializedReceiver, result);
		}

		/**
		 * Yields the events generated by the execution of the method or constructor.
		 * 
		 * @return the events
		 */
		public final List<Event> events() {
			return events;
		}

		/**
		 * Yields the gas consumed for the execution of the method or constructor.
		 * 
		 * @return the gas consumed
		 */
		public final BigInteger gasConsumed() {
			return gas.subtract(remainingGas());
		}

		/**
		 * Yields the classes of the formal arguments of the method or constructor.
		 * 
		 * @return the array of classes, in the same order as the formals
		 * @throws ClassNotFoundException if some class cannot be found
		 */
		protected Class<?>[] formalsAsClass() throws ClassNotFoundException {
			List<Class<?>> classes = new ArrayList<>();
			for (StorageType type: methodOrConstructor.formals().collect(Collectors.toList()))
				classes.add(type.toClass(AbstractBlockchain.this));

			return classes.toArray(new Class<?>[classes.size()]);
		}

		/**
		 * Yields the classes of the formal arguments of the method or constructor, assuming that it is
		 * and {@link takamaka.lang.Entry}. Entries are instrumented with the addition of
		 * trailing contract formal (the caller) and of a dummy type.
		 * 
		 * @return the array of classes, in the same order as the formals
		 * @throws ClassNotFoundException if some class cannot be found
		 */
		protected Class<?>[] formalsAsClassForEntry() throws ClassNotFoundException {
			List<Class<?>> classes = new ArrayList<>();
			for (StorageType type: methodOrConstructor.formals().collect(Collectors.toList()))
				classes.add(type.toClass(AbstractBlockchain.this));

			classes.add(classLoader.loadClass(CONTRACT_NAME));
			classes.add(Dummy.class);

			return classes.toArray(new Class<?>[classes.size()]);
		}

		/**
		 * Adds to the actual parameters the implicit actuals that are passed
		 * to {@link takamaka.lang.Entry} methods or constructors. They are the caller of
		 * the entry and {@code null} for the dummy argument.
		 * 
		 * @return the resulting actual parameters
		 */
		protected Object[] addExtraActualsForEntry() {
			int al = deserializedActuals.length;
			Object[] result = new Object[al + 2];
			System.arraycopy(deserializedActuals, 0, result, 0, al);
			result[al] = deserializedCaller;
			result[al + 1] = null; // Dummy is not used

			return result;
		}

		/**
		 * Stores in the blockchain the result of the execution of the transaction.
		 * 
		 * @throws Exception if anything goes wrong. In that case, the transaction will be aborted
		 */
		protected abstract void addTransactionInternal() throws Exception;
	}

	/**
	 * The thread that executes a constructor of a storage object. It creates the class loader
	 * from the class path and deserializes the actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private class ConstructorExecutor extends CodeExecutor {

		/**
		 * Builds the executor of a constructor.
		 * 
		 * @param classpath the class path that must be used to find the classes during the execution of the constructor
		 * @param constructor the constructor to call
		 * @param caller the caller, that pays for the execution
		 * @param deseralizedCaller the deserialized caller
		 * @param gas the gas provided for the execution
		 * @param actuals the actuals provided to the constructor
		 */
		private ConstructorExecutor(Classpath classpath, ConstructorSignature constructor, StorageReference caller, Storage deserializedCaller, BigInteger gas, StorageValue... actuals) {
			super(classpath, caller, deserializedCaller, gas, constructor, null, actuals);
		}

		@Override
		public void run() {
			try {
				Class<?> clazz = classLoader.loadClass(methodOrConstructor.definingClass.name);
				Constructor<?> constructorJVM;
				Object[] deserializedActuals;

				try {
					// we first try to call the constructor with exactly the parameter types explicitly provided
					constructorJVM = clazz.getConstructor(formalsAsClass());
					deserializedActuals = this.deserializedActuals;
				}
				catch (NoSuchMethodException e) {
					// if not found, we try to add the trailing types that characterize the @Entry constructors
					try {
						constructorJVM = clazz.getConstructor(formalsAsClassForEntry());
					}
					catch (NoSuchMethodException ee) {
						throw e; // the message must be relative to the constructor as the user sees it
					}

					deserializedActuals = addExtraActualsForEntry();
				}

				try {
					result = (Storage) constructorJVM.newInstance(deserializedActuals);
				}
				catch (InvocationTargetException e) {
					if (e.getCause() instanceof Exception && constructorJVM.isAnnotationPresent(ThrowsExceptions.class))
						exception = e;
					else
						exception = e.getCause();
				}
			}
			catch (Throwable t) {
				exception = t;
			}
		}

		@Override
		protected void addTransactionInternal() throws Exception {
			addConstructorCallTransactionInternal(this);
		}
	}

	/**
	 * The thread that executes an instance method of a storage object. It creates the class loader
	 * from the class path and deserializes receiver and actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private class InstanceMethodExecutor extends CodeExecutor {

		/**
		 * Builds the executor of an instance method.
		 * 
		 * @param classpath the class path that must be used to find the classes during the execution of the method
		 * @param method the method to call
		 * @param caller the caller, that pays for the execution
		 * @param deseralizedCaller the deserialized caller
		 * @param gas the gas provided for the execution
		 * @param receiver the receiver of the method
		 * @param actuals the actuals provided to the method
		 */
		private InstanceMethodExecutor(Classpath classpath, MethodSignature method, StorageReference caller, Storage deserializedCaller, BigInteger gas, StorageReference receiver, StorageValue... actuals) {
			super(classpath, caller, deserializedCaller, gas, method, receiver, actuals);
		}

		@Override
		public void run() {
			try {
				Class<?> clazz = classLoader.loadClass(methodOrConstructor.definingClass.name);
				String methodName = ((MethodSignature) methodOrConstructor).methodName;
				Method methodJVM;
				Object[] deserializedActuals;

				try {
					// we first try to call the method with exactly the parameter types explicitly provided
					methodJVM = clazz.getMethod(methodName, formalsAsClass());
					deserializedActuals = this.deserializedActuals;
				}
				catch (NoSuchMethodException e) {
					// if not found, we try to add the trailing types that characterize the @Entry methods
					try {
						methodJVM = clazz.getMethod(methodName, formalsAsClassForEntry());
					}
					catch (NoSuchMethodException ee) {
						throw e; // the message must be relative to the method as the user sees it
					}
					deserializedActuals = addExtraActualsForEntry();
				}

				if (Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("Cannot call a static method: use addStaticMethodCallTransaction instead");

				result = methodJVM.invoke(deserializedReceiver, deserializedActuals);
			}
			catch (InvocationTargetException e) {
				exception = e.getCause();
			}
			catch (Throwable t) {
				exception = wrapAsTransactionException(t, "Could not call the method");
			}
		}

		@Override
		protected void addTransactionInternal() throws Exception {
			addInstanceMethodCallTransactionInternal(this);
		}
	}

	/**
	 * The thread that executes a static method of a storage object. It creates the class loader
	 * from the class path and deserializes the actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private class StaticMethodExecutor extends CodeExecutor {

		/**
		 * Builds the executor of a static method.
		 * 
		 * @param classpath the class path that must be used to find the classes during the execution of the method
		 * @param method the method to call
		 * @param caller the caller, that pays for the execution
		 * @param deseralizedCaller the deserialized caller
		 * @param gas the gas provided for the execution
		 * @param actuals the actuals provided to the method
		 */
		private StaticMethodExecutor(Classpath classpath, MethodSignature method, StorageReference caller, Storage deserializedCaller, BigInteger gas, StorageValue... actuals) {
			super(classpath, caller, deserializedCaller, gas, method, null, actuals);
		}

		@Override
		public void run() {
			try {
				Class<?> clazz = classLoader.loadClass(methodOrConstructor.definingClass.name);
				Method methodJVM = clazz.getMethod(((MethodSignature) methodOrConstructor).methodName, formalsAsClass());

				if (!Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("Cannot call an instance method: use addInstanceMethodCallTransaction instead");

				result = methodJVM.invoke(null, deserializedActuals);
			}
			catch (InvocationTargetException e) {
				exception = e.getCause();
			}
			catch (Throwable t) {
				exception = wrapAsTransactionException(t, "Could not call the method");
			}
		}

		@Override
		protected void addTransactionInternal() throws Exception {
			addStaticMethodCallTransactionInternal(this);
		}
	}

	/**
	 * Yields the amount of gas still available to the
	 * currently executing transaction.
	 * 
	 * @return the remaining gas
	 */
	private BigInteger remainingGas() {
		return gas;
	}

	/**
	 * Builds the program that contains the classes of a jar and its dependencies.
	 * 
	 * @param jar the jar
	 * @param dependencies the dependencies
	 * @return the resulting program
	 * @throws Exception if the program cannot be built
	 */
	private Program mkProgram(Path jar, Stream<Classpath> dependencies) throws Exception {
		List<Path> result = new ArrayList<>();
		result.add(jar);

		for (Classpath dependency: dependencies.toArray(Classpath[]::new))
			extractPathsRecursively(dependency, result);

		Program program = new Program(result.stream());
		for (Path classpathElement: result)
			if (classpathElement != jar)
				Files.delete(classpathElement);

		return program;
	}

	/**
	 * Wraps the given throwable in a {@link takamaka.blockchain.TransactionException}, if it not
	 * already an instance of that exception.
	 * 
	 * @param t the throwable to wrap
	 * @param message the message added to the {@link takamaka.blockchain.TransactionException}, if wrapping occurs
	 * @return the wrapped or original exception
	 */
	private static TransactionException wrapAsTransactionException(Throwable t, String message) {
		if (t instanceof TransactionException)
			return (TransactionException) t;
		else
			return new TransactionException(message, t);
	}

	/**
	 * Initializes the state at the beginning of the execution of a new transaction
	 * 
	 * @param gas the amount of gas available for the transaction
	 */
	private void initTransaction(BigInteger gas) {
		Takamaka.init(AbstractBlockchain.this); // this blockchain will be used during the execution of the code
		events.clear();
		cache.clear();
		this.gas = gas;
		oldGas.clear();
	}
}