package org.readybell.platform.persistence;

import org.junit.jupiter.api.Test;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.domain.jpa.CrossTenant;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant isolation is structural: the tenant filter is bound by {@code TenantFilterInterceptor}, so a persistence
 * adapter method is safe only if the interceptor can tell which tenant it works for. Adding a method that takes no
 * {@link TenantId} and is not marked {@link CrossTenant} fails here.
 */
class TenantGuardArchitectureTest {

    private static List<Class<?>> persistenceAdapters() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        var classes = new ArrayList<Class<?>>();
        for (BeanDefinition bd : scanner.findCandidateComponents("org.readybell")) {
            if (bd.getBeanClassName().contains(".adapter.out.persistence.")) {
                classes.add(Class.forName(bd.getBeanClassName()));
            }
        }
        return classes;
    }

    @Test
    void foundTheAdapters() throws Exception {
        assertThat(persistenceAdapters()).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void everyAdapterMethodIsTenantScopedOrExplicitlyCrossTenant() throws Exception {
        var unprotected = new ArrayList<String>();
        for (Class<?> adapter : persistenceAdapters()) {
            for (Method m : adapter.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) continue;
                boolean takesTenant = Arrays.stream(m.getParameterTypes()).anyMatch(TenantId.class::isAssignableFrom);
                CrossTenant cross = m.getAnnotation(CrossTenant.class);
                if (!takesTenant && cross == null) {
                    unprotected.add(adapter.getSimpleName() + "." + m.getName());
                }
                if (cross != null) {
                    assertThat(cross.value()).as("reason on %s.%s", adapter.getSimpleName(), m.getName()).isNotBlank();
                    assertThat(takesTenant).as("%s.%s is @CrossTenant but takes a TenantId",
                            adapter.getSimpleName(), m.getName()).isFalse();
                }
            }
        }
        assertThat(unprotected)
                .as("add a TenantId parameter, or @CrossTenant(\"reason\") if the method must see every tenant")
                .isEmpty();
    }

    @Test
    void adaptersDoNotEnableTheFilterByHand() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            var offenders = files
                    .filter(p -> p.toString().replace('\\', '/').contains("/adapter/out/persistence/"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("enableFilter(");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList();
            assertThat(offenders).isEmpty();
        }
    }
}
