package io.hotmoka.beans;

/**
 * An unexpected error, during the execution of some Hotmoka code.
 */
public class InternalFailureException extends RuntimeException {
	private static final long serialVersionUID = 3975906281624182199L;

	private InternalFailureException(Exception e) {
		super("unexpected exception", e);
	}

	public InternalFailureException(String message) {
		super(message);
	}

	public static InternalFailureException of(Exception e) {
		if (e instanceof InternalFailureException)
			return (InternalFailureException) e;
		else
			return new InternalFailureException(e);
	}
}