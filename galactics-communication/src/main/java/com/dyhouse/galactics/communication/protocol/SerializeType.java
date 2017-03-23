package com.dyhouse.galactics.communication.protocol;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public enum SerializeType {
    JSON((byte) 0),
    GALACTICS((byte) 1);

    private byte code;

    SerializeType(byte code) {
        this.code = code;
    }

    public static SerializeType valueOf(byte code) {
        for (SerializeType serializeType : SerializeType.values()) {
            if (serializeType.getCode() == code) {
                return serializeType;
            }
        }
        return null;
    }

    public byte getCode() {
        return code;
    }
}
