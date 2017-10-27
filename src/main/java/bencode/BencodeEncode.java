package bencode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class BencodeEncode {

    private BencodeEncode(){}

    public static void bEncodeObject(Object obj, OutputStream outputStream) throws IOException, BencodeException {
        if (obj instanceof Long){
            encodeInt((Long) obj, outputStream);
        } else if (obj instanceof List){
            encodeList((List<Object>) obj, outputStream);
        } else if (obj instanceof Map){
            encodeMap((Map<String, Object>) obj, outputStream);
        } else if (obj instanceof String){
            encodeString((String) obj, outputStream);
        } else {
            throw new BencodeException("Can't encode this object");
        }
    }

    private static void encodeInt(Long value, OutputStream outputStream) throws IOException {
        outputStream.write('i');
        outputStream.write(value.toString().getBytes());
        outputStream.write('e');
    }

    private static void encodeString(String string, OutputStream outputStream) throws IOException {
        outputStream.write(Integer.toString(string.length()).getBytes());
        outputStream.write(':');
        outputStream.write(string.getBytes("ISO-8859-1"));
    }

    private static void encodeList(List<Object> list, OutputStream outputStream) throws IOException, BencodeException {
        outputStream.write('l');
        for (Object obj : list){
            bEncodeObject(obj, outputStream);
        }
        outputStream.write('e');
    }

    private static void encodeMap(Map<String, Object> map, OutputStream outputStream) throws IOException, BencodeException {
        outputStream.write('d');
        TreeMap<String, Object> sortedMap = new TreeMap<>(map);
        for (Map.Entry<String, Object> entry : sortedMap.entrySet()){
            encodeString(entry.getKey(), outputStream);
            bEncodeObject(entry.getValue(), outputStream);
        }
        outputStream.write('e');
    }
}
