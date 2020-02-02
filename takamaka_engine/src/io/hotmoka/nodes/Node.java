package io.hotmoka.nodes;

import java.math.BigInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.beans.values.StorageReference;
import io.takamaka.code.engine.TransactionRun;

/**
 * A node of the HotMoka network, that provides the storage
 * facilities for the execution of Takamaka code.
 */
public interface Node {

	/**
	 * Yields the transaction reference that installed the jar
	 * where the given class is defined.
	 * 
	 * @param clazz the class
	 * @return the transaction reference
	 * @throws IllegalStateException if the transaction reference cannot be determined
	 */
	TransactionReference transactionThatInstalledJarFor(Class<?> clazz);

	/**
	 * Yields the run-time class of the storage object with the given reference.
	 * 
	 * @param storageReference the storage reference of the object
	 * @return the name of the class
	 * @throws DeserializationError if the class of the object cannot be found
	 */
	String getClassNameOf(StorageReference storageReference);

	/**
	 * Yields a transaction reference whose {@code toString()} is the given string.
	 * 
	 * @param toString the result of {@code toString()} on the desired transaction reference
	 * @return the transaction reference
	 */
	TransactionReference getTransactionReferenceFor(String toString);

	/**
	 * Yields the request that generated the transaction with the given reference.
	 * 
	 * @param transactionReference the reference to the transaction
	 * @return the request
	 * @throws Exception if the request could not be found
	 */
	TransactionRequest<?> getRequestAt(TransactionReference transactionReference) throws Exception;

	/**
	 * Yields the response that was generated by the transaction with the given reference.
	 * 
	 * @param transactionReference the reference to the transaction
	 * @return the response
	 * @throws Exception if the response could not be found
	 */
	TransactionResponse getResponseAt(TransactionReference transactionReference) throws Exception;

	/**
	 * Yields the most recent eager updates for the given storage reference.
	 * 
	 * @param storageReference the storage reference
	 * @param run the run that is building the transaction for which the updates are required
	 * @return the updates; these include the class tag update for the reference
	 * @throws Exception if the updates cannot be found
	 */
	Stream<Update> getLastEagerUpdatesFor(StorageReference storageReference, Consumer<BigInteger> chargeForCPU, TransactionRun run) throws Exception;

	/**
	 * Yields the most recent update for the given non-{@code final} field,
	 * of lazy type, of the object with the given storage reference.
	 * 
	 * @param storageReference the storage reference
	 * @param field the field whose update is being looked for
	 * @param run the run that is building the transaction for which the updates are required
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	UpdateOfField getLastLazyUpdateToNonFinalFieldOf(StorageReference storageReference, FieldSignature field, Consumer<BigInteger> chargeForCPU) throws Exception;

	/**
	 * Yields the most recent update for the given {@code final} field,
	 * of lazy type, of the object with the given storage reference.
	 * Its implementation can be identical to
	 * that of {@link #getLastLazyUpdateToNonFinalFieldOf(StorageReference, FieldSignature, TransactionRun)},
	 * or instead exploit the fact that the field is {@code final}, for an optimized look-up.
	 * 
	 * @param storageReference the storage reference
	 * @param field the field whose update is being looked for
	 * @param run the run that is building the transaction for which the updates are required
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	UpdateOfField getLastLazyUpdateToFinalFieldOf(StorageReference storageReference, FieldSignature field, Consumer<BigInteger> chargeForCPU) throws Exception;

	/**
	 * Yields the UTC time that must be used for a transaction, if it is executed
	 * with this node in this moment.
	 * 
	 * @return the UTC time, as returned by {@link java.lang.System#currentTimeMillis()}
	 */
	long getNow() throws Exception;

	/**
	 * Yields the gas cost model of this node.
	 * 
	 * @return the gas cost model
	 */
	GasCostModel getGasCostModel();
}