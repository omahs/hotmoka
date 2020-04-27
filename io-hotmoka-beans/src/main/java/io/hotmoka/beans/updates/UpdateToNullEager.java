package io.hotmoka.beans.updates;

import java.io.IOException;
import java.io.ObjectOutputStream;

import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.values.NullValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;

/**
 * An update that states that the field of a given storage object has been
 * modified to {@code null}. The field has eager type.
 * Updates are stored in blockchain and describe the shape of storage objects.
 */
@Immutable
public final class UpdateToNullEager extends AbstractUpdateOfField {
	final static byte SELECTOR = 18;

	/**
	 * Builds an update of a {@link java.math.BigInteger} field.
	 * 
	 * @param object the storage reference of the object whose field is modified
	 * @param field the field that is modified
	 */
	public UpdateToNullEager(StorageReference object, FieldSignature field) {
		super(object, field);
	}

	@Override
	public StorageValue getValue() {
		return NullValue.INSTANCE;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof UpdateToNullEager && super.equals(other);
	}

	@Override
	public boolean isEager() {
		return true;
	}

	@Override
	public void into(ObjectOutputStream oos) throws IOException {
		oos.writeByte(SELECTOR);
		super.into(oos);
	}
}