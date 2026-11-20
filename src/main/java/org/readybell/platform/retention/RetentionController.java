package org.readybell.platform.retention;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/** Runs the retention tasks now instead of at night (after a restore, or to see a dry run): platform operator only. */
@RestController
@RequestMapping("/api/platform/retention")
public class RetentionController {

    private final RetentionService retention;
    private final Clock clock;

    public RetentionController(RetentionService retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    /** @return rows changed per task (rows that would change when {@code readybell.retention.dry-run} is on) */
    @PostMapping("/run")
    public Map<String, Integer> run() {
        return retention.run(clock.instant(), null);
    }
}
