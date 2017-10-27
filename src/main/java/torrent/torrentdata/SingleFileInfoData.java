package torrent.torrentdata;

import java.util.Map;

public class SingleFileInfoData extends InfoData {
    private FileData file;

    public SingleFileInfoData(Map<String, Object> base) {
        super(base);

        String fileName = (String) base.get("name");
        Long fileLength = (Long) base.get("length");
        file = new FileData(fileName, fileLength);
    }

    public FileData getFile() {
        return file;
    }
}
