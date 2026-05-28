package asia.rgp.mock.wsproxy.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ValueType;
import org.springframework.stereotype.Service;

@Service
public class MessagePackHelper {

  public byte[] encode(Object data) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MessagePacker packer = MessagePack.newDefaultPacker(out);

    if (data instanceof Map<?, ?> map) {
      packer.packMapHeader(map.size());
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        packer.packString(entry.getKey().toString());
        packValue(packer, entry.getValue());
      }
    } else if (data instanceof Integer) {
      packer.packInt((Integer) data);
    } else if (data instanceof Long) {
      packer.packLong((Long) data);
    } else if (data instanceof String) {
      packer.packString((String) data);
    } else if (data instanceof Boolean) {
      packer.packBoolean((Boolean) data);
    } else {
      packer.packString(data.toString());
    }

    packer.close();
    return out.toByteArray();
  }

  public byte[] encodeResponse(int cmd, Map<String, Object> data) throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("cmd", cmd);
    response.putAll(data);
    return encode(response);
  }

  private void packValue(MessagePacker packer, Object value) throws IOException {
    if (value == null) {
      packer.packNil();
    } else if (value instanceof Integer) {
      packer.packInt((Integer) value);
    } else if (value instanceof Long) {
      packer.packLong((Long) value);
    } else if (value instanceof String) {
      packer.packString((String) value);
    } else if (value instanceof Boolean) {
      packer.packBoolean((Boolean) value);
    } else if (value instanceof Double) {
      packer.packDouble((Double) value);
    } else if (value instanceof Float) {
      packer.packFloat((Float) value);
    } else if (value instanceof Map<?, ?>) {
      packer.packMapHeader(((Map<?, ?>) value).size());
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        packer.packString(entry.getKey().toString());
        packValue(packer, entry.getValue());
      }
    } else {
      packer.packString(value.toString());
    }
  }

  public Map<String, Object> decode(byte[] data) throws IOException {
    Map<String, Object> result = new LinkedHashMap<>();
    MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);

    if (unpacker.hasNext() && unpacker.getNextFormat().getValueType() == ValueType.MAP) {
      int size = unpacker.unpackMapHeader();
      for (int i = 0; i < size; i++) {
        String key = unpacker.unpackString();
        Object value = unpackValue(unpacker);
        result.put(key, value);
      }
    }

    unpacker.close();
    return result;
  }

  private Object unpackValue(MessageUnpacker unpacker) throws IOException {
    if (!unpacker.hasNext())
      return null;
    ValueType type = unpacker.getNextFormat().getValueType();
    switch (type) {
      case NIL:
        unpacker.unpackNil();
        return null;
      case BOOLEAN:
        return unpacker.unpackBoolean();
      case INTEGER:
        return unpacker.unpackLong();
      case FLOAT:
        return unpacker.unpackDouble();
      case STRING:
        return unpacker.unpackString();
      case MAP:
        int size = unpacker.unpackMapHeader();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
          String key = unpacker.unpackString();
          map.put(key, unpackValue(unpacker));
        }
        return map;
      case ARRAY:
        int arraySize = unpacker.unpackArrayHeader();
        Object[] array = new Object[arraySize];
        for (int i = 0; i < arraySize; i++) {
          array[i] = unpackValue(unpacker);
        }
        return array;
      default:
        unpacker.skipValue();
        return null;
    }
  }
}
