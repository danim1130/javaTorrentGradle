package tracker;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;

/**
 * Created by Dani on 2016. 11. 19..
 */
class TrackerUDP extends Tracker {  //TODO: Completely rework udp to use a single port, cache connection id

    private URI uri;

    private long lastConnectionTime = Long.MIN_VALUE;
    private long downloadedAtStart = 0;

    TrackerUDP(TrackerManager listener, String address){
        super(listener);
        try {
            this.uri = new URI(address);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void connect(TrackerTorrentInformation information) {
        DatagramChannel channel = null;
        try {
            channel = DatagramChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
            channel.socket().setSoTimeout(15);

            Random random = new Random();
            int transactionId = random.nextInt();

            ByteBuffer buffer = ByteBuffer.allocate(1024 * 16);
            buffer.putLong(0x41727101980L); //Connection ID
            buffer.putInt(0); //Action: connect
            buffer.putInt(transactionId);
            buffer.flip();
            while(buffer.hasRemaining()) {
                channel.write(buffer);
            }

            buffer.clear();
            channel.read(buffer);

            buffer.flip();
            if (buffer.getInt() != 0){ //Action type : connect
                throw new IOException("Wrong action id!");
            }
            if (buffer.getInt() != transactionId){
                throw new IOException("Wrong transaction id!");
            }
            long connectionId = buffer.getLong();

            int event;
            switch (state)
            {
                case COMPLETING: event = 1; downloadedAtStart = information.getDownloaded(); break;
                case STARTING: event = 2;  break;
                case STOPPING: event = 3; break;
                default: event = 0; break;
            }

            transactionId = random.nextInt();
            buffer.clear();
            buffer.putLong(connectionId)
                    .putInt(1) //Action : announce
                    .putInt(transactionId);
            buffer.put(information.getInfoHash());
            buffer.put(information.getPeerId().getBytes())
                    .putLong(information.getDownloaded() - downloadedAtStart)
                    .putLong(information.getLeft())
                    .putLong(information.getUploaded());

            buffer.putInt(event)
                    .putInt(0) //IP-address
                    .putInt(information.getKey())
                    .putInt(information.getPeerWanted())
                    .putShort(information.getPort());
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            buffer.clear();
            channel.read(buffer);
            buffer.flip();

            int action = buffer.getInt();
            if (action == 3) {
                buffer.getInt();
                String error = new String(buffer.array(), buffer.position(), buffer.remaining());
                throw new IOException(error);
            } else if (action != 1){
                throw  new IOException("Wrong action type received");
            }

            if (buffer.getInt() != transactionId){
                throw new IOException("Wrong transaction id received");
            }
            this.interval = buffer.getInt();
            this.leecherCount = buffer.getInt();
            this.seederCount = buffer.getInt();

            Set<SocketAddress> addressList = new HashSet<>();
            byte[] ipAddress = new byte[4];
            while (buffer.hasRemaining()){
                buffer.get(ipAddress);
                short port = buffer.getShort();
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(ipAddress), port & 0x0000_ffff);
                addressList.add(address);
            }

            lastResponseTime = System.nanoTime();
            switch (state){
                case COMPLETING:
                case STARTING:
                    state = TrackerState.STARTED; break;
                case STOPPING:
                    state = TrackerState.STOPPED; break;
            }

            this.listener.onResponseReceived(addressList);
        } catch (Exception e) {
            state = TrackerState.FAILED;
            failureReason = e.getMessage();
            this.listener.onFailedRequest(this, e.toString());
        } finally {
            if (channel != null){
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TrackerUDP that = (TrackerUDP) o;

        return uri != null ? uri.equals(that.uri) : that.uri == null;

    }

    @Override
    public int hashCode() {
        return uri != null ? uri.hashCode() : 0;
    }
}
