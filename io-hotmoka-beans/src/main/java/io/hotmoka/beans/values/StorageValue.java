package io.hotmoka.beans.values;

import java.io.IOException;
import java.math.BigInteger;

import io.hotmoka.beans.GasCostModel;
import io.hotmoka.beans.Marshallable;
import io.hotmoka.beans.UnmarshallingContext;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.types.StorageType;

/**
 * A value that can be stored in the blockchain, passed as argument to an entry
 * or returned from an entry.
 */
public abstract class StorageValue extends Marshallable implements Comparable<StorageValue> {

	/**
	 * Yields a storage value from the given string and of the given type.
	 * 
	 * @param s the string; use "null" (without quotes) for null; use the fully-qualified
	 *        representation for enum's (such as "com.mycompany.MyEnum.CONSTANT")
	 * @param type the type of the storage value
	 * @return the resulting storage value
	 */
	public static StorageValue of(String s, StorageType type) {
		if (type instanceof BasicTypes)
			switch ((BasicTypes) type) {
			case BOOLEAN: return new BooleanValue(Boolean.valueOf(s));
			case BYTE: return new ByteValue(Byte.valueOf(s));
			case CHAR: {
				if (s.length() != 1)
					throw new IllegalArgumentException("the value is not a character");
				else
					return new CharValue(Character.valueOf(s.charAt(0)));
			}
			case SHORT: return new ShortValue(Short.valueOf(s));
			case INT: return new IntValue(Integer.valueOf(s));
			case LONG: return new LongValue(Long.valueOf(s));
			case FLOAT: return new FloatValue(Float.valueOf(s));
			default: return new DoubleValue(Double.valueOf(s));
			}
		else if (ClassType.STRING.equals(type))
			return new StringValue(s);
		else if (ClassType.BIG_INTEGER.equals(type))
			return new BigIntegerValue(new BigInteger(s));
		else if ("null".equals(s))
			return NullValue.INSTANCE;
		else if (!s.contains("#")) {
			int lastDot = s.lastIndexOf('.');
			if (lastDot < 0)
				throw new IllegalArgumentException("Cannot interpret value " + s);
			else
				return new EnumValue(s.substring(0, lastDot), s.substring(lastDot + 1));
		}
		else
			return new StorageReference(s);
	}

	/**
	 * Yields the size of this value, in terms of gas units consumed in store.
	 * 
	 * @param gasCostModel the model of gas costs
	 * @return the size
	 */
	public BigInteger size(GasCostModel gasCostModel) {
		return BigInteger.valueOf(gasCostModel.storageCostPerSlot());
	}

	/**
	 * Factory method that unmarshals a value from the given stream.
	 * 
	 * @param context the unmarshalling context
	 * @return the value
	 * @throws IOException if the value could not be unmarshalled
	 * @throws ClassNotFoundException if the value could not be unmarshalled
	 */
	public static StorageValue from(UnmarshallingContext context) throws IOException, ClassNotFoundException {
		byte selector = context.ois.readByte();
		switch (selector) {
		case BigIntegerValue.SELECTOR: return new BigIntegerValue(unmarshallBigInteger(context));
		case BooleanValue.SELECTOR_TRUE: return BooleanValue.TRUE;
		case BooleanValue.SELECTOR_FALSE: return BooleanValue.FALSE;
		case ByteValue.SELECTOR: return new ByteValue(context.ois.readByte());
		case CharValue.SELECTOR: return new CharValue(context.ois.readChar());
		case DoubleValue.SELECTOR: return new DoubleValue(context.ois.readDouble());
		case EnumValue.SELECTOR: return new EnumValue(context.ois.readUTF(), context.ois.readUTF());
		case FloatValue.SELECTOR: return new FloatValue(context.ois.readFloat());
		case IntValue.SELECTOR: return new IntValue(context.ois.readInt());
		case LongValue.SELECTOR: return new LongValue(context.ois.readLong());
		case NullValue.SELECTOR: return NullValue.INSTANCE;
		case ShortValue.SELECTOR: return new ShortValue(context.ois.readShort());
		case StorageReference.SELECTOR: return StorageReference.from(context);
		case StringValue.SELECTOR: return new StringValue(context.ois.readUTF());
		default:
			if (selector < 0)
				return new IntValue((selector + 256) - IntValue.SELECTOR - 1);
			else
				return new IntValue(selector - IntValue.SELECTOR - 1);
		}
	}
}