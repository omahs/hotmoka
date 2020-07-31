package io.hotmoka.beans.responses;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.stream.Stream;

import io.hotmoka.beans.GasCostModel;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.values.StorageValue;

/**
 * A response for a failed transaction that should have called a method in blockchain.
 */
@Immutable
public class MethodCallTransactionFailedResponse extends MethodCallTransactionResponse implements TransactionResponseFailed {
	final static byte SELECTOR = 8;

	/**
	 * The amount of gas consumed by the transaction as penalty for the failure.
	 */
	public final BigInteger gasConsumedForPenalty;

	/**
	 * The fully-qualified class name of the cause exception.
	 */
	public final String classNameOfCause;

	/**
	 * The message of the cause exception.
	 */
	public final String messageOfCause;

	/**
	 * The program point where the cause exception occurred.
	 */
	public final String where;

	/**
	 * Builds the transaction response.
	 * 
	 * @param classNameOfCause the fully-qualified class name of the cause exception
	 * @param messageOfCause of the message of the cause exception; this might be {@code null}
	 * @param where the program point where the cause exception occurred; this might be {@code null}
	 * @param updates the updates resulting from the execution of the transaction
	 * @param gasConsumedForCPU the amount of gas consumed by the transaction for CPU execution
	 * @param gasConsumedForRAM the amount of gas consumed by the transaction for RAM allocation
	 * @param gasConsumedForStorage the amount of gas consumed by the transaction for storage consumption
	 * @param gasConsumedForPenalty the amount of gas consumed by the transaction as penalty for the failure
	 */
	public MethodCallTransactionFailedResponse(String classNameOfCause, String messageOfCause, String where, Stream<Update> updates, BigInteger gasConsumedForCPU, BigInteger gasConsumedForRAM, BigInteger gasConsumedForStorage, BigInteger gasConsumedForPenalty) {
		super(updates, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);

		this.gasConsumedForPenalty = gasConsumedForPenalty;
		this.classNameOfCause = classNameOfCause;
		this.messageOfCause = messageOfCause == null ? "" : messageOfCause;
		this.where = where == null ? "" : where;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof MethodCallTransactionFailedResponse) {
			MethodCallTransactionFailedResponse otherCast = (MethodCallTransactionFailedResponse) other;
			return super.equals(other) && gasConsumedForPenalty.equals(otherCast.gasConsumedForPenalty)
				&& classNameOfCause.equals(otherCast.classNameOfCause)
				&& messageOfCause.equals(otherCast.messageOfCause)
				&& where.equals(otherCast.where);
		}
		else
			return false;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ gasConsumedForPenalty.hashCode() ^ classNameOfCause.hashCode()
			^ messageOfCause.hashCode() ^ where.hashCode();
	}

	@Override
	protected String gasToString() {
		return super.gasToString() + "  gas consumed for penalty: " + gasConsumedForPenalty + "\n";
	}

	@Override
	public BigInteger gasConsumedForPenalty() {
		return gasConsumedForPenalty;
	}

	@Override
	public String getClassNameOfCause() {
		return classNameOfCause;
	}

	@Override
	public String getMessageOfCause() {
		return messageOfCause;
	}

	@Override
	public String toString() {
        return super.toString()
        	+ "\n  cause: " + classNameOfCause + ":" + messageOfCause;
	}

	@Override
	public StorageValue getOutcome() throws TransactionException {
		throw new TransactionException(classNameOfCause, messageOfCause, where);
	}

	@Override
	public BigInteger size(GasCostModel gasCostModel) {
		return super.size(gasCostModel)
			.add(gasCostModel.storageCostOf(gasConsumedForPenalty))
			.add(gasCostModel.storageCostOf(classNameOfCause))
			.add(gasCostModel.storageCostOf(messageOfCause))
			.add(gasCostModel.storageCostOf(where));
	}

	@Override
	public void into(ObjectOutputStream oos) throws IOException {
		oos.writeByte(SELECTOR);
		super.into(oos);
		marshal(gasConsumedForPenalty, oos);
		oos.writeUTF(classNameOfCause);
		oos.writeUTF(messageOfCause);
		oos.writeUTF(where);
	}
}