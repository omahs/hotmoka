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

package io.hotmoka.tools.internal.moka;

import java.math.BigInteger;
import java.security.KeyPair;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.Account;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.helpers.GasHelper;
import io.hotmoka.helpers.NonceHelper;
import io.hotmoka.helpers.SignatureHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.remote.RemoteNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "buy-validation",
	description = "Accept a sale offer of validation power",
	showDefaultValues = true)
public class BuyValidation extends AbstractCommand {

	private final static String OFFER_DEFAULT = "all offers at cost 0 reserved to the buyer";

	@Parameters(index = "0", description = "the reference to the validator that accepts and pays the sale offer of validation power")
    private String buyer;

	@Parameters(index = "1", description = "the reference to the sale offer that gets accepted", defaultValue = OFFER_DEFAULT)
    private String offer;

	@Option(names = { "--password-of-buyer" }, description = "the password of the buyer validator; if not specified, it will be asked interactively")
    private String passwordOfBuyer;

	@Option(names = { "--url" }, description = "the url of the node (without the protocol)", defaultValue = "localhost:8080")
    private String url;

	@Option(names = { "--interactive" }, description = "run in interactive mode", defaultValue = "true") 
	private boolean interactive;

	@Option(names = { "--gas-limit" }, description = "the gas limit used for accepting the offer", defaultValue = "500000") 
	private BigInteger gasLimit;

	@Option(names = { "--print-costs" }, description = "print the incurred gas costs", defaultValue = "true") 
	private boolean printCosts;

	@Override
	protected void execute() throws Exception {
		new Run();
	}

	private class Run {
		private final Node node;

		private Run() throws Exception {
			if (passwordOfBuyer != null && interactive)
				throw new IllegalArgumentException("the password of the buyer validator can be provided as command switch only in non-interactive mode");

			passwordOfBuyer = ensurePassword(passwordOfBuyer, "the buyer validator", interactive, false);

			try (Node node = this.node = RemoteNode.of(remoteNodeConfig(url))) {
				GasHelper gasHelper = new GasHelper(node);
				NonceHelper nonceHelper = new NonceHelper(node);
				TransactionReference takamakaCode = node.getTakamakaCode();
				StorageReference manifest = node.getManifest();
				StorageReference validators = (StorageReference) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
					(manifest, _100_000, takamakaCode, CodeSignature.GET_VALIDATORS, manifest));
				StorageReference buyer = new StorageReference(BuyValidation.this.buyer);
				SignatureAlgorithm<SignedTransactionRequest> algorithm = new SignatureHelper(node).signatureAlgorithmFor(buyer);
				String chainId = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
					(manifest, _100_000, takamakaCode, CodeSignature.GET_CHAIN_ID, manifest))).value;
				KeyPair keys = readKeys(new Account(buyer), node, passwordOfBuyer);
				Signer signer = Signer.with(algorithm, keys);				
				InstanceMethodCallTransactionRequest request;

				if (OFFER_DEFAULT.equals(offer)) {
					askForConfirmation(gasLimit);

					request = new InstanceMethodCallTransactionRequest
							(signer, buyer, nonceHelper.getNonceOf(buyer), chainId, gasLimit, gasHelper.getSafeGasPrice(), takamakaCode,
									new VoidMethodSignature(ClassType.ABSTRACT_VALIDATORS, "acceptAllAtCostZero", ClassType.VALIDATOR),
									validators, buyer);

					int count = ((IntValue) node.addInstanceMethodCallTransaction(request)).value;
					if (count == 1)
						System.out.println("1 offer accepted");
					else
						System.out.println(count + " offers accepted");

					printCosts(request);
				}
				else {
					int buyerSurcharge = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
							(manifest, _100_000, takamakaCode, new NonVoidMethodSignature(ClassType.VALIDATORS, "getBuyerSurcharge", BasicTypes.INT), validators))).value;

					StorageReference offer = new StorageReference(BuyValidation.this.offer);

					BigInteger cost = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
							(manifest, _100_000, takamakaCode, new NonVoidMethodSignature(ClassType.SHARED_ENTITY_OFFER, "getCost", ClassType.BIG_INTEGER), offer))).value;
					BigInteger costWithSurcharge = cost.multiply(BigInteger.valueOf(buyerSurcharge + 100_000_000L)).divide(_100_000_000);

					askForConfirmation(gasLimit, costWithSurcharge);

					request = new InstanceMethodCallTransactionRequest
							(signer, buyer, nonceHelper.getNonceOf(buyer), chainId, gasLimit, gasHelper.getSafeGasPrice(), takamakaCode,
									new VoidMethodSignature(ClassType.ABSTRACT_VALIDATORS, "accept", ClassType.BIG_INTEGER, ClassType.VALIDATOR, ClassType.SHARED_ENTITY_OFFER),
									validators, new BigIntegerValue(costWithSurcharge), buyer, offer);

					node.addInstanceMethodCallTransaction(request);
					System.out.println("Offer accepted");
				}

				printCosts(request);
			}
		}

		private void askForConfirmation(BigInteger gas, BigInteger cost) {
			if (interactive)
				yesNo("Do you really want to spend up to " + gas + " gas units and " + cost + " panareas to accept the sale of validation power [Y/N] ");
		}

		private void askForConfirmation(BigInteger gas) {
			if (interactive)
				yesNo("Do you really want to spend up to " + gas + " gas units to accept all reserved sale offers of validation power at cost zero [Y/N] ");
		}

		private void printCosts(TransactionRequest<?>... requests) {
			if (printCosts)
				BuyValidation.this.printCosts(node, requests);
		}
	}
}