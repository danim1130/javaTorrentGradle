package bencode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BencodeDecode {

    private BencodeDecode(){}

    public static Object bDecodeStream(InputStream bEncodeStream) throws IOException, BencodeException {
        InputStream buffered = bEncodeStream.markSupported() ? bEncodeStream : new BufferedInputStream(bEncodeStream, 1);

        char firstChar = peekCharacterFromStream(buffered);
        if (firstChar == 'i'){
            return decodeInt(buffered);
        } else if (firstChar == 'd'){
            return decodeMap(buffered);
        } else if (firstChar == 'l'){
            return decodeList(buffered);
        } else if (firstChar >= '0' && firstChar <= '9'){
            return decodeString(buffered);
        } else {
            throw new BencodeException("Illegal character in stream");
        }
    }

    private static char readCharFromStream(InputStream stream) throws BencodeException {
        try {
            int read = stream.read();
            if (read == -1){
                throw new BencodeException("InputStream overrun");
            }
            return (char)read;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static char peekCharacterFromStream(InputStream stream) throws BencodeException, IOException {
        if (!stream.markSupported()) {
            throw new BencodeException("Mark not supported on stream");
        }

        stream.mark(1);
        char ret = readCharFromStream(stream);
        stream.reset();
        return ret;
    }

    private static String decodeString(InputStream stream) throws BencodeException {
        int stringLength = 0;

        for (char currentChar = readCharFromStream(stream); currentChar != ':'; currentChar = readCharFromStream(stream)) {
            if (currentChar == '0' && stringLength == 0) {
                throw new BencodeException("Illegal character in stream!");
            } else if (currentChar >= '0' && currentChar <= '9') {
                stringLength = stringLength * 10 + currentChar - '0';
            } else {
                throw new BencodeException("Illegal character in stream!");
            }
        }

        StringBuilder stringBuilder = new StringBuilder(stringLength);
        for (; stringLength > 0; stringLength--){
            stringBuilder.append(readCharFromStream(stream));
        }

        return stringBuilder.toString();
    }

    private static Long decodeInt(InputStream stream) throws BencodeException {
        if (readCharFromStream(stream) != 'i'){
            throw new BencodeException("Illegal character");
        }

        long value = 0;
        boolean isNegative;

        char currentChar = readCharFromStream(stream);
        if (currentChar == 'e'){
            throw new BencodeException("Illegal character");
        }
        isNegative = currentChar == '-';
        if (!isNegative){
            value = currentChar - '0';
            if (value > 9 || value < 0){
                throw new BencodeException("Illegal character in stream");
            }
        }

        for (currentChar = readCharFromStream(stream); currentChar != 'e'; currentChar = readCharFromStream(stream)){
            if (currentChar == '0' && value == 0){
                throw new BencodeException("Illegal zero in stream");
            } else if (currentChar >= '0' && currentChar <= '9'){
                value = value * 10 + currentChar - '0';
            } else {
                throw new BencodeException("Illegal character in stream");
            }
        }

        return isNegative ? -value : value;
    }

    private static List<Object> decodeList(InputStream stream) throws BencodeException, IOException {
        if (readCharFromStream(stream) != 'l'){
            throw new BencodeException("Illegal character");
        }

        List<Object> ret = new ArrayList<>();
        while (peekCharacterFromStream(stream) != 'e'){
            ret.add(bDecodeStream(stream));
        }
        readCharFromStream(stream); //ending char
        return ret;
    }

    private static Map<String, Object> decodeMap(InputStream stream) throws BencodeException, IOException {
        if (readCharFromStream(stream) != 'd'){
            throw new BencodeException("Illegal character");
        }

        Map<String, Object> ret = new HashMap<>();
        while(peekCharacterFromStream(stream) != 'e'){
            ret.put(decodeString(stream), bDecodeStream(stream));
        }
        readCharFromStream(stream);

        return ret;
    }
}
