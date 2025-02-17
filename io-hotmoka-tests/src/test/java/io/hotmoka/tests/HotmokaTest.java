/*
Copyright 2021 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.hotmoka.tests;

/**
 * MODIFY AT LINE 200 TO SELECT THE NODE IMPLEMENTATION TO TEST.
 */
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.Coin;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.StaticMethodCallTransactionRequest;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.constants.Constants;
import io.hotmoka.crypto.Entropies;
import io.hotmoka.crypto.Signers;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.helpers.AccountsNodes;
import io.hotmoka.helpers.InitializedNodes;
import io.hotmoka.helpers.JarsNodes;
import io.hotmoka.helpers.api.AccountsNode;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.api.Node;
import io.hotmoka.nodes.api.Node.CodeSupplier;
import io.hotmoka.nodes.api.Node.JarSupplier;
import io.hotmoka.nodes.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.remote.RemoteNodeConfig;
import io.hotmoka.service.NodeService;
import io.hotmoka.service.NodeServiceConfig;
import io.hotmoka.tendermint.TendermintBlockchain;
import io.hotmoka.tendermint.TendermintBlockchainConfig;
import io.hotmoka.tendermint.helpers.TendermintInitializedNode;
import io.hotmoka.verification.VerificationException;

public abstract class HotmokaTest {

	protected static final BigInteger _50_000 = BigInteger.valueOf(50_000);
	protected static final BigInteger _100_000 = BigInteger.valueOf(100_000);
	protected static final BigInteger _500_000 = BigInteger.valueOf(500_000);
	protected static final BigInteger _1_000_000 = BigInteger.valueOf(1_000_000);
	protected static final BigInteger _10_000_000 = BigInteger.valueOf(10_000_000);
	protected static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);
	protected static final BigInteger _10_000_000_000 = BigInteger.valueOf(10_000_000_000L);

	/**
	 * The node that gets created before starting running the tests.
	 * This node will hence be created only once and
	 * each test will decorate it into {@linkplain #nodeWithAccountsView},
	 * with the addition of the jar and accounts that the test needs.
	 */
	protected final static Node node;

	/**
	 * The consensus parameters of the node.
	 */
	protected final static ConsensusParams consensus;

	/**
	 * The private key of the account used at each run of the tests.
	 */
	private final static PrivateKey privateKeyOfLocalGamete;

	/**
	 * The account that can be used as gamete for each run of the tests.
	 */
	private final static StorageReference localGamete;

	/**
	 * The signature algorithm used for signing the requests.
	 */
	private final static SignatureAlgorithm<SignedTransactionRequest> signature;

	/**
	 * The jar under test.
	 */
	private static TransactionReference jar;

	/**
	 * The node under test. This is a view of {@linkplain #node},
	 * with the addition of some initial accounts, recreated before each test.
	 */
	private AccountsNode nodeWithAccountsView;

	/**
	 * The nonce of each externally owned account used in the test.
	 */
	private final ConcurrentMap<StorageReference, BigInteger> nonces = new ConcurrentHashMap<>();

	/**
	 * The chain identifier of the node used for the tests.
	 */
	protected final static String chainId;

	/**
	 * Non-null if the node is based on Tendermint, so that a specific initialization can be run.
	 */
	protected static TendermintBlockchain tendermintBlockchain;

	/**
	 * The private key of the gamete.
	 */
	protected static final PrivateKey privateKeyOfGamete;

	private final static Logger LOGGER = Logger.getLogger(HotmokaTest.class.getName());

	@BeforeEach
	void logTestName(TestInfo testInfo) {
		LOGGER.info("**** Starting test " + testInfo.getTestClass().get().getSimpleName() + '.' + testInfo.getTestMethod().get().getName() + ": " + testInfo.getDisplayName());
	}

	public interface TestBody {
		void run() throws Exception;
	}

	static {
		try {
			String current = System.getProperty("java.util.logging.config.file");
			if (current == null) {
				// if the property is not set, we provide a default (if it exists)
				URL resource = HotmokaTest.class.getClassLoader().getResource("logging.properties");
				if (resource != null)
					LogManager.getLogManager().readConfiguration(resource.openStream());
			}

			tendermintBlockchain = null; // Tendermint would reassign

	        // we use always the same entropy and password, so that the tests become deterministic (if they are not explicitly non-deterministic)
	        var entropy = Entropies.of(new byte[16]);
			var password = "";
			var localSignature = SignatureAlgorithmForTransactionRequests.ed25519det();
			var keys = entropy.keys(password, localSignature);
			String publicKeyOfGamete = Base64.getEncoder().encodeToString(localSignature.encodingOf(keys.getPublic()));
			consensus = new ConsensusParams.Builder()
	    			.signRequestsWith("ed25519det") // good for testing
	    			.allowUnsignedFaucet(true) // good for testing
	    			.allowMintBurnFromGamete(true) // good for testing
	    			.ignoreGasPrice(true) // good for testing
	    			.setInitialSupply(Coin.level7(10000000)) // enough for all tests
	    			.setInitialRedSupply(Coin.level7(10000000)) // enough for all tests
	    			.setPublicKeyOfGamete(publicKeyOfGamete)
	    			.build();
	        privateKeyOfGamete = keys.getPrivate();

	        // Change this to test with different node implementations
	        node = mkMemoryBlockchain();
	        //node = mkTendermintBlockchain();
	        //node = mkRemoteNode(mkMemoryBlockchain());
	        //node = mkRemoteNode(mkTendermintBlockchain());
	        //node = mkRemoteNode("ec2-54-194-239-91.eu-west-1.compute.amazonaws.com:8080");
	        //node = mkRemoteNode("localhost:8080");

	        signature = SignatureAlgorithmForTransactionRequests.mk(node.getNameOfSignatureAlgorithmForRequests());
	        initializeNodeIfNeeded();
	        var signerOfGamete = Signers.with(signature, privateKeyOfGamete);

	        StorageReference manifest = node.getManifest();
	        var takamakaCode = node.getTakamakaCode();
	        var gamete = (StorageReference) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
	    		(manifest, _100_000, takamakaCode, CodeSignature.GET_GAMETE, manifest));

			chainId = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_CHAIN_ID, manifest))).value;

			BigInteger nonce = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(gamete, _100_000, takamakaCode, CodeSignature.NONCE, gamete))).value;

			BigInteger aLot = Coin.level6(1000000000);

			// we set the thresholds for the faucets of the gamete
			node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(signerOfGamete, gamete, nonce, chainId, _100_000, BigInteger.ONE, takamakaCode,
				new VoidMethodSignature(ClassType.GAMETE, "setMaxFaucet", ClassType.BIG_INTEGER, ClassType.BIG_INTEGER), gamete,
				new BigIntegerValue(aLot), new BigIntegerValue(aLot)));

	        var local = AccountsNodes.ofGreenRed(node, gamete, privateKeyOfGamete, aLot, aLot);
	        localGamete = local.account(0);
	        privateKeyOfLocalGamete = local.privateKey(0);
		}
		catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static void initializeNodeIfNeeded() throws TransactionRejectedException, TransactionException,
			CodeExecutionException, IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException, ClassNotFoundException {

		try {
			node.getManifest();
		}
		catch (NoSuchElementException e) {
			// if the original node has no manifest yet, it means that it is not initialized and we initialize it

			var takamakaCode = Paths.get("../modules/explicit/io-takamaka-code-" + Constants.TAKAMAKA_VERSION + ".jar");
			if (tendermintBlockchain != null)
				TendermintInitializedNode.of(tendermintBlockchain, consensus, takamakaCode);
			else
				InitializedNodes.of(node, consensus, takamakaCode);
		}
	}

	@SuppressWarnings("unused")
	private static Node mkTendermintBlockchain() throws NoSuchAlgorithmException, IOException {
		var config = new TendermintBlockchainConfig.Builder()
			.setTendermintConfigurationToClone(Paths.get("tendermint_config"))
			.setMaxGasPerViewTransaction(_10_000_000)
			.build();

		return tendermintBlockchain = TendermintBlockchain.init(config, consensus);
	}

	@SuppressWarnings("unused")
	private static Node mkMemoryBlockchain() throws NoSuchAlgorithmException, IOException {
		var config = new MemoryBlockchainConfig.Builder()
			.setMaxGasPerViewTransaction(_10_000_000)
			.build();

		return MemoryBlockchain.init(config, consensus);
	}

	@SuppressWarnings("unused")
	private static Node mkRemoteNode(Node exposed) throws IOException {
		// we use port 8080, so that it does not interfere with the other service opened at port 8081 by the network tests
		var serviceConfig = new NodeServiceConfig.Builder()
			.setPort(8080)
			.setSpringBannerModeOn(false).build();

		NodeService.of(serviceConfig, exposed);

		var remoteNodeConfig = new RemoteNodeConfig.Builder()
			// comment for using http
			.setWebSockets(true)
			.setURL("localhost:8080")
			.build();

		return RemoteNode.of(remoteNodeConfig);
	}

	@SuppressWarnings("unused")
	private static Node mkRemoteNode(String url) throws IOException {
		var remoteNodeConfig = new RemoteNodeConfig.Builder()
			//.setWebSockets(true)
			.setURL(url).build();
		return RemoteNode.of(remoteNodeConfig);
	}

	protected final void setAccounts(BigInteger... coins) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionRejectedException, TransactionException, CodeExecutionException, NoSuchElementException, ClassNotFoundException {
		nodeWithAccountsView = AccountsNodes.of(node, localGamete, privateKeyOfLocalGamete, coins);
	}

	protected final void setAccounts(String containerClassName, TransactionReference classpath, BigInteger... coins) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionRejectedException, TransactionException, CodeExecutionException, ClassNotFoundException {
		nodeWithAccountsView = AccountsNodes.of(node, localGamete, privateKeyOfLocalGamete, containerClassName, classpath, coins);
	}

	protected final void setAccounts(Stream<BigInteger> coins) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionRejectedException, TransactionException, CodeExecutionException, NoSuchElementException, ClassNotFoundException {
		setAccounts(coins.toArray(BigInteger[]::new));
	}

	protected final static AccountsNode mkAccounts(Stream<BigInteger> coins) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionRejectedException, TransactionException, CodeExecutionException, NoSuchElementException, ClassNotFoundException {
		return AccountsNodes.of(node, localGamete, privateKeyOfLocalGamete, coins.toArray(BigInteger[]::new));
	}

	protected final void setAccounts(String containerClassName, TransactionReference classpath, Stream<BigInteger> coins) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionRejectedException, TransactionException, CodeExecutionException, ClassNotFoundException {
		setAccounts(containerClassName, classpath, coins.toArray(BigInteger[]::new));
	}

	protected final void setGreenRedAccounts(BigInteger... coins) throws TransactionRejectedException, TransactionException, CodeExecutionException, InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchElementException, ClassNotFoundException {
		nodeWithAccountsView = AccountsNodes.ofGreenRed(node, localGamete, privateKeyOfLocalGamete, coins);
	}

	protected static void setJar(String jar) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionRejectedException, TransactionException, CodeExecutionException, IOException, NoSuchElementException, ClassNotFoundException {
		HotmokaTest.jar = JarsNodes.of(node, localGamete, privateKeyOfLocalGamete, pathOfExample(jar)).jar(0);
	}

	protected final TransactionReference takamakaCode() {
		return node.getTakamakaCode();
	}

	protected static TransactionReference jar() {
		return jar;
	}

	protected final StorageReference account(int i) {
		return nodeWithAccountsView.account(i);
	}

	protected final Stream<StorageReference> accounts() {
		return nodeWithAccountsView.accounts();
	}

	protected final StorageReference containerOfAccounts() {
		return nodeWithAccountsView.container();
	}

	protected final Stream<PrivateKey> privateKeys() {
		return nodeWithAccountsView.privateKeys();
	}

	protected final PrivateKey privateKey(int i) {
		return nodeWithAccountsView.privateKey(i);
	}

	protected static SignatureAlgorithm<SignedTransactionRequest> signature() {
		return signature;
	}

	protected final TransactionRequest<?> getRequest(TransactionReference reference) {
		return node.getRequest(reference);
	}

	protected final TransactionResponse getResponse(TransactionReference reference) throws NoSuchElementException, TransactionRejectedException {
		return node.getResponse(reference);
	}

	protected final TransactionReference addJarStoreInitialTransaction(byte[] jar, TransactionReference... dependencies) throws TransactionRejectedException {
		return node.addJarStoreInitialTransaction(new JarStoreInitialTransactionRequest(jar, dependencies));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final TransactionReference addJarStoreTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, byte[] jar, TransactionReference... dependencies) throws TransactionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.addJarStoreTransaction(new JarStoreTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, jar, dependencies));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageReference addConstructorCallTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, ConstructorSignature constructor, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.addConstructorCallTransaction(new ConstructorCallTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, constructor, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue addInstanceMethodCallTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, method, receiver, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue addStaticMethodCallTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, method, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue runInstanceMethodCallTransaction(StorageReference caller, BigInteger gasLimit, TransactionReference classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(caller, gasLimit, classpath, method, receiver, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final StorageValue runStaticMethodCallTransaction(StorageReference caller, BigInteger gasLimit, TransactionReference classpath, MethodSignature method, StorageValue... actuals) throws TransactionException, CodeExecutionException, TransactionRejectedException {
		return node.runStaticMethodCallTransaction(new StaticMethodCallTransactionRequest(caller, gasLimit, classpath, method, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final JarSupplier postJarStoreTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, byte[] jar, TransactionReference... dependencies) throws TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.postJarStoreTransaction(new JarStoreTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, jar, dependencies));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final CodeSupplier<StorageValue> postInstanceMethodCallTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, MethodSignature method, StorageReference receiver, StorageValue... actuals) throws TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.postInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, method, receiver, actuals));
	}

	/**
	 * Takes care of computing the next nonce.
	 */
	protected final CodeSupplier<StorageReference> postConstructorCallTransaction(PrivateKey key, StorageReference caller, BigInteger gasLimit, BigInteger gasPrice, TransactionReference classpath, ConstructorSignature constructor, StorageValue... actuals) throws TransactionRejectedException, InvalidKeyException, SignatureException {
		return node.postConstructorCallTransaction(new ConstructorCallTransactionRequest(Signers.with(signature, key), caller, getNonceOf(caller), chainId, gasLimit, gasPrice, classpath, constructor, actuals));
	}

	protected static byte[] bytesOf(String fileName) throws IOException {
		return Files.readAllBytes(pathOfExample(fileName));
	}

	protected static Path pathOfExample(String fileName) {
		return Paths.get("../io-hotmoka-examples/target/io-hotmoka-examples-" + Constants.HOTMOKA_VERSION + '-' + fileName);
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

	protected static void throwsTransactionExceptionWithCauseAndMessageContaining(Class<? extends Throwable> expected, String subMessage, TestBody what) {
		throwsTransactionExceptionWithCauseAndMessageContaining(expected.getName(), subMessage, what);
	}

	protected static void throwsTransactionExceptionWithCauseAndMessageContaining(String expected, String subMessage, TestBody what) {
		try {
			what.run();
		}
		catch (TransactionException e) {
			if (e.getMessage().startsWith(expected)) {
				if (e.getMessage().contains(subMessage))
					return;

				fail("wrong message: it does not contain " + subMessage);
			}

			fail("wrong cause: expected " + expected + " but got " + e.getMessage());
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

	protected static void throwsVerificationExceptionWithMessageContaining(String subMessage, TestBody what) {
		throwsTransactionExceptionWithCauseAndMessageContaining(VerificationException.class, subMessage, what);
	}

	/**
	 * Gets the nonce of the given account. It calls the {@code Account.nonce()} method.
	 * 
	 * @param account the account
	 * @return the nonce
	 * @throws TransactionRejectedException if the nonce cannot be found
	 */
	protected final BigInteger getNonceOf(StorageReference account) throws TransactionRejectedException {
		try {
			BigInteger nonce = nonces.get(account);
			if (nonce != null)
				nonce = nonce.add(BigInteger.ONE);
			else
				// we ask the account: 100,000 units of gas should be enough to run the method
				nonce = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
					(account, _100_000, node.getClassTag(account).jar, CodeSignature.NONCE, account))).value;

			nonces.put(account, nonce);
			return nonce;
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, "failed computing nonce", e);
			throw new TransactionRejectedException("cannot compute the nonce of " + account);
		}
	}

	/**
	 * Gets the (green) balance of the given account. It calls the {@code AccountWithAccessibleBalance.getBalance()} method.
	 * 
	 * @param account the account
	 * @return the balance
	 * @throws TransactionRejectedException if the balance cannot be found
	 */
	protected static BigInteger getBalanceOf(StorageReference account) throws TransactionRejectedException {
		try {
			// we ask the account: 10,000 units of gas should be enough to run the method
			var classTag = node.getClassTag(account);
			return ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(account, _100_000, classTag.jar, CodeSignature.BALANCE, account))).value;
		}
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, "failed computing the balance", e);
			throw new TransactionRejectedException("cannot compute the balance of " + account);
		}
	}
}