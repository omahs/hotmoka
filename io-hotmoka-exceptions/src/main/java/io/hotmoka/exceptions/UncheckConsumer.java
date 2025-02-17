/*
Copyright 2023 Fausto Spoto

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

package io.hotmoka.exceptions;

import java.util.function.Consumer;

/**
 * This class provides a method to transform a consumer with exceptions
 * into a consumer, by unchecking its exceptions.
 */
public abstract class UncheckConsumer {

	private UncheckConsumer() {}

	/**
	 * Transforms a consumer with exceptions into a consumer without checked
	 * exceptions. This means that all checked exceptions get wrapped into
	 * a {@link UncheckedException}. They can later be recovered through
	 * a method from {@link CheckRunnable} or {@link CheckSupplier}.
	 * 
	 * @param <T> the type of the consumed value
	 * @param wrapped the consumer with exceptions
	 * @return the consumer without exceptions
	 */
	public static <T> Consumer<T> uncheck(ConsumerWithExceptions<T> wrapped) {
		return new Consumer<>() {
	
			@Override
			public void accept(T t) {
				try {
					wrapped.accept(t);
				}
				catch (RuntimeException | Error e) {
					throw e;
				}
				catch (Throwable e) {
					if (InterruptedException.class.isInstance(e))
						Thread.currentThread().interrupt();

					throw new UncheckedException(e);
				}
			}
		};
	}
}