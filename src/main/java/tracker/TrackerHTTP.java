package tracker;

import bencode.BencodeDecode;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by Dani on 2016. 11. 19..
 */
class TrackerHTTP extends Tracker {

    private String baseUrl;
    private String trackerId;

    private long downloadedAtStart = 0;

    TrackerHTTP(TrackerManager listener, String baseUrl) {
        super(listener);
        this.baseUrl = baseUrl;
    }

    protected void connect(TrackerTorrentInformation information){
        StringBuilder fullUriB = new StringBuilder();
        fullUriB.append(baseUrl);

        switch (state){
            default:
            case STARTING: fullUriB.append("?event=started"); downloadedAtStart = information.getDownloaded(); break;
            case STOPPING: fullUriB.append("?event=stopped"); break;
            case COMPLETING: fullUriB.append("?event=completed"); break;
        }

        try {
            fullUriB.append("&info_hash=").append(URLEncoder.encode(new String(information.getInfoHash(),"ISO-8859-1"),"ISO-8859-1"))  //TODO: Encoding, maybe write custom
                    .append("&peer_id=").append(information.getPeerId())
                    .append("&port=").append(information.getPort())
                    .append("&downloaded=").append(information.getDownloaded() - downloadedAtStart)
                    .append("&uploaded=").append(information.getUploaded())
                    .append("&left=").append(information.getLeft())
                    .append("&compact=").append(information.isCompact() ? "1" : "0");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (information.getKey() != -1){
            fullUriB.append("&key=").append(information.getKey());
        }

        if (trackerId != null){
            fullUriB.append(trackerId);
        }

        String fullUri = fullUriB.toString();

        HttpURLConnection targetUrl;
        try {
            targetUrl = (HttpURLConnection) new URL(fullUri).openConnection();
            //int responseCode = targetUrl.getResponseCode();

            Map<String, Object> item = (Map<String, Object>) BencodeDecode.bDecodeStream(targetUrl.getInputStream());
            targetUrl.disconnect();
            if (item != null){
                if (item.get("failure reason") != null){
                    String error = (String) item.get("failure reason");
                    this.listener.onFailedRequest(this, error);
                    state = TrackerState.FAILED;
                    failureReason = error;
                } else {
                    lastResponseTime = System.nanoTime();

                    Long interval = (Long) item.get("interval");
                    if (interval != null){
                        this.interval = TimeUnit.SECONDS.toNanos(interval);
                    } else {
                        this.interval = TimeUnit.MINUTES.toNanos(30);
                    }

                    Long minInterval = (Long) item.get("min interval");
                    if (minInterval != null){
                        this.minInterval = TimeUnit.SECONDS.toNanos(minInterval);
                    }

                    String trackerId = (String) item.get("trackerid");
                    if (trackerId != null){
                        this.trackerId = trackerId;
                    }

                    Long complete = (Long) item.get("complete");
                    if (complete != null){
                        this.seederCount = complete;
                    }

                    Long incomplete = (Long) item.get("incomplete");
                    if (incomplete != null){
                        this.leecherCount = incomplete;
                    }

                    switch (state){
                        case COMPLETING:
                        case STARTING:
                            state = TrackerState.STARTED; break;
                        case STOPPING:
                            state = TrackerState.STOPPED; break;
                    }

                    String peers = (String) item.get("peers");
                    if (peers != null){
                        Set<SocketAddress> foundPeers = new HashSet<>();
                        //TODO: check if it's not compact
                        byte[] peerArray = peers.getBytes("ISO-8859-1");
                        for (int i = 0; i < peerArray.length / 6; i++){
                            InetAddress peerAddress = InetAddress.getByAddress(Arrays.copyOfRange(peerArray,i*6,i*6+4));
                            int peerPort = ((peerArray[i*6 + 4] & 0xff) << 8) | (peerArray[i*6+5] & 0xff);
                            foundPeers.add(new InetSocketAddress(peerAddress, peerPort));
                        }
                        listener.onResponseReceived(foundPeers);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            listener.onFailedRequest(this, e.toString());
            state = TrackerState.FAILED;
            failureReason = e.getMessage();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TrackerHTTP that = (TrackerHTTP) o;

        return baseUrl != null ? baseUrl.equals(that.baseUrl) : that.baseUrl == null;

    }

    @Override
    public int hashCode() {
        return baseUrl != null ? baseUrl.hashCode() : 0;
    }
}
