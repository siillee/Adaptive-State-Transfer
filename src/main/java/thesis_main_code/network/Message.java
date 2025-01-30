package thesis_main_code.network;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * This class represents a message we will use in the communication between nodes.
 * For this project, the message will probably be fairly simple.
 */
public class Message {

    // Types are as follows: 0 = EPOCH, 1 = STATE_REQUEST, 2 = STATE_RESPONSE
    private byte type;
    private int replicaId;
    private int epoch;
    private byte[] payload;

    public Message(byte type, int replicaId, int epoch, byte[] payload) {
        this.type = type;
        this.replicaId = replicaId;
        this.epoch = epoch;
        this.payload = payload;
    }

    public Message(byte type, int replicaId, byte[] payload) {
        this.type = type;
        this.replicaId = replicaId;
        this.epoch = -1;
        this.payload = payload;
    }

    public byte getType() {
        return type;
    }
    public byte[] getPayload() {
        return payload;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getReplicaId() {
        return replicaId;
    }

    public void setReplicaId(int replicaId) {
        this.replicaId = replicaId;
    }

    public int getEpoch() {
        return epoch;
    }

    public void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }

    public byte[] toByteArray() {

        // 4 bytes for the payload length, 4 bytes for the replica id,
        // 1 byte for the type of message, 4 bytes in case we have an epoch for the state response messages,
        // and the rest is for the payload
        ByteBuffer buffer = this.type == 2
                ? ByteBuffer.allocate(4 + 4 + 1 + 4 + payload.length)
                : ByteBuffer.allocate(4 + 4 + 1 + payload.length);
        buffer.putInt(this.type == 2 ? payload.length + 4 + 1 + 4 : payload.length + 4 + 1);
        buffer.putInt(replicaId);
        buffer.put(type);
        if (this.type == 2) {
            buffer.putInt(this.epoch);
        }
        buffer.put(payload);
        return buffer.array();
    }

    public static Message fromByteArray(byte[] bytes) {
        int epoch = -1;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int replicaId = buffer.getInt();
        byte type = buffer.get();
        if (type == 2) {
            epoch = buffer.getInt();
        }
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return new Message(type, replicaId, epoch, payload);
    }
}
