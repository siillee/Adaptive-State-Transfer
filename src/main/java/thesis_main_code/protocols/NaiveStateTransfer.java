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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class contains methods related to the naive implementations of state transfer protocols.
 */
public class NaiveStateTransfer implements StateTransferProtocol {

    private List<byte[]> state;
    private int replicaId;
    private AtomicInteger epoch;
    private AtomicInteger lastStableCheckpoint;

    private String stateTransferProtocolName;
    private Map<Integer, ClientTCP> otherReplicas;
    private ConcurrentLinkedQueue<Message> stateResponseMessages;
    private AtomicBoolean stateTransferFinished;

    private DiskManagement diskManagement;

    private AtomicBoolean foundByzantine = new AtomicBoolean(false);
    private AtomicInteger byzantineReplicaId = new AtomicInteger(-1);
    private AtomicInteger nonByzantineReplicaId = new AtomicInteger(-1);
    private int lastEpochToReceive = -1;

    private Map<Integer, List<Message>> stateResponsePerEpoch = new HashMap<>();

    // Used for the performance evaluation
    private BufferedWriter testResultsWriterTime;
    private long startTime = 0;
    private long endTime = 0;
    private BufferedWriter testResultsWriterBandwidth;
    private long totalBytesSent = 0;
    public static long totalBytesWritten = 0;
    private long totalBytesRead = 0;

    public NaiveStateTransfer(List<byte[]> state, int replicaId, AtomicInteger epoch,
                              AtomicInteger lastStableCheckpoint,
                              String stateTransferProtocolName,
                              Map<Integer, ClientTCP> otherReplicas,
                              ConcurrentLinkedQueue<Message> stateResponseMessages,
                              AtomicBoolean stateTransferFinished) {

        this.state = state;
        this.replicaId = replicaId;
        this.epoch = epoch;
        this.lastStableCheckpoint = lastStableCheckpoint;
        this.stateTransferProtocolName = stateTransferProtocolName;
        this.otherReplicas = otherReplicas;
        this.stateResponseMessages = stateResponseMessages;
        this.stateTransferFinished = stateTransferFinished;

        this.diskManagement = new DiskManagement("checkpoints/checkpoints_replica_" + replicaId);

        try {
            this.testResultsWriterTime = new BufferedWriter(new FileWriter("state_transfer-test_results/"
                    + stateTransferProtocolName + "/all_healthy.txt", true)); // 'true' to append data

            this.testResultsWriterBandwidth = new BufferedWriter(new FileWriter("state_transfer-test_results/"
                    + stateTransferProtocolName + "/replica" + this.replicaId + "/all_healthy.txt", true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void triggerStateTransfer(Message message, int currentEpoch) {

        startTime = System.nanoTime();

        if (stateTransferProtocolName.equals("naive1")) {
            triggerStateTransferNaive1(message, currentEpoch);
        } else {
            triggerStateTransferNaive2(message, currentEpoch);
        }
    }

    @Override
    public void processStateRequest(Message message) {

        ByteBuffer buffer = ByteBuffer.wrap(message.getPayload());
        int faultyRepEpoch = buffer.getInt();

        for (int i = faultyRepEpoch + 1 ; i < this.epoch.get() ; i++) {
            int stableCheckpoint = i % DiskManagement.WRITE_THRESHOLD == 0
                    ? i / DiskManagement.WRITE_THRESHOLD
                    : i / DiskManagement.WRITE_THRESHOLD + 1;
            int chkptInStableChkpt = i % DiskManagement.WRITE_THRESHOLD == 0
                    ? DiskManagement.WRITE_THRESHOLD
                    : i % DiskManagement.WRITE_THRESHOLD;

            if (stableCheckpoint != lastStableCheckpoint.get() + 1) {
                // If the epoch i is not in the current state, i.e. needs to be read from disk
                try {
                    if (stateTransferProtocolName.equals("naive2")) {
                        ByteBuffer buf = ByteBuffer.allocate(DiskManagement.EPOCH_DATA_SIZE);
                        buf.put(diskManagement.readFromDisk(stableCheckpoint,
                                (chkptInStableChkpt-1) * DiskManagement.EPOCH_DATA_SIZE,
                                DiskManagement.EPOCH_DATA_SIZE));
                        totalBytesRead += DiskManagement.EPOCH_DATA_SIZE;
                        Message stateResponseMessage = new Message((byte) 2, this.replicaId, i, buf.array());
                        otherReplicas.get(message.getReplicaId()).sendMessage(stateResponseMessage);
                        totalBytesSent += stateResponseMessage.getPayload().length;
                    } else {
                        Message stateResponseMessage = new Message(
                                (byte) 2,
                                this.replicaId,
                                i,
                                diskManagement.readFromDisk(stableCheckpoint,
                                        (chkptInStableChkpt-1) * DiskManagement.EPOCH_DATA_SIZE,
                                        DiskManagement.EPOCH_DATA_SIZE));
                        totalBytesRead += DiskManagement.EPOCH_DATA_SIZE;
                        otherReplicas.get(message.getReplicaId()).sendMessage(stateResponseMessage);
                        totalBytesSent += stateResponseMessage.getPayload().length;
                    }
//                    System.out.println("Sent STATE_RESPONSE message for epoch " + i + " to Replica " + message.getReplicaId());
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } else {
                // Else, we just get from the current state and send it
                Message stateResponseMessage = new Message((byte) 2, this.replicaId, i, state.get(chkptInStableChkpt - 1));
                otherReplicas.get(message.getReplicaId()).sendMessage(stateResponseMessage);
                totalBytesSent += stateResponseMessage.getPayload().length;
//                System.out.println("Sent STATE_RESPONSE message for epoch " + i + " to Replica " + message.getReplicaId());
            }
        }

        try {
            testResultsWriterBandwidth.write(ControlNodeTCP.EPOCH_WAIT + "," + totalBytesSent + "," + totalBytesWritten + "," + totalBytesRead);
            testResultsWriterBandwidth.newLine();
            testResultsWriterBandwidth.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        totalBytesSent = 0;
        totalBytesRead = 0;
        totalBytesWritten = 0;
    }

    @Override
    public void processStateResponse(Message message) {

        if (stateTransferProtocolName.equals("naive1")) {
            processStateResponseNaive1(message);
        } else {
            processStateResponseNaive2(message);
        }
    }

    // Sequentially ask other replicas for the state
    private void triggerStateTransferNaive1(Message message, int currentEpoch) {

        int epochDifference = currentEpoch - this.epoch.get();
        lastEpochToReceive = this.epoch.get() + epochDifference - 1;

        for (Map.Entry<Integer, ClientTCP> entry: otherReplicas.entrySet()) {
            if (stateTransferFinished.get()) {
                break;
            }
            Message stateRequestMessage = new Message((byte) 1, this.replicaId, ByteBuffer.allocate(4).putInt(this.epoch.get()).array());
            entry.getValue().sendMessage(stateRequestMessage);
            totalBytesSent += stateRequestMessage.getPayload().length;

            while(!stateTransferFinished.get() && !foundByzantine.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            foundByzantine.set(false);
        }
    }

    // Concurrently ask other replicas for the state
    private void triggerStateTransferNaive2(Message message, int currentEpoch) {

        int epochDifference = currentEpoch - this.epoch.get();
        lastEpochToReceive = this.epoch.get() + epochDifference - 1;

        Message stateRequestMessage = new Message((byte) 1, this.replicaId, ByteBuffer.allocate(4).putInt(epoch.get()).array());

        // This is basically sending to a weak quorum of replicas, f+1, in this case just 2
        // TODO: In case this needs to be tested on a larger scale, change this!
        otherReplicas.get(this.replicaId % 4 + 1).sendMessage(stateRequestMessage);
        totalBytesSent += stateRequestMessage.getPayload().length;
        otherReplicas.get((this.replicaId - 1) % 4).sendMessage(stateRequestMessage);
        totalBytesSent += stateRequestMessage.getPayload().length;

        while(!stateTransferFinished.get() && !foundByzantine.get()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }

        // If one of the previous ones was Byzantine, ask the third one
        // New message needed in case part of the state was delivered and the epoch changed
        if (foundByzantine.get()) {
            stateRequestMessage = new Message((byte) 1, this.replicaId, ByteBuffer.allocate(4).putInt(epoch.get()).array());
            nonByzantineReplicaId.set(this.replicaId % 4 + 2);
            otherReplicas.get(nonByzantineReplicaId.get()).sendMessage(stateRequestMessage);
            totalBytesSent += stateRequestMessage.getPayload().length;
        }
    }

    private void processStateResponseNaive1(Message message) {

        if (byzantineReplicaId.get() == message.getReplicaId()) {
            return;
        }

        byte[] messagePayload = message.getPayload();
        if (messagePayload[0] == 0) {
            foundByzantine.set(true);
            byzantineReplicaId.set(message.getReplicaId());
            return;
        }
        synchronized (this) {
            state.add(messagePayload);
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
        }

        if (this.epoch.get() == lastEpochToReceive) {
            stateTransferFinished.set(true);
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

//            System.out.println("State transfer finished. The current epoch is: " + epoch +
//                    "  and the latest stable checkpoint is: " + lastStableCheckpoint);
        }
    }

    private synchronized void processStateResponseNaive2(Message message) {

        int senderId = message.getReplicaId();
        int messageEpoch = message.getEpoch();

        if (senderId == nonByzantineReplicaId.get()) {
            state.add(message.getPayload());
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
            if (foundByzantine.get()) {
                // Case where messages from the first two replicas come, but we established one of them is Byzantine
                return;
            }

            if (!stateResponsePerEpoch.containsKey(messageEpoch)) {
                stateResponsePerEpoch.put(messageEpoch, new ArrayList<>());
            }
            stateResponsePerEpoch.get(messageEpoch).add(message);

            if (stateResponsePerEpoch.get(messageEpoch).size() == ReplicaNode.WEAK_QUORUM) {
                byte[] message1Payload = stateResponsePerEpoch.get(messageEpoch).get(0).getPayload();
                byte[] message2Payload = stateResponsePerEpoch.get(messageEpoch).get(1).getPayload();
                // Since the messageEpoch is at the start of payload, check the 5th byte, which is the start of data
                if (message1Payload[5] != message2Payload[5]) {
                    foundByzantine.set(true);
                    return;
                }
                state.add(message1Payload);
                stateResponsePerEpoch.get(messageEpoch).clear();
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
            }
        }

        if (this.epoch.get() == lastEpochToReceive) {
            stateTransferFinished.set(true);
            nonByzantineReplicaId.set(-1);
            foundByzantine.set(false);

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
//            System.out.println("State transfer finished. The current epoch is: " + epoch +
//                    "  and the latest stable checkpoint is: " + lastStableCheckpoint);
        }
    }
}
