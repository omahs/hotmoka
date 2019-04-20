package takamaka.blockchain;

/**
 * An exception thrown when an illegal request is being executed on a blockchain.
 */
@SuppressWarnings("serial")
public class IllegalTransactionRequestException extends Exception {

	public IllegalTransactionRequestException(String message) {
		super(message);
	}
}