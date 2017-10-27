package tracker;

import torrent.Torrent;
import util.Scheduler;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by danim on 13/01/2017.
 */

public class TrackerManager {

    private List<List<Tracker>> trackers;

    private Torrent listener;
    private ScheduledFuture scheduledFuture;

    public TrackerManager(Torrent torrent, List<List<String>> trackerAddresses) {
        this.listener = torrent;
        trackers = trackerAddresses.stream().map(it ->
                it.stream().map(itIn -> Tracker.fromAddress(this, itIn)).collect(Collectors.toList()))
            .collect(Collectors.toList());
    }

    public void startManager(){
        Scheduler.submit(() -> {
            if (scheduledFuture != null){
                if (scheduledFuture.isDone() == false && scheduledFuture.cancel(false) == false){
                    return;
                }
                scheduledFuture = null;
            }

            Tracker connectedTracker = null;
            for (List<Tracker> tierList : trackers){
                for (Tracker tracker : tierList){
                    tracker.sendInformation(getCommonInformationBuilder(listener).setPeerWanted(100).build());
                    if (!tracker.hasFailed()){
                        connectedTracker = tracker;
                        scheduledFuture = Scheduler.schedule(this::startManager, tracker.getInterval(), TimeUnit.NANOSECONDS);
                        break;
                    }
                }
                if (connectedTracker != null){
                    tierList.remove(connectedTracker);
                    tierList.add(0, connectedTracker);
                    break;
                }
            }
        });
    }

    public void onResponseReceived(Set<SocketAddress> response) {
        if (response.size() != 0){
            listener.foundPeers(response);
        }
    }

    public void onFailedRequest(Tracker tracker, String failureReason) {

    }

    private TrackerTorrentInformation.Builder getCommonInformationBuilder(Torrent data){
        TrackerTorrentInformation.Builder builder = new TrackerTorrentInformation.Builder();
        builder.setCompact(true).setDownloaded(data.getDownloaded()).setUploaded(data.getUploaded())
                .setLeft(data.getLeft()).setInfoHash(data.getTorrentData().getInfoHash().getByteRepresentation())
                .setPeerId(data.getPeerId()).setPort((short) 6881);
        return builder;
    }
}
