package thesis_main_code.network;

import thesis_main_code.protocols.CollaborativeStateTransfer;

import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientTCP implements Runnable {

    private BlockingQueue<Message> messageQueue;
    private Socket socket;
    private OutputStream outputStream;

    public ClientTCP(Socket socket, OutputStream outputStream) {
        this.messageQueue = new LinkedBlockingQueue<>();
        this.socket = socket;
        this.outputStream = outputStream;
    }

    @Override
    public void run() {

        while (true) {
            try {
//                System.out.println("Source port is : " + socket.getLocalPort());
//                System.out.println("Desstination port is : " + socket.getPort());
                Message message = messageQueue.take();
                long startTime = System.nanoTime();
                outputStream.write(message.toByteArray());
                outputStream.flush();
                long endTime = System.nanoTime();
                CollaborativeStateTransfer.totalTimeSending += (endTime - startTime) / 1000000000.0;
//                System.out.println("Sent message of type " + message.getType() + " to a replica at port: " + socket.getPort());
            } catch (Exception e) {
                System.out.println("Error when sending a message to another replica");
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(Message message) {
        messageQueue.add(message);
    }
}
