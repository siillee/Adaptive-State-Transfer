package thesis_main_code.new_algorithm;

import thesis_main_code.network.ClientTCP;
import thesis_main_code.network.Message;
import thesis_main_code.network.MessageReceiver;
import thesis_main_code.network.ServerTCP;
import thesis_main_code.utils.CommandExecutor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Sender implements MessageReceiver {

    private static final int MAX_INTERFACE_BANDWIDTH_IN_MBIT = 1000;
    // This scale calculated as (1024 * 1024 * 8) / 1000000.
    // This calculation considers using memory definitions of values such as MegaBytes for actual data,
    // and network definitions of values such as MegaBits for the transfer through the network.
    private static final double MB_TO_MBIT_SCALE = 8.388608;
    public static final int NUMBER_OF_CHUNKS = 20;
    private static final int CHUNK_SIZE_IN_MB = 100;
    private static final int DATA_SIZE = 1024 * 1024 * CHUNK_SIZE_IN_MB;

    // Size of the sliding window, currently at 2 minutes
    private static final int SLIDING_WINDOW_SIZE_IN_MILIS = 2 * 60 * 1000;
    private static final double NOISY_BANDWIDTH_THRESHOLD_IN_MBIT = 2500.0;
    private static final int NOISY_REQUEST_THRESHOLD = 4;

    // TODO: Change this to lo or enp39s0 depending on where you run, either locally or on aws
    public static final String INTERFACE_NAME = "enp39s0";

    private Map<Integer, ClientTCP> receivers;

    private int[] serverPorts;
    private int[] receiverPorts;
    private String[] receiverHosts;
    private int[] receiverIds;

    private BufferedWriter writer;
    private BufferedWriter writer1;
    private BufferedWriter writer2;

    private String scenario;

    // Just to know when to end the run of the program. I want to have 60 lines in result file
    private int requestCounter = 0;

    // New algorithm fields I need
    ConcurrentHashMap<Integer, BandwidthAndPriorityInformation> receiverBwInfo;
    List<BandwidthAndPriorityInformation> priorityList;

    // Stuff I need for Scenario 3, where two receivers are just recovering, none are annoying
    // If no replica finished, it means it's the first one.
    // If one did, it means it's the second one now and I can set this back to false and tell them they can start another transfer.
    private AtomicBoolean finishedOneReplicaAlready = new AtomicBoolean(false);

    private volatile double ceilingRatesSum;

    public Sender(int[] serverPorts, int[] receiverPorts, String[] receiverHosts, int[] receiverIds, String scenario) {

        this.serverPorts = serverPorts;
        this.receiverPorts = receiverPorts;
        this.receiverHosts = receiverHosts;
        this.receiverIds = receiverIds;

        this.receivers = new HashMap<>();

        this.priorityList = new ArrayList<>();
        this.receiverBwInfo = new ConcurrentHashMap<>();
        for (int id: receiverIds) {
            int minRate = 500;
            int ceilRate = 1000;
            if (!scenario.isEmpty()) {
                if (id == 2) {
                    minRate = 412;
                    ceilRate = 700;
                } else {
                    minRate = 588;
                    ceilRate = 1000;
                }
            }
            BandwidthAndPriorityInformation newReceiver = new BandwidthAndPriorityInformation(id, minRate, ceilRate, 0, 1);
            receiverBwInfo.put(id, newReceiver);
            priorityList.add(newReceiver);
        }

        System.out.println(receiverBwInfo);

        // Each connection can get the interface max at the beginning
        for (Map.Entry<Integer, BandwidthAndPriorityInformation> receiver : receiverBwInfo.entrySet()) {
            this.ceilingRatesSum += receiver.getValue().getCeilingRate();
        }

        this.scenario = scenario;

        // Scenario 3 case where it is better to have two writers for separate files
        try {
            this.writer1 = new BufferedWriter(new FileWriter("new_algo_test_results/scenario_3_new_algo_receiver_1_with_ingress_again.txt",
                    true)); // 'true' to append data

            this.writer2 = new BufferedWriter(new FileWriter("new_algo_test_results/scenario_3_new_algo_receiver_2_with_ingress_again.txt",
                    true)); // 'true' to append data

            this.writer = new BufferedWriter(new FileWriter("new_algo_test_results/scenario_2_new_algo_new_version_with_sliding_window_both_limits_fixed.txt",
                    true)); // 'true' to append data
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startSenderNode() {
        startServerThreads();
        connectToReceivers();
    }

    @Override
    public void receiveMessage(Message message) {

        byte messageType = message.getType();

        switch (messageType) {
            case 11: {
                // Case the message is an ack
                long ackEndTime = System.nanoTime();

                final int receiverId = message.getReplicaId();
                BandwidthAndPriorityInformation receiverInfo = receiverBwInfo.get(receiverId);
                receiverInfo.increaseAckTimeSum(ackEndTime - receiverInfo.getAckStartTime());
                synchronized (receiverInfo) {
                    receiverInfo.notify();
                }
                receiverInfo.setAckCount(receiverInfo.getAckCount() + 1);

                // If all chunks were delivered, it means the transfer is done and we do the necessary calculations
                if (receiverInfo.getAckCount() == NUMBER_OF_CHUNKS) {
                    double totalTransferSeconds = receiverInfo.getAckTimeSum() / 1000000000.0;
                    receiverInfo.setAckTimeSum(0);
                    double bandwidthApprox = (receiverInfo.getAckCount() / totalTransferSeconds) * CHUNK_SIZE_IN_MB * MB_TO_MBIT_SCALE;

//                    if (receiverInfo.getReplicaId() == 2) {
                    BufferedWriter writer = this.scenario.equals("scenario3")
                        ? receiverInfo.getReplicaId() == 1 ? writer1 : writer2
                        : this.writer;
                        try {
                            writer.write("Receiver " + receiverId + ": " + totalTransferSeconds);
//                            writer.write("Receiver " + receiverId + ": " + totalTransferSeconds + " seconds AND " + bandwidthApprox + " Mbit/s bandwidth");
                            writer.newLine();
                            writer.flush();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }
//                    }

                    // Initial testing purposes. TODO: Delete or comment these prints later
//                    System.out.println("Seconds it took for the transfer to receiver " + receiverId + ": " + totalTransferSeconds);
//                    System.out.println("Calculated bandwidth of the receiver " + receiverId + ": " + bandwidthApprox);


                    if (bandwidthApprox < receiverInfo.getCeilingRate() * 0.9) {
                        // Initial testing purposes. TODO: Delete or comment these prints later
//                        System.out.println("Entered the if, which means the bandwidthApprox was less that 90% of the current ceil rate");

                        synchronized (this) {

                            // Updating transfer log
                            receiverInfo.getTransferLog().add(new TimeBandwidthPair(System.currentTimeMillis(), bandwidthApprox));
                            receiverInfo.increaseTotalBandwidthConsumed(bandwidthApprox);
                            TimeBandwidthPair curr = receiverInfo.getTransferLog().peek();
                            long currentTime = System.currentTimeMillis();
                            while (currentTime - curr.getTimestamp() >= SLIDING_WINDOW_SIZE_IN_MILIS) {
                                receiverInfo.getTransferLog().remove();
                                receiverInfo.decreaseTotalBandwidthConsumed(curr.getBandwidth());
                                curr = receiverInfo.getTransferLog().peek();
                            }

                            // If the replica is deemed noisy, it gets 100Mbit as max, if not, then the bandwidthApprox
                            if (receiverInfo.getTransferLog().size() >= NOISY_REQUEST_THRESHOLD &&
                                    receiverInfo.getTotalBandwidthConsumed() >= NOISY_BANDWIDTH_THRESHOLD_IN_MBIT) {
//                            if (receiverInfo.getTotalBandwidthConsumed() >= NOISY_BANDWIDTH_THRESHOLD_IN_MBIT) {
                                double ceilDiff = receiverInfo.getCeilingRate() - 100;
                                ceilingRatesSum -= ceilDiff;
                                receiverInfo.setCeilingRate(100.0);

//                                receiverInfo.setNoisy(true);
                            } else {
                                if (receiverInfo.isWasOnlyActive()) {
                                    double ceilDiff = receiverInfo.getCeilingRate() - bandwidthApprox;
                                    ceilingRatesSum -= ceilDiff;
                                    receiverInfo.setCeilingRate(bandwidthApprox);
                                }
                            }

//                            System.out.println("Current ceil rate: " + receiverInfo.getCeilingRate());
                            // Trigger recalculation of all rates and update of the tc configuration
                            priorityChanges();
                            rateRecalculation();
                        }
                    } else {
                        // Case where the bandwidth of the connection is as high as expected of the ceil rate,
                        // but maybe the connection can be even faster, so we try and increase the ceil rate.
                        // Similar to TCP behaviour.

                        // Initial testing purposes. TODO: Delete or comment these prints later
//                        System.out.println("The rate was as expected, maybe it could be better so we increase if we can");
//                        System.out.println("We can increase, so we do by 20% of the max interface rate, or up to max interface rate");

                        synchronized (this) {
//                            receiverInfo.increaseTotalBandwidthConsumed(bandwidthApprox);

                            receiverInfo.getTransferLog().add(new TimeBandwidthPair(System.currentTimeMillis(), bandwidthApprox));
                            receiverInfo.increaseTotalBandwidthConsumed(bandwidthApprox);
                            TimeBandwidthPair curr = receiverInfo.getTransferLog().peek();
                            long currentTime = System.currentTimeMillis();
                            while (currentTime - curr.getTimestamp() >= SLIDING_WINDOW_SIZE_IN_MILIS) {
                                receiverInfo.getTransferLog().remove();
                                receiverInfo.decreaseTotalBandwidthConsumed(curr.getBandwidth());
                                curr = receiverInfo.getTransferLog().peek();
                            }

                            // If the replica is deemed noisy, it gets 100Mbit as max, if not, then the increase
                            if (receiverInfo.getTransferLog().size() >= NOISY_REQUEST_THRESHOLD &&
                                    receiverInfo.getTotalBandwidthConsumed() >= NOISY_BANDWIDTH_THRESHOLD_IN_MBIT) {
//                            if (receiverInfo.getTotalBandwidthConsumed() >= NOISY_BANDWIDTH_THRESHOLD_IN_MBIT) {
                                double ceilDiff = receiverInfo.getCeilingRate() - 100;
                                ceilingRatesSum -= ceilDiff;
                                receiverInfo.setCeilingRate(100.0);
                            } else {
//                                if (receiverInfo.isNoisy()) {
//                                    receiverInfo.setNoisy(false);
//                                    receiverInfo.setCeilingRate(500.0);
//                                } else {
                                // Increase by 20% of the max interface bw limit, or up to interface max
                                double ceilDiff = receiverInfo.getCeilingRate() < MAX_INTERFACE_BANDWIDTH_IN_MBIT * 0.8
                                        ? MAX_INTERFACE_BANDWIDTH_IN_MBIT * 0.2
                                        : MAX_INTERFACE_BANDWIDTH_IN_MBIT - receiverInfo.getCeilingRate();

                                ceilingRatesSum += ceilDiff;
                                receiverInfo.increaseCeilingRate(ceilDiff);
//                                }
                            }

                            // Trigger recalculation of all rates and update of the tc configuration
                            priorityChanges();
                            rateRecalculation();

                            receiverInfo.setWasOnlyActive(true);
                            receiverInfo.setCurrentlyActive(false);
                        }
                    }



                    // Reset the ack count to prepare for next transfer
                    receiverInfo.setAckCount(0);


                    // Scneario 3 stuff
                    if (scenario.equals("scenario3")) {
                        if (!finishedOneReplicaAlready.get()) {
                            finishedOneReplicaAlready.set(true);
                        } else {
                            finishedOneReplicaAlready.set(false);
                            for (Map.Entry<Integer, ClientTCP> receiver: receivers.entrySet()) {
                                receiver.getValue().sendMessage(new Message((byte) 20, 0, new byte[0]));
                            }
                        }
                    }
                }

                break;
            }
            case 12: {
                // Case the message is a request
                final int receiverId = message.getReplicaId();

                // TODO: Delete or comment this
//                if (receiverId == 2) {
//                    requestCounter++;
//                    System.out.println("This is request number : " + requestCounter);
//                }

                if (isAnyReplicaActive()) {
                    receiverBwInfo.get(receiverId).setWasOnlyActive(false);
                }
                receiverBwInfo.get(receiverId).setCurrentlyActive(true);

                new Thread(() -> {
                    receiverBwInfo.get(receiverId).setAckCheck(false);
                    receiverBwInfo.get(receiverId).setAckCount(0);
                    receiverBwInfo.get(receiverId).setStartTime(System.nanoTime());
                    byte[] data = generateData();

                    for (int j = 0; j < NUMBER_OF_CHUNKS; j++) {
                        // TODO: this is a stupid solution, but for now it works.
                        // The problem is how the replicas are connecting, I have to wait a bit to make sure everything connects nicely
                        // Another fix is to start receivers first, and then the sender
                        while (!receivers.containsKey(receiverId)) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }
                        }

                        receiverBwInfo.get(receiverId).setAckStartTime(System.nanoTime());
                        receivers.get(receiverId).sendMessage(new Message((byte) 10, 0, data));

                        synchronized (receiverBwInfo.get(receiverId)) {
                            try {
                                receiverBwInfo.get(receiverId).wait();
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }
                        }
                        receiverBwInfo.get(receiverId).setAckCheck(false);
                    }
                }).start();

                break;
            }
            default:
                break;
        }
    }

    /**
     * Recalculation of min. guaranteed rates.
     * This is called whenever some connection needs changing, either by becoming slower or faster.
     */
    private void rateRecalculation() {

        double extraBw = 0.0;
        // Count how many connections are fully served. For the case where there is still extraBw,
        // but all connections are fully server, i.e. for every receiver, min. guar. rate cannot be higher,
        // because it's already equal to ceil rate, and to speed up the whole thing
        Set<Integer> fullyServedConnections = new HashSet<>();

        // Recalculate min rates based on ceil rates and which proportion they take of the total sum of ceil rates
        for (Map.Entry<Integer, BandwidthAndPriorityInformation> element: receiverBwInfo.entrySet()) {
            BandwidthAndPriorityInformation elementInfo = element.getValue();
            double proportion = elementInfo.getCeilingRate() / ceilingRatesSum;
            elementInfo.setMinGuaranteedRate(ceilingRatesSum < MAX_INTERFACE_BANDWIDTH_IN_MBIT
                    ? ceilingRatesSum * proportion
                    : MAX_INTERFACE_BANDWIDTH_IN_MBIT * proportion);

            //  Recalculate min rate based on the current priority.
            // I separated this in case I need to change something, this way it will be easier.
            extraBw += elementInfo.getMinGuaranteedRate() / elementInfo.getPriority();
            elementInfo.divideMinGuaranteedRate(elementInfo.getPriority());
        }


        // Proportional recalculation of min rates. Second version of algo
//        while (extraBw > 1E-10 && fullyServedConnections.size() < receiverBwInfo.size()) {
//            double extraBwDistributed = 0.0;
//
//            for (Map.Entry<Integer, BandwidthAndPriorityInformation> receiver: receiverBwInfo.entrySet()) {
//                BandwidthAndPriorityInformation receiverInfo = receiver.getValue();
//                if (!fullyServedConnections.contains(receiverInfo.getReplicaId())) {
//                    double proportion = receiverInfo.getMinGuaranteedRate() / MAX_INTERFACE_BANDWIDTH_IN_MBIT;
//                    double extraBwForReceiver = proportion * extraBw;
//                    double bandwidthDiff = receiverInfo.getCeilingRate() - receiverInfo.getMinGuaranteedRate();
//                    if (extraBwForReceiver <= bandwidthDiff) {
//                        receiverInfo.increaseMinGuaranteedRate(extraBwForReceiver);
//                        extraBwDistributed += extraBwForReceiver;
//                    } else if (extraBwForReceiver > bandwidthDiff && bandwidthDiff > 0) {
//                        receiverInfo.increaseMinGuaranteedRate(bandwidthDiff);
//                        extraBwDistributed += bandwidthDiff;
//                        fullyServedConnections.add(receiverInfo.getReplicaId());
//                    } else if (bandwidthDiff == 0) {
//                        fullyServedConnections.add(receiverInfo.getReplicaId());
//                    } else {
//                        System.out.println("Some mistake happened, there is no other case to be dealt with!!!!");
//                    }
//                }
//            }
//
//            extraBw -= extraBwDistributed;
//        }


        // Recalculation of min rates based on the current priorities. Original version of algo

        while (extraBw > 1E-10 && fullyServedConnections.size() < receiverBwInfo.size()) {

            System.out.println("Extra bw before spending: " + extraBw);

            double extraBwPerReceiver = extraBw / (receiverBwInfo.size() - fullyServedConnections.size());

            for (Map.Entry<Integer, BandwidthAndPriorityInformation> element: receiverBwInfo.entrySet()) {
                BandwidthAndPriorityInformation elementInfo = element.getValue();
                if (!fullyServedConnections.contains(elementInfo.getReplicaId())) {
                    double bandwidthDiff = elementInfo.getCeilingRate() - elementInfo.getMinGuaranteedRate();
                    if (extraBwPerReceiver <= bandwidthDiff) {
                        elementInfo.increaseMinGuaranteedRate(extraBwPerReceiver);
                        extraBw -= extraBwPerReceiver;
                    } else if (extraBwPerReceiver > bandwidthDiff && bandwidthDiff > 0) {
                        elementInfo.increaseMinGuaranteedRate(bandwidthDiff);
                        extraBw -= bandwidthDiff;
                        fullyServedConnections.add(elementInfo.getReplicaId());
                    } else if (bandwidthDiff <= 1E-3) {
                        fullyServedConnections.add(elementInfo.getReplicaId());
                    } else {
                        System.out.println("Some mistake happened, there is no other case to be dealt with!!!!");
                    }
                }
            }
        }

        // Call tc terminal commands which implement the change
        StringBuilder command = new StringBuilder();
        for (Map.Entry<Integer, BandwidthAndPriorityInformation> element: receiverBwInfo.entrySet()) {
            BandwidthAndPriorityInformation elementInfo = element.getValue();
            command.append("tc class change dev " + INTERFACE_NAME + " parent 1:1 classid 1:" + element.getKey() + "0 htb rate " + elementInfo.getMinGuaranteedRate() +
                    "mbit ceil " + elementInfo.getCeilingRate() + "mbit ; ");

            // Used in the Kernel-based allocation of bandwidth. Third version of algo.
//            command.append("tc class change dev " + INTERFACE_NAME + " parent 1:1 classid 1:" + element.getKey() + "0 htb rate " + elementInfo.getMinGuaranteedRate() +
//                    "mbit ceil " + elementInfo.getCeilingRate() + "mbit prio " + elementInfo.getPriority() + " ; ");
        }
        try {
            CommandExecutor.executeCommand(command.toString(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Changing of priorities based on how much bandwidth each connection consumed.
     * This priority is later used in the rate recalculations
     */
    private void priorityChanges() {

        // Sort by bandwidth consumption
        priorityList.sort(Comparator.comparingDouble(BandwidthAndPriorityInformation::getTotalBandwidthConsumed));

        // Change priorities based on the new order. These priorities are used for the rate recalculation
        int currPriority = 0;
        double currBwConsumption = -1;
        for (BandwidthAndPriorityInformation element: priorityList) {
            if (element.getTotalBandwidthConsumed() == currBwConsumption) {
                element.setPriority(currPriority);
            } else {
                element.setPriority(++currPriority);
                currBwConsumption = element.getTotalBandwidthConsumed();
            }
        }
    }

    private boolean isAnyReplicaActive() {

        for (Map.Entry<Integer, BandwidthAndPriorityInformation> element: receiverBwInfo.entrySet()) {
            if (element.getValue().isCurrentlyActive()) {
                return true;
            }
        }

        return false;
    }

    private void markReplicasAsNotAlone() {

        for (Map.Entry<Integer, BandwidthAndPriorityInformation> element: receiverBwInfo.entrySet()) {
            element.getValue().setWasOnlyActive(false);
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
