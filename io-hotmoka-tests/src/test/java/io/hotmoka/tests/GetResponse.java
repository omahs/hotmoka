package io.hotmoka.tests;

import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.LocalTransactionReference;
import io.hotmoka.beans.responses.ConstructorCallTransactionResponse;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;

/**
 * A test for {@link io.hotmoka.nodes.Node#getResponse(io.hotmoka.beans.references.TransactionReference)}.
 */
class GetResponse extends TakamakaTest {
	private static final ConstructorSignature ABSTRACT_FAIL_IMPL_CONSTRUCTOR = new ConstructorSignature(new ClassType("io.hotmoka.examples.abstractfail.AbstractFailImpl"), BasicTypes.INT);

	@BeforeAll
	static void beforeAll() throws Exception {
		setJar("abstractfail.jar");
	}

	@BeforeEach
	void beforeEach() throws Exception {
		setAccounts(_1_000_000_000);
	}

	@Test @DisplayName("getResponse works for an existing transaction")
	void getResponse() throws CodeExecutionException, TransactionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference abstractfail = addConstructorCallTransaction(privateKey(0), account(0), _50_000, BigInteger.ONE, jar(), ABSTRACT_FAIL_IMPL_CONSTRUCTOR, new IntValue(42));
		TransactionResponse response = getResponse(abstractfail.transaction);
		Assertions.assertTrue(response instanceof ConstructorCallTransactionResponse);
	}

	@Test @DisplayName("getResponse works for a non-existing transaction")
	void getResponseNonExisting() throws CodeExecutionException, TransactionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		try {
			StorageReference abstractfail = addConstructorCallTransaction(privateKey(0), account(0), _50_000, BigInteger.ONE, jar(), ABSTRACT_FAIL_IMPL_CONSTRUCTOR, new IntValue(42));
			String hash = abstractfail.transaction.getHash();
			// re replace the first digit: the resulting transaction reference does not exist
			char digit = (hash.charAt(0) == '0') ? '1' : '0';
			hash = digit + hash.substring(1);
			getResponse(new LocalTransactionReference(hash));
			fail("missing exception");
		}
		catch (NoSuchElementException e) {
		}
	}
}