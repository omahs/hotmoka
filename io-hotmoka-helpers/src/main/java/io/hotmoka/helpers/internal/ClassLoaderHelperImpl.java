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

package io.hotmoka.helpers.internal;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.AbstractJarStoreTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.helpers.api.ClassLoaderHelper;
import io.hotmoka.nodes.api.Node;
import io.hotmoka.verification.TakamakaClassLoaders;
import io.hotmoka.verification.api.TakamakaClassLoader;

/**
 * Implementation of a helper object for building class loaders for the jar installed at a given
 * transaction reference inside a node.
 */
public class ClassLoaderHelperImpl implements ClassLoaderHelper {
	private final Node node;
	private final StorageReference manifest;
	private final TransactionReference takamakaCode;
	private final StorageReference versions;
	private final static BigInteger _100_000 = BigInteger.valueOf(100_000L);

	/**
	 * Creates the helper class for building class loaders for jars installed in the given node.
	 * 
	 * @param node the node
	 * @throws TransactionRejectedException if some transaction was rejected
	 * @throws TransactionException if some transaction failed
	 * @throws CodeExecutionException if some transaction generated an exception
	 */
	public ClassLoaderHelperImpl(Node node) throws TransactionRejectedException, TransactionException, CodeExecutionException {
		this.node = node;
		this.manifest = node.getManifest();
		this.takamakaCode = node.getTakamakaCode();
		this.versions = (StorageReference) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(manifest, _100_000, takamakaCode, CodeSignature.GET_VERSIONS, manifest));
	}

	@Override
	public TakamakaClassLoader classloaderFor(TransactionReference jar) throws ClassNotFoundException, TransactionRejectedException, TransactionException, CodeExecutionException {
		var ws = new ArrayList<TransactionReference>();
		var seen = new HashSet<TransactionReference>();
		var jars = new ArrayList<byte[]>();
		ws.add(jar);
		seen.add(jar);

		do {
			TransactionReference current = ws.remove(ws.size() - 1);
			var request = (AbstractJarStoreTransactionRequest) node.getRequest(current);
			jars.add(request.getJar());
			request.getDependencies().filter(seen::add).forEachOrdered(ws::add);
		}
		while (!ws.isEmpty());

		int verificationVersion = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(manifest, _100_000, takamakaCode, CodeSignature.GET_VERIFICATION_VERSION, versions))).value;

		return TakamakaClassLoaders.of(jars.stream(), verificationVersion);
	}
}