package torrent.torrentdata;

public class FileData {

    private String name;
    private Long length;

    public FileData(String name, Long length) {
        this.name = name;
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public Long getLength() {
        return length;
    }
}
