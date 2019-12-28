package io.takamaka.code.blockchain.requests;

import java.util.stream.Stream;

import io.takamaka.code.blockchain.Classpath;
import io.takamaka.code.blockchain.annotations.Immutable;

/**
 * A request for a transaction that installs a jar in a blockchain.
 */
@Immutable
public interface AbstractJarStoreTransactionRequest {
	
	/**
	 * Yields the bytes of the jar to install.
	 * 
	 * @return the bytes of the jar to install
	 */
	public byte[] getJar();

	/**
	 * Yields the length, in bytes, of the jar to install.
	 * 
	 * @return the length
	 */
	public int getJarLength();

	/**
	 * Yields the dependencies of the jar to install.
	 * 
	 * @return the dependencies
	 */
	public Stream<Classpath> getDependencies();
}