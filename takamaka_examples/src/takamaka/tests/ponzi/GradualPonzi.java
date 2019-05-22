package takamaka.tests.ponzi;

import static takamaka.lang.Takamaka.require;

import java.math.BigInteger;

import takamaka.lang.Contract;
import takamaka.lang.Entry;
import takamaka.lang.Payable;
import takamaka.lang.PayableContract;
import takamaka.util.StorageList;

/**
 * A contract for a Ponzi investment scheme:
 * It involves taking the money sent by the current investor
 * and transferring it to the previous investors. Each investor
 * gets payed back gradually as soon as new investors arrive.
 * 
 * This example is translated from a Solidity contract shown
 * in "Building Games with Ethereum Smart Contracts", by Iyer and Dannen,
 * page 150, Apress 2018.
 */
public class GradualPonzi extends Contract {
	private final BigInteger MINIMUM_INVESTMENT = BigInteger.valueOf(1_000L);

	/**
	 * All investors up to now. This list might contain the same investor
	 * many times, which is important to pay it back more than investors
	 * who only invested ones.
	 */
	private final StorageList<PayableContract> investors = new StorageList<>();

	public @Entry(PayableContract.class) GradualPonzi() {
		investors.add((PayableContract) caller());
	}

	public @Payable @Entry(PayableContract.class) void invest(BigInteger amount) {
		require(amount.compareTo(MINIMUM_INVESTMENT) >= 0, () -> "You must invest at least " + MINIMUM_INVESTMENT);
		BigInteger eachInvestorGets = amount.divide(BigInteger.valueOf(investors.size()));
		investors.stream().forEach(investor -> send(investor, eachInvestorGets));
		investors.add((PayableContract) caller());
	}

	private void send(PayableContract investor, BigInteger amount) {
		investor.receive(amount);
	}
}