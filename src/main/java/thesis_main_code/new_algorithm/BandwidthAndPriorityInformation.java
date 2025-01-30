package thesis_main_code.new_algorithm;

import java.util.LinkedList;

/**
 * This class represents some information regarding the receiving replicas in the system.
 * This information is vital in configuring the traffic control (tc) settings and
 * will be used to keep track of which replicas deserve priority over others.
 */
public class BandwidthAndPriorityInformation {

    private int replicaId;
    private double minGuaranteedRate;
    private double ceilingRate;
    private double totalBandwidthConsumed;
    private int priority;

    // List of state transfers that happened, used to implement a sliding time window
    // and keep track of only those that happened in that window.
    private LinkedList<TimeBandwidthPair> transferLog;
    private boolean noisy;
    private boolean currentlyActive;
    private boolean wasOnlyActive;

    // Messaging details
    private boolean ackCheck;
    private int ackCount;
    private long startTime;

    private long ackStartTime;
    private long ackTimeSum;

    public BandwidthAndPriorityInformation(int replicaId, double minGuaranteedRate, double ceilingRate, double totalBandwidthConsumed, int priority) {
        this.replicaId = replicaId;
        this.minGuaranteedRate = minGuaranteedRate;
        this.ceilingRate = ceilingRate;
        this.totalBandwidthConsumed = totalBandwidthConsumed;
        this.priority = priority;

        this.transferLog = new LinkedList<>();
        this.noisy = false;
        this.currentlyActive = false;
        this.wasOnlyActive = true;

        this.ackCheck = false;
        this.ackCount = 0;
        this.startTime = 0;

        this.ackStartTime = 0;
        this.ackTimeSum = 0;
    }

    public int getReplicaId() {
        return replicaId;
    }

    public void setReplicaId(int replicaId) {
        this.replicaId = replicaId;
    }

    public double getMinGuaranteedRate() {
        return minGuaranteedRate;
    }

    public void setMinGuaranteedRate(double minGuaranteedRate) {
        this.minGuaranteedRate = minGuaranteedRate;
    }

    public double getCeilingRate() {
        return ceilingRate;
    }

    public void setCeilingRate(double ceilingRate) {
        this.ceilingRate = ceilingRate;
    }

    public double getTotalBandwidthConsumed() {
        return totalBandwidthConsumed;
    }

    public void setTotalBandwidthConsumed(double totalBandwidthConsumed) {
        this.totalBandwidthConsumed = totalBandwidthConsumed;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public LinkedList<TimeBandwidthPair> getTransferLog() {
        return transferLog;
    }

    public void setTransferLog(LinkedList<TimeBandwidthPair> transferLog) {
        this.transferLog = transferLog;
    }

    public boolean isNoisy() {
        return noisy;
    }

    public void setNoisy(boolean noisy) {
        this.noisy = noisy;
    }

    public boolean isCurrentlyActive() {
        return currentlyActive;
    }

    public void setCurrentlyActive(boolean currentlyActive) {
        this.currentlyActive = currentlyActive;
    }

    public boolean isWasOnlyActive() {
        return wasOnlyActive;
    }

    public void setWasOnlyActive(boolean wasOnlyActive) {
        this.wasOnlyActive = wasOnlyActive;
    }

    public boolean isAckCheck() {
        return ackCheck;
    }

    public void setAckCheck(boolean ackCheck) {
        this.ackCheck = ackCheck;
    }

    public int getAckCount() {
        return ackCount;
    }

    public void setAckCount(int ackCount) {
        this.ackCount = ackCount;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getAckStartTime() {
        return ackStartTime;
    }

    public void setAckStartTime(long ackStartTime) {
        this.ackStartTime = ackStartTime;
    }

    public long getAckTimeSum() {
        return ackTimeSum;
    }

    public void setAckTimeSum(long ackTimeSum) {
        this.ackTimeSum = ackTimeSum;
    }

    public void increaseAckTimeSum(long increase) {
        this.ackTimeSum += increase;
    }

    public void increaseTotalBandwidthConsumed(double increase) {
        this.totalBandwidthConsumed += increase;
    }

    public void decreaseTotalBandwidthConsumed(double decrease) {
        this.totalBandwidthConsumed -= decrease;
    }

    public void increaseCeilingRate(double increase) {
        this.ceilingRate += increase;
    }

    public void increaseMinGuaranteedRate(double increase) {
        this.minGuaranteedRate += increase;
    }

    public void divideMinGuaranteedRate(int coef) {
        this.minGuaranteedRate /= coef;
    }


    @Override
    public String toString() {
        return "BandwidthAndPriorityInformation{" +
                "replicaId=" + replicaId +
                ", minGuaranteedRate=" + minGuaranteedRate +
                ", ceilingRate=" + ceilingRate +
                ", totalBandwidthConsumed=" + totalBandwidthConsumed +
                ", priority=" + priority +
                ", ackCheck=" + ackCheck +
                ", ackCount=" + ackCount +
                ", startTime=" + startTime +
                ", ackTimeSum=" + ackTimeSum +
                "} \n";
    }
}
