package dev.servicelog.job;

import dev.servicelog.servicerecord.ServiceKind;

public enum JobType {
    INSTALL("Install", ServiceKind.INSTALLATION),
    SERVICE_CALL("Service call", ServiceKind.REPAIR),
    TUNE_UP("Tune-up", ServiceKind.TUNE_UP),
    ESTIMATE_VISIT("Estimate visit", ServiceKind.INSPECTION);

    private final String label;
    // The kind of service record a finished job of this type creates
    private final ServiceKind serviceKind;

    JobType(String label, ServiceKind serviceKind) {
        this.label = label;
        this.serviceKind = serviceKind;
    }

    public String getLabel() { return label; }
    public ServiceKind getServiceKind() { return serviceKind; }
}
