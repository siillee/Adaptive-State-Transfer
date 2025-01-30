package thesis_main_code.protocols;

import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.nodes.ReplicaNode;
import thesis_main_code.utils.DiskManagement;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class contains methods related to the State Transfer protocol as done in PBFT.
 */
public class PBFTStateTransfer implements StateTransferProtocol {

    private List<byte[]> state;
    private int replicaId;
    private AtomicInteger epoch;
    private AtomicInteger lastStableCheckpoint;

    private Map<Integer, ClientTCP> otherReplicas;
    private ConcurrentLinkedQueue<Message> stateResponseMessages;
    private AtomicBoolean stateTransferFinished;

    private DiskManagement diskManagement;

    public PBFTStateTransfer(List<byte[]> state, int replicaId, AtomicInteger epoch,
                             AtomicInteger lastStableCheckpoint,
                             Map<Integer, ClientTCP> otherReplicas,
                             ConcurrentLinkedQueue<Message> stateResponseMessages,
                             AtomicBoolean stateTransferFinished) {

        this.state = state;
        this.replicaId = replicaId;
        this.epoch = epoch;
        this.lastStableCheckpoint = lastStableCheckpoint;
        this.otherReplicas = otherReplicas;
        this.stateResponseMessages = stateResponseMessages;
        this.stateTransferFinished = stateTransferFinished;

        this.diskManagement = new DiskManagement("checkpoints/checkpoints_replica_" + replicaId);
    }

    @Override
    public void triggerStateTransfer(Message message, int currentEpoch) {

        // Send a hash of your epoch, wait for the response, send again, wait again, and then send state request
        // to one of the replicas. This mimics the pbft rounds of fetching from partition tree.
        try {
            MessageDigest messDigest = MessageDigest.getInstance("SHA-256");
            byte[] messPayload = messDigest.digest(ByteBuffer.allocate(4).putInt(epoch.get()).array());
            Message pbftFetchMessage = new Message((byte) 3, this.replicaId, -1, messPayload);
            // We go 5 rounds, could be changed if needed
            for (int i = 0 ; i < 5 ; i++) {
                for (Map.Entry<Integer, ClientTCP> entry: otherReplicas.entrySet()) {
                    // Broadcast the request, and wait for the responses concurrently
                    entry.getValue().sendMessage(pbftFetchMessage);
                }
                // I use the stateResponseMessages object here to avoid cluttering this class with more fields
                while (stateResponseMessages.size() < ReplicaNode.QUORUM) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
                stateResponseMessages.clear();
            }

            Message stateRequestMessage = new Message((byte) 1, this.replicaId, -1, ByteBuffer.allocate(4).putInt(epoch.get()).array());
            // Ask any replica, let's say the one on the "right", so this.replicaId + 1
            otherReplicas.get(this.replicaId % 4 + 1).sendMessage(stateRequestMessage);

        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Wrong name of algorithm chosen for hashing");
            nsae.printStackTrace();
        }
    }

    @Override
    public void processStateRequest(Message message) {

//        ByteBuffer buffer = ByteBuffer.wrap(message.getPayload());
//        // Is this replica supposed to transfer the state, or just hash "validation"
//        int faultyRepEpoch = buffer.getInt();
//        int faultyRepLastStableChkpt = faultyRepEpoch / DiskManagement.WRITE_THRESHOLD;
//        int faultyRepLastChkptInLastStableChkpt = faultyRepEpoch % DiskManagement.WRITE_THRESHOLD;
//
//        List<byte[]> requestedState = new ArrayList<>();
//
//        try {
//            if (faultyRepLastStableChkpt != lastStableCheckpoint.get()) {
//                // First read the last checkpoint of which the faulty maybe has some info
//                byte[] lastFaultyChkptData = diskManagement.readFromDisk(faultyRepLastStableChkpt + 1);
//                requestedState.add(
//                        Arrays.copyOfRange(
//                                lastFaultyChkptData,
//                                faultyRepLastChkptInLastStableChkpt * DiskManagement.DATA_SIZE_MB * DiskManagement.MB_TO_BYTES,
//                                lastFaultyChkptData.length
//                        )
//                );
//
//                // Now, read the rest of the stable checkpoints that are fully missing from the faulty
//                for (int i = faultyRepLastStableChkpt + 2 ; i <= lastStableCheckpoint.get() ; i++) {
//                    requestedState.add(diskManagement.readFromDisk(i));
//                }
//                // Add the data from the current checkpoint, not yet stable (accumulatedData) to the message
//                requestedState.addAll(state);
//            } else {
//                for (int i = faultyRepLastChkptInLastStableChkpt ; i < state.size() - 1 ; i++) {
//                    requestedState.add(state.get(i));
//                }
//            }
//        } catch (IOException ioe) {
//            System.out.println("Error while reading checkpoint from file");
//        }
//
//        // Create state response message
//        int totalLength = requestedState.stream().mapToInt(byteArray -> byteArray.length).sum();
//        ByteBuffer reqStateBuffer = ByteBuffer.allocate(totalLength);
//        requestedState.forEach(reqStateBuffer::put);
//        Message stateResponseMessage = new Message((byte) 2, this.replicaId, reqStateBuffer.array());
//
//        // Send state response message
//        otherReplicas.get(message.getReplicaId()).sendMessage(stateResponseMessage);
//        stateResponseMessage = null;
//        System.out.println("Sent STATE_RESPONSE message to Replica " + message.getReplicaId());
    }

    @Override
    public void processStateResponse(Message message) {

        byte[] messagePayload = message.getPayload();
        for (int i = 0 ; i < messagePayload.length ; i += DiskManagement.DATA_SIZE_MB * DiskManagement.MB_TO_BYTES) {
            state.add(Arrays.copyOfRange(messagePayload, i, i + DiskManagement.DATA_SIZE_MB * DiskManagement.MB_TO_BYTES));
            epoch.incrementAndGet();
            try {
                if (state.size() == DiskManagement.WRITE_THRESHOLD) {
                    diskManagement.writeToDisk(lastStableCheckpoint.get(), state);
                    lastStableCheckpoint.incrementAndGet();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        stateTransferFinished.set(true);
        System.out.println("State transfer finished. The current epoch is: " + epoch +
                "  and the latest stable checkpoint is: " + lastStableCheckpoint);
    }

    public void receivePBFTFetchMessage(Message message) {

        try {
            MessageDigest messDigest = MessageDigest.getInstance("SHA-256");
            byte[] messPayload = messDigest.digest(ByteBuffer.allocate(4).putInt(epoch.get()).array());
            Message pbftFetchResponseMessage = new Message((byte) 4, this.replicaId, -1, messPayload);
            otherReplicas.get(message.getReplicaId()).sendMessage(pbftFetchResponseMessage);

        } catch (NoSuchAlgorithmException nsae) {
            System.out.println("Wrong name of algorithm chosen for hashing");
            nsae.printStackTrace();
        }
    }

    public void receivePBFTFetchResponseMessage(Message message) {
        stateResponseMessages.add(message);
    }
}
