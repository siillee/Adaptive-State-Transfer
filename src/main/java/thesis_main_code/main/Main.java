package thesis_main_code.main;

import thesis_main_code.nodes.ControlNode;
import thesis_main_code.nodes.ReplicaNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * This is the class used to run the whole program.
 * Based on the arguments passed to the main method of this class, we start the appropriate node.
 */
public class Main {

    private static final String CONFIG_FILENAME = "configs/config_localhost";
    // Number of replicas in the system. Simultaneously used to know how many servers each replica should run,
    // since they also receive messages from the Control node.
    private static final int NUMBER_OF_REPLICAS = 4;

    public static void main(String[] args) {

        // TODO: Change the message printed here, and the condition if any arguments change throughout the thesis
        if (args.length != 1 && args.length != 4) {
            System.out.println("Wrong number of arguments! Arguments needed are: " +
                    "type of node [, replica_id, state_transfer_protocol, isByzantine (0 for no or 1 for yes)]");
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


        if (nodeName.equals("control")) {
            try {
                for (int i = 0 ; i < NUMBER_OF_REPLICAS ; i++) {
                    br.readLine();
                }
                String[] replicaInfo = br.readLine().split(",");
                String[] replicaHosts = new String[replicaInfo.length];
                int[] replicaPorts = new int[replicaInfo.length];
                for (int i = 0 ; i < replicaInfo.length ; i++) {
                    String[] tokens = replicaInfo[i].split(":");
                    replicaHosts[i] = tokens[0].trim();
                    replicaPorts[i] = Integer.parseInt(tokens[1].trim());
                }

                // Start the control node
                ControlNode controlNode = new ControlNode(replicaHosts, replicaPorts);
                controlNode.startControlNode();
            } catch (Exception e) {
                System.out.println("Error while reading a line of the config file for the Control Node");
                e.printStackTrace();
            }

        } else if (nodeName.equals("replica")) {
            try {
                int replicaId = Integer.parseInt(args[1].trim());
                String stateTransferProtocol = args[2];
                // Make sure to read the correct row. If this is replica 3, skip the first two rows and read the third
                for (int i = 1 ; i < replicaId ; i++) {
                    br.readLine();
                }

                String[] replicaInfo = br.readLine().split(",");
                // One server for each other replica, plus the control node
                int[] replicaServerPorts = new int[NUMBER_OF_REPLICAS];
                int[] replicaClientPorts = new int[NUMBER_OF_REPLICAS - 1];
                // Ids of other replicas, in the same order as their ports
                int[] replicaIds = new int[NUMBER_OF_REPLICAS - 1];
                String[] replicaHosts = new String[NUMBER_OF_REPLICAS - 1];

                for (int i = 0 ; i < replicaInfo.length ; i++) {
                    if (i < replicaServerPorts.length) {
                        replicaServerPorts[i] = Integer.parseInt(replicaInfo[i].trim());
                    } else {
                        String[] tokens = replicaInfo[i].split(":");
                        replicaIds[i - replicaServerPorts.length] = Integer.parseInt(tokens[0].trim());
                        replicaHosts[i - replicaServerPorts.length] = tokens[1].trim();
                        replicaClientPorts[i - replicaServerPorts.length] = Integer.parseInt(tokens[2].trim());
                    }
                }

                // Start the replica node
                ReplicaNode replicaNode = new ReplicaNode(replicaServerPorts,
                        replicaClientPorts,
                        replicaHosts,
                        replicaIds,
                        replicaId,
                        stateTransferProtocol,
                        Integer.parseInt(args[3].trim()) == 1);
                replicaNode.startReplicaNode();
            } catch (Exception e) {
                System.out.println("Error while reading a line of the config file for a Replica Node");
                e.printStackTrace();
            }

        } else {
            System.out.println("Wrong name of node entered as the first argument. The options are: control, replica");
        }
    }
}
