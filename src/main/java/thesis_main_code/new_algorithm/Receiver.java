package thesis_main_code.new_algorithm;

import thesis_main_code.bw_behaviour_testing.Sender;
import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.network.MessageReceiver;
import thesis_main_code.network.ServerTCP;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

public class Receiver implements MessageReceiver {

    private ClientTCP sender;

    private int receiverId;

    private int serverPort;
    private String senderHost;
    private int senderPort;

    private int messageCount = 0;
    private boolean requestAlreadyLive = false;
    private Object lock = new Object();

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

        startSendingRequests();
    }

    @Override
    public void receiveMessage(Message message) {


        if (message.getType() == 20) {
            System.out.println("Received special Scenario 3 message from Sender");
            // This is the special message from Scenario 3, telling the receiver he can send next request
            synchronized (lock) {
                lock.notify();
            }
        } else {
            System.out.println("Received message from Sender");
            messageCount++;
            sender.sendMessage(new Message((byte) 11, this.receiverId, new byte[0]));

            if (messageCount == Sender.NUMBER_OF_CHUNKS) {
                if (!scenario.equals("scenario3")) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
                messageCount = 0;
            }
            message.setPayload(null);
            System.gc();
        }

    }

    private void startSendingRequests() {

        if (scenario.isEmpty()) {
            new Thread(() -> {

                while (true) {
                    try {
                        Thread.sleep((this.receiverId + new Random().nextInt(61 - 1) + 1) * 1000L);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }

                    sender.sendMessage(new Message((byte) 12, this.receiverId, new byte[0]));

                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }

            }).start();
        } else {
            if (scenario.equals("scenario2")) {
                if (receiverId == 2) {
                    new Thread(() -> {
                        while (true) {
                            try {
                                Thread.sleep(25000);
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }

                            sender.sendMessage(new Message((byte) 12, this.receiverId, new byte[0]));
                            synchronized (lock) {
                                try {
                                    lock.wait();
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                            }
                        }

                    }).start();
                } else {
                    new Thread(() -> {
                        while (true) {
                            sender.sendMessage(new Message((byte) 12, this.receiverId, new byte[0]));
                            synchronized (lock) {
                                try {
                                    lock.wait();
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                            }
                        }
                    }).start();
                }
            } else if (scenario.equals("scenario1")) {
                if (receiverId == 2) {
                    new Thread(() -> {
                        while (true) {
                            try {
                                Thread.sleep(15000);
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }

                            sender.sendMessage(new Message((byte) 12, this.receiverId, new byte[0]));
                            synchronized (lock) {
                                try {
                                    lock.wait();
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                            }
                        }

                    }).start();
                }
            } else {

                final int sleep_time = receiverId == 1 ? 20000 : 25000;
                new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(sleep_time);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }

                        sender.sendMessage(new Message((byte) 12, this.receiverId, new byte[0]));
                        synchronized (lock) {
                            try {
                                lock.wait();
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }
                        }
                    }
                }).start();
            }
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
