package io.takamaka.code.memory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.nodes.CodeExecutionException;
import io.hotmoka.nodes.SequentialNode;
import io.takamaka.code.memory.internal.InitializedRedGreenMemoryBlockchainImpl;

/**
 * An implementation of a blockchain that stores transactions in a directory
 * on disk memory. It is only meant for experimentation and testing. It is not
 * really a blockchain, since there is no peer-to-peer network, nor mining.
 * Updates are stored inside the blocks, rather than in an external database.
 * It provides support for the creation of a given number of initial red/green accounts.
 */
public interface InitializedRedGreenMemoryBlockchain extends SequentialNode {

	/**
	 * Yields a blockchain in disk memory and initializes user accounts with the given initial funds.
	 * 
	 * @param takamakaCodePath the path where the base Takamaka classes can be found. They will be
	 *                         installed in blockchain and will be available later as {@link io.takamaka.code.memory.InitializedRedGreenMemoryBlockchain#takamakaCode()}
	 * @param funds the initial funds of the accounts that are created; they must be understood in pairs, each pair for the green/red
	 *              initial funds of each account (green before red)
	 * @throws IOException if a disk error occurs
	 * @throws TransactionException if some transaction for initialization fails
	 * @throws CodeExecutionException if some transaction for initialization throws an exception
	 */
	static InitializedRedGreenMemoryBlockchain of(Path takamakaCodePath, BigInteger... funds) throws IOException, TransactionException, CodeExecutionException {
		return new InitializedRedGreenMemoryBlockchainImpl(takamakaCodePath, funds);
	}

	/**
	 * Yields the reference, in the blockchain, where the base Takamaka classes have been installed.
	 */
	Classpath takamakaCode();

	/**
	 * Yields the {@code i}th account.
	 * 
	 * @param i the account number
	 * @return the reference to the account, in blockchain
	 */
	StorageReference account(int i);
}