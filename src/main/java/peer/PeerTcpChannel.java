package peer;

import piece.PieceBlock;
import piece.RequestBlock;
import util.BitField;
import util.SHA1;
import util.Scheduler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;

public class PeerTcpChannel {

    private AsynchronousSocketChannel socketChannel;
    private boolean isConnected;

    private ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;

    public void close() {
        isConnected = false;
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private enum MessageReadState {HANDSHAKE_PROTOCOL_LENGTH, HANDSHAKE_INFOHASH, HANDSHAKE_PEERID, MESSAGE_LENGTH, MESSAGE_PAYLOAD};
    private MessageReadState readState;
    private int targetLength = 0;
    private Object[] handshakeObjectBuffer = new Object[3];

    private InfoHashListener infoHashListener;
    private Peer peer;

    private boolean isWriting = false;
    private List<byte[]> writeBufferList = new ArrayList<>();

    private SocketAddress targetAddress;

    public PeerTcpChannel (AsynchronousSocketChannel socketChannel, InfoHashListener infoHashListener){
        this.socketChannel = socketChannel;
        this.infoHashListener = infoHashListener;
        connected();
    }

    public PeerTcpChannel (SocketAddress address){
        isConnected = false;
        targetAddress = address;
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
        this.infoHashListener = peer;

        if (!isConnected) {
            try {
                socketChannel = AsynchronousSocketChannel.open();
                socketChannel.connect(targetAddress, this, connectHandler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendHandshake(String protocol, BitField extensions, SHA1 infoHash, String peerId){
        ByteBuffer msgBuff = ByteBuffer.allocate(protocol.length() + 49);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.put((byte) protocol.length());
        try {
            msgBuff.put(protocol.getBytes("ISO-8859-1"));
            msgBuff.put(extensions.getByteRepresentation());
            msgBuff.put(infoHash.getByteRepresentation());
            msgBuff.put(peerId.getBytes("ISO-8859-1"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        addToWriteBufferList(msgBuff.array());
    }

    public void sendKeepAlive(){
        byte[] msg = new byte[4];
        addToWriteBufferList(msg);
    }

    public void sendChoke(){
        byte[] msg = new byte[5];
        msg[3] = 1;
        msg[4] = 0;
        addToWriteBufferList(msg);
    }

    public void sendUnchoke(){
        byte[] msg = new byte[5];
        msg[3] = 1;
        msg[4] = 1;
        addToWriteBufferList(msg);
    }

    public void sendInterested(){
        byte[] msg = new byte[5];
        msg[3] = 1;
        msg[4] = 2;
        addToWriteBufferList(msg);
    }

    public void sendNotInterested(){
        byte[] msg = new byte[5];
        msg[3] = 1;
        msg[4] = 3;
        addToWriteBufferList(msg);
    }

    public void sendHave(int pieceIndex){
        ByteBuffer msgBuff = ByteBuffer.allocate(9);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.putInt(5).put((byte) 4).putInt(pieceIndex);
        addToWriteBufferList(msgBuff.array());
    }

    public void sendBitfield(BitField field){
        byte[] fieldBytes = field.getByteRepresentation();
        ByteBuffer msgBuff = ByteBuffer.allocate(fieldBytes.length + 5);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.putInt(fieldBytes.length + 1).put((byte) 5).put(fieldBytes);
        addToWriteBufferList(msgBuff.array());
    }

    public void sendRequest(RequestBlock block){
        ByteBuffer msgBuff = ByteBuffer.allocate(17);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.putInt(13).put((byte) 6).putInt(block.getPieceIndex()).putInt(block.getBaseOffset()).putInt(block.getLength());
        addToWriteBufferList(msgBuff.array());
    }

    public void sendPiece(PieceBlock block){
        ByteBuffer msgBuff = ByteBuffer.allocate(13 + block.getData().length);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.putInt(9 + block.getData().length).put((byte) 7).putInt(block.getPieceIndex())
                .putInt(block.getBaseOffset()).put(block.getData());
        addToWriteBufferList(msgBuff.array());
    }

    public void sendCancel(RequestBlock block){
        ByteBuffer msgBuff = ByteBuffer.allocate(17);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.putInt(13).put((byte) 8).putInt(block.getPieceIndex()).putInt(block.getBaseOffset()).putInt(block.getLength());
        addToWriteBufferList(msgBuff.array());
    }

    public void sendPort(int port){
        ByteBuffer msgBuff = ByteBuffer.allocate(7);
        msgBuff.order(ByteOrder.BIG_ENDIAN);
        msgBuff.putInt(3).put((byte) 9).putShort((short) port);
        addToWriteBufferList(msgBuff.array());
    }

    private void connected(){
        readBuffer = ByteBuffer.allocateDirect(17*1024);
        readBuffer.order(ByteOrder.BIG_ENDIAN);
        writeBuffer = ByteBuffer.allocateDirect(17*1024);
        writeBuffer.order(ByteOrder.BIG_ENDIAN);
        isConnected = true;

        startRead();

        synchronized (this){
            if (writeBufferList.size() != 0){
                isWriting = true;
                writeFromListToBuffer();
                writeBuffer.flip();
                socketChannel.write(writeBuffer, this, writeHandler);
            }
        }
    }

    private void startRead(){
        readState = MessageReadState.HANDSHAKE_PROTOCOL_LENGTH;
        waitForData(1);
        socketChannel.read(readBuffer, this, readHandler);
    }

    private void handleReadCompletion(Integer read){
        if (read == -1){
            onChannelFailed();
            return;
        }
        readBuffer.flip();
        while (readBuffer.remaining() >= targetLength){
            readDataAvailable();
        }
        readBuffer.compact();
        socketChannel.read(readBuffer, this, readHandler);
    }

    private void readDataAvailable(){

        switch (readState){
            case HANDSHAKE_PROTOCOL_LENGTH: {
                byte protocolLength = readBuffer.get();
                waitForData(protocolLength + 28);
                readState = MessageReadState.HANDSHAKE_INFOHASH;
                break;
            }
            case HANDSHAKE_INFOHASH: {
                handshakeObjectBuffer[0] = getStringFromBuffer(targetLength - 28);
                handshakeObjectBuffer[1] = getBitfieldFromBuffer(8);
                handshakeObjectBuffer[2] = getHashFromBuffer();

                infoHashListener.infoHashReceived(this, (SHA1) handshakeObjectBuffer[2]);
                infoHashListener = null;

                waitForData(20);
                readState = MessageReadState.HANDSHAKE_PEERID;
                break;
            }
            case HANDSHAKE_PEERID: {
                String peerId = getStringFromBuffer(20);
                Scheduler.submit(() -> peer.onHandshake(
                        (String) handshakeObjectBuffer[0],
                        (BitField) handshakeObjectBuffer[1],
                        (SHA1) handshakeObjectBuffer[2],
                        peerId));

                waitForData(4);
                readState = MessageReadState.MESSAGE_LENGTH;
                break;
            }
            case MESSAGE_LENGTH: {
                int messageLength = readBuffer.getInt();
                if (messageLength == 0){ //Keep-alive
                    waitForData(4);
                    readState = MessageReadState.MESSAGE_LENGTH;
                    break;
                } else {
                    waitForData(messageLength);
                    readState = MessageReadState.MESSAGE_PAYLOAD;
                    break;
                }
            }
            case MESSAGE_PAYLOAD: {
                byte messageId = readBuffer.get();
                switch (messageId){
                    case 0: {Scheduler.submit(() -> peer.setChokingMe(true)); break;}
                    case 1: {Scheduler.submit(() -> peer.setChokingMe(false)); break;}
                    case 2: {Scheduler.submit(() -> peer.setInterestedInMe(true)); break;}
                    case 3: {Scheduler.submit(() -> peer.setInterestedInMe(false)); break;}
                    case 4: {int pieceIndex = readBuffer.getInt(); Scheduler.submit(() -> peer.onHavePiece(pieceIndex)); break;}
                    case 5: {BitField field = getBitfieldFromBuffer(targetLength - 1); Scheduler.submit(() -> peer.onBitfield(field)); break;}
                    case 6: {
                        int pieceIndex = readBuffer.getInt();
                        int baseOffset = readBuffer.getInt();
                        int length = readBuffer.getInt();
                        Scheduler.submit(() -> peer.onRequestBlock(new RequestBlock(pieceIndex, baseOffset, length)));
                        break;
                    }
                    case 7: {
                        int pieceIndex = readBuffer.getInt();
                        int baseOffset = readBuffer.getInt();
                        byte[] data = new byte[targetLength - 9];
                        readBuffer.get(data);
                        Scheduler.submit(() -> peer.onPieceBlock(new PieceBlock(pieceIndex, baseOffset, data)));
                        break;
                    }
                    case 8: {
                        int pieceIndex = readBuffer.getInt();
                        int baseOffset = readBuffer.getInt();
                        int length = readBuffer.getInt();
                        Scheduler.submit(() -> peer.onCancelBlock(new RequestBlock(pieceIndex, baseOffset, length)));
                        break;
                    }
                    case 9: {int port = Short.toUnsignedInt(readBuffer.getShort()); Scheduler.submit(() -> peer.onPort(port)); break;}
                    default: readBuffer.position(readBuffer.position() + targetLength - 1);
                }

                waitForData(4);
                readState = MessageReadState.MESSAGE_LENGTH;
                break;
            }
        }
    }

    private void waitForData(int length){
        targetLength = length;
    }

    private String getStringFromBuffer(int length){
        byte[] stringBuff = new byte[length];
        readBuffer.get(stringBuff);
        return new String(stringBuff);
    }

    private BitField getBitfieldFromBuffer(int byteLength){
        byte[] bitfieldBuff = new byte[byteLength];
        readBuffer.get(bitfieldBuff);
        return new BitField(bitfieldBuff, 8*byteLength);
    }

    private SHA1 getHashFromBuffer(){
        byte[] hashBuff = new byte[20];
        readBuffer.get(hashBuff);
        return SHA1.fromByteArray(hashBuff);
    }

    private void addToWriteBufferList(byte[] data){
        synchronized (this){
            writeBufferList.add(data);
            if (isConnected && writeBufferList.size() == 1 && !isWriting){
                isWriting = true;
                writeFromListToBuffer();
                writeBuffer.flip();
                socketChannel.write(writeBuffer, this, writeHandler);
            }
        }
    }

    private void handleWriteCompletion(Integer write){
        if (write == -1){ onChannelFailed(); return;}

        Scheduler.submit(() -> {
            synchronized (this){
                writeBuffer.compact();
                writeFromListToBuffer();
                writeBuffer.flip();
                if (writeBuffer.remaining() == 0){
                    isWriting = false;
                } else {
                    socketChannel.write(writeBuffer, this, writeHandler);
                }
            }
        });
    }

    private void writeFromListToBuffer(){
        while (writeBufferList.size() != 0 && writeBuffer.remaining() >= writeBufferList.get(0).length) {
            writeBuffer.put(writeBufferList.get(0));
            writeBufferList.remove(0);
        }
    }


    private void onChannelFailed(){

    }

    public SocketAddress getRemoteAddress() throws IOException {
        return socketChannel.getRemoteAddress();
    }

    private static final CompletionHandler<Void, PeerTcpChannel> connectHandler = new CompletionHandler<Void, PeerTcpChannel>(){

        @Override
        public void completed(Void result, PeerTcpChannel attachment) {
            attachment.connected();
        }

        @Override
        public void failed(Throwable exc, PeerTcpChannel attachment) {
            attachment.onChannelFailed();
        }
    };

    private static final CompletionHandler<Integer, PeerTcpChannel> readHandler = new CompletionHandler<Integer, PeerTcpChannel>() {
        @Override
        public void completed(Integer result, PeerTcpChannel attachment) {
            attachment.handleReadCompletion(result);
        }

        @Override
        public void failed(Throwable exc, PeerTcpChannel attachment) {
            attachment.onChannelFailed();
        }
    };

    private static final CompletionHandler<Integer, PeerTcpChannel> writeHandler = new CompletionHandler<Integer, PeerTcpChannel>() {
        @Override
        public void completed(Integer result, PeerTcpChannel attachment) {
            attachment.handleWriteCompletion(result);
        }

        @Override
        public void failed(Throwable exc, PeerTcpChannel attachment) {
            attachment.onChannelFailed();
        }
    };
}
