package thesis_main_code.nodes;

import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.network.MessageReceiver;
import thesis_main_code.network.ServerTCP;
import thesis_main_code.protocols.CollaborativeStateTransfer;
import thesis_main_code.protocols.NaiveStateTransfer;
import thesis_main_code.protocols.PBFTStateTransfer;
import thesis_main_code.protocols.StateTransferProtocol;
import thesis_main_code.utils.DiskManagement;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents a replica node. Replica nodes are the ones participating in consensus, state transfer, etc.
 * These nodes can be either correct or faulty, and in the tests there will be 3f + 1 of them (the Byzantine case).
 */
public class ReplicaNode implements MessageReceiver {

    // With 3f+1 replicas, during the state transfer we wait for 2f+1 responses to make sure messages are correct
    // We have 4 replicas, 1 of which can be faulty. Therefore, the quorum needed is 3.
    public static final int QUORUM = 3;
    // Same as above, only f+1.
    public static final int WEAK_QUORUM = 2;

    private DiskManagement diskManagement;
    private StateTransferProtocol stateTransferProtocol;

    // These arrays are used when creating all the necessary sockets for a replica.
    // Servers are listening to the control node and other replicas. Clients are other replicas.
    private int[] replicaServerPorts;
    private int[] replicaClientPorts;
    private String[] replicaHosts;
    private int[] replicaIds;

    private Map<Integer, ClientTCP> otherReplicas;
    private int replicaId;
    private String stateTransferProtocolName;

    private List<byte[]> state = new ArrayList<>();
    private AtomicInteger epoch;
    private AtomicInteger lastStableCheckpoint = new AtomicInteger(0);
    public static boolean isByzantine;

    private AtomicBoolean stateTransferFinished = new AtomicBoolean(true);

    private ConcurrentLinkedQueue<Message> stateResponseMessages = new ConcurrentLinkedQueue<>();

    public ReplicaNode(int[] replicaServerPorts,
                       int[] replicaClientPorts,
                       String[] replicaHosts,
                       int[] replicaIds,
                       int replicaId,
                       String stateTransferProtocol,
                       boolean isByzantine) {

        this.diskManagement = new DiskManagement("checkpoints/checkpoints_replica_" + replicaId);

        this.replicaServerPorts = replicaServerPorts;
        this.replicaClientPorts = replicaClientPorts;
        this.replicaHosts = replicaHosts;
        this.replicaIds = replicaIds;

        this.otherReplicas = new HashMap<>();
        this.epoch = new AtomicInteger(0);
        this.replicaId = replicaId;
        this.stateTransferProtocolName = stateTransferProtocol;

        this.isByzantine = isByzantine;
    }

    public void startReplicaNode() {

        switch (stateTransferProtocolName) {
            case "naive1":
            case "naive2":
                stateTransferProtocol = new NaiveStateTransfer(
                    state, replicaId, epoch, lastStableCheckpoint,
                    stateTransferProtocolName, otherReplicas, stateResponseMessages, stateTransferFinished
                );
                break;
            case "pbft":
                stateTransferProtocol = new PBFTStateTransfer(
                        state, replicaId, epoch, lastStableCheckpoint,
                        otherReplicas, stateResponseMessages, stateTransferFinished
                );
                break;
            case "collab":
                stateTransferProtocol = new CollaborativeStateTransfer(
                        state, replicaId, epoch, lastStableCheckpoint,
                        otherReplicas, stateTransferFinished
                );
                break;
            default:
                break;
        }

        startServerThreads();
        connectToOtherReplicas();
    }

    @Override
    public void receiveMessage(Message message) {
        // Different behaviour depending on the type of message
        switch (message.getType()) {
            // Case it is an EPOCH message
            case 0:
                receiveEpochMessage(message);
                break;
            // Case it is a STATE_REQUEST message
            case 1:
                receiveStateRequestMessage(message);
                break;
            // Case it is a STATE_RESPONSE message
            case 2:
                receiveStateResponseMessage(message);
                break;
            default:
                break;
        }
    }

    /************ Functions that handle all necessary network connections at the start *****************/

    // Starts all servers for this replica. These include listening to the control node, as well as other replicas
    private void startServerThreads() {

        for (int i = 0 ; i < replicaServerPorts.length ; i++) {
            ServerTCP controlNodeServer = new ServerTCP(replicaServerPorts[i], this);
            new Thread(controlNodeServer).start();
        }
    }

    // Connects to all other replicas in the system, starting a thread that will be used to send direct messages
    private void connectToOtherReplicas() {

        for (int i = 0; i < replicaClientPorts.length; i++) {
            boolean connected = false;
            while (!connected) {
                try {
                    Socket socket = new Socket(replicaHosts[i], replicaClientPorts[i]);
                    connected = true;
                    ClientTCP newReplica = new ClientTCP(socket, socket.getOutputStream());
                    otherReplicas.put(replicaIds[i], newReplica);
                    new Thread(newReplica).start();
                } catch (IOException e) {
                    System.out.println("Replica failed to connect to replica. Retrying...");
                    try {
                        Thread.sleep(5000); // Wait 5 seconds before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // Restore interrupted state
                    }
                }
            }
        }
    }

    /************************** Functions for receiving specific messages *****************************/

    private void receiveEpochMessage(Message message) {

        ByteBuffer buffer = ByteBuffer.wrap(message.getPayload());
        int currentEpoch = buffer.getInt();

        if (currentEpoch - this.epoch.get() >= 2) {
            stateTransferFinished.set(false);
            // Skipped some rounds, which means we need to update the state
            // Trigger state transfer
            stateTransferProtocol.triggerStateTransfer(message, currentEpoch);
        }
        // Wait until state transfer is finished.
        while (!stateTransferFinished.get()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }

        if (currentEpoch <= this.epoch.get()) {
            return;
        }
        this.epoch.set(currentEpoch);

        try {
            // Generate 200MB of dummy data
            byte[] data = diskManagement.generateData(epoch.get(), isByzantine);

            synchronized (this.stateTransferProtocol) {
                state.add(data);
            }

            // Check if WRITE_THRESHOLD messages have been received
            if (state.size() == DiskManagement.WRITE_THRESHOLD) {
                diskManagement.writeToDisk(lastStableCheckpoint.get(), state);
                state.clear();
                System.gc();
                lastStableCheckpoint.incrementAndGet();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
//      System.out.println("Delivered EPOCH message from Control Node, the current epoch is : " + this.epoch);
    }

    private void receiveStateRequestMessage(Message message) {
        System.out.println("Received STATE_REQUEST message from Replica " + message.getReplicaId());
        stateTransferProtocol.processStateRequest(message);
    }

    private void receiveStateResponseMessage(Message message) {
        stateTransferProtocol.processStateResponse(message);
    }
}
