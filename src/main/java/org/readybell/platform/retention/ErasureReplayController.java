package org.readybell.platform.retention;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operator action after restoring a backup: repeats the erasures recorded in {@code erasure_log}, so that what was
 * forgotten stays forgotten. Under {@code /api/platform/**}: platform operator only.
 */
@RestController
@RequestMapping("/api/platform/erasures")
public class ErasureReplayController {
    private final CustomerDataService data;

    public ErasureReplayController(CustomerDataService data) {
        this.data = data;
    }

    @PostMapping("/replay")
    public Map<String, Integer> replay() {
        return Map.of("sessionsErasedAgain", data.replayErasures());
    }
}
