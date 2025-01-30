package thesis_main_code.bw_behaviour_testing;

import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.network.MessageReceiver;
import thesis_main_code.network.ServerTCP;

import java.io.IOException;
import java.net.Socket;

/**
 * This class represents a receiver of data. Used in bandwidth behaviour tests.
 */
public class Receiver implements MessageReceiver {

    private ClientTCP sender;

    private int receiverId;

    private int serverPort;
    private String senderHost;
    private int senderPort;

    private int messageCount = 0;

    private String scenario;

    public Receiver(int serverPort, String senderHost, int senderPort, int receiverId, String scenario) {
        this.serverPort = serverPort;
        this.senderHost = senderHost;
        this.senderPort = senderPort;
        this.receiverId = receiverId;

        this.scenario = scenario;
    }

    public void startReceiverNode() {

        startServerThread();
        connectToSender();
    }

    @Override
    public void receiveMessage(Message message) {

        if (scenario.equals("ackTest")) {
//            System.out.println("Entered ackTest if");
            System.out.println("Received message from Sender");
            sender.sendMessage(new Message((byte) 11, this.receiverId, new byte[0]));
            message.setPayload(null);
            System.gc();
        } else {
            System.out.println("Received message from Sender");
            messageCount++;
            if (messageCount == Sender.NUMBER_OF_CHUNKS) {
                sender.sendMessage(new Message((byte) 11, this.receiverId, new byte[0]));
                messageCount = 0;
            }
            message.setPayload(null);
            System.gc();
        }

    }

    private void startServerThread() {

        ServerTCP controlNodeServer = new ServerTCP(serverPort, this);
        new Thread(controlNodeServer).start();
    }

    private void connectToSender() {

        boolean connected = false;
        while (!connected) {
            try {
                Socket socket = new Socket(senderHost, senderPort);
                connected = true;
                sender = new ClientTCP(socket, socket.getOutputStream());
                new Thread(sender).start();
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
