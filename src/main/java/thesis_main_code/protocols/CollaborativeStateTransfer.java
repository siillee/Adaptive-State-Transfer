package thesis_main_code.protocols;

import thesis_main_code.network.ControlNodeTCP;
import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.nodes.ReplicaNode;
import thesis_main_code.utils.DiskManagement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class contains methods related to the Collaborative State Transfer protocol, as done in Dura-SMaRt.
 */
public class CollaborativeStateTransfer implements StateTransferProtocol {

    private List<byte[]> state;
    private int replicaId;
    private AtomicInteger epoch;
    private AtomicInteger lastStableCheckpoint;

    private Map<Integer, ClientTCP> otherReplicas;
    private AtomicBoolean stateTransferFinished;

    private DiskManagement diskManagement;

    // Map used to keep parts of the state that were received, but cannot yet be applied
    private Map<Integer, byte[]> pendingState;

    private int lastEpochToReceive = -1;

    private Map<Integer, List<Integer>> requestedChkptsPerReplica = new HashMap<>();
    private AtomicInteger byzantineReplicaId = new AtomicInteger(-1);

    // Used for the performance evaluation
    private BufferedWriter testResultsWriterTime;
    private long startTime = 0;
    private long endTime = 0;
    private BufferedWriter testResultsWriterBandwidth;
    private long totalBytesSent = 0;
    public static long totalBytesWritten = 0;
    private long totalBytesRead = 0;
    public static double totalTimeSending = 0;
    // Used to take care of the case where one replica is asked twice for some data,
    // which happens in the healthy byzantine case
    private boolean firstStateReqReceived = false;


    public CollaborativeStateTransfer(List<byte[]> state, int replicaId, AtomicInteger epoch,
                                      AtomicInteger lastStableCheckpoint,
                                      Map<Integer, ClientTCP> otherReplicas,
                                      AtomicBoolean stateTransferFinished) {

        this.state = state;
        this.replicaId = replicaId;
        this.epoch = epoch;
        this.lastStableCheckpoint = lastStableCheckpoint;
        this.otherReplicas = otherReplicas;
        this.stateTransferFinished = stateTransferFinished;

        this.diskManagement = new DiskManagement("checkpoints/checkpoints_replica_" + replicaId);

        this.pendingState = new HashMap<>();

        try {
            this.testResultsWriterTime = new BufferedWriter(new FileWriter("state_transfer-test_results/collab/healthy_byzantine_tc_geo.txt",
                    true)); // 'true' to append data

            this.testResultsWriterBandwidth = new BufferedWriter(new FileWriter("state_transfer-test_results/collab/replica"
                    + this.replicaId + "/healthy_byzantine_tc_geo.txt", true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void triggerStateTransfer(Message message, int currentEpoch) {

        startTime = System.nanoTime();


        int epochDifference = ReplicaNode.isByzantine ? currentEpoch : currentEpoch - this.epoch.get();
        lastEpochToReceive = ReplicaNode.isByzantine ? epochDifference - 1 : this.epoch.get() + epochDifference - 1;
        // Ask each replica for this many checkpoints
//        int chkptsToEachReplica = (epochDifference - 1) / otherReplicas.size();
        // Ask some of them for a bit more, if there is still some checkpoints not accounted for
//        int extraChkpts = (epochDifference - 1) % otherReplicas.size();
        int nextEpoch = ReplicaNode.isByzantine ? 1 : epoch.get() + 1;

        // This is for the version where one replica is much closer than the other two
        Map<Integer, Integer> chkptsToEachReplica = new HashMap<>();
        int leftoverChkpts = epochDifference - 1;
        chkptsToEachReplica.put(1, (int) Math.round((epochDifference - 1) * 0.66));
        leftoverChkpts -= (int) Math.round((epochDifference - 1) * 0.66);

        // Case I use the 5th replica
//        chkptsToEachReplica.put(1, (int) Math.round((epochDifference - 1) * 0.33));
//        leftoverChkpts -= (int) Math.round((epochDifference - 1) * 0.33);
//        chkptsToEachReplica.put(5, (int) Math.round((epochDifference - 1) * 0.33));
//        leftoverChkpts -= (int) Math.round((epochDifference - 1) * 0.33);

        chkptsToEachReplica.put(3, (int) Math.round((epochDifference - 1) * 0.16));
        leftoverChkpts -= (int) Math.round((epochDifference - 1) * 0.16);
        chkptsToEachReplica.put(4, leftoverChkpts);

        for (Map.Entry<Integer, ClientTCP> replica: otherReplicas.entrySet()) {
            List<Integer> requestChkpts = new ArrayList<>();
            while (requestChkpts.size() < chkptsToEachReplica.get(replica.getKey())) {
                requestChkpts.add(nextEpoch);
                nextEpoch++;
            }

            requestedChkptsPerReplica.put(replica.getKey(), requestChkpts);

            // Create and send the state request message
            ByteBuffer buf = ByteBuffer.allocate(4 * requestChkpts.size());
            for (Integer chkpt: requestChkpts) {
                buf.putInt(chkpt);
            }
            Message stateRequestMessage = new Message (
                    (byte) 1,
                    this.replicaId,
                    buf.array()
            );
            replica.getValue().sendMessage(stateRequestMessage);
            totalBytesSent += stateRequestMessage.getPayload().length;
        }


//        for (Map.Entry<Integer, ClientTCP> replica: otherReplicas.entrySet()) {
//            List<Integer> requestChkpts = new ArrayList<>();
//            while (requestChkpts.size() < chkptsToEachReplica) {
//                requestChkpts.add(nextEpoch);
//                nextEpoch++;
//            }
//            if (extraChkpts > 0) {
//                requestChkpts.add(nextEpoch);
//                nextEpoch++;
//                extraChkpts--;
//            }
//
//            requestedChkptsPerReplica.put(replica.getKey(), requestChkpts);
//
//            // Create and send the state request message
//            ByteBuffer buf = ByteBuffer.allocate(4 * requestChkpts.size());
//            for (Integer chkpt: requestChkpts) {
//                buf.putInt(chkpt);
//            }
//            Message stateRequestMessage = new Message (
//                    (byte) 1,
//                    this.replicaId,
//                    buf.array()
//            );
//            replica.getValue().sendMessage(stateRequestMessage);
//            totalBytesSent += stateRequestMessage.getPayload().length;
//        }
    }

    @Override
    public void processStateRequest(Message message) {

        ByteBuffer buffer = ByteBuffer.wrap(message.getPayload());
        List<Integer> requestedChkpts = new ArrayList<>();
        while (buffer.hasRemaining()) {
            requestedChkpts.add(buffer.getInt());
        }
        for (Integer reqChkpt: requestedChkpts) {
            int stableChkpt = reqChkpt % DiskManagement.WRITE_THRESHOLD == 0
                    ? reqChkpt / DiskManagement.WRITE_THRESHOLD
                    : reqChkpt / DiskManagement.WRITE_THRESHOLD + 1;

            int reqChkptPlace = (reqChkpt - 1) % DiskManagement.WRITE_THRESHOLD;
            int startIndex = reqChkptPlace * DiskManagement.EPOCH_DATA_SIZE;

            if (stableChkpt == lastStableCheckpoint.get() + 1) {
                // Case it is the current checkpoint, and data is in the state. No need for disk read.
                ByteBuffer buf = ByteBuffer.allocate(DiskManagement.EPOCH_DATA_SIZE);
                buf.put(state.get((reqChkpt - 1) % DiskManagement.WRITE_THRESHOLD));
                Message stateResponseMessage = new Message((byte) 2, this.replicaId, reqChkpt, buf.array());
                otherReplicas.get(message.getReplicaId()).sendMessage(stateResponseMessage);
                totalBytesSent += stateResponseMessage.getPayload().length;

                System.out.println("Sent STATE_RESPONSE message for epoch " + reqChkpt + " to Replica " + message.getReplicaId());
            } else {
                // Case there is a need to first read from disk, and then add to the requestedState.
                try {
                    ByteBuffer buf = ByteBuffer.allocate(DiskManagement.EPOCH_DATA_SIZE);
                    buf.put(diskManagement.readFromDisk(
                            stableChkpt,
                            startIndex,
                            DiskManagement.EPOCH_DATA_SIZE));
                    totalBytesRead += DiskManagement.EPOCH_DATA_SIZE;
                    Message stateResponseMessage = new Message((byte) 2, this.replicaId, reqChkpt, buf.array());
                    otherReplicas.get(message.getReplicaId()).sendMessage(stateResponseMessage);
                    totalBytesSent += stateResponseMessage.getPayload().length;

                    System.out.println("Sent STATE_RESPONSE message for epoch " + reqChkpt + " to Replica " + message.getReplicaId());
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }

        try {
            // The commented code used when testing the healthy_byzantine case
//            if (!firstStateReqReceived) {
//                testResultsWriterBandwidth.write("First request received: "
//                        + ClientTCP.EPOCH_WAIT + "," + totalBytesSent + "," + totalBytesWritten + "," + totalBytesRead);
//                firstStateReqReceived = true;
//            } else {
//                testResultsWriterBandwidth.write("Second request received: "
//                        + ClientTCP.EPOCH_WAIT + "," + totalBytesSent + "," + totalBytesWritten + "," + totalBytesRead);
//                firstStateReqReceived = false;
//            }
            testResultsWriterBandwidth.write(
                    ControlNodeTCP.EPOCH_WAIT
                            + "," + totalBytesSent
                            + "," + totalBytesWritten
                            + "," + totalBytesRead
                            + "," + (totalBytesSent / DiskManagement.MB_TO_BYTES) / totalTimeSending * 8 + " Mbit/s");
            testResultsWriterBandwidth.newLine();
            testResultsWriterBandwidth.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        totalBytesSent = 0;
        totalBytesRead = 0;
        totalBytesWritten = 0;
        totalTimeSending = 0;
    }

    @Override
    public void processStateResponse(Message message) {

        if (message.getReplicaId() == byzantineReplicaId.get()) {
            return;
        }
//        if (ReplicaNode.isByzantine) {
//            stateTransferFinished.set(true);
//            return;
//        }

        byte[] messagePayload = message.getPayload();
        int checkpoint = message.getEpoch();
        if (checkpoint <= this.epoch.get()) {
            return;
        }

        if (messagePayload[0] == 0) {
            byzantineReplicaId.set(message.getReplicaId());
            // Create and send the state request message for the missing checkpoints to another replica
            ByteBuffer buf = ByteBuffer.allocate(4 * requestedChkptsPerReplica.get(message.getReplicaId()).size());
            for (Integer chkpt: requestedChkptsPerReplica.get(message.getReplicaId())) {
                buf.putInt(chkpt);
            }
            Message stateRequestMessage = new Message (
                    (byte) 1,
                    this.replicaId,
                    buf.array()
            );
            int anotherReplicaId = message.getReplicaId() % 4 + 1;
            int targetReplicaId = anotherReplicaId == this.replicaId ? anotherReplicaId % 4 + 1 : anotherReplicaId;
            // For tests with the 5th replica
//            int targetReplicaId = 5;
            otherReplicas.get(targetReplicaId).sendMessage(stateRequestMessage);
            totalBytesSent += stateRequestMessage.getPayload().length;
        } else {
            synchronized (this) {
                if (checkpoint - this.epoch.get() == 1) {
                    // If it is the next checkpoint to be received, add it to state
                    state.add(messagePayload);
                    requestedChkptsPerReplica.get(message.getReplicaId()).remove((Integer) checkpoint);
                    epoch.incrementAndGet();
                    try {
                        if (state.size() == DiskManagement.WRITE_THRESHOLD) {
                            diskManagement.writeToDisk(lastStableCheckpoint.get(), state);
                            for (byte[] dataBlock : state) {
                                totalBytesWritten += dataBlock.length;
                            }
                            state.clear();
                            System.gc();
                            lastStableCheckpoint.incrementAndGet();
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                } else {
                    // Else, add it to the map, so it will be added to the state later
                    pendingState.put(checkpoint, messagePayload);
                }
            }
        }
        synchronized (this) {
            while (pendingState.containsKey(this.epoch.get() + 1)) {
            System.out.println("Adding all missing data to the state, from the pending map");
                state.add(pendingState.get(epoch.incrementAndGet()));
                try {
                    if (state.size() == DiskManagement.WRITE_THRESHOLD) {
                        diskManagement.writeToDisk(lastStableCheckpoint.get(), state);
                        for (byte[] dataBlock : state) {
                            totalBytesWritten += dataBlock.length;
                        }
                        state.clear();
                        System.gc();
                        lastStableCheckpoint.incrementAndGet();
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }

        if (this.epoch.get() == lastEpochToReceive) {
            stateTransferFinished.set(true);
            pendingState.clear();
            byzantineReplicaId.set(-1);

            endTime = System.nanoTime();
            try {
                testResultsWriterTime.write(ControlNodeTCP.EPOCH_WAIT + "," + ((endTime - startTime) / 1000000000.0));
                testResultsWriterTime.newLine();
                testResultsWriterTime.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                testResultsWriterBandwidth.write(ControlNodeTCP.EPOCH_WAIT + "," + totalBytesSent + "," + totalBytesWritten + "," + totalBytesRead);
                testResultsWriterBandwidth.newLine();
                testResultsWriterBandwidth.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            totalBytesRead = 0;
            totalBytesWritten = 0;
            totalBytesSent = 0;
            System.out.println("State transfer finished. The current epoch is: " + epoch +
                    "  and the latest stable checkpoint is: " + lastStableCheckpoint);
        }
    }
}
