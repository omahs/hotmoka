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
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.GameteCreationTransactionRequest;
import io.hotmoka.beans.responses.GameteCreationTransactionResponse;
import io.hotmoka.local.EngineClassLoader;
import io.hotmoka.local.InitialResponseBuilder;
import io.hotmoka.local.internal.NodeInternal;
import io.hotmoka.verification.UnsupportedVerificationVersionException;

/**
 * The creator of a response for a transaction that creates a gamete.
 */
public class GameteCreationResponseBuilder extends InitialResponseBuilder<GameteCreationTransactionRequest, GameteCreationTransactionResponse> {

	/**
	 * Creates the builder of a response.
	 * 
	 * @param reference the reference to the transaction that is building the response
	 * @param request the request of the transaction
	 * @param node the node that is running the transaction
	 * @throws TransactionRejectedException if the builder cannot be created
	 */
	public GameteCreationResponseBuilder(TransactionReference reference, GameteCreationTransactionRequest request, NodeInternal node) throws TransactionRejectedException {
		super(reference, request, node);
	}

	@Override
	protected EngineClassLoader mkClassLoader() throws ClassNotFoundException, UnsupportedVerificationVersionException, IOException {
		return node.getCaches().getClassLoader(request.classpath);
	}

	@Override
	public GameteCreationTransactionResponse getResponse() throws TransactionRejectedException {
		return new ResponseCreator() {

			@Override
			protected GameteCreationTransactionResponse body() {
				try {
					Object gamete = classLoader.getGamete().getDeclaredConstructor(String.class).newInstance(request.publicKey);
					classLoader.setBalanceOf(gamete, request.initialAmount);
					classLoader.setRedBalanceOf(gamete, request.redInitialAmount);
					return new GameteCreationTransactionResponse(updatesExtractor.extractUpdatesFrom(Stream.of(gamete)), classLoader.getStorageReferenceOf(gamete));
				}
				catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
					throw new RuntimeException("unexpected exception", e);
				}
			}
		}
		.create();
	}
}