package tracker;

import java.net.InetAddress;
import java.util.Arrays;

/**
 * Created by danim on 01/11/2016.
 */
public class TrackerTorrentInformation {

    public static class Builder {
        private byte[] infoHash;
        private String peerId;
        private short port;
        private long downloaded;
        private long uploaded;
        private long left;
        private boolean compact = true;
        private InetAddress ip;
        private int peerWanted = -1;
        private int key = -1;

        public Builder setInfoHash(byte[] infoHash) {
            this.infoHash = infoHash;
            return this;
        }

        public Builder setPeerId(String peerId) {
            this.peerId = peerId;
            return this;
        }

        public Builder setPort(short port) {
            this.port = port;
            return this;
        }

        public Builder setDownloaded(long downloaded) {
            this.downloaded = downloaded;
            return this;
        }

        public Builder setUploaded(long uploaded) {
            this.uploaded = uploaded;
            return this;
        }

        public Builder setLeft(long left) {
            this.left = left;
            return this;
        }

        public Builder setCompact(boolean compact) {
            this.compact = compact;
            return this;
        }

        public Builder setIp(InetAddress ip) {
            this.ip = ip;
            return this;
        }

        public Builder setPeerWanted(int peerWanted) {
            this.peerWanted = peerWanted;
            return this;
        }

        public Builder setKey(int key) {
            this.key = key;
            return this;
        }

        public TrackerTorrentInformation build() {
            return new TrackerTorrentInformation(infoHash, peerId, port, downloaded, uploaded, left, compact, ip, peerWanted, key);
        }
    }

    private byte[] infoHash;
    private String peerId;
    private short port;
    private long downloaded;
    private long uploaded;
    private long left;
    private boolean compact;
    private InetAddress ip;
    private int peerWanted;
    private int key;
    //private int trackerId;


    private TrackerTorrentInformation(byte[] infoHash, String peerId, short port, long downloaded, long uploaded,
                                      long left, boolean compact, InetAddress ip,
                                      int peerWanted, int key) {
        this.infoHash = infoHash;
        this.peerId = peerId;
        this.port = port;
        this.downloaded = downloaded;
        this.uploaded = uploaded;
        this.left = left;
        this.compact = compact;
        this.ip = ip;
        this.peerWanted = peerWanted;
        this.key = key;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public String getPeerId() {
        return peerId;
    }

    public short getPort() {
        return port;
    }

    public long getDownloaded() {
        return downloaded;
    }

    public long getUploaded() {
        return uploaded;
    }

    public long getLeft() {
        return left;
    }

    public boolean isCompact() {
        return compact;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPeerWanted() {
        return peerWanted;
    }

    public int getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TrackerTorrentInformation that = (TrackerTorrentInformation) o;

        if (port != that.port) return false;
        if (downloaded != that.downloaded) return false;
        if (uploaded != that.uploaded) return false;
        if (left != that.left) return false;
        if (compact != that.compact) return false;
        if (peerWanted != that.peerWanted) return false;
        if (key != that.key) return false;
        if (!Arrays.equals(infoHash, that.infoHash)) return false;
        if (peerId != null ? !peerId.equals(that.peerId) : that.peerId != null) return false;
        return ip != null ? ip.equals(that.ip) : that.ip == null;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(infoHash);
        result = 31 * result + (peerId != null ? peerId.hashCode() : 0);
        result = 31 * result + (int) port;
        result = 31 * result + (int) (downloaded ^ (downloaded >>> 32));
        result = 31 * result + (int) (uploaded ^ (uploaded >>> 32));
        result = 31 * result + (int) (left ^ (left >>> 32));
        result = 31 * result + (compact ? 1 : 0);
        result = 31 * result + (ip != null ? ip.hashCode() : 0);
        result = 31 * result + peerWanted;
        result = 31 * result + key;
        return result;
    }
}
