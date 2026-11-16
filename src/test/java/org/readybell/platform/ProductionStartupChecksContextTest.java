package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** The checks are wired to the right profiles: a real deployment refuses unsafe settings, a developer's machine does not. */
class ProductionStartupChecksContextTest {

    @Configuration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class Properties {
    }

    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Properties.class, ProductionStartupChecks.class, DevProfileBanner.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile));
    }

    @Test
    void aProductionStyleProfileWithPlaceholderKeysFailsToStart() {
        runner("prod").withPropertyValues(
                        "readybell.vapid.public-key=placeholder-public-key", "readybell.vapid.private-key=placeholder-private-key",
                        "readybell.security.jwt.secret=a-long-random-secret-0123456789-xyz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("VAPID keys are not set");
                });
    }

    @Test
    void aProductionStyleProfileWithRealSettingsStarts() {
        runner("prod").withPropertyValues(
                        "readybell.vapid.public-key=BNem4e8WUGXqteeTJNwrrS5D4fIytCUELHDh7fD8zGNy5qgC0tlw7ABC",
                        "readybell.vapid.private-key=BNem4e8WUGXqteeTJNwrrS5D4fIytCUELHDh7fD8zGNy5qgC0tlw7ABC",
                        "readybell.security.jwt.secret=a-long-random-secret-0123456789-xyz")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ProductionStartupChecks.class));
    }

    @Test
    void theDevProfileKeepsPlaceholdersAndShowsTheBannerInstead() {
        runner("dev").withPropertyValues("readybell.vapid.public-key=placeholder-public-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProductionStartupChecks.class);
                    assertThat(context).hasSingleBean(DevProfileBanner.class);
                });
    }

    @Test
    void theTestProfileIsExempt() {
        runner("test").run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ProductionStartupChecks.class));
    }
}
