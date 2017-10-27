package piece;

import peer.Peer;
import util.SHA1;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Piece {
    public static final int BlockLength = 1 << 14;

    private int index;
    private int length;
    private SHA1 pieceHash;

    private int blockCount;
    private List<Block> blockList = new ArrayList<>();

    private byte[] data;
    private boolean isCompleted = false;

    public Piece(int index, int length, SHA1 pieceHash) {
        this.index = index;
        this.length = length;
        this.pieceHash = pieceHash;

        blockCount = (length + BlockLength - 1) / BlockLength;
        for (int i = 0; i < blockCount; i++){
            blockList.add(i, new Block(i));
        }
    }

    public synchronized void onBlockReceived(PieceBlock block){
        if (isCompleted) return;
        blockList.get(block.getBaseOffset() / BlockLength).setData(block.getData());
        if (blockList.stream().allMatch(it -> it.getData() != null)){
            if (data == null || data.length != length) data = new byte[length];
            for (int i = 0; i < blockList.size(); i++){
                Block blockIt = blockList.get(i);
                System.arraycopy(blockIt.getData(), 0,
                        data, i * BlockLength, blockIt.getData().length);
                blockIt.setData(null);
            }
            SHA1 hash = SHA1.getHash(data);
            if (hash.equals(pieceHash)){
                isCompleted = true;
            } else {
                clearData();
            }
        }
    }

    public RequestBlock getNextEmptyBlockForPeer(Peer peer){
        Optional<Block> oBlock = blockList.stream()
                .filter(it -> it.getData() == null && it.getPeersDownloading().size() == 0).findFirst();
        if (oBlock.isPresent()){
            Block block = oBlock.get();
            block.getPeersDownloading().add(peer);
            return new RequestBlock(index,
                    block.getBlockIndex() * BlockLength,
                    Math.min(this.length - block.getBlockIndex() * BlockLength, BlockLength));
        } else {
            return null;
        }
    }

    public RequestBlock getNextRarestBlockForPeer(Peer peer){
        Optional<Block> oBlock = blockList.stream()
                .filter(it -> it.getData() != null && !it.getPeersDownloading().contains(peer))
                .min(Comparator.comparingInt(it -> it.getPeersDownloading().size()));
        if (oBlock.isPresent()){
            Block block = oBlock.get();
            block.getPeersDownloading().add(peer);
            return new RequestBlock(index,
                    block.getBlockIndex() * BlockLength,
                    Math.min(this.length - block.getBlockIndex() * BlockLength, BlockLength));
        } else {
            return null;
        }
    }

    private synchronized void clearData(){
        data = null;
        blockList.forEach(it -> it.setData(null));
    }

    public int getIndex() {
        return index;
    }

    public List<Block> getBlockList() {
        return blockList;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public static class Block{
        private int blockIndex;

        private byte[] data;
        private List<Peer> peersDownloading = new ArrayList<>();

        public Block(int blockIndex) {
            this.blockIndex = blockIndex;
        }

        public int getBlockIndex() {
            return blockIndex;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public List<Peer> getPeersDownloading() {
            return peersDownloading;
        }
    }
}
