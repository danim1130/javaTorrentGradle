package util;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class SHA1 {

    private int[] value;
    private byte[] byteRepresentation;

    private SHA1(int[] arr){
        this.value = arr;
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.asIntBuffer().put(value);
        this.byteRepresentation = buffer.array();
    }

    public static SHA1 fromByteArray(byte[] arr){
        if (arr.length != 20){
            throw new AssertionError("Length of byte array is not 20");
        }
        int[] intArr = new int[5];
        ByteBuffer.wrap(arr).asIntBuffer().get(intArr);
        return SHA1.fromIntArray(intArr);
    }

    public static SHA1 fromIntArray(int[] arr){
        if (arr.length != 5){
            throw new AssertionError("Length of int array is not 5");
        }
        return new SHA1(arr);
    }

    public static SHA1 getHash(byte[] msg){
        try {
            MessageDigest hasher = MessageDigest.getInstance("SHA1");
            hasher.reset();
            return fromByteArray(hasher.digest(msg));
        } catch (NoSuchAlgorithmException e) {
        }
        return null;
    }

    public static SHA1 getHash(String msg){
        return getHash(msg.getBytes());
    }

    public byte[] getByteRepresentation(){
        return byteRepresentation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SHA1 sha1 = (SHA1) o;

        return Arrays.equals(value, sha1.value);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "SHA1{" +
                "value=" + Arrays.toString(value) +
                '}';
    }

    public String getReadableRepresentation(){
        StringBuilder builder = new StringBuilder(40);
        for (int i = 0; i < value.length; i++) {
            builder.append(String.format("%08x", value[i]));
        }
        return builder.toString();
    }
}
