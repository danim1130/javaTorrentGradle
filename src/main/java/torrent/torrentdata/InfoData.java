package torrent.torrentdata;

import util.SHA1;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class InfoData {

    private String name;
    private Long pieceLength;
    private List<SHA1> pieces = new ArrayList<>();
    private boolean priv;

    public InfoData(Map<String, Object> base) {
        this.name = (String) base.get("name");
        this.pieceLength = (Long) base.get("piece length");

        try {
            byte[] pieceArr = ((String) base.get("pieces")).getBytes("ISO-8859-1");
            for (int i = 0; i < pieceArr.length; i+=20){
                SHA1 hash = SHA1.fromByteArray(Arrays.copyOfRange(pieceArr, i, i + 20));
                pieces.add(hash);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        this.priv = base.containsKey("private") ? ((Long) base.get("private")) == 1 : false;
    }

    public String getName() {
        return name;
    }

    public Long getPieceLength() {
        return pieceLength;
    }

    public List<SHA1> getPieces() {
        return pieces;
    }

    public boolean isPriv() {
        return priv;
    }
}
