package com.dyhouse.galactics.communication.protocol;

/**
 * Created by aaron.pan on 2017/3/10.
 */
public enum  LanguageCode  {
    JAVA((byte) 0),
    HTTP((byte) 8);

    private byte code;

    LanguageCode(byte code) {
        this.code = code;
    }

    public static LanguageCode valueOf(byte code) {
        for (LanguageCode languageCode : LanguageCode.values()) {
            if (languageCode.getCode() == code) {
                return languageCode;
            }
        }
        return null;
    }

    public byte getCode() {
        return code;
    }
}
