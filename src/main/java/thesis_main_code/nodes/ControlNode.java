package thesis_main_code.nodes;

import thesis_main_code.network.ControlNodeTCP;

import java.io.OutputStream;
import java.net.Socket;

/**
 * This class represents a control node. This node is considered to be always correct and running, and it
 * is responsible for organizing the flow of replicas, such as imitating consensus (i.e. just changing the epoch).
 */
public class ControlNode {

    private String[] replicaHosts;
    private int[] replicaPorts;

    public ControlNode(String[] replicaHosts, int[] replicaPorts) {
        this.replicaHosts = replicaHosts;
        this.replicaPorts = replicaPorts;
    }

    public void startControlNode() {

        // Create and start the thread which will send messages to replicas
        Socket[] replicaSockets = new Socket[replicaPorts.length];
        OutputStream[] replicaOutputStreams = new OutputStream[replicaPorts.length];
        try {
            for (int i = 0 ; i < replicaPorts.length ; i++) {
                replicaSockets[i] = new Socket(replicaHosts[i], replicaPorts[i]);
                replicaOutputStreams[i] = replicaSockets[i].getOutputStream();
                System.out.println("Control Node connected to replica with port " + replicaPorts[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ControlNodeTCP tcpThread = new ControlNodeTCP(replicaSockets, replicaOutputStreams);
        new Thread(tcpThread).start();
    }
}
