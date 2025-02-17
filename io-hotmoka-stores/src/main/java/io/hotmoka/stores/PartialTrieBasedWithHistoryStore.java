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

package io.hotmoka.stores;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import io.hotmoka.annotations.ThreadSafe;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.stores.internal.TrieOfHistories;
import io.hotmoka.xodus.env.Transaction;

/**
 * A historical store of a node. It is a transactional database that keeps
 * the successful responses of the Hotmoka transactions, together with their
 * history (for this reason it is <i>with history</i>).
 * This store has the ability of changing its <i>world view</i> by checking out different
 * hashes of its roots. Hence, it can be used to come back in time or change
 * history branch or create a snapshot of it by simply checking out a different root. Its implementation
 * is based on Merkle-Patricia tries, supported by JetBrains' Xodus transactional database.
 * 
 * The information kept in this store consists of:
 * 
 * <ul>
 * <li> a trie that maps each Hotmoka request reference to the response computed for that request
 * <li> a trie that maps each storage reference to the transaction references that contribute
 *      to provide values to the fields of the storage object at that reference (its <i>history</i>);
 *      this is used by a node to reconstruct the state of the objects in store
 * <li> miscellaneous control information, such as where the node's manifest
 *      is installed or the current number of commits
 * </ul>
 * 
 * This information is added in store by push methods and accessed through get methods.
 */
@ThreadSafe
public abstract class PartialTrieBasedWithHistoryStore extends PartialTrieBasedStore {

	/**
	 * The Xodus store that holds the history of each storage reference, ie, a list of
	 * transaction references that contribute
	 * to provide values to the fields of the storage object at that reference.
	 */
	private final io.hotmoka.xodus.env.Store storeOfHistory;

	/**
	 * The root of the trie of histories. It is an empty array if the trie is empty.
	 */
	private final byte[] rootOfHistories = new byte[32];

	/**
	 * The trie of histories.
	 */
	private TrieOfHistories trieOfHistories;

	/**
     * Creates the store. Its roots are not yet initialized. Hence, after this constructor,
	 * a call to {@link #setRootsTo(byte[])} or {@link #setRootsAsCheckedOut()}
	 * should occur, to set the roots of the store.
     * 
     * @param getResponseUncommittedCached a function that yields the transaction response for the given transaction reference, if any, using a cache
	 * @param dir the path where the database of the store gets created
	 * @param checkableDepth the number of last commits that can be checked out, in order to
	 *                       change the world-view of the store (see {@link #checkout(byte[])}).
	 *                       This entails that such commits are not garbage-collected, until
	 *                       new commits get created on top and they end up being deeper.
	 *                       This is useful if we expect an old state to be checked out, for
	 *                       instance because a blockchain swaps to another history, but we can
	 *                       assume a reasonable depth for that to happen. Use 0 if the store
	 *                       is not checkable, so that all its successive commits can be immediately
	 *                       garbage-collected as soon as a new commit is created on top
	 *                       (which corresponds to a blockchain that never swaps to a previous
	 *                       state, because it has deterministic finality). Use a negative
	 *                       number if all commits must be checkable (hence garbage-collection
	 *                       is disabled)
     */
	protected PartialTrieBasedWithHistoryStore(Function<TransactionReference, Optional<TransactionResponse>> getResponseUncommittedCached, Path dir, long checkableDepth) {
		super(getResponseUncommittedCached, dir, checkableDepth);

		AtomicReference<io.hotmoka.xodus.env.Store> storeOfHistory = new AtomicReference<>();
		env.executeInTransaction(txn -> storeOfHistory.set(env.openStoreWithoutDuplicates("history", txn)));
		this.storeOfHistory = storeOfHistory.get();
	}

    @Override
	public Stream<TransactionReference> getHistory(StorageReference object) {
    	synchronized (lock) {
    		return env.computeInReadonlyTransaction
    			(txn -> new TrieOfHistories(storeOfHistory, txn, nullIfEmpty(rootOfHistories), -1L).get(object));
    	}
	}

	@Override
	public Stream<TransactionReference> getHistoryUncommitted(StorageReference object) {
		synchronized (lock) {
			return duringTransaction() ? trieOfHistories.get(object) : getHistory(object);
		}
	}

	@Override
	public Transaction beginTransaction(long now) {
		synchronized (lock) {
			Transaction txn = super.beginTransaction(now);
			trieOfHistories = new TrieOfHistories(storeOfHistory, txn, nullIfEmpty(rootOfHistories), getNumberOfCommits());
			return txn;
		}
	}

	@Override
	protected void garbageCollect(long commitNumber) {
		super.garbageCollect(commitNumber);
		trieOfHistories.garbageCollect(commitNumber);
	}

	@Override
	public void checkout(byte[] root) {
		synchronized (lock) {
			super.checkout(root);
		}
	}

	@Override
	protected void setHistory(StorageReference object, Stream<TransactionReference> history) {
		trieOfHistories.put(object, history);
	}

	@Override
	protected byte[] mergeRootsOfTries() {
		// this can be null if this is called before any new transaction has been executed over this store
		if (trieOfHistories == null)
			return super.mergeRootsOfTries();

		byte[] superMerge = super.mergeRootsOfTries();
		byte[] result = new byte[superMerge.length + 32];
		System.arraycopy(superMerge, 0, result, 0, superMerge.length);

		byte[] rootOfHistories = trieOfHistories.getRoot();
		if (rootOfHistories != null)
			System.arraycopy(rootOfHistories, 0, result, superMerge.length, 32);

		return result;
	}

	@Override
	protected void setRootsTo(byte[] root) {
		super.setRootsTo(root);

		if (root == null)
			Arrays.fill(rootOfHistories, (byte) 0);
		else
			System.arraycopy(root, 64, rootOfHistories, 0, 32);
	}

	@Override
	protected boolean isEmpty() {
		return super.isEmpty() && isEmpty(rootOfHistories);
	}
}