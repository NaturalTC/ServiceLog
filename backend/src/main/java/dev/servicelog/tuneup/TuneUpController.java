package dev.servicelog.tuneup;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Login required (see SecurityConfig)
@RestController
public class TuneUpController {

    private final TuneUpService tuneUps;

    public TuneUpController(TuneUpService tuneUps) {
        this.tuneUps = tuneUps;
    }

    // Equipment due for a seasonal tune-up today, not-yet-booked and longest overdue first
    @GetMapping("/api/tune-ups/due")
    public TuneUpDtos.DueResponse due() {
        return tuneUps.due();
    }
}
