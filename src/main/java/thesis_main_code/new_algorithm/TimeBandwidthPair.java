package thesis_main_code.new_algorithm;

public class TimeBandwidthPair {

    private long timestamp;
    private double bandwidth;

    public TimeBandwidthPair(long timestamp, double bandwidth) {
        this.timestamp = timestamp;
        this.bandwidth = bandwidth;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }
}
