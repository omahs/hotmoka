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

package io.hotmoka.beans.marshalling;

import java.io.IOException;
import java.io.InputStream;

import io.hotmoka.marshalling.Marshallable;
import io.hotmoka.marshalling.Unmarshaller;
import io.hotmoka.marshalling.UnmarshallingContext;

/**
 * A function that unmarshals a single marshallable bean.
 *
 * @param <T> the type of the marshallable bean
 */
public interface BeanUnmarshaller<T extends Marshallable> extends Unmarshaller<T> {

	@Override
	default UnmarshallingContext mkContext(InputStream is) throws IOException {
		return new BeanUnmarshallingContext(is);
	}
}