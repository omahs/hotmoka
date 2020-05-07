package io.hotmoka.tendermint.internal;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hotmoka.beans.Marshallable;
import io.hotmoka.beans.Marshallable.Unmarshaller;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.references.LocalTransactionReference;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.values.StorageReference;
import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.ExodusException;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;

/**
 * The state of a blockchain built over Tendermint. It is a transactional database that keeps
 * information about the state of the objects created by the transactions executed
 * by the blockchain. This state is external to the blockchain and only
 * its hash is stored in blockchain at the end of each block, for consensus.
 * The information kept in this state consists of:
 * 
 * <ul>
 * <li> a map from each Hotmoka transaction reference to the response computed for that transaction
 * <li> a map from each storage reference to the transaction references that contribute
 *      to provide values to the fields of the storage object at that reference
 * <li> a map from each Hotmoka transaction reference to the hash of the corresponding
 *      Tendermint transaction 
 * <li> some miscellaneous control information, such as  where the jar with basic
 *      Takamaka classes is installed, or which is the reference that must be
 *      used for the next transaction 
 * </ul>
 * 
 * This information is added in store by put methods and accessed through get methods.
 * Information between paired begin transaction and commit transaction is committed into
 * the file system.
 * 
 * The implementation of this state uses JetBrains's Xodus transactional database.
 */
class State implements AutoCloseable {

	/**
	 * The Xodus environment that holds the state.
	 */
	private final Environment env;

	/**
	 * The transaction that accumulates all changes from begin of block to commit of block.
	 */
	private Transaction txn;

	/**
	 * The store that holds the responses to the transactions.
	 */
	private Store responses;

	/**
	 * The store that holds the history of each storage reference, ie, a list of
	 * transaction references that contribute
	 * to provide values to the fields of the storage object at that reference.
	 */
	private Store history;

	/**
	 * The store that holds a map from each Hotmoka transaction reference to the hash of the
	 * corresponding Tendermint transaction 
	 */
	private Store hashes;

	/**
	 * The store that holds miscellaneous information about the state.
	 */
    private Store info;

    /**
	 * The time spent inside the state procedures, for profiling.
	 */
	private long stateTime;

	/**
     * The key used inside {@linkplain #info} to keep the transaction reference
     * that installed the Takamaka base classes in blockchain.
     */
    private final static ByteIterable TAKAMAKA_CODE = ArrayByteIterable.fromByte((byte) 0);

    /**
     * The key used inside {@linkplain #info} to keep the transaction reference
     * that installed a user jar in blockchain, if any. This is mainly used to simplify the tests.
     */
    private final static ByteIterable JAR = ArrayByteIterable.fromByte((byte) 1);

    /**
     * The key used inside {@linkplain #info} to keep the storage references
     * of the initial accounts in blockchain, created in the constructor of
     * {@linkplain io.hotmoka.tendermint.internal.TendermintBlockchainImpl}.
     * This is an array of storage references, from the first account to the last account.
     */
    private final static ByteIterable ACCOUNTS = ArrayByteIterable.fromByte((byte) 2);

    /**
     * The key used inside {@linkplain #info} to keep the number of commits executed over this state.
     */
    private final static ByteIterable COMMIT_COUNT = ArrayByteIterable.fromByte((byte) 3);

    /**
     * The key used inside {@linkplain #info} to keep the last committed transaction reference.
     */
    private final static ByteIterable NEXT = ArrayByteIterable.fromByte((byte) 4);

    private final static Logger logger = LoggerFactory.getLogger(State.class);

    /**
     * Creates a state that gets persisted inside the given directory.
     * 
     * @param dir the directory where the state is persisted
     */
    State(String dir) {
    	this.env = Environments.newInstance(dir);

    	// enforces that all stores exist
    	recordTime(() ->
    		env.executeInTransaction(txn -> {
    			responses = env.openStore("responses", StoreConfig.WITHOUT_DUPLICATES, txn);
    			history = env.openStore("history", StoreConfig.WITHOUT_DUPLICATES, txn);
    			hashes = env.openStore("hashes", StoreConfig.WITHOUT_DUPLICATES, txn);
    			info = env.openStore("info", StoreConfig.WITHOUT_DUPLICATES, txn);
    		})
    	);
    }

    @Override
    public void close() {
    	if (txn != null && !txn.isFinished())
    		// blockchain closed with yet uncommitted transactions: we commit them
    		if (!txn.commit())
    			logger.error("Transaction commit returned false");

    	try {
    		env.close();
    	}
    	catch (ExodusException e) {
    		logger.error("Failed to close environment", e);
    	}

    	logger.info("Time spent in state procedures: " + stateTime + "ms");
    }

    /**
     * Starts a transaction. All updates during the transaction are saved
     * if the transaction will later be committed. This is called at the beginning
     * of the execution of the transactions inside a block.
     */
    void beginTransaction() {
    	recordTime(() -> {
    		txn = env.beginTransaction();
    		responses = env.openStore("responses", StoreConfig.USE_EXISTING, txn);
    		history = env.openStore("history", StoreConfig.USE_EXISTING, txn);
    		hashes = env.openStore("hashes", StoreConfig.USE_EXISTING, txn);
    		info = env.openStore("info", StoreConfig.USE_EXISTING, txn);
    	});
    }

	/**
	 * Commits all data put from last call to {@linkplain #beginTransaction()}.
	 */
	void commitTransaction() {
		recordTime(() -> {
			increaseNumberOfCommits();
			if (!txn.commit())
				logger.info("Transaction commit returned false");
		});
	}

	/**
	 * Puts in state the result of a transaction having the given reference.
	 * 
	 * @param reference the reference of the transaction
	 * @param response the response of the transaction
	 */
	void putResponse(TransactionReference reference, TransactionResponse response) {
		recordTime(() -> {
			ByteIterable referenceAsByteArray = intoByteArray(reference);
			ByteIterable responseAsByteArray = intoByteArray(response);
			env.executeInTransaction(txn -> responses.put(txn, referenceAsByteArray, responseAsByteArray));
		});
	}

	/**
	 * Puts in state the hash that Tendermint uses to refer to the Hotmoka transaction having the given reference.
	 * 
	 * @param reference the reference of the Hotmoka transaction
	 * @param hash the Tendermint hash
	 */
	void putHash(TransactionReference reference, String hash) {
		recordTime(() -> {
			ByteIterable referenceAsByteArray = intoByteArray(reference);
			ByteIterable hashAsByteArray = new ArrayByteIterable(hash.getBytes());
			env.executeInTransaction(txn -> hashes.put(txn, referenceAsByteArray, hashAsByteArray));
		});
	}

	/**
	 * Sets the history of the given object.
	 * 
	 * @param object the object
	 * @param history the history, that is, the transaction references transaction references that contribute
	 *                to provide values to the fields of {@code object}
	 */
	void putHistory(StorageReference object, Stream<TransactionReference> history) {
		recordTime(() -> {
			ByteIterable historyAsByteArray = intoByteArray(history.toArray(TransactionReference[]::new));
			ByteIterable objectAsByteArray = intoByteArray(object);
			env.executeInTransaction(txn -> this.history.put(txn, objectAsByteArray, historyAsByteArray));
		});
	}

	/**
	 * Puts in state the classpath of the transaction that installed the Takamaka
	 * base classes in blockchain.
	 * 
	 * @param takamakaCode the classpath
	 */
	void putTakamakaCode(Classpath takamakaCode) {
		putIntoInfo(TAKAMAKA_CODE, takamakaCode);
	}

	/**
	 * Puts in state the classpath of the transaction that installed a user jar in blockchain.
	 * This might be missing and is mainly used to simplify the tests.
	 * 
	 * @param takamakaCode the classpath
	 */
	void putJar(Classpath jar) {
		putIntoInfo(JAR, jar);
	}

	/**
	 * Puts in state the reference that can be used for the next transaction.
	 * 
	 * @param next the reference
	 */
	void putNext(TransactionReference next) {
		putIntoInfo(NEXT, next);
	}

	/**
	 * Puts in state the storage reference to a new initial account.
	 * 
	 * @param account the storage reference of the account to add
	 */
	void putAccount(StorageReference account) {
		recordTime(() -> {
			ByteIterable accountsAsByteArray = intoByteArrayWithoutSelector(Stream.concat(getAccounts(), Stream.of(account)).toArray(StorageReference[]::new));
			env.executeInTransaction(txn -> info.put(txn, ACCOUNTS, accountsAsByteArray));
		});
	}

	/**
	 * Yields the response of the transaction having the given reference.
	 * 
	 * @param reference the reference of the transaction
	 * @return the response, if any
	 */
	Optional<TransactionResponse> getResponse(TransactionReference reference) {
		return recordTime(() -> {
			ByteIterable referenceAsByteArray = intoByteArray(reference);
			ByteIterable responseAsByteArray = env.computeInReadonlyTransaction(txn -> responses.get(txn, referenceAsByteArray));
			return responseAsByteArray == null ? Optional.empty() : Optional.of(fromByteArray(TransactionResponse::from, responseAsByteArray));
		});
	}

	/**
	 * Yields the hash that Tendermint uses to refer to the given Hotmoka transaction.
	 * 
	 * @param reference the reference of the Hotmoka transaction
	 * @return the hash, if any
	 */
	Optional<String> getHash(TransactionReference reference) {
		return recordTime(() -> {
			ByteIterable referenceAsByteArray = intoByteArray(reference);
			ByteIterable responseAsByteArray = env.computeInReadonlyTransaction(txn -> hashes.get(txn, referenceAsByteArray));
			return responseAsByteArray == null ? Optional.empty() : Optional.of(new String(responseAsByteArray.getBytesUnsafe()));
		});
	}

	/**
	 * Yields the history of the given object, that is, the references of the transactions
	 * that provide information about the current values of its fields.
	 * 
	 * @param object the reference of the object
	 * @return the history. Yields an empty stream if there is no history for {@code object}
	 */
	Stream<TransactionReference> getHistory(StorageReference object) {
		return recordTime(() -> {
			ByteIterable historyAsByteArray = env.computeInReadonlyTransaction(txn -> history.get(txn, intoByteArray(object)));
			return historyAsByteArray == null ? Stream.empty() : Stream.of(fromByteArray(TransactionReference::from, TransactionReference[]::new, historyAsByteArray));
		});
	}

	/**
	 * Yields the number of commits already performed over this state.
	 * 
	 * @return the number of commits
	 */
	long getNumberOfCommits() {
		ByteIterable numberOfCommitsAsByteIterable = getFromInfo(COMMIT_COUNT);
		return numberOfCommitsAsByteIterable == null ? 0L : Long.valueOf(new String(numberOfCommitsAsByteIterable.getBytesUnsafe()));
	}

	/**
	 * Yields the classpath of the Takamaka base classes in blockchain.
	 * 
	 * @return the classpath
	 */
	Optional<Classpath> getTakamakaCode() {
		ByteIterable takamakaCode = getFromInfo(TAKAMAKA_CODE);
		return takamakaCode == null ? Optional.empty() : Optional.of(fromByteArray(Classpath::from, takamakaCode));
	}

	/**
	 * Yields the classpath of a user jar installed in blockchain, if any.
	 * This is mainly used to simplify the tests.
	 * 
	 * @return the classpath
	 */
	Optional<Classpath> getJar() {
		ByteIterable jar = getFromInfo(JAR);
		return jar == null ? Optional.empty() : Optional.of(fromByteArray(Classpath::from, jar));
	}

	/**
	 * Yields the reference that can be used for the next transaction.
	 * 
	 * @return the reference, if any
	 */
	Optional<TransactionReference> getNext() {
		ByteIterable next = getFromInfo(NEXT);
		return next == null ? Optional.empty() : Optional.of(fromByteArray(LocalTransactionReference::from, next));
	}

	/**
	 * Yields the initial accounts.
	 * 
	 * @return the accounts, as an ordered stream from the first to the last account
	 */
	Stream<StorageReference> getAccounts() {
		ByteIterable accounts = getFromInfo(ACCOUNTS);
		return accounts == null ? Stream.empty() : Stream.of(fromByteArray(StorageReference::from, StorageReference[]::new, accounts));
	}

	/**
	 * Increases the number of commits performed over this state.
	 */
	private void increaseNumberOfCommits() {
		recordTime(() -> 
			env.executeInTransaction(txn -> {
				ByteIterable numberOfCommitsAsByteIterable = info.get(txn, COMMIT_COUNT);
				long numberOfCommits = numberOfCommitsAsByteIterable == null ? 0L : Long.valueOf(new String(numberOfCommitsAsByteIterable.getBytesUnsafe()));
				info.put(txn, COMMIT_COUNT, new ArrayByteIterable(Long.toString(numberOfCommits + 1).getBytes()));
			}));
	}

	/**
	 * Yields the value of the given property in the {@linkplain #info} store.
	 * 
	 * @return true if and only if {@code markAsInitialized()} has been already called
	 */
	private ByteIterable getFromInfo(ByteIterable key) {
		return recordTime(() -> env.computeInReadonlyTransaction(txn -> info.get(txn, key)));
	}

	/**
	 * Puts in state the given value, inside the {@linkplain #info} store.
	 * 
	 * @param key the key where the value must be put
	 * @param value the value to put
	 */
	private void putIntoInfo(ByteIterable key, Marshallable value) {
		recordTime(() -> {
			ByteIterable valueAAsByteArray = intoByteArray(value);
			env.executeInTransaction(txn -> info.put(txn, key, valueAAsByteArray));
		});
	}

	/**
	 * Executes the given task, taking note of the time required for it.
	 * 
	 * @param task the task
	 */
	private void recordTime(Runnable task) {
		long start = System.currentTimeMillis();
		task.run();
		stateTime += (System.currentTimeMillis() - start);
	}

	private interface TimedTask<T> {
		T call();
	}

	/**
	 * Executes the given task, taking note of the time required for it.
	 * 
	 * @param task the task
	 */
	private <T> T recordTime(TimedTask<T> task) {
		long start = System.currentTimeMillis();
		T result = task.call();
		stateTime += (System.currentTimeMillis() - start);
		return result;
	}

	private static ArrayByteIterable intoByteArray(Marshallable marshallable) throws UncheckedIOException {
		try {
			return new ArrayByteIterable(marshallable.toByteArray());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ArrayByteIterable intoByteArray(StorageReference reference) throws UncheckedIOException {
		try {
			return new ArrayByteIterable(reference.toByteArrayWithoutSelector()); // more optimized than a normal marshallable
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ArrayByteIterable intoByteArray(Marshallable[] marshallables) throws UncheckedIOException {
		try {
			return new ArrayByteIterable(Marshallable.toByteArray(marshallables));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ArrayByteIterable intoByteArrayWithoutSelector(StorageReference[] references) throws UncheckedIOException {
		try {
			return new ArrayByteIterable(Marshallable.toByteArrayWithoutSelector(references)); // more optimized than an array of normal marshallables
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static <T extends Marshallable> T fromByteArray(Unmarshaller<T> unmarshaller, ByteIterable bytes) throws UncheckedIOException {
		try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes.getBytesUnsafe())))) {
			return unmarshaller.from(ois);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static <T extends Marshallable> T[] fromByteArray(Unmarshaller<T> unmarshaller, Function<Integer,T[]> supplier, ByteIterable bytes) throws UncheckedIOException {
		try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes.getBytesUnsafe())))) {
			return Marshallable.unmarshallingOfArray(unmarshaller, supplier, ois);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}