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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.hotmoka.crypto;

import io.hotmoka.crypto.internal.AbstractSignatureAlgorithmImpl;

/**
 * Partial implementation of a signature algorithm.
 * 
 * @param <T> the type of the values that get signed
 */
public abstract class AbstractSignatureAlgorithm<T> extends AbstractSignatureAlgorithmImpl<T> {

	/**
	 * Creates the algorithm.
	 */
	protected AbstractSignatureAlgorithm() {}
}