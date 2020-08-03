/**
 * 
 */
package io.takamaka.code.tests;

import com.google.gson.JsonObject;
import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.requests.NonInitialTransactionRequest;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.network.NodeService;
import io.hotmoka.network.NodeServiceConfig;
import io.hotmoka.network.internal.services.NetworkExceptionResponse;
import io.hotmoka.network.internal.services.RestClientService;
import io.hotmoka.network.models.errors.ErrorModel;
import io.hotmoka.network.models.requests.ConstructorCallTransactionRequestModel;
import io.hotmoka.network.models.requests.JarStoreInitialTransactionRequestModel;
import io.hotmoka.network.models.updates.ClassTagModel;
import io.hotmoka.network.models.updates.StateModel;
import io.hotmoka.network.models.values.StorageReferenceModel;
import io.hotmoka.network.models.values.TransactionReferenceModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;

import static io.hotmoka.beans.types.BasicTypes.INT;
import static java.math.BigInteger.ONE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A test for creating a network server from a Hotmoka node.
 */
class NetworkFromNode extends TakamakaTest {
	private static final BigInteger ALL_FUNDS = BigInteger.valueOf(1_000_000_000);
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final ConstructorSignature CONSTRUCTOR_INTERNATIONAL_TIME = new ConstructorSignature("io.takamaka.tests.basicdependency.InternationalTime", INT, INT, INT);

	private final NodeServiceConfig configNoBanner = new NodeServiceConfig.Builder().setPort(8080).setSpringBannerModeOn(false).build();

	/**
	 * The account that holds all funds.
	 */
	private StorageReference master;

	/**
	 * The classpath of the classes being tested.
	 */
	private TransactionReference classpath;

	/**
	 * The private key of {@linkplain #master}.
	 */
	private PrivateKey key;

	@BeforeEach
	void beforeEach() throws Exception {
		setNode("basicdependency.jar", ALL_FUNDS, BigInteger.ZERO);
		master = account(0);
		key = privateKey(0);
		classpath = addJarStoreTransaction(key, master, BigInteger.valueOf(10000), BigInteger.ONE, takamakaCode(), bytesOf("basic.jar"), jar());
	}

	@Test @DisplayName("starts a network server from a Hotmoka node")
	void startNetworkFromNode() {
		NodeServiceConfig config = new NodeServiceConfig.Builder().setPort(8080).setSpringBannerModeOn(true).build();
		try (NodeService nodeRestService = NodeService.of(config, nodeWithJarsView)) {
		}
	}

	@Test @DisplayName("starts a network server from a Hotmoka node and checks its signature algorithm")
	void startNetworkFromNodeAndTestSignatureAlgorithm() throws InterruptedException, IOException {
		String answer;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
			answer = RestClientService.get("http://localhost:8080/get/signatureAlgorithmForRequests", String.class);
		}

		assertEquals("sha256dsa", answer);
	}

	@Test @DisplayName("starts a network server from a Hotmoka node and runs getTakamakaCode()")
	void testGetTakamakaCode() throws InterruptedException, IOException {
		TransactionReferenceModel result;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
			result = RestClientService.get("http://localhost:8080/get/takamakaCode", TransactionReferenceModel.class);
		}

		assertEquals(nodeWithJarsView.getTakamakaCode().getHash(), result.hash);
	}

	@Test @DisplayName("starts a network server from a Hotmoka node and runs addJarStoreInitialTransaction()")
	void addJarStoreInitialTransaction() throws InterruptedException, IOException {
		ErrorModel errorModel = null;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
			JarStoreInitialTransactionRequest request = new JarStoreInitialTransactionRequest(Files.readAllBytes(Paths.get("jars/c13.jar")), nodeWithJarsView.getTakamakaCode());

			try {
				RestClientService.post(
						"http://localhost:8080/add/jarStoreInitialTransaction",
						new JarStoreInitialTransactionRequestModel(request),
						TransactionReferenceModel.class
				);
			} catch (NetworkExceptionResponse networkExceptionResponse){
				errorModel = networkExceptionResponse.toErrorModel();
			}
		}

		assertNotNull(errorModel);
		assertEquals("Transaction rejected", errorModel.message);
		assertEquals("io.hotmoka.beans.TransactionRejectedException", errorModel.exceptionType);
	}

	@Test @DisplayName("starts a network server from a Hotmoka node and runs addJarStoreInitialTransaction() without a jar")
	void addJarStoreInitialTransactionWithoutAJar() throws InterruptedException, IOException {
		ErrorModel errorModel = null;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {

			String jar = null;
			JsonObject bodyJson = new JsonObject();
			bodyJson.addProperty("jar", jar);

			try {
				RestClientService.post(
						"http://localhost:8080/add/jarStoreInitialTransaction",
						bodyJson.toString(),
						TransactionReferenceModel.class
				);
			} catch (NetworkExceptionResponse networkExceptionResponse) {
				errorModel = networkExceptionResponse.toErrorModel();
			}
		}

		assertNotNull(errorModel);
		assertEquals("unexpected null jar", errorModel.message);
		assertEquals("io.hotmoka.beans.InternalFailureException", errorModel.exceptionType);
	}

	@Test @DisplayName("starts a network server from a Hotmoka node and calls addConstructorCallTransaction - new Sub(1973")
	void addConstructorCallTransaction() throws InterruptedException, IOException, SignatureException, InvalidKeyException, NoSuchAlgorithmException {
		StorageReferenceModel result;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
			ConstructorCallTransactionRequest request = new ConstructorCallTransactionRequest(
					NonInitialTransactionRequest.Signer.with(signature(), key),
					master,
					ONE,
					chainId,
					_20_000,
					ONE,
					classpath,
					new ConstructorSignature("io.takamaka.tests.basic.Sub", INT),
					new IntValue(1973)
			);

			result = RestClientService.post(
					"http://localhost:8080/add/constructorCallTransaction",
					new ConstructorCallTransactionRequestModel(request),
					StorageReferenceModel.class
			);
		}

		assertNotNull(result.transaction);
	}

	@Test @DisplayName("starts a network server from a Hotmoka node, creates an object and calls getState() on it")
	void testGetState() throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionException, CodeExecutionException, TransactionRejectedException, IOException {
		StateModel state;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
			ConstructorCallTransactionRequest request = new ConstructorCallTransactionRequest(
					NonInitialTransactionRequest.Signer.with(signature(), key),
					master,
					ONE,
					chainId,
					_20_000,
					ONE,
					classpath,
					CONSTRUCTOR_INTERNATIONAL_TIME,
					new IntValue(13), new IntValue(25), new IntValue(40)
			);

			// we execute the creation of the object
			StorageReferenceModel object = RestClientService.post(
					"http://localhost:8080/add/constructorCallTransaction",
					new ConstructorCallTransactionRequestModel(request),
					StorageReferenceModel.class
			);

			// we query the state of the object
			state = RestClientService.post("http://localhost:8080/get/state", object, StateModel.class);
		}

		// the state contains two updates
		assertSame(2L, state.getUpdates().count());
	}

	@Test @DisplayName("starts a network server from a Hotmoka node, creates an object and calls getState() on it")
	void testGetClassTag() throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, TransactionException, CodeExecutionException, TransactionRejectedException, IOException {
		ClassTagModel classTag;

		try (NodeService nodeRestService = NodeService.of(configNoBanner, nodeWithJarsView)) {
			ConstructorCallTransactionRequest request = new ConstructorCallTransactionRequest(
					NonInitialTransactionRequest.Signer.with(signature(), key),
					master,
					ONE,
					chainId,
					_20_000,
					ONE,
					classpath,
					CONSTRUCTOR_INTERNATIONAL_TIME,
					new IntValue(13), new IntValue(25), new IntValue(40)
			);

			// we execute the creation of the object
			StorageReferenceModel object = RestClientService.post(
					"http://localhost:8080/add/constructorCallTransaction",
					new ConstructorCallTransactionRequestModel(request),
					StorageReferenceModel.class
			);

			// we query the class tag of the object
			classTag = RestClientService.post("http://localhost:8080/get/classTag", object, ClassTagModel.class);
		}

		// the state that the class tag holds the name of the class that has been created
		assertEquals(CONSTRUCTOR_INTERNATIONAL_TIME.definingClass.name, classTag.className);
	}
}