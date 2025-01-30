package thesis_main_code.network;

import thesis_main_code.bw_behaviour_testing.Receiver;
import thesis_main_code.nodes.ReplicaNode;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * This class represents a thread used for opening a server-side TCP connection with a replica/control node.
 * This thread is just used to keep the connection open, and send messages when needed.
 * Server-side TCP is needed for receiving messages, which only replicas do, not the control node.
 */
public class ServerTCP implements Runnable {

    private int replicaPort;
    private MessageReceiver messageReceiver;

    public ServerTCP(int replicaPort, MessageReceiver messageReceiver) {
        this.replicaPort = replicaPort;
        this.messageReceiver = messageReceiver;
    }

    @Override
    public void run() {

        try (ServerSocket serverSocket = new ServerSocket(replicaPort)) {
            System.out.println("Replica listening on port " + replicaPort);
            Socket controlNodeSocket = serverSocket.accept();
            System.out.println("Replica connected to node on port : " + replicaPort);

            InputStream inputStream = controlNodeSocket.getInputStream();

            while (true) {

                // Read the message length first (4 bytes)
                byte[] lengthBuffer = new byte[4];
                int lengthBytesRead = inputStream.read(lengthBuffer);
                if (lengthBytesRead == -1) {
                    // Handle disconnection
                    System.out.println("Connection closed by control node.");
                    break;
                }
                // Get the length of the incoming message
                int messageLength = ByteBuffer.wrap(lengthBuffer).getInt();

                byte[] messageBuffer = new byte[messageLength];
                int totalBytesRead = 0;
                while (totalBytesRead < messageLength) {
                    int bytesRead = inputStream.read(messageBuffer, totalBytesRead, messageLength - totalBytesRead);
                    if (bytesRead == -1) {
                        // Handle disconnection or end of stream
                        System.out.println("Connection lost.");
                        break;
                    }
                    totalBytesRead += bytesRead;
                }

                Message receivedMessage = Message.fromByteArray(messageBuffer);
                System.out.println("Received a message from Replica " + receivedMessage.getReplicaId() + ".  Continuing the processing of the message");
                messageReceiver.receiveMessage(receivedMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
