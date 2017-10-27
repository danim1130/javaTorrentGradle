package tracker;

/**
 * Created by danim on 01/11/2016.
 */
public abstract class Tracker {

    protected TrackerState state = TrackerState.STARTING;

    protected TrackerManager listener;

    protected long lastResponseTime = Long.MIN_VALUE;
    protected long interval = Long.MAX_VALUE;
    protected long minInterval = 0;
    protected String failureReason = null;
    protected long seederCount;
    protected long leecherCount;

    public Tracker(TrackerManager listener){
        this.listener = listener;
    }

    public static Tracker fromAddress(TrackerManager listener, String address){
        if (address.startsWith("http://")){
            return new TrackerHTTP(listener, address);
        } else if (address.startsWith("udp://")){
            return new TrackerUDP(listener, address);
        }

        return null;
    }

    public void stopTracker(TrackerTorrentInformation currentTorrent){
        if (state == TrackerState.STARTED) {
            state = TrackerState.STOPPING;
            connect(currentTorrent);
        }
    }

    public void completeTracker(TrackerTorrentInformation currentTorrent){
        if (state == TrackerState.STARTED) {
            state = TrackerState.COMPLETING;
            connect(currentTorrent);
        }
    }

    public void sendInformation(TrackerTorrentInformation currentTorrent){
        if (state != TrackerState.FAILED){
            connect(currentTorrent);
        }
    }

    public void retryConnection(TrackerTorrentInformation currentTorrent){
        if (state == TrackerState.FAILED){
            state = TrackerState.STARTING;
            connect(currentTorrent);
        }
    }

    public boolean hasFailed(){
        return state == TrackerState.FAILED;
    }

    public long getInterval() {
        return interval;
    }

    public String getFailureReason() {
        return failureReason;
    }

    protected abstract void connect(TrackerTorrentInformation information);
}
