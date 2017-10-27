import bencode.BencodeDecode;
import bencode.BencodeException;
import torrent.Torrent;
import torrent.TorrentManager;
import torrent.torrentdata.TorrentData;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static java.time.temporal.WeekFields.ISO;

public class Main {

    public static void main(String[] args) throws IOException, BencodeException, InterruptedException, NoSuchAlgorithmException {
        FileInputStream inputStream = new FileInputStream("ubuntu.torrent");
        Map<String, Object> map = (Map<String, Object>) BencodeDecode.bDecodeStream(inputStream);
        TorrentData torrentData = new TorrentData(map);

        Torrent torrent = new Torrent(torrentData);

        TorrentManager torrentManager = new TorrentManager();
        torrentManager.addTorrent(torrent);

        torrent.startTorrent();

        while (true){
            System.out.println("Left: " + torrent.getLeft());
            Thread.sleep(5000);
        }
    }
}
