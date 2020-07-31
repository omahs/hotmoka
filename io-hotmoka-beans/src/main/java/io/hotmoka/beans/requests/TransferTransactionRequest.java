package io.hotmoka.beans.requests;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.SignatureException;

import io.hotmoka.beans.GasCostModel;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.LongValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;

/**
 * A request for calling the {@code receive} method of a payable contract in a node.
 * This is an optimized version of a request for calling an instance method, whose
 * goal is to have a more compact marshalling in the memory of the node.
 */
public class TransferTransactionRequest extends InstanceMethodCallTransactionRequest {

	final static byte SELECTOR_TRANSFER_INT = 7;
	final static byte SELECTOR_TRANSFER_LONG = 8;
	final static byte SELECTOR_TRANSFER_BIG_INTEGER = 9;

	/**
	 * The fixed gas limit used for a coin transfer.
	 */
	public final static BigInteger GAS_LIMIT = BigInteger.valueOf(10_000L);

	/**
	 * The method that gets called with a big integer.
	 */
	private final static MethodSignature receiveBigInteger = new VoidMethodSignature(ClassType.PAYABLE_CONTRACT, "receive", ClassType.BIG_INTEGER);

	/**
	 * The method that gets called with an int.
	 */
	private final static MethodSignature receiveInt = new VoidMethodSignature(ClassType.PAYABLE_CONTRACT, "receive", BasicTypes.INT);

	/**
	 * The method that gets called with a long.
	 */
	private final static MethodSignature receiveLong = new VoidMethodSignature(ClassType.PAYABLE_CONTRACT, "receive", BasicTypes.LONG);

	/**
	 * Builds a request for calling the {@code receive} method of a payable contract in a node.
	 * 
	 * @param signer the signer of the request
	 * @param caller the caller, that pays for the transferred coins
	 * @param nonce the nonce used for transaction ordering and to forbid transaction replay; it is relative to the {@code caller}
	 * @param chainId the chain identifier where this request can be executed, to forbid transaction replay across chains
	 * @param gasPrice the coins payed for each unit of gas consumed by the transaction
	 * @param classpath the class path where the {@code caller} can be interpreted and the code must be executed
	 * @param receiver the receiver of the call
	 * @param howMuch how much coins must be transferred
	 * @throws SignatureException if the signer cannot sign the request
	 * @throws InvalidKeyException if the signer uses an invalid private key
	 */
	public TransferTransactionRequest(Signer signer, StorageReference caller, BigInteger nonce, String chainId, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, BigInteger howMuch) throws InvalidKeyException, SignatureException {
		super(signer, caller, nonce, chainId, GAS_LIMIT, gasPrice, classpath, receiveBigInteger, receiver, new BigIntegerValue(howMuch));
	}

	/**
	 * Builds a request for calling the {@code receive} method of a payable contract in a node.
	 * 
	 * @param signature the signature of the request
	 * @param caller the caller, that pays for the transferred coins
	 * @param nonce the nonce used for transaction ordering and to forbid transaction replay; it is relative to the {@code caller}
	 * @param chainId the chain identifier where this request can be executed, to forbid transaction replay across chains
	 * @param gasPrice the coins payed for each unit of gas consumed by the transaction
	 * @param classpath the class path where the {@code caller} can be interpreted and the code must be executed
	 * @param receiver the receiver of the call
	 * @param howMuch how much coins must be transferred
	 */
	public TransferTransactionRequest(byte[] signature, StorageReference caller, BigInteger nonce, String chainId, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, BigInteger howMuch) {
		super(signature, caller, nonce, chainId, GAS_LIMIT, gasPrice, classpath, receiveBigInteger, receiver, new BigIntegerValue(howMuch));
	}

	/**
	 * Builds a request for calling the {@code receive} method of a payable contract in a node.
	 * 
	 * @param signer the signer of the request
	 * @param caller the caller, that pays for the transferred coins
	 * @param nonce the nonce used for transaction ordering and to forbid transaction replay; it is relative to the {@code caller}
	 * @param chainId the chain identifier where this request can be executed, to forbid transaction replay across chains
	 * @param gasPrice the coins payed for each unit of gas consumed by the transaction
	 * @param classpath the class path where the {@code caller} can be interpreted and the code must be executed
	 * @param receiver the receiver of the call
	 * @param howMuch how much coins must be transferred
	 * @throws SignatureException if the signer cannot sign the request
	 * @throws InvalidKeyException if the signer uses an invalid private key
	 */
	public TransferTransactionRequest(Signer signer, StorageReference caller, BigInteger nonce, String chainId, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, int howMuch) throws InvalidKeyException, SignatureException {
		super(signer, caller, nonce, chainId, GAS_LIMIT, gasPrice, classpath, receiveInt, receiver, new IntValue(howMuch));
	}

	/**
	 * Builds a request for calling the {@code receive} method of a payable contract in a node.
	 * 
	 * @param signature the signature of the request
	 * @param caller the caller, that pays for the transferred coins
	 * @param nonce the nonce used for transaction ordering and to forbid transaction replay; it is relative to the {@code caller}
	 * @param chainId the chain identifier where this request can be executed, to forbid transaction replay across chains
	 * @param gasPrice the coins payed for each unit of gas consumed by the transaction
	 * @param classpath the class path where the {@code caller} can be interpreted and the code must be executed
	 * @param receiver the receiver of the call
	 * @param howMuch how much coins must be transferred
	 */
	public TransferTransactionRequest(byte[] signature, StorageReference caller, BigInteger nonce, String chainId, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, int howMuch) {
		super(signature, caller, nonce, chainId, GAS_LIMIT, gasPrice, classpath, receiveInt, receiver, new IntValue(howMuch));
	}

	/**
	 * Builds a request for calling the {@code receive} method of a payable contract in a node.
	 * 
	 * @param signer the signer of the request
	 * @param caller the caller, that pays for the transferred coins
	 * @param nonce the nonce used for transaction ordering and to forbid transaction replay; it is relative to the {@code caller}
	 * @param chainId the chain identifier where this request can be executed, to forbid transaction replay across chains
	 * @param gasPrice the coins payed for each unit of gas consumed by the transaction
	 * @param classpath the class path where the {@code caller} can be interpreted and the code must be executed
	 * @param receiver the receiver of the call
	 * @param howMuch how much coins must be transferred
	 * @throws SignatureException if the signer cannot sign the request
	 * @throws InvalidKeyException if the signer uses an invalid private key
	 */
	public TransferTransactionRequest(Signer signer, StorageReference caller, BigInteger nonce, String chainId, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, long howMuch) throws InvalidKeyException, SignatureException {
		super(signer, caller, nonce, chainId, GAS_LIMIT, gasPrice, classpath, receiveLong, receiver, new LongValue(howMuch));
	}

	/**
	 * Builds a request for calling the {@code receive} method of a payable contract in a node.
	 * 
	 * @param signature the signature of the request
	 * @param caller the caller, that pays for the transferred coins
	 * @param nonce the nonce used for transaction ordering and to forbid transaction replay; it is relative to the {@code caller}
	 * @param chainId the chain identifier where this request can be executed, to forbid transaction replay across chains
	 * @param gasPrice the coins payed for each unit of gas consumed by the transaction
	 * @param classpath the class path where the {@code caller} can be interpreted and the code must be executed
	 * @param receiver the receiver of the call
	 * @param howMuch how much coins must be transferred
	 */
	public TransferTransactionRequest(byte[] signature, StorageReference caller, BigInteger nonce, String chainId, BigInteger gasPrice, TransactionReference classpath, StorageReference receiver, long howMuch) {
		super(signature, caller, nonce, chainId, GAS_LIMIT, gasPrice, classpath, receiveLong, receiver, new LongValue(howMuch));
	}

	@Override
	public BigInteger size(GasCostModel gasCostModel) {
		// we remove the costs for the gas limit and the static target,
		// since these are fixed and not included in the marshalling of the request
		return super.size(gasCostModel)
			.subtract(gasCostModel.storageCostOf(gasLimit))
			.subtract(getStaticTarget().size(gasCostModel));
	}

	@Override
	public void intoWithoutSignature(ObjectOutputStream oos) throws IOException {
		// more compact implementation than the inherited one

		StorageValue howMuch = actuals().findFirst().get();
		boolean isInt = howMuch instanceof IntValue;
		boolean isLong = howMuch instanceof LongValue;

		if (isInt)
			oos.writeByte(SELECTOR_TRANSFER_INT);
		else if (isLong)
			oos.writeByte(SELECTOR_TRANSFER_LONG);
		else
			oos.writeByte(SELECTOR_TRANSFER_BIG_INTEGER);
			
		caller.intoWithoutSelector(oos);
		marshal(gasPrice, oos);
		classpath.into(oos);
		marshal(nonce, oos);
		oos.writeUTF(chainId);
		receiver.intoWithoutSelector(oos);

		if (isInt)
			oos.writeInt(((IntValue) howMuch).value);
		else if (isLong)
			oos.writeLong(((LongValue) howMuch).value);
		else
			marshal(((BigIntegerValue) howMuch).value, oos);
	}
}