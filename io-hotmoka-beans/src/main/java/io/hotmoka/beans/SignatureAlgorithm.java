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

package io.hotmoka.beans;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

/**
 * An algorithm that signs values and verifies such signatures back.
 *
 * @param <T> the type of values that get signed
 */
public interface SignatureAlgorithm<T> {

	/**
	 * Yields a pair of keys (private/public) that can be used with
	 * this signature algorithm.
	 * 
	 * @return the pair of keys
	 */
	KeyPair getKeyPair();

    /**
	 * Yields the signature of the given value, by using the given private key.
	 * 
	 * @param what the value to sign
	 * @param privateKey the private key used for signing
	 * @return the sequence of bytes
	 * @throws InvalidKeyException if the provided private key is invalid
	 * @throws SignatureException if the value cannot be signed
	 */
	byte[] sign(T what, PrivateKey privateKey) throws InvalidKeyException, SignatureException;

	/**
	 * Verifies that the given signature corresponds to the given value, by using
	 * the given public key.
	 * 
	 * @param what the value whose signature gets verified
	 * @param publicKey the public key; its corresponding private key should have been used for signing
	 * @param signature the signature to verify
	 * @return true if and only if the signature matches
	 * @throws InvalidKeyException if the provided public key is invalid
	 * @throws SignatureException if the value cannot be signed
	 */
	boolean verify(T what, PublicKey publicKey, byte[] signature) throws InvalidKeyException, SignatureException;

	/**
	 * Yields a public key that can be used with this signature, from
	 * its encoded version as a byte array.
	 * 
	 * @param encoding the encoded version of the public key
	 * @return the public key
	 * @throws InvalidKeySpecException if the {@code encoded} key does not match the expected specification
	 */
	PublicKey publicKeyFromEncoding(byte[] encoding) throws InvalidKeySpecException;

	/**
	 * Yields the encoded bytes of the given public key.
	 * 
	 * @param publicKey the public key
	 * @return the encoded bytes of {@code publicKey}
	 */
	byte[] encodingOf(PublicKey publicKey);

	/**
	 * Yields a private key that can be used with this signature, from
	 * its encoded version as a byte array.
	 * 
	 * @param encoding the encoded version of the private key
	 * @return the private key
	 * @throws InvalidKeySpecException if the {@code encoded} key does not match the expected specification
	 */
	PrivateKey privateKeyFromEncoding(byte[] encoding) throws InvalidKeySpecException;

	/**
	 * Yields the encoded bytes of the given private key.
	 * 
	 * @param privateKey the private key
	 * @return the encoded bytes of {@code privateKey}
	 */
	byte[] encodingOf(PrivateKey privateKey);

	/**
	 * Yields the name of the algorithm.
	 * 
	 * @return the name of the algorithm
	 */
	String getName();

	/**
	 * Dumps the given key pair into two pem files, one for the private key and one for the public key.
	 * 
	 * @param filePrefix the prefix of the file names for the private and public key files. The keys
	 *                   will be dumped into {@code filePrefix.pri} and {@code filePrefix.pub}
	 * @param keys the key pair to dump
	 * @throws IOException if the key pair cannot be dumped
	 */
	void dumpAsPem(String filePrefix, KeyPair keys) throws IOException;

	/**
	 * Reads a key pair from its pem files, one for the private key and one for the public key.
	 * 
	 * @param filePrefix the prefix of the file names for the private and public key files. The keys
	 *                   must be contained in {@code filePrefix.pri} and {@code filePrefix.pub}
	 * @return the key pair
	 * @throws IOException if the key pair cannot be read
	 * @throws InvalidKeySpecException if the files contain invalid keys
	 */
	KeyPair readKeys(String filePrefix) throws IOException, InvalidKeySpecException ;
}