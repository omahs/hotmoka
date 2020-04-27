package io.hotmoka.beans.responses;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;

/**
 * A response for a successful transaction that calls a method
 * in blockchain. The method has been called without problems and
 * without generating exceptions. The method does not return {@code void}.
 */
@Immutable
public class MethodCallTransactionSuccessfulResponse extends MethodCallTransactionResponse implements TransactionResponseWithEvents {
	final static byte SELECTOR = 9;

	/**
	 * The return value of the method.
	 */
	public final StorageValue result;

	/**
	 * The events generated by this transaction.
	 */
	private final StorageReference[] events;

	/**
	 * Builds the transaction response.
	 * 
	 * @param result the value returned by the method
	 * @param updates the updates resulting from the execution of the transaction
	 * @param events the events resulting from the execution of the transaction
	 * @param gasConsumedForCPU the amount of gas consumed by the transaction for CPU execution
	 * @param gasConsumedForRAM the amount of gas consumed by the transaction for RAM allocation
	 * @param gasConsumedForStorage the amount of gas consumed by the transaction for storage consumption
	 */
	public MethodCallTransactionSuccessfulResponse(StorageValue result, Stream<Update> updates, Stream<StorageReference> events, BigInteger gasConsumedForCPU, BigInteger gasConsumedForRAM, BigInteger gasConsumedForStorage) {
		super(updates, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);

		this.events = events.toArray(StorageReference[]::new);
		this.result = result;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof MethodCallTransactionSuccessfulResponse) {
			MethodCallTransactionSuccessfulResponse otherCast = (MethodCallTransactionSuccessfulResponse) other;
			return super.equals(other) && result.equals(otherCast.result) && Arrays.equals(events, otherCast.events);
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ Arrays.hashCode(events) ^ result.hashCode();
	}

	@Override
	public String toString() {
        return super.toString() + "\n"
        	+ "  returned value: " + result + "\n"
        	+ "  events:\n" + getEvents().map(StorageReference::toString).collect(Collectors.joining("\n    ", "    ", ""));
	}

	@Override
	public Stream<StorageReference> getEvents() {
		return Stream.of(events);
	}

	@Override
	public StorageValue getOutcome() {
		return result;
	}

	@Override
	public void into(ObjectOutputStream oos) throws IOException {
		oos.writeByte(SELECTOR);
		super.into(oos);
		result.into(oos);
		intoArray(events, oos);
	}
}