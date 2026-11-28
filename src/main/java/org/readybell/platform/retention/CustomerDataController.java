package org.readybell.platform.retention;

import org.readybell.ordering.adapter.in.web.SessionUnknownException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Public, like the tracking page it belongs to: the unguessable session id in the link is the credential. Rate-limited per
 * session in {@code readybell.rate-limit.rules}.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/data")
public class CustomerDataController {
    private final CustomerDataService data;

    public CustomerDataController(CustomerDataService data) {
        this.data = data;
    }

    /** The customer's data as a downloadable JSON document. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> export(@PathVariable UUID sessionId) {
        Map<String, Object> doc = data.export(sessionId);
        if (doc == null) {
            throw new SessionUnknownException(sessionId);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"my-readybell-data.json\"")
                .body(doc);
    }

    /** Deletes the customer's push subscriptions and personal data in their orders. */
    @DeleteMapping
    public ResponseEntity<CustomerDataService.Erased> erase(@PathVariable UUID sessionId) {
        CustomerDataService.Erased erased = data.erase(sessionId);
        if (erased == null) {
            throw new SessionUnknownException(sessionId);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(erased);
    }
}
