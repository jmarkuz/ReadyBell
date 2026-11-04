package org.readybell.platform.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Wires {@link TenantFilterInterceptor} around every {@code @Repository} in an {@code adapter.out.persistence}
 * package. The transaction advisor is ordered outside of it, so the filter is bound to the transaction's session.
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true, order = Ordered.LOWEST_PRECEDENCE - 100)
public class TenantFilterConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DefaultPointcutAdvisor tenantFilterAdvisor(EntityManagerFactory entityManagerFactory) {
        var advisor = new DefaultPointcutAdvisor(persistenceAdapterMethods(),
                new TenantFilterInterceptor(entityManagerFactory));
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        return advisor;
    }

    static Pointcut persistenceAdapterMethods() {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return Modifier.isPublic(method.getModifiers())
                        && method.getDeclaringClass() != Object.class
                        && targetClass.isAnnotationPresent(Repository.class)
                        && targetClass.getPackageName().contains(".adapter.out.persistence");
            }
        };
    }
}
