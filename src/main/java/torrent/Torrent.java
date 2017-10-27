package torrent;

import peer.Peer;
import peer.PeerTcpChannel;
import piece.Piece;
import piece.PieceBlock;
import piece.PieceToFileMapper;
import piece.RequestBlock;
import torrent.torrentdata.*;
import tracker.TrackerManager;
import util.BitField;
import util.Scheduler;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;
import java.util.stream.Collectors;

public class Torrent {

    private static final int PARALELL_BLOCK_PER_PEER = 8;
    private static final int TARGET_PEER_NUMBER = 50;

    private Set<Peer> peers = new HashSet<>();
    private Set<SocketAddress> foundPeers = new HashSet<>();

    private TrackerManager trackerManager;
    private TorrentData torrentData;
    private PieceToFileMapper pieceMapper;

    private Long torrentLength;

    private String peerId = "-qB33A0-DSh6r9y0E_DH";
    private Long downloaded = 0L;
    private Long uploaded = 0L;
    private Long left;

    private int[] piecesCount;
    private BitField pieceSet;
    private List<Piece> pieces;

    private Map<RequestBlock, Set<Peer>> peersRequestedBlocks = new HashMap<>();

    public Torrent(TorrentData torrentData) {
        this.torrentData = torrentData;

        trackerManager = new TrackerManager(this, torrentData.getAnnounceList());
        if (torrentData.getInfo() instanceof SingleFileInfoData){
            pieceMapper = new PieceToFileMapper(Collections.singletonList(((SingleFileInfoData) torrentData.getInfo()).getFile()));
            torrentLength = ((SingleFileInfoData) torrentData.getInfo()).getFile().getLength();
        } else if (torrentData.getInfo() instanceof MultiFileInfoData){
            pieceMapper = new PieceToFileMapper(((MultiFileInfoData) torrentData.getInfo()).getFiles());
            torrentLength = ((MultiFileInfoData) torrentData.getInfo()).getFiles().stream()
                    .mapToLong(it -> it.getLength()).sum();
        }

        left = torrentLength;

        piecesCount = new int[torrentData.getInfo().getPieces().size()];
        pieceSet = new BitField(torrentData.getInfo().getPieces().size());

        pieces = new ArrayList<>();
        for (int i = 0; i < torrentData.getInfo().getPieces().size(); i++){
            int pieceLength = (int) Math.min(torrentLength - i * torrentData.getInfo().getPieceLength(), torrentData.getInfo().getPieceLength());
            pieces.add(new Piece(i, pieceLength, torrentData.getInfo().getPieces().get(i)));
        }
    }

    private void fillPeerWithRequests(Peer peer){
        synchronized (peer.getRequestedBlocks()){
            if (peer.getRequestedBlocks().size() >= PARALELL_BLOCK_PER_PEER) return;

            List<Piece> availablePieceList = new ArrayList<>();
            for (int i = 0; i < pieces.size(); i++){
                if (!pieces.get(i).isCompleted() && peer.getHaveBitfield().getBit(i)){
                    availablePieceList.add(pieces.get(i));
                }
            }
            availablePieceList.sort(Comparator.comparingInt(e -> piecesCount[e.getIndex()]));

            for (Piece it : availablePieceList) {
                RequestBlock block = it.getNextEmptyBlockForPeer(peer);
                while (block != null){
                    peer.sendRequest(block);
                    if (peer.getRequestedBlocks().size() >= PARALELL_BLOCK_PER_PEER) return;
                    block = it.getNextEmptyBlockForPeer(peer);
                }
            }

            for (Piece it : availablePieceList) {
                RequestBlock block = it.getNextRarestBlockForPeer(peer);
                while (block != null){
                    peer.sendRequest(block);
                    if (peer.getRequestedBlocks().size() >= PARALELL_BLOCK_PER_PEER) return;
                    block = it.getNextRarestBlockForPeer(peer);
                }
            }

            if (peer.getRequestedBlocks().size() == 0){
                peer.setAmInterested(false);
            }
        }
    }

    public synchronized void peerChangedInterested(Peer peer, boolean interestedInMe) {
        peer.setAmChoking(!interestedInMe);
    }

    public synchronized void peerChangedChoke(Peer peer, boolean chokingMe) {
        if (!chokingMe){
            fillPeerWithRequests(peer);
        } else {
            peer.getRequestedBlocks().forEach(it -> {
                pieces.get(it.getPieceIndex()).getBlockList().get(it.getBaseOffset()/Piece.BlockLength).getPeersDownloading().remove(peer);
            });
        }
    }

    public TorrentData getTorrentData() {
        return torrentData;
    }

    public String getPeerId() {
        return peerId;
    }

    public synchronized void peerBlockRequest(Peer peer, RequestBlock block) {
        Scheduler.submit(() -> {
            boolean loadBlock;
            synchronized (peersRequestedBlocks){
                if (peersRequestedBlocks.containsKey(block)){
                    peersRequestedBlocks.get(block).add(peer);
                    loadBlock = false;
                } else {
                    peersRequestedBlocks.put(block, new HashSet<Peer>(){{add(peer);}});
                    loadBlock = true;
                }
            }
            if (loadBlock) {
                pieceMapper.startReadBlock(block.getPieceIndex() * Piece.BlockLength + block.getBaseOffset(), block.getLength(), (it) -> {
                    PieceBlock piece = new PieceBlock(block.getPieceIndex(), block.getBaseOffset(), it);
                    Set<Peer> peers;
                    synchronized (peersRequestedBlocks){
                        peers = peersRequestedBlocks.remove(block);
                    }
                    for (Peer piecePeer : peers) {
                        piecePeer.sendPiece(new PieceBlock(block.getPieceIndex(), block.getBaseOffset(), it));
                        uploaded += it.length;
                    }
                });
            }
        });
    }

    public synchronized void peerPieceMessage(Peer peer, PieceBlock block) {
        Piece piece = pieces.get(block.getPieceIndex());
        piece.onBlockReceived(block);

        List<Peer> peersDownloading = piece.getBlockList().get(block.getBaseOffset()/Piece.BlockLength).getPeersDownloading();
        peersDownloading.forEach(it -> {
            if (it != peer) it.sendCancelBlock(new RequestBlock(block.getPieceIndex(), block.getBaseOffset(), block.getData().length));
        });
        peersDownloading.clear();
        if (piece.isCompleted() && piece.getData() != null){
            pieceMapper.writePiece(piece.getData(), piece.getIndex() * torrentData.getInfo().getPieceLength());
            synchronized (peers){
                peers.forEach(it -> it.sendHavePiece(block.getPieceIndex()));
            }
        }


        fillPeerWithRequests(peer);

        downloaded += block.getData().length;
        left = Math.max(left - block.getData().length, 0);
    }

    public synchronized void peerHasPiece(Peer peer, int pieceIndex) {
        piecesCount[pieceIndex]++;
        if (!pieces.get(pieceIndex).isCompleted()) peer.setAmInterested(true);
    }

    public synchronized void peerBitfieldReceived(Peer peer, BitField bitfield) {
        int index = bitfield.getNextSetBit(0);
        while(index != -1){
            piecesCount[index]++;
            if (!pieces.get(index).isCompleted()) peer.setAmInterested(true);

            index = bitfield.getNextSetBit(index + 1);
        }
    }

    public synchronized void peerCancelMessage(Peer peer, RequestBlock block) {
        Scheduler.submit(() -> {
            synchronized (peersRequestedBlocks){
                if (peersRequestedBlocks.containsKey(block)){
                    peersRequestedBlocks.get(block).remove(peer);
                }
            }
        });
    }

    public synchronized void removePeer(Peer peer) {
        peers.remove(peer);
        if (peers.size() < TARGET_PEER_NUMBER) {
            connectOnePeer();
        }
    }

    public synchronized void foundPeers(Set<SocketAddress> response) {
        Set<SocketAddress> connectedPeers = peers.stream().map(it -> it.getRemoteAddress()).collect(Collectors.toSet());
        Set<SocketAddress> unconnectedPeers = new HashSet<>(response);
        unconnectedPeers.removeAll(connectedPeers);

        foundPeers.addAll(unconnectedPeers);
        fillPeersToTarget();
    }

    public synchronized void onNewPeerChannel(PeerTcpChannel peerChannel){
        peers.add(new Peer(peerChannel, this));
    }

    public synchronized void startTorrent(){
        trackerManager.startManager();
    }

    private void fillPeersToTarget() {
        int needed = TARGET_PEER_NUMBER - peers.size();
        if (needed <= 0) return;

        int has = foundPeers.size();
        int canConnectTo = Math.min(needed, has);
        for (; canConnectTo > 0; canConnectTo--){
            connectOnePeer();
        }
    }

    private void connectOnePeer() {
        if (foundPeers.size() == 0){
            return;
        }
        SocketAddress socketAddress = foundPeers.stream().findFirst().get();
        foundPeers.remove(socketAddress);

        PeerTcpChannel channel = new PeerTcpChannel(socketAddress);
        Peer peer = new Peer(channel, this);
        peers.add(peer);
    }

    public Long getDownloaded() {
        return downloaded;
    }

    public Long getUploaded() {
        return uploaded;
    }

    public Long getLeft() {
        return left;
    }
}
