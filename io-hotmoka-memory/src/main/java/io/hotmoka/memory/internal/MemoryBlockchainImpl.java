package io.hotmoka.memory.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hotmoka.beans.InternalFailureException;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.memory.Config;
import io.hotmoka.memory.MemoryBlockchain;
import io.takamaka.code.engine.AbstractNodeWithHistory;

/**
 * An implementation of a blockchain that stores transactions in a directory
 * on disk memory. It is only meant for experimentation and testing. It is not
 * really a blockchain, since there is no peer-to-peer network, nor mining,
 * nor transactions. Updates are stored in files, rather than in an external database.
 */
public class MemoryBlockchainImpl extends AbstractNodeWithHistory<Config, Store> implements MemoryBlockchain {
	private final static Logger logger = LoggerFactory.getLogger(MemoryBlockchainImpl.class);

	/**
	 * The mempool where transaction requests are stored and eventually executed.
	 */
	private final Mempool mempool;

	/**
	 * Builds a blockchain in disk memory.
	 * 
	 * @param config the configuration of the blockchain
	 */
	public MemoryBlockchainImpl(Config config) {
		super(config);

		try {
			this.mempool = new Mempool(this);
		}
		catch (Exception e) {
			logger.error("failed creating memory blockchain", e);

			try {
				close();
			}
			catch (Exception e1) {
				logger.error("cannot close the blockchain", e1);
				throw InternalFailureException.of(e1);
			}

			throw InternalFailureException.of(e);
		}
	}

	@Override
	protected Store mkStore() {
		return new Store(this);
	}

	@Override
	public void close() throws Exception {
		if (isNotYetClosed()) {
			mempool.stop();
			super.close();
		}
	}

	@Override
	protected void postTransaction(TransactionRequest<?> request) {
		mempool.add(request);
	}
}