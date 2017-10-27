package piece;

public class RequestBlock {

    private int pieceIndex;
    private int baseOffset;
    private int length;

    public RequestBlock(int pieceIndex, int baseOffset, int length) {
        this.pieceIndex = pieceIndex;
        this.baseOffset = baseOffset;
        this.length = length;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getBaseOffset() {
        return baseOffset;
    }

    public int getLength() {
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RequestBlock that = (RequestBlock) o;

        if (pieceIndex != that.pieceIndex) return false;
        if (baseOffset != that.baseOffset) return false;
        return length == that.length;
    }

    @Override
    public int hashCode() {
        int result = pieceIndex;
        result = 31 * result + baseOffset;
        result = 31 * result + length;
        return result;
    }
}
