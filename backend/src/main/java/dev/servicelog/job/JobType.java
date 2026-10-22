package dev.servicelog.job;

public enum JobType {
    INSTALL("Install"),
    SERVICE_CALL("Service call"),
    TUNE_UP("Tune-up"),
    ESTIMATE_VISIT("Estimate visit");

    private final String label;

    JobType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
