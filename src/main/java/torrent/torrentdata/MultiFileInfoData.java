package torrent.torrentdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MultiFileInfoData extends InfoData {
    private List<FileData> files = new ArrayList<>();

    public MultiFileInfoData(Map<String, Object> base) {
        super(base);

        List<Map<String, Object>> fileMap = (List<Map<String, Object>>) base.get("files");
        for (Map<String, Object> baseMap : fileMap){

            Long fileLength = (Long) baseMap.get("length");
            List<String> pathNames = (List<String>) baseMap.get("path");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : pathNames){
                pathBuilder.append(part);
                pathBuilder.append('/');
            }
            String name = pathBuilder.substring(0, pathBuilder.length()-1);

            files.add(new FileData(name, fileLength));
        }
    }

    public List<FileData> getFiles() {
        return files;
    }
}
