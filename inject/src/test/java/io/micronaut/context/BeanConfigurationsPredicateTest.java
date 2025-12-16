package io.micronaut.context;

import io.micronaut.inject.BeanConfiguration;
import io.micronaut.context.exceptions.NoSuchBeanException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanConfigurationsPredicateTest {

    @Test
    void configurationDisablesBeansWhenPredicateIsNotSet() {
        try (ApplicationContext context = ApplicationContext.builder()
            .beanDefinitions(
                RuntimeBeanDefinition.builder(test.enabled.MyEnabledBean.class, test.enabled.MyEnabledBean::new).singleton(true).build(),
                RuntimeBeanDefinition.builder(test.disabled.MyDisabledBean.class, test.disabled.MyDisabledBean::new).singleton(true).build()
            )
            .beanConfigurations(
                // Disable everything under package test.disabled
                BeanConfiguration.disabled("test.disabled")
            )
            .start()) {

            // Enabled package bean is present
            Assertions.assertTrue(context.containsBean(test.enabled.MyEnabledBean.class));
            Assertions.assertNotNull(context.getBean(test.enabled.MyEnabledBean.class));

            // Disabled package bean should not be available
            Assertions.assertFalse(context.containsBean(test.disabled.MyDisabledBean.class));
            assertThrows(NoSuchBeanException.class, () -> context.getBean(test.disabled.MyDisabledBean.class));
        }
    }

    @Test
    void configurationIsIgnoredWhenFilteredByBeanConfigurationsPredicate() {
        try (ApplicationContext context = ApplicationContext.builder()
            // Filter out the configuration so it is not considered by the bean definition service
            .beanConfigurationsPredicate(cfg -> !"test.disabled".equals(cfg.getName()))
            .beanDefinitions(
                RuntimeBeanDefinition.builder(test.enabled.MyEnabledBean.class, test.enabled.MyEnabledBean::new).singleton(true).build(),
                RuntimeBeanDefinition.builder(test.disabled.MyDisabledBean.class, test.disabled.MyDisabledBean::new).singleton(true).build()
            )
            .beanConfigurations(
                // This configuration would normally disable test.disabled, but the predicate filters it out
                BeanConfiguration.disabled("test.disabled")
            )
            .start()) {

            // Both beans should be available because the disabling configuration was filtered out
            Assertions.assertTrue(context.containsBean(test.enabled.MyEnabledBean.class));
            Assertions.assertNotNull(context.getBean(test.enabled.MyEnabledBean.class));

            Assertions.assertTrue(context.containsBean(test.disabled.MyDisabledBean.class));
            Assertions.assertNotNull(context.getBean(test.disabled.MyDisabledBean.class));
        }
    }
}
