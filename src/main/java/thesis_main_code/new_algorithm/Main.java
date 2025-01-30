package thesis_main_code.new_algorithm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Main {

    private static final String CONFIG_FILENAME = "configs/config_bw-testing_aws";
    private static final int NUMBER_OF_NODES = 2;

    public static void main(String[] args) {

        // TODO: Change the message printed here, and the condition if any arguments change throughout the thesis
        if (args.length != 1 && args.length != 2 && args.length != 3) {
            System.out.println("Wrong number of arguments! Arguments needed are: " +
                    "type of node [, receiver_id] [scenario1 or scenario2]");
            System.exit(1);
        }

        String nodeName = args[0];

        // Read the relevant network data from the config file
        BufferedReader br = null;
        try {
            File file = new File(CONFIG_FILENAME);
            br = new BufferedReader(new FileReader(file));
        } catch (IOException ioe) {
            System.out.println("Error while creating a reader for the config file");
            ioe.printStackTrace();
            System.exit(2);
        }

        int numberOfSkips = nodeName.equals("sender")
                ? NUMBER_OF_NODES
                : Integer.parseInt(args[1].trim()) - 1;

        try {
            for (int i = 0 ; i < numberOfSkips ; i++) {
                br.readLine();
            }

            int arraySize = nodeName.equals("sender") ? NUMBER_OF_NODES : 1;
            String[] nodeInfo = br.readLine().split(",");
            int[] nodeServerPorts = new int[arraySize];
            int[] nodeClientPorts = new int[arraySize];
            int[] nodeIds = new int[arraySize];
            String[] nodeHosts = new String[arraySize];

            for (int i = 0 ; i < nodeInfo.length ; i++) {
                if (i < nodeServerPorts.length) {
                    nodeServerPorts[i] = Integer.parseInt(nodeInfo[i].trim());
                } else {
                    String[] tokens = nodeInfo[i].split(":");
                    if (tokens.length == 3) {
                        nodeIds[i - nodeServerPorts.length] = Integer.parseInt(tokens[0].trim());
                        nodeHosts[i - nodeServerPorts.length] = tokens[1].trim();
                        nodeClientPorts[i - nodeServerPorts.length] = Integer.parseInt(tokens[2].trim());
                    } else {
                        nodeHosts[i - nodeServerPorts.length] = tokens[0].trim();
                        nodeClientPorts[i - nodeServerPorts.length] = Integer.parseInt(tokens[1].trim());
                    }
                }
            }

            if (nodeName.equals("sender")) {
                Sender senderNode = new Sender(nodeServerPorts, nodeClientPorts, nodeHosts, nodeIds, args.length == 1 ? "" : args[1]);
                senderNode.startSenderNode();
            } else {
                Receiver receiverNode = new Receiver(nodeServerPorts[0], nodeHosts[0], nodeClientPorts[0],
                        Integer.parseInt(args[1]), args.length == 2 ? "" : args[2]);
                receiverNode.startReceiverNode();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
