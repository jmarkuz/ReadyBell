package org.readybell.platform.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.domain.jpa.CrossTenant;
import org.readybell.shared.tenancy.MissingTenantContextException;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;

import java.lang.reflect.Method;

/**
 * Binds the Hibernate {@code tenantFilter} for every persistence-adapter call, so no repository method has to
 * remember to do it (Req 10.2, 10.4).
 *
 * <ol>
 *   <li>{@link CrossTenant} method: the filter is switched off (a filter left over on the session must not
 *       leak into it).</li>
 *   <li>A {@link TenantId} argument: the filter is enabled for that tenant.</li>
 *   <li>Otherwise the tenant of the current request context; without one the call fails with
 *       {@link MissingTenantContextException}.</li>
 * </ol>
 *
 * Runs inside the repository's transaction, so the filter lives on the session the query will use.
 */
public class TenantFilterInterceptor implements MethodInterceptor {

    static final String FILTER = "tenantFilter";

    private final EntityManagerFactory entityManagerFactory;

    public TenantFilterInterceptor(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        if (em == null) {
            throw new IllegalStateException("Persistence adapter method " + invocation.getMethod().getName()
                    + " must run inside a transaction so the tenant filter binds to its session");
        }
        Session session = em.unwrap(Session.class);

        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(),
                AopUtils.getTargetClass(invocation.getThis()));
        if (AnnotatedElementUtils.hasAnnotation(method, CrossTenant.class)) {
            if (session.getEnabledFilter(FILTER) != null) {
                session.disableFilter(FILTER);
            }
            return invocation.proceed();
        }

        TenantId tenant = tenantArgument(invocation.getArguments());
        if (tenant == null) {
            tenant = TenantContextHolder.get().orElseThrow(() -> new MissingTenantContextException(
                    "tenant context missing for " + method.getDeclaringClass().getSimpleName() + "."
                            + method.getName() + "; pass a TenantId or mark the method @CrossTenant"));
        }
        session.enableFilter(FILTER).setParameter("tenantId", tenant.value());
        return invocation.proceed();
    }

    private static TenantId tenantArgument(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof TenantId id) {
                return id;
            }
        }
        return null;
    }
}
