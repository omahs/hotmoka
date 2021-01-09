package io.takamaka.code.dao;

import static io.takamaka.code.lang.Takamaka.require;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.util.stream.Stream;

import io.takamaka.code.lang.FromContract;
import io.takamaka.code.lang.Payable;
import io.takamaka.code.lang.PayableContract;

/**
 * A shared entity where each shareholder cannot own more than a given percent of all shares.
 *
 * @param <O> the type of the offers of sale of shares for this entity
 */
public class SharedEntityWithCappedShares<O extends SharedEntity.Offer> extends SimpleSharedEntity<O> {

	/**
     * The maximal percent of shares that a single shareholder can own.
     */
    private final int percentLimit;

    /**
     * The maximal number of shares that a single shareholder can own.
     */
    private final BigInteger limit;

    /**
     * Creates a shared entity with the given set of shareholders and shares.
     *
     * @param shareholders the initial shareholders; if there are repetitions, their shares are merged
     * @param shares       the initial shares of each initial shareholder. This must have the same length as {@code shareholders}
     * @param percentLimit the maximal percent of shares that a single shareholder can own
     */
    public SharedEntityWithCappedShares(PayableContract[] shareholders, BigInteger[] shares, int percentLimit) {
        super(shareholders, shares);

        require(percentLimit <= 100 && percentLimit > 0, "invalid share limit: it must be between 1 and 100 inclusive");

        this.percentLimit = percentLimit;
        BigInteger totalShares = Stream.of(shares).reduce(ZERO, BigInteger::add);
        this.limit = totalShares.multiply(BigInteger.valueOf(percentLimit)).divide(BigInteger.valueOf(100));
        boolean sharesAreNotOverLimit = Stream.of(shareholders).map(this::sharesOf).allMatch(_shares -> _shares.compareTo(limit) <= 0);
		require(sharesAreNotOverLimit, () -> "a shareholder cannot hold more than " + percentLimit + "% of shares");
    }

    /**
     * Creates a shared entity with one shareholder.
     *
     * @param shareholder the initial shareholder
     * @param share       the initial share of the initial shareholder
     * @param shareLimit  the share limit for a single shareholder in percentage
     */
    public SharedEntityWithCappedShares(PayableContract shareholder, BigInteger share, int shareLimit) {
        this(new PayableContract[]{ shareholder }, new BigInteger[]{ share }, shareLimit);
    }

    /**
     * Creates a shared entity with two shareholders.
     *
     * @param shareholder1 the first initial shareholder
     * @param shareholder2 the second initial shareholder
     * @param share1       the initial share of the first shareholder
     * @param share2       the initial share of the second shareholder
     * @param shareLimit   the share limit for a single shareholder in percentage
     */
    public SharedEntityWithCappedShares(PayableContract shareholder1, PayableContract shareholder2, BigInteger share1, BigInteger share2, int shareLimit) {
        this(new PayableContract[]{ shareholder1, shareholder2 }, new BigInteger[]{ share1, share2 }, shareLimit);
    }

    @Override
    public @FromContract(PayableContract.class) @Payable void accept(BigInteger amount, O offer) {
        super.accept(amount, offer);
        require(sharesOf((PayableContract) caller()).compareTo(limit) <= 0, "a shareholder cannot hold more than " + percentLimit + "% of shares");
    }
}