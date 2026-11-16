package org.readybell.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Makes it impossible to miss that the development profile (known passwords, schema recreated on start) is running. */
@Component
@Profile("dev")
class DevProfileBanner {

    private static final Logger log = LoggerFactory.getLogger(DevProfileBanner.class);

    DevProfileBanner() {
        log.warn("""

                ============================================================================
                 DEV PROFILE ACTIVE: well-known sign-in passwords (staff/password, admin/admin,
                 platform/platform), the database schema is recreated on every start and
                 push keys may be placeholders. Never expose this instance to a network you
                 do not control, and never point it at real data.
                ============================================================================""");
    }
}
