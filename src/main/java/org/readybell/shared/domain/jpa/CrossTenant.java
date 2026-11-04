package org.readybell.shared.domain.jpa;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a persistence-adapter method that deliberately runs without the tenant filter (lookups that happen
 * before the tenant is known, platform-admin listings, writes of entities that carry their own tenant).
 *
 * <p>Every other adapter method must take a {@link org.readybell.shared.domain.TenantId} argument or run
 * inside a resolved tenant context; the tenant filter is then bound automatically by
 * {@code TenantFilterInterceptor}. A method with neither fails the architecture test.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CrossTenant {

    /** Why this method may see or write rows of any tenant. */
    String value();
}
