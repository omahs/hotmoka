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

import static io.hotmoka.beans.Coin.panarea;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.Signers;

/**
 * A test for wrong use of keys for signing a transaction.
 */
class WrongKey extends HotmokaTest {

	@BeforeEach
	void beforeEach() throws Exception {
		setAccounts(_100_000, _100_000);
	}

	@Test @DisplayName("constructor call with wrong key fails")
	void createAbstractFailImpl() throws NoSuchAlgorithmException {
		// the empty signature algorithm cannot fail
		if (consensus != null && "empty".equals(consensus.signature))
			return;

		// key 1 for account 0 !
		PrivateKey key = privateKey(1);
		StorageReference caller = account(0);

		throwsTransactionRejectedWithCause("invalid request signature", () ->
			node.addConstructorCallTransaction(new ConstructorCallTransactionRequest(Signers.with(signature(), key), caller, BigInteger.ZERO, chainId,
				_100_000, panarea(1), takamakaCode(), CodeSignature.EOA_CONSTRUCTOR, new BigIntegerValue(_50_000), new StringValue("ciao")))
		);
	}
}