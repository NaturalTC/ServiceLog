package dev.servicelog.job;

import java.util.List;

// The new time clashes with other scheduled jobs; the owner can confirm to book anyway
public class JobOverlapException extends RuntimeException {

    private final List<JobDtos.JobResponse> conflicts;

    public JobOverlapException(List<JobDtos.JobResponse> conflicts) {
        super("This overlaps with " + conflicts.size() + " other job" + (conflicts.size() == 1 ? "" : "s") + ".");
        this.conflicts = conflicts;
    }

    public List<JobDtos.JobResponse> getConflicts() {
        return conflicts;
    }
}
