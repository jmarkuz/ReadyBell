package org.readybell.notification.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Web Push configuration for browsers: the VAPID <em>public</em> key a client needs as
 * {@code applicationServerKey} when subscribing. {@code enabled} is false while the placeholder
 * keys are in place, so the tracking page can hide the notification opt-in instead of failing.
 */
@RestController
@RequestMapping("/api/push")
public class PushConfigController {

    private final String publicKey;
    private final boolean enabled;

    public PushConfigController(@Value("${readybell.vapid.public-key}") String publicKey,
                                @Value("${readybell.vapid.enabled:true}") boolean vapidEnabled) {
        this.publicKey = publicKey;
        this.enabled = vapidEnabled && publicKey != null && !publicKey.isBlank()
                && !publicKey.startsWith("placeholder-");
    }

    @GetMapping("/config")
    public PushConfig config() {
        return new PushConfig(enabled, enabled ? publicKey : null);
    }

    public record PushConfig(boolean enabled, String publicKey) {}
}
