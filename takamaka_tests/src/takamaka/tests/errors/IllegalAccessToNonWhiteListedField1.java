package takamaka.tests.errors;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.takamaka.code.memory.InitializedMemoryBlockchain;
import io.takamaka.code.verification.issues.IllegalAccessToNonWhiteListedFieldError;
import takamaka.tests.TakamakaTest;

class IllegalAccessToNonWhiteListedField1 extends TakamakaTest {
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);

	/**
	 * The blockchain under test. This is recreated before each test.
	 */
	private InitializedMemoryBlockchain blockchain;

	@BeforeEach
	void beforeEach() throws Exception {
		blockchain = InitializedMemoryBlockchain.of(Paths.get("../distribution/dist/io-takamaka-code-1.0.jar"), _1_000_000_000);
	}

	@Test @DisplayName("install jar")
	void installJar() {
		throwsVerificationExceptionWithCause(IllegalAccessToNonWhiteListedFieldError.class, () ->
			blockchain.addJarStoreTransaction
				(new JarStoreTransactionRequest(blockchain.account(0), _20_000, BigInteger.ONE, blockchain.takamakaCode(),
				Files.readAllBytes(Paths.get("../takamaka_examples/dist/illegalaccesstononwhitelistedfield1.jar")), blockchain.takamakaCode()))
		);
	}
}