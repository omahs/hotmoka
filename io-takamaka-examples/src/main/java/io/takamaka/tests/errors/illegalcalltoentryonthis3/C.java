package io.takamaka.tests.errors.illegalcalltoentryonthis3;

import java.util.stream.IntStream;

import io.takamaka.code.lang.Contract;
import io.takamaka.code.lang.Entry;

public class C extends Contract {

	public @Entry void entry(int i) {}

	public void m() {
		IntStream.iterate(0, i -> i < 5, i -> i + 1).forEachOrdered(this::entry); // OK, this passes this as caller
	}
}