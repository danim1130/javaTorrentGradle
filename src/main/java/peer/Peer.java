package peer;

import piece.PieceBlock;
import piece.RequestBlock;
import torrent.Torrent;
import util.BitField;
import util.SHA1;
import util.Scheduler;

import javax.management.RuntimeErrorException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

public class Peer implements InfoHashListener {

    private PeerTcpChannel messageChannel;
    private Torrent torrent;

    private String peerId;
    private BitField extensions;
    private BitField haveBitfield;
    private Long lastMessageTime = Long.valueOf(-1);

    private Set<RequestBlock> requestedBlocks = new HashSet<>();

    private boolean amChoking = true;
    private boolean amInterested = false;

    private boolean chokingMe = true;
    private boolean interestedInMe = false;

    public Peer(PeerTcpChannel messageChannel, Torrent torrent) {
        this.messageChannel = messageChannel;
        this.torrent = torrent;

        haveBitfield = new BitField(torrent.getTorrentData().getInfo().getPieces().size());

        messageChannel.setPeer(this);

        messageChannel.sendHandshake("BitTorrent protocol", new BitField(64), torrent.getTorrentData().getInfoHash(), torrent.getPeerId());
    }

    @Override
    public synchronized void infoHashReceived(PeerTcpChannel tcpChannel, SHA1 infoHash) {
    }

    public synchronized void onHandshake(String protocol, BitField extensions, SHA1 infoHash, String peerId) {
        if (!infoHash.equals(torrent.getTorrentData().getInfoHash())){
            throw new RuntimeException("Infohash does not match");
        }
        this.peerId = peerId;
        this.extensions = extensions;
    }

    public synchronized void onHavePiece(int pieceIndex) {
        haveBitfield.setBit(pieceIndex);
        Scheduler.submit(() -> torrent.peerHasPiece(this, pieceIndex));
    }

    public synchronized void onBitfield(BitField field) {
        this.haveBitfield = field;
        Scheduler.submit(() -> torrent.peerBitfieldReceived(this, field));
    }

    public synchronized void onRequestBlock(RequestBlock block) {
        Scheduler.submit(() -> torrent.peerBlockRequest(this, block));
    }

    public synchronized void onPieceBlock(PieceBlock block) {
        requestedBlocks.remove(new RequestBlock(block.getPieceIndex(), block.getBaseOffset(), block.getData().length));
        Scheduler.submit(() -> torrent.peerPieceMessage(this, block));
    }

    public synchronized void onCancelBlock(RequestBlock block) {
        Scheduler.submit(() -> torrent.peerCancelMessage(this, block));
    }

    public synchronized void onPort(int port) {

    }

    public synchronized void onChannelFailed(){
        Scheduler.submit(() -> torrent.removePeer(this));
    }

    public boolean amChoking() {
        return amChoking;
    }

    public void setAmChoking(boolean amChoking) {
        if (this.amChoking != amChoking){
            this.amChoking = amChoking;
            if (amChoking) messageChannel.sendChoke(); else messageChannel.sendUnchoke();
        }
    }

    public boolean amInterested() {
        return amInterested;
    }

    public void setAmInterested(boolean amInterested) {
        if (amInterested != this.amInterested){
            this.amInterested = amInterested;
            if (amInterested) messageChannel.sendInterested(); else messageChannel.sendNotInterested();
        }
    }

    public boolean isChokingMe() {
        return chokingMe;
    }

    public boolean isInterestedInMe() {
        return interestedInMe;
    }

    public synchronized void setChokingMe(boolean chokingMe) {
        if (this.chokingMe != chokingMe) {
            this.chokingMe = chokingMe;
            Scheduler.submit(() -> torrent.peerChangedChoke(this, chokingMe));
        }
    }

    public synchronized void setInterestedInMe(boolean interestedInMe) {
        if (this.interestedInMe != interestedInMe) {
            this.interestedInMe = interestedInMe;
            Scheduler.submit(() -> torrent.peerChangedInterested(this, interestedInMe));
        }
    }

    public synchronized void sendPiece(PieceBlock block){
        Scheduler.submit(() -> messageChannel.sendPiece(block));
    }

    public synchronized void sendHavePiece(int pieceIndex){
        Scheduler.submit(() -> messageChannel.sendHave(pieceIndex));
    }

    public synchronized void sendRequest(RequestBlock block){
        if (requestedBlocks.contains(block)) return;
        requestedBlocks.add(block);
        Scheduler.submit(() -> messageChannel.sendRequest(block));
    }

    public synchronized void sendCancelBlock(RequestBlock block){
        requestedBlocks.remove(block);
        Scheduler.submit(() -> messageChannel.sendCancel(block));
    }

    public SocketAddress getRemoteAddress(){
        try {
            return messageChannel.getRemoteAddress();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public BitField getExtensions() {
        return extensions;
    }

    public Long getLastMessageTime() {
        return lastMessageTime;
    }

    public Set<RequestBlock> getRequestedBlocks() {
        return requestedBlocks;
    }

    public boolean isAmChoking() {
        return amChoking;
    }

    public boolean isAmInterested() {
        return amInterested;
    }

    public BitField getHaveBitfield() {
        return haveBitfield;
    }
}
