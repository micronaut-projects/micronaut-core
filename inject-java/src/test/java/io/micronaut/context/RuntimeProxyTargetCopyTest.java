package io.micronaut.context;

import io.micronaut.aop.runtimetarget.CachingTarget;
import io.micronaut.aop.runtimetarget.InjectingTarget;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The copy a runtime proxy retains stays with the context creating it only for a definition that takes it back.
 */
class RuntimeProxyTargetCopyTest {

    @Test
    void theCopyIsLeftForTheCreationOfAProxyDefinitionToTakeBack() {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<InjectingTarget> proxy = context.getBeanDefinition(InjectingTarget.class);
            assertInstanceOf(ProxyBeanDefinition.class, proxy);
            BeanDefinition<InjectingTarget> target = context.getProxyTargetBeanDefinition(Argument.of(InjectingTarget.class), null);
            try (DefaultBeanResolutionContext creating = new DefaultBeanResolutionContext(context, proxy)) {
                BeanResolutionContext retained = creating.copyForRuntimeProxyTarget(proxy, target);

                assertSame(retained, creating.takeLazyProxyTargetCopy(target.getClass()));
            }
        }
    }

    @Test
    void noCopyIsLeftForAnotherDefinitionWhichNeverTakesItBack() {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<CachingTarget.Dependency> notAProxy = context.getBeanDefinition(CachingTarget.Dependency.class);
            BeanDefinition<InjectingTarget> target = context.getProxyTargetBeanDefinition(Argument.of(InjectingTarget.class), null);
            try (DefaultBeanResolutionContext creating = new DefaultBeanResolutionContext(context, notAProxy)) {
                assertNotNull(creating.copyForRuntimeProxyTarget(notAProxy, target));

                assertNull(creating.takeLazyProxyTargetCopy(target.getClass()));
            }
        }
    }
}
