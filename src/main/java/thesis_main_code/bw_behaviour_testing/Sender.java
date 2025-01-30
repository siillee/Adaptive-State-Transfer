package thesis_main_code.bw_behaviour_testing;

import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.network.MessageReceiver;
import thesis_main_code.network.ServerTCP;
import thesis_main_code.utils.CommandExecutor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class represents a sender of data. Used in bandwidth behaviour tests.
 */
public class Sender implements MessageReceiver {

    private static final int REPETITIONS = 20;
    public static final int NUMBER_OF_CHUNKS = 20;
    private static final int DATA_SIZE = 1024 * 1024 * 100;
    private static final int SLEEP_TIME = 15000;


    private Map<Integer, ClientTCP> receivers;

    private int[] serverPorts;
    private int[] receiverPorts;
    private String[] receiverHosts;
    private int[] receiverIds;
    private String scenario;

    private boolean receiver1Ack = false;
    private boolean receiver2Ack = false;

    private Object lock = new Object();

    // Stuff I need for Scenario 3, where two receivers are just recovering, none are annoying
    // If no replica finished, it means it's the first one.
    // If one did did, it means it's the second one now and I can set this back to false and tell them they can start another transfer.
    private AtomicBoolean finishedOneReplicaAlready = new AtomicBoolean(false);

    ConcurrentLinkedQueue<Integer> receivedAcks;
    private BufferedWriter writer;
    private BufferedWriter writer1;
    private BufferedWriter writer2;
    private long startTime;
    private long startTime2 = 0;
    private long endTime;

    // Stuff for ack tests
    private ConcurrentHashMap<Integer, Boolean> receiverAckChecks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Integer> receiverAckCounts = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Double> receiverAckTimeSums = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Long> receiverStartTimes = new ConcurrentHashMap<>();

    public Sender(int[] serverPorts, int[] receiverPorts, String[] receiverHosts, int[] receiverIds, String scenario) {
        this.serverPorts = serverPorts;
        this.receiverPorts = receiverPorts;
        this.receiverHosts = receiverHosts;
        this.receiverIds = receiverIds;
        this.scenario = scenario;

        this.receivedAcks = new ConcurrentLinkedQueue<>();
        this.receivers = new HashMap<>();


        try {
            this.writer = new BufferedWriter(new FileWriter("bw-behaviour-test_results/scenario_1_with_ingress.txt",
                    true)); // 'true' to append data

            this.writer1 = new BufferedWriter(new FileWriter("bw-behaviour-test_results/scenario_3_htb_static_allocation_receiver_1_flipped.txt",
                    true)); // 'true' to append data

            this.writer2 = new BufferedWriter(new FileWriter("bw-behaviour-test_results/scenario_3_htb_static_allocation_receiver_2_flipped.txt",
                    true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startSenderNode() {
        startServerThreads();
        connectToReceivers();

        byte[] data = generateData();
        if (scenario.isEmpty()) {
            startRegular();
        } else if (scenario.equals("ackTest")) {
            startAckTest();
        } else {
            startScenarios(data);
        }
    }

    private void startRegular() {

        for (int i = 0 ; i < REPETITIONS ; i++) {
            startTime = System.nanoTime();
            for (int j = 0 ; j < NUMBER_OF_CHUNKS ; j++) {
                byte[] data = generateData();
                sendData(data);
            }

            while (receivedAcks.size() != 1/*receiverHosts.length*/) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }

            receivedAcks.clear();
            System.gc();
            try {
                writer.newLine();
                writer.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    private void startAckTest() {

        for (Map.Entry<Integer, ClientTCP> receiver: receivers.entrySet()) {

            // TODO: Delete this if later you don't need it
            if (receiver.getKey() == 1) {
                new Thread(() -> {

                    while (true) {
                        int receiverId = receiver.getKey();
                        receiverAckChecks.put(receiverId, false);
                        receiverAckCounts.put(receiverId, 0);
//                        startTime = System.nanoTime();
                        byte[] data = generateData();

                        for (int j = 0; j < NUMBER_OF_CHUNKS; j++) {
                            receiverStartTimes.put(receiverId, System.nanoTime());
                            receiver.getValue().sendMessage(new Message((byte) 10, 0, data));

                            synchronized (receiverAckChecks.get(receiverId)) {
                                try {
                                    receiverAckChecks.get(receiverId).wait();
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                            }

//                            receiverAckChecks.put(receiverId, false);
                        }
//                }
                    }
                }).start();
            } else if (receiver.getKey() == 2) {
                new Thread(() -> {

                    while (true) {

                        try {
                            Thread.sleep(15000);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }

                        int receiverId = receiver.getKey();
                        receiverAckChecks.put(receiverId, false);
                        receiverAckCounts.put(receiverId, 0);
                        startTime = System.nanoTime();
                        byte[] data = generateData();

                        for (int j = 0; j < NUMBER_OF_CHUNKS; j++) {
                            receiverStartTimes.put(receiverId, System.nanoTime());
                            receiver.getValue().sendMessage(new Message((byte) 10, 0, data));

                            synchronized (receiverAckChecks.get(receiverId)) {
                                try {
                                    receiverAckChecks.get(receiverId).wait();
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                            }

                            receiverAckChecks.put(receiverId, false);
                        }
//                }
                    }
                }).start();
            }

        }
    }

    private void startScenarios(byte[] data) {

        if (scenario.equals("scenario3")) {

            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(10000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    startTime = System.nanoTime();
                    for (int j = 0 ; j < NUMBER_OF_CHUNKS ; j++) {
//                    byte[] data = generateData();
                        receivers.get(1).sendMessage(new Message((byte) 10, 0, data));
                    }

                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }

                    receiver1Ack = false;
                    System.gc();
                }
            }).start();

            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    startTime2 = System.nanoTime();
                    for (int j = 0 ; j < NUMBER_OF_CHUNKS ; j++) {
//                    byte[] data = generateData();
                        receivers.get(2).sendMessage(new Message((byte) 10, 0, data));
                    }

                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }

                    receiver2Ack = false;
                    System.gc();
                }
            }).start();

        } else {
            if (scenario.equals("scenario2")) {
                new Thread(() ->  {
                    while (true) {
                        for (int i = 0 ; i < REPETITIONS ; i++) {
                            for (int j = 0 ; j < NUMBER_OF_CHUNKS ; j++) {
//                           byte[] data = generateData();
                                receivers.get(1).sendMessage(new Message((byte) 10, 0, data));
                            }

                            while (!receiver1Ack) {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException ie) {
                                    ie.printStackTrace();
                                }
                            }
                            receiver1Ack = false;
                            System.gc();
                        }
                    }
                }).start();
            }

            new Thread(() -> {
                for (int i = 0 ; i < REPETITIONS ; i++) {
                    try {
                        Thread.sleep(SLEEP_TIME);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    startTime = System.nanoTime();
                    for (int j = 0 ; j < NUMBER_OF_CHUNKS ; j++) {
//                    byte[] data = generateData();
                        receivers.get(2).sendMessage(new Message((byte) 10, 0, data));
                    }

                    synchronized (lock) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }

                    receiver2Ack = false;
                    System.gc();
                    try {
                        writer.newLine();
                        writer.flush();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }).start();
        }

    }

    private void sendData(byte[] data) {

        Message message = new Message((byte) 10, 0, data);

        for (Map.Entry<Integer, ClientTCP> receiver: receivers.entrySet()) {
            receiver.getValue().sendMessage(message);
        }
    }

    @Override
    public void receiveMessage(Message message) {

        if (!scenario.isEmpty()) {

            if (scenario.equals("ackTest")) {
//                System.out.println("Entered ackTest if");
                int receiverId = message.getReplicaId();
                long endTime = System.nanoTime();
                receiverAckTimeSums.putIfAbsent(receiverId, 0.0);
                receiverAckTimeSums.put(receiverId, receiverAckTimeSums.get(receiverId) + ((endTime - receiverStartTimes.get(receiverId)) / 1000000000.0));
//                receiverAckChecks.put(receiverId, true);
                synchronized (receiverAckChecks.get(receiverId)) {
                    receiverAckChecks.get(receiverId).notify();
                }
                receiverAckCounts.putIfAbsent(receiverId, 0);
                receiverAckCounts.put(receiverId, receiverAckCounts.get(receiverId) + 1);

//                if (receiverId == 2) {
//                    try {
//                        writer.write("Ack from receiver " + receiverId + ": " + ((endTime - receiverStartTimes.get(receiverId)) / 1000000000.0));
//                        writer.newLine();
//                        writer.flush();
//                    } catch (IOException ioe) {
//                        ioe.printStackTrace();
//                    }
//                }

                if (receiverAckCounts.get(receiverId) == NUMBER_OF_CHUNKS) {
                    // Reset the ack count to prepare for next transfer
                    receiverAckCounts.put(receiverId, 0);
                    long endTimeAll = System.nanoTime();
                    if (receiverId == 2) {
                        try {
//                            writer.write("Total time of receiver " + receiverId + ": " + ((endTimeAll - startTime) / 1000000000.0));
                            writer.write("Total time of receiver " + receiverId + ": " + receiverAckTimeSums.get(receiverId));
                            writer.newLine();
                            writer.flush();
                            receiverAckTimeSums.put(receiverId, 0.0);
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
                    }

                }

            } else if (scenario.equals("scenario3")) {
                BufferedWriter writer = message.getReplicaId() == 1 ? writer1 : writer2;
                long startTimeToUse = message.getReplicaId() == 1 ? startTime : startTime2;
                endTime = System.nanoTime();
                try {
                    writer.write("" + ((endTime - startTimeToUse) / 1000000000.0));
                    writer.newLine();
                    writer.flush();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }

                if (!finishedOneReplicaAlready.get()) {
                    finishedOneReplicaAlready.set(true);
                } else {
                    finishedOneReplicaAlready.set(false);
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }

            } else {
                if (message.getReplicaId() == 2) {
                    endTime = System.nanoTime();
                    try {
                        writer.write("" + ((endTime - startTime) / 1000000000.0));
                        writer.newLine();
                        writer.flush();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    receiver2Ack = true;
                    synchronized (lock) {
                        lock.notify();
                    }
                } else {
                    receiver1Ack = true;
                }
            }

        } else {
//        System.out.println("Sender received an ack from receiver " + message.getReplicaId());
            endTime = System.nanoTime();
//        System.out.println("Receiver " + message.getReplicaId() + " got the data in " + ((endTime - startTime) / 1000000000.0) + " seconds");
            try {
                writer.write("" + ((endTime - startTime) / 1000000000.0));
                writer.newLine();
                writer.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            receivedAcks.add(message.getReplicaId());
        }
    }

    private void startServerThreads() {

        for (int i = 0 ; i < serverPorts.length ; i++) {
            ServerTCP controlNodeServer = new ServerTCP(serverPorts[i], this);
            new Thread(controlNodeServer).start();
        }
    }

    private void connectToReceivers() {

        for (int i = 0; i < receiverPorts.length; i++) {
            boolean connected = false;
            int retryCount = 0;
            while (!connected && retryCount < 5) {
                retryCount++;
                try {
                    Socket socket = new Socket(receiverHosts[i], receiverPorts[i]);
                    connected = true;
                    ClientTCP newReceiver = new ClientTCP(socket, socket.getOutputStream());
                    receivers.put(receiverIds[i], newReceiver);
                    new Thread(newReceiver).start();
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

    private byte[] generateData() {
        byte[] data = new byte[DATA_SIZE];
        Arrays.fill(data, (byte) 1);

        return data;
    }
}
