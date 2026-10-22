package dev.servicelog.servicerecord;

// What kind of visit this was. Tune-up reminders (S10) look for the last TUNE_UP.
public enum ServiceKind {
    TUNE_UP("Tune-up"),
    REPAIR("Repair"),
    INSTALLATION("Installation"),
    INSPECTION("Inspection"),
    OTHER("Other");

    private final String label;

    ServiceKind(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
