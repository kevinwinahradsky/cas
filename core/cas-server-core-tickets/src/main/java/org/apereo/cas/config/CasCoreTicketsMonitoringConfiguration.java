package org.apereo.cas.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.features.CasFeatureModule;
import org.apereo.cas.monitor.ExecutableObserver;
import org.apereo.cas.monitor.MonitorableTask;
import org.apereo.cas.util.function.FunctionUtils;
import org.apereo.cas.util.spring.boot.ConditionalOnFeatureEnabled;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;

/**
 * This is {@link CasCoreTicketsMonitoringConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 7.0.0
 */
@EnableConfigurationProperties(CasConfigurationProperties.class)
@ConditionalOnFeatureEnabled(feature = {
    CasFeatureModule.FeatureCatalog.Monitoring,
    CasFeatureModule.FeatureCatalog.TicketRegistry
})
@ConditionalOnBean(name = ExecutableObserver.BEAN_NAME)
@Configuration(proxyBeanMethods = false)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@Lazy(false)
@ConditionalOnEnabledTracing
class CasCoreTicketsMonitoringConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ticketRegistryMonitoringAspect")
    public TicketRegistryMonitoringAspect ticketRegistryMonitoringAspect(final ObjectProvider<ExecutableObserver> observer) {
        return new TicketRegistryMonitoringAspect(observer);
    }

    @Aspect
    @Slf4j
    @SuppressWarnings("UnusedMethod")
    record TicketRegistryMonitoringAspect(ObjectProvider<ExecutableObserver> observerProvider) {
        @Around("allComponentsInTicketRegistryNamespace()")
        public Object aroundTicketRegistryOperations(final ProceedingJoinPoint joinPoint) throws Throwable {
            val observer = observerProvider.getObject();
            val taskName = joinPoint.getSignature().getDeclaringTypeName() + '.' + joinPoint.getSignature().getName();
            val task = new MonitorableTask(taskName);
            return observer.supply(task, () -> executeJoinpoint(joinPoint));
        }

        private static Object executeJoinpoint(final ProceedingJoinPoint joinPoint) {
            return FunctionUtils.doUnchecked(() -> {
                var args = joinPoint.getArgs();
                LOGGER.trace("Executing [{}]", joinPoint.getStaticPart().toLongString());
                return joinPoint.proceed(args);
            });
        }

        @Pointcut("within(org.apereo.cas.ticket.registry.*)")
        private void allComponentsInTicketRegistryNamespace() {
        }
    }
}
