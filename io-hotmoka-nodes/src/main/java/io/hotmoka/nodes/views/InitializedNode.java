package io.hotmoka.nodes.views;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.internal.InitializedNodeImpl;

/**
 * A node where the jar with the basic Takamaka classes have been installed,
 * along with a gamete and a manifest.
 */
public interface InitializedNode extends Node {

	/**
	 * Yields a decorated node with basic Takamaka classes, gamete and manifest.
	 * 
	 * @param parent the node to decorate
	 * @param takamakaCode the jar containing the basic Takamaka classes
	 * @param greenAmount the amount of green coins that must be put in the gamete
	 * @param redAmount the amount of red coins that must be put in the gamete
	 * @return a decorated view of {@code parent}
	 * @throws TransactionRejectedException if some transaction that installs the jar or creates the accounts is rejected
	 * @throws TransactionException if some transaction that installs the jar or creates the accounts fails
	 * @throws CodeExecutionException if some transaction that installs the jar or creates the accounts throws an exception
	 * @throws IOException if the jar file cannot be accessed
	 */
	static InitializedNode of(Node parent, Path takamakaCode, BigInteger greenAmount, BigInteger redAmount) throws TransactionRejectedException, TransactionException, CodeExecutionException, IOException {
		return new InitializedNodeImpl(parent, takamakaCode, greenAmount, redAmount);
	}
}