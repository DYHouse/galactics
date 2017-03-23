package com.dyhouse.galactics.communication.protocol;

import com.alibaba.fastjson.annotation.JSONField;
import com.dyhouse.galactics.communication.CustomHeader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by aaron.pan on 2017/3/9.
 */
public class CommunicationCommand {
    private static AtomicInteger requestId = new AtomicInteger(0);
    public static final String COMMUNICATION_VERSION_KEY = "galactics.communication.version";
    private static final int RPC_TYPE = 0;
    private static final int RPC_ONEWAY = 1; // 0, RPC
    private static SerializeType serializeTypeConfigInThisServer = SerializeType.JSON;
    private static final Map<Class<? extends CustomHeader>, Field[]> CLASS_HASH_MAP =
            new HashMap<Class<? extends CustomHeader>, Field[]>();
    private int code;
    private LanguageCode language = LanguageCode.JAVA;
    private int version = 0;
    private int opaque = requestId.getAndIncrement();
    private int flag = 0;
    private HashMap<String, String> extFields;
    private transient CustomHeader customHeader;
    private static volatile int configVersion = -1;
    private SerializeType serializeTypeCurrent = serializeTypeConfigInThisServer;
    private transient byte[] body;

    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public HashMap<String, String> getExtFields() {
        return extFields;
    }

    public void setExtFields(HashMap<String, String> extFields) {
        this.extFields = extFields;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getOpaque() {
        return opaque;
    }

    public void setOpaque(int opaque) {
        this.opaque = opaque;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public LanguageCode getLanguage() {
        return language;
    }

    public void setLanguage(LanguageCode language) {
        this.language = language;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @JSONField(serialize = false)
    public CommunicationCommandType getType() {
        if (this.isResponseType()) {
            return CommunicationCommandType.RESPONSE_COMMAND;
        }

        return CommunicationCommandType.REQUEST_COMMAND;
    }

    @JSONField(serialize = false)
    public boolean isResponseType() {
        int bits = 1 << RPC_TYPE;
        return (this.flag & bits) == bits;
    }

    @JSONField(serialize = false)
    public boolean isOnewayRPC() {
        int bits = 1 << RPC_ONEWAY;
        return (this.flag & bits) == bits;
    }

    public ByteBuffer encode() {
        int length = 4;

        byte[] headerData = this.headerEncode();
        length += headerData.length;

        if (this.body != null) {
            length += body.length;
        }

        ByteBuffer result = ByteBuffer.allocate(4 + length);

        result.putInt(length);

        result.put(markProtocolType(headerData.length, serializeTypeCurrent));

        result.put(headerData);

        if (this.body != null) {
            result.put(this.body);
        }

        result.flip();

        return result;
    }

    public void markOnewayRPC() {
        int bits = 1 << RPC_ONEWAY;
        this.flag |= bits;
    }

    public static byte[] markProtocolType(int source, SerializeType type) {
        byte[] result = new byte[4];

        result[0] = type.getCode();
        result[1] = (byte) ((source >> 16) & 0xFF);
        result[2] = (byte) ((source >> 8) & 0xFF);
        result[3] = (byte) (source & 0xFF);
        return result;
    }

    private byte[] headerEncode() {
        this.makeCustomHeaderToNet();
        if (SerializeType.GALACTICS == serializeTypeCurrent) {
            return GalacticsSerializable.galacticsProtocolEncode(this);
        } else {
            return CommunicationSerializable.encode(this);
        }
    }

    public void makeCustomHeaderToNet() {
        if (this.customHeader != null) {
            Field[] fields = getClazzFields(customHeader.getClass());
            if (null == this.extFields) {
                this.extFields = new HashMap<String, String>();
            }

            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    String name = field.getName();
                    if (!name.startsWith("this")) {
                        Object value = null;
                        try {
                            field.setAccessible(true);
                            value = field.get(this.customHeader);
                        } catch (IllegalArgumentException e) {
                        } catch (IllegalAccessException e) {
                        }

                        if (value != null) {
                            this.extFields.put(name, value.toString());
                        }
                    }
                }
            }
        }
    }

    private Field[] getClazzFields(Class<? extends CustomHeader> classHeader) {
        Field[] field = CLASS_HASH_MAP.get(classHeader);

        if (field == null) {
            field = classHeader.getDeclaredFields();
            synchronized (CLASS_HASH_MAP) {
                CLASS_HASH_MAP.put(classHeader, field);
            }
        }
        return field;
    }

    public ByteBuffer encodeHeader() {
        return encodeHeader(this.body != null ? this.body.length : 0);
    }

    public ByteBuffer encodeHeader(final int bodyLength) {
        // 1> header length size
        int length = 4;

        // 2> header data length
        byte[] headerData;
        headerData = this.headerEncode();

        length += headerData.length;

        // 3> body data length
        length += bodyLength;

        ByteBuffer result = ByteBuffer.allocate(4 + length - bodyLength);

        // length
        result.putInt(length);

        // header length
        result.put(markProtocolType(headerData.length, serializeTypeCurrent));

        // header data
        result.put(headerData);

        result.flip();

        return result;
    }

    public static CommunicationCommand decode(final byte[] array) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(array);
        return decode(byteBuffer);
    }

    public static CommunicationCommand decode(final ByteBuffer byteBuffer) {
        int length = byteBuffer.limit();
        int oriHeaderLen = byteBuffer.getInt();
        int headerLength = getHeaderLength(oriHeaderLen);

        byte[] headerData = new byte[headerLength];
        byteBuffer.get(headerData);

        CommunicationCommand cmd = headerDecode(headerData, getProtocolType(oriHeaderLen));

        int bodyLength = length - 4 - headerLength;
        byte[] bodyData = null;
        if (bodyLength > 0) {
            bodyData = new byte[bodyLength];
            byteBuffer.get(bodyData);
        }
        cmd.body = bodyData;

        return cmd;
    }

    public static CommunicationCommand createRequestCommand(int code, CustomHeader customHeader) {
        CommunicationCommand cmd = new CommunicationCommand();
        cmd.setCode(code);
        cmd.customHeader = customHeader;
        setCmdVersion(cmd);
        return cmd;
    }

    private static void setCmdVersion(CommunicationCommand cmd) {
        if (configVersion >= 0) {
            cmd.setVersion(configVersion);
        } else {
            String v = System.getProperty(COMMUNICATION_VERSION_KEY);
            if (v != null) {
                int value = Integer.parseInt(v);
                cmd.setVersion(value);
                configVersion = value;
            }
        }
    }

    public static int getHeaderLength(int length) {
        return length & 0xFFFFFF;
    }

    private static CommunicationCommand headerDecode(byte[] headerData, SerializeType type) {
        switch (type) {
            case JSON:
                CommunicationCommand resultJson = CommunicationSerializable.decode(headerData, CommunicationCommand.class);
                resultJson.setSerializeTypeCurrent(type);
                return resultJson;
            case GALACTICS:
                CommunicationCommand galactics = GalacticsSerializable.galacticsProtocolDecode(headerData);
                galactics.setSerializeTypeCurrent(type);
                return galactics;
            default:
                break;
        }

        return null;
    }

    public static SerializeType getProtocolType(int source) {
        return SerializeType.valueOf((byte) ((source >> 24) & 0xFF));
    }
    public SerializeType getSerializeTypeCurrent() {
        return serializeTypeCurrent;
    }

    public void setSerializeTypeCurrent(SerializeType serializeTypeCurrentRPC) {
        this.serializeTypeCurrent = serializeTypeCurrentRPC;
    }

    @Override
    public String toString() {
        return "RemotingCommand [code=" + code + ", language=" + language + ", version=" + version + ", opaque=" + opaque + ", flag(B)="
                + Integer.toBinaryString(flag) + ", remark=" + remark + ", extFields=" + extFields + ", serializeTypeCurrent="
                + serializeTypeCurrent + "]";
    }
}
