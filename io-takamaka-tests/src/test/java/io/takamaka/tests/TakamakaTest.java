package io.takamaka.tests;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InitializationTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.RedGreenGameteCreationTransactionRequest;
import io.hotmoka.beans.requests.StaticMethodCallTransactionRequest;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.requests.TransferTransactionRequest;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.nodes.InitializedNode;
import io.hotmoka.nodes.InitializedNodeWithHistory;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.Node.CodeSupplier;
import io.hotmoka.nodes.Node.JarSupplier;
import io.hotmoka.nodes.NodeWithHistory;
import io.takamaka.code.constants.Constants;
import io.takamaka.code.engine.AbstractNode;
import io.takamaka.code.verification.VerificationException;

public abstract class TakamakaTest {

	/**
	 * The node that gets created before starting running the tests.
	 * This node will hence be created only once and
	 * each test will decorate it into {@linkplain #node},
	 * with the addition of the jar and accounts that the test needs.
	 */
	private final static Node initialNode;

	/**
	 * The account that can be used to pay for the initialization of
	 * {@linkplain #initialNode} before each test runs.
	 */
	private final static StorageReference gamete;

	/**
	 * The node under test. This is a view of {@linkplain #initialNode},
	 * with the addition of a jar to test and of some initial accounts,
	 * recreated before each test.
	 */
	private InitializedNode node;

	/**
	 * The nonce of each externally owned account used in the test.
	 */
	private final Map<StorageReference, BigInteger> nonces = new HashMap<>();

	private final static Logger logger = LoggerFactory.getLogger(AbstractNode.class);

	@BeforeEach
	void logTestName(TestInfo testInfo) {
		logger.info("**** Starting test " + testInfo.getTestClass().get().getSimpleName() + "." + testInfo.getTestMethod().get().getName() + ": " + testInfo.getDisplayName());
	}

	public interface TestBody {
		public void run() throws Exception;
	}

	static {
		try {
			io.hotmoka.tendermint.Config config = new io.hotmoka.tendermint.Config.Builder().build();
			initialNode = io.hotmoka.tendermint.TendermintBlockchain.of(config);
			//io.hotmoka.memory.Config config = new io.hotmoka.memory.Config.Builder().build();
			//initialNode = io.hotmoka.memory.MemoryBlockchain.of(config);

			TransactionReference takamakaCode = initialNode.addJarStoreInitialTransaction(new JarStoreInitialTransactionRequest(Files.readAllBytes(Paths.get("../io-takamaka-code/target/io-takamaka-code-1.0.jar"))));
			// the gamete has both red and green coins, enough for all tests
			gamete = initialNode.addRedGreenGameteCreationTransaction(new RedGreenGameteCreationTransactionRequest(takamakaCode, BigInteger.valueOf(999_999_999).pow(5), BigInteger.valueOf(999_999_999).pow(5)));
			StorageReference manifest = initialNode.addConstructorCallTransaction(new ConstructorCallTransactionRequest
				(gamete, BigInteger.ZERO, BigInteger.valueOf(10_000), BigInteger.ZERO, takamakaCode, new ConstructorSignature(Constants.MANIFEST_NAME, ClassType.RGEOA), gamete));
			initialNode.addInitializationTransaction(new InitializationTransactionRequest(takamakaCode, manifest));
			System.out.println("manifest: " + initialNode.getManifest());
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}

	protected final void setNode(BigInteger... coins) throws TransactionRejectedException, TransactionException, CodeExecutionException, IOException {
		if (initialNode instanceof NodeWithHistory)
			this.node = InitializedNodeWithHistory.of((NodeWithHistory) initialNode, gamete, coins);
		else
			this.node = InitializedNode.of(initialNode, gamete, coins);
	}

	protected final void setNodeRedGreen(BigInteger... coins) throws TransactionRejectedException, TransactionException, CodeExecutionException, IOException {
		if (initialNode instanceof NodeWithHistory)
			this.node = InitializedNodeWithHistory.ofRedGreen((NodeWithHistory) initialNode, gamete, coins);
		else
			this.node = InitializedNode.ofRedGreen(initialNode, gamete, coins);
	}

	protected final void setNode(String jar, BigInteger... coins) throws TransactionRejectedException, TransactionException, CodeExecutionException, IOException {
		if (initialNode instanceof NodeWithHistory)
			this.node = InitializedNodeWithHistory.of((NodeWithHistory) initialNode, gamete, pathOfExample(jar), coins);
		else
			this.node = InitializedNode.of(initialNode, gamete, pathOfExample(jar), coins);
	}

	protected final void setNodeRedGreen(String jar, BigInteger... coins) throws TransactionRejectedException, TransactionException, CodeExecutionException, IOException {
		if (initialNode instanceof NodeWithHistory)
			this.node = InitializedNodeWithHistory.ofRedGreen((NodeWithHistory) initialNode, gamete, pathOfExample(jar), coins);
		else
			this.node = InitializedNode.ofRedGreen(initialNode, gamete, pathOfExample(jar), coins);
	}

	protected final TransactionReference takamakaCode() {
		return node.getTakamakaCode();
	}

	protected final TransactionReference jar() {
		return node.jar().get();
	}

	protected final StorageReference account(int i) {
		return node.account(i);
	}

	protected final TransactionRequest<?> getRequestAt(TransactionReference reference) {
		return ((NodeWithHistory) node).getRequestAt(reference);
	}

	protected final TransactionReference addJarStoreInitialTransaction(byte[] jar, TransactionReference... dependencies) throws TransactionException, TransactionRejectedException {
		return node.addJarStoreInitialTransaction(new JarStoreInitialTransactionRequest(jar, dependencies));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final TransactionReference addJarStoreTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, byte[] jar, TransactionReference... dependencies) throws TransactionException, TransactionRejectedException {
		return node.addJarStoreTransaction(new JarStoreTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, jar, dependencies));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageReference addConstructorCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, ConstructorSignature constructor, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.addConstructorCallTransaction(new ConstructorCallTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, constructor, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue addInstanceMethodCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, method, receiver, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue addStaticMethodCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, method, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final void addTransferTransaction(StorageReference caller, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, int howMuch) throws TransactionRejectedException, TransactionException, CodeExecutionException {
		node.addInstanceMethodCallTransaction(new TransferTransactionRequest(caller, getNonceOf(caller), gasPrice, classpath, receiver, howMuch));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue runViewInstanceMethodCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.runViewInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(caller, BigInteger.ZERO, gasLimit, gasPrice, classpath, method, receiver, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue runViewStaticMethodCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.runViewStaticMethodCallTransaction(new StaticMethodCallTransactionRequest(caller, BigInteger.ZERO, gasLimit, gasPrice, classpath, method, actuals));
	}

	protected final JarSupplier postJarStoreTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, byte[] jar, TransactionReference... dependencies) throws TransactionRejectedException {
		return node.postJarStoreTransaction(new JarStoreTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, jar, dependencies));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final CodeSupplier<StorageValue> postInstanceMethodCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionRejectedException {
		return node.postInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, method, receiver, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final CodeSupplier<StorageValue> postTransferTransaction(StorageReference caller, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, int howMuch) throws TransactionRejectedException {
		return node.postInstanceMethodCallTransaction(new TransferTransactionRequest(caller, getNonceOf(caller), gasPrice, classpath, receiver, howMuch));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final CodeSupplier<StorageReference> postConstructorCallTransaction(StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, ConstructorSignature constructor, StorageValue... actuals) throws TransactionRejectedException {
		return node.postConstructorCallTransaction(new ConstructorCallTransactionRequest(caller, getNonceOf(caller), gasLimit, gasPrice, classpath, constructor, actuals));
	}

	protected static byte[] bytesOf(String fileName) throws IOException {
		return Files.readAllBytes(pathOfExample(fileName));
	}

	protected static Path pathOfExample(String fileName) {
		return Paths.get("../io-takamaka-examples/target/io-takamaka-examples-1.0-" + fileName);
	}

	protected static void throwsTransactionExceptionWithCause(Class<? extends Throwable> expected, TestBody what) {
		try {
			what.run();
		}
		catch (TransactionException e) {
			if (e.getMessage().startsWith(expected.getName()))
				return;

			fail("wrong cause: expected " + expected.getName() + " but got " + e.getMessage());
		}
		catch (Exception e) {
			fail("wrong exception: expected " + TransactionException.class.getName() + " but got " + e.getClass().getName());
		}

		fail("no exception: expected " + TransactionException.class.getName());
	}

	protected static void throwsTransactionRejectedWithCause(Class<? extends Throwable> expected, TestBody what) {
		try {
			what.run();
		}
		catch (TransactionRejectedException e) {
			if (e.getMessage().startsWith(expected.getName()))
				return;

			fail("wrong cause: expected " + expected.getName() + " but got " + e.getMessage());
		}
		catch (Exception e) {
			fail("wrong exception: expected " + TransactionRejectedException.class.getName() + " but got " + e.getClass().getName());
		}

		fail("no exception: expected " + TransactionRejectedException.class.getName());
	}

	protected static void throwsTransactionExceptionWithCause(String expected, TestBody what) {
		try {
			what.run();
		}
		catch (TransactionException e) {
			if (e.getMessage().startsWith(expected))
				return;

			fail("wrong cause: expected " + expected + " but got " + e.getMessage());
		}
		catch (Exception e) {
			fail("wrong exception: expected " + TransactionException.class.getName() + " but got " + e.getClass().getName());
		}

		fail("no exception: expected " + TransactionException.class.getName());
	}

	protected static void throwsTransactionRejectedWithCause(String expected, TestBody what) {
		try {
			what.run();
		}
		catch (TransactionRejectedException e) {
			if (e.getMessage().startsWith(expected))
				return;

			fail("wrong cause: expected " + expected + " but got " + e.getMessage());
		}
		catch (Exception e) {
			fail("wrong exception: expected " + TransactionRejectedException.class.getName() + " but got " + e.getClass().getName());
		}

		fail("no exception: expected " + TransactionRejectedException.class.getName());
	}

	protected static void throwsTransactionException(TestBody what) {
		try {
			what.run();
		}
		catch (TransactionException e) {
			return;
		}
		catch (Exception e) {
			fail("wrong exception: expected " + TransactionException.class.getName() + " but got " + e.getClass().getName());
		}

		fail("no exception: expected " + TransactionException.class.getName());
	}

	protected static void throwsTransactionRejectedException(TestBody what) {
		try {
			what.run();
		}
		catch (TransactionRejectedException e) {
			return;
		}
		catch (Exception e) {
			fail("wrong exception: expected " + TransactionRejectedException.class.getName() + " but got " + e.getClass().getName());
		}

		fail("no exception: expected " + TransactionRejectedException.class.getName());
	}

	protected static void throwsVerificationException(TestBody what) {
		throwsTransactionExceptionWithCause(VerificationException.class, what);
	}

	/**
	 * Gets the nonce of the given account. It calls the {@code Account.nonce()} method.
	 * 
	 * @param account the account
	 * @return the nonce
	 * @throws TransactionException if the nonce cannot be found
	 */
	private BigInteger getNonceOf(StorageReference account) throws TransactionRejectedException {
		try {
			BigInteger nonce = nonces.get(account);
			if (nonce != null)
				nonce = nonce.add(BigInteger.ONE);
			else
				// we ask the account: 10,000 units of gas should be enough to run the method
				nonce = ((BigIntegerValue) node.runViewInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
					(account, BigInteger.ZERO, BigInteger.valueOf(10_000), BigInteger.ZERO, node.getClassTag(account).jar, new NonVoidMethodSignature(Constants.ACCOUNT_NAME, "nonce", ClassType.BIG_INTEGER), account))).value;

			nonces.put(account, nonce);
			return nonce;
		}
		catch (Exception e) {
			logger.error("failed computing nonce", e);
			throw new TransactionRejectedException("cannot compute the nonce of " + account);
		}
	}
}