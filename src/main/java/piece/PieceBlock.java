package piece;

public class PieceBlock {
    private int pieceIndex;
    private int baseOffset;
    private byte[] data;

    public PieceBlock(int pieceIndex, int baseOffset, byte[] data) {
        this.pieceIndex = pieceIndex;
        this.baseOffset = baseOffset;
        this.data = data;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public int getBaseOffset() {
        return baseOffset;
    }

    public byte[] getData() {
        return data;
    }
}
