import {MarshallingContext} from "./MarshallingContext";
import {StorageReferenceModel} from "../../models/values/StorageReferenceModel";
import {Buffer} from "buffer";

/**
 * An object that can be marshalled into a stream.
 */
export abstract class Marshallable {

    /**
     * Marshalls this object into a buffer.
     * @return the buffer of bytes
     */
    protected marshall(): Buffer {
        const marshallingContext = new MarshallingContext()
        this.into(marshallingContext)

        return marshallingContext.getBuffer()
    }

    /**
     * Marshals this object into a given stream.
     * @param context the context holding the stream
     */
    protected abstract into(context: MarshallingContext): void

    /**
     * Marshals an array of marshallables into a given stream.
     * @param marshallables the array of marshallables
     * @param context the context holding the stream
     */
    public static intoArray(marshallables: Array<Marshallable>, context: MarshallingContext): void {
        context.writeCompactInt(marshallables.length)
        marshallables.forEach(marshallable => marshallable.into(context))
    }

    public static intoArrayWithoutSelector(marshallables: Array<StorageReferenceModel>, context: MarshallingContext): void {
        context.writeCompactInt(marshallables.length);
        marshallables.forEach(marshallable => marshallable.intoWithoutSelector(context))
    }

    /**
     * Marshals an array of marshallables into a buffer.
     * @return the buffer resulting from marshalling the array of marshallables
     */
    public static toBuffer(marshallables: Array<Marshallable>): Buffer {
        const marshallingContext = new MarshallingContext()
        Marshallable.intoArray(marshallables, marshallingContext)

        return marshallingContext.getBuffer()
    }

    /**
     * Marshals this object into a buffer.
     * @return the buffer resulting from marshalling this object
     */
    protected toBuffer(): Buffer {
        const marshallingContext = new MarshallingContext()
        this.into(marshallingContext)

        return marshallingContext.getBuffer()
    }
}