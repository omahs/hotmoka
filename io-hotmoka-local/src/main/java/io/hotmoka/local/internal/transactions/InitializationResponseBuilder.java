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

package io.hotmoka.local.internal.transactions;

import java.io.IOException;

import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InitializationTransactionRequest;
import io.hotmoka.beans.responses.InitializationTransactionResponse;
import io.hotmoka.local.EngineClassLoader;
import io.hotmoka.local.InitialResponseBuilder;
import io.hotmoka.local.internal.NodeInternal;
import io.hotmoka.verification.UnsupportedVerificationVersionException;

/**
 * The creator of a response for a transaction that initializes a node.
 */
public class InitializationResponseBuilder extends InitialResponseBuilder<InitializationTransactionRequest, InitializationTransactionResponse> {

	/**
	 * Creates the builder of the response.
	 * 
	 * @param reference the reference to the transaction that is building the response
	 * @param request the request of the transaction
	 * @param node the node that is running the transaction
	 * @throws TransactionRejectedException if the builder cannot be created
	 */
	public InitializationResponseBuilder(TransactionReference reference, InitializationTransactionRequest request, NodeInternal node) throws TransactionRejectedException {
		super(reference, request, node);
	}

	@Override
	public InitializationTransactionResponse getResponse() throws TransactionRejectedException {
		return new ResponseCreator() {

			@Override
			protected InitializationTransactionResponse body() {
				return new InitializationTransactionResponse();	
			}
		}
		.create();
	}

	@Override
	protected EngineClassLoader mkClassLoader() throws ClassNotFoundException, UnsupportedVerificationVersionException, IOException {
		return node.getCaches().getClassLoader(request.classpath); // currently not used for this transaction
	}
}