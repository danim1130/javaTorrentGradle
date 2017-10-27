package torrent;

import peer.InfoHashListener;
import peer.Peer;
import peer.PeerTcpChannel;
import util.SHA1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Map;

public class TorrentManager implements InfoHashListener{

    private Map<SHA1, Torrent> torrents = new HashMap<>();
    private AsynchronousServerSocketChannel serverSocketChannel;

    public TorrentManager(){
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(6881));
        } catch (IOException e) {
            e.printStackTrace();
        }
        startListener();
    }

    private void startListener() {
        //serverSocketChannel.accept(this, acceptHandler);
    }

    private void onNewSocketChannel(AsynchronousSocketChannel channel){
        PeerTcpChannel peerTcpChannel = new PeerTcpChannel(channel, this);
        serverSocketChannel.accept(this, acceptHandler);
    }

    @Override
    public void infoHashReceived(PeerTcpChannel tcpChannel, SHA1 infoHash) {
        Torrent torrent = torrents.get(infoHash);
        if (torrent != null){
            torrent.onNewPeerChannel(tcpChannel);
        } else {
            tcpChannel.close();
        }
    }

    public void addTorrent(Torrent torrent){
        torrents.put(torrent.getTorrentData().getInfoHash(), torrent);
    }

    private static final CompletionHandler<AsynchronousSocketChannel, TorrentManager> acceptHandler = new CompletionHandler<AsynchronousSocketChannel, TorrentManager>() {
        @Override
        public void completed(AsynchronousSocketChannel result, TorrentManager attachment) {
            attachment.onNewSocketChannel(result);
        }

        @Override
        public void failed(Throwable exc, TorrentManager attachment) {
        }
    };
}
