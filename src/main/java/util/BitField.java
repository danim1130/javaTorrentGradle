package util;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Created by danim on 01/11/2016.
 */
public final class BitField {

    private byte[] value;
    private int length;

    public BitField(int length){
        this(new byte[(length + 7) / 8], length);
    }

    public BitField(byte[] array, int length){
        this.value = array;
        this.length = length;
    }

    public boolean getBit(int index){
        if (index >= length || index < 0){
            throw new IndexOutOfBoundsException();
        }
        return (value[index / 8] & (0x80 >>> (index % 8))) != 0;
    }

    public void setBit(int index){
        if (index >= length){
            throw new IndexOutOfBoundsException();
        }
        value[index / 8] |= (0x80 >>> (index % 8));
    }

    public void clearBit(int index){
        if (index >= length){
            throw new IndexOutOfBoundsException();
        }
        value[index / 8] &= ~(0x80 >>> (index % 8));
    }

    public int getNextSetBit(int from){
        for (int i = from; i < length; i++){
            if (getBit(i)){
                return i;
            }
        }
        return -1;
    }

    public int getLength(){
        return length;
    }

    public byte[] getByteRepresentation(){
        return value;
    }
}
