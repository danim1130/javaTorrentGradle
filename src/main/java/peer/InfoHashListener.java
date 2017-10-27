package peer;

import util.SHA1;

public interface InfoHashListener {
    void infoHashReceived(PeerTcpChannel tcpChannel, SHA1 infoHash);
}
