package io.takamaka.code.dao;

import io.takamaka.code.lang.*;

import java.math.BigInteger;

/**
 * A test contract that represents a shareholder
 */
public class MyClass extends PayableContract {

    /**
     * Simple and basic constructor that refers to the Contract one
     */
    public MyClass() {
        super();
    }

    /**
     * Create an offer using this contract (SharedEntity2.Offer or SharedEntity3.Offer)
     *
     * @param sharesOnSale the shares on sale, positive
     * @param cost the cost, non-negative
     * @param duration the duration of validity of the offer, in milliseconds from now, always non-negative
     * @return the created offer
     */
    public @FromContract(PayableContract.class) SharedEntity2.Offer<MyClass> createOffer(BigInteger sharesOnSale, BigInteger cost, long duration) {
        return new SharedEntity2.Offer<>(sharesOnSale, cost, duration);
    }

    public @FromContract(PayableContract.class) SharedEntity3.Offer<MyClass> createOffer3(BigInteger sharesOnSale, BigInteger cost, long duration) {
        return new SharedEntity3.Offer<>(this, sharesOnSale, cost, duration);
    }

    /**
     * Place an offer of sale of shares for a shared entity (v2 or v3)
     *
     * @param sh the shared entity where the offer will be placed
     * @param amount the ticket payed to place the offer; implementations may allow zero for this
     * @param offer the offer that is going to be placed
     */
    public @FromContract(PayableContract.class) void placeOffer(SharedEntity2<SharedEntity2.Offer<MyClass>, MyClass> sh, BigInteger amount, SharedEntity2.Offer<MyClass> offer) {
        sh.place(amount, offer);
    }

    public @FromContract(PayableContract.class) void placeOffer(SharedEntity3<SharedEntity3.Offer<MyClass>, MyClass> sh, BigInteger amount, SharedEntity3.Offer<MyClass> offer) {
        sh.place(amount, offer);
    }

}
