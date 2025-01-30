package thesis_main_code.network;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * This class represents a thread used to send message from the control node to replicas.
 */
public class ControlNodeTCP implements Runnable {

    private Socket[] sockets;
    private OutputStream[] outputStreams;
    // Used to keep track of which replica is faulty, if any
    private boolean[] faultyReplicas;
    private int epoch;
    private final int BROADCAST_INTERVAL = 5000;

    public static final int EPOCH_WAIT = 20;
//    private int randomEpochWait = 0;
    private int randomEpochWaitCounter = 0;
    private int randomFaulty = 1;

    public ControlNodeTCP(Socket[] sockets, OutputStream[] outputStreams) {
        this.sockets = sockets;
        this.outputStreams = outputStreams;
        this.faultyReplicas = new boolean[outputStreams.length];
        this.epoch = 1;
    }

    @Override
    public void run() {

        while (true) {
            try {
                if (epoch % 3 == 0 && noneFaulty()) {
//                    randomEpochWait = 3 + (int) (Math.random() * 10);
//                    randomFaulty = (int)(Math.random() * 4);
                    faultyReplicas[randomFaulty] = true;
                    randomEpochWaitCounter = 0;
                    System.out.println("One replica becomes faulty for " + EPOCH_WAIT + " epochs");
                }
                if (randomEpochWaitCounter == EPOCH_WAIT) {
                    faultyReplicas[randomFaulty] = false;
                }
                randomEpochWaitCounter++;
                // Create new epoch message, broadcast, and sleep and increment epoch.
                byte[] epochBytes = ByteBuffer.allocate(4).putInt(epoch).array();
                Message epochMessage = new Message((byte) 0, 0, -1, epochBytes);
                broadcastMessage(epochMessage);
                Thread.sleep(BROADCAST_INTERVAL);
                epoch++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void broadcastMessage(Message message) {

        for (int i = 0 ; i < outputStreams.length ; i++) {
            if (!faultyReplicas[i]) {
                sendMessage(message, outputStreams[i]);
            }
        }
    }

    // Method used to send a message through the TCP channel.
    // The message can be an epoch increment.
    private void sendMessage(Message message, OutputStream outputStream) {
        if (outputStream != null) {
            try {
                outputStream.write(message.toByteArray());
                outputStream.flush();
                System.out.println("Sent message: " + message);
            } catch (IOException ioe) {
                System.out.println("Failed to send message " + message + " because of some error during writing to output stream");
                ioe.printStackTrace();
            }
        } else {
            System.err.println("Failed to send message " + message + " because the output stream is null");
        }
    }

    private boolean noneFaulty() {

        for (int i = 0 ; i < faultyReplicas.length ; i++) {
            if (faultyReplicas[i]) {
                return false;
            }
        }
        return true;
    }
}
