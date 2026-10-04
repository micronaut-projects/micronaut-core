package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.dev.staleness.PackagedBean;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationStalenessTest {

    private static final String SPEC = "ConfigurationStalenessTest";

    private static final String[][] READ_KEYS = {
        // property, whatever it compares the value with
        {"staleness.present", "PresentBean"},
        {"staleness.present.nested", "PresentBean"},
        {"staleness.value", "ValueBean"},
        {"staleness.not-equals", "NotEqualsBean"},
        {"staleness.pattern", "PatternBean"},
        {"staleness.default", "DefaultValueBean"},
        // missingProperty, the key or one under it
        {"staleness.missing", "MissingBean"},
        {"staleness.missing.child", "MissingBean"},
        // a second @Requires of the repeated ones
        {"staleness.repeated", "RepeatedBean"},
        // inherited from a stereotype
        {"staleness.stereotype", "StereotypedBean"},
        // the factory's own requirement
        {"staleness.factory", "StalenessFactory"},
        // a package-level requirement of a bean configuration
        {"staleness.package", "io.micronaut.dev.staleness"},
        // bean with beanProperty, beans and missingBeans of a type bound to the configuration, directly or as an @EachBean
        {"staleness.toggle.enabled", "BeanPropertyBean"},
        {"staleness.entries.one.url", "BeansBean"},
        {"staleness.others.two", "MissingBeansBean"},
    };

    @Test
    void aChangedKeyARequirementReadsRestarts() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            for (String[] row : READ_KEYS) {
                String key = row[0];
                String component = row[1];
                String stale = ConfigurationStaleness.afterRefresh(context, ConfigurationChange.ofKeys(Set.of(key)),
                    ConfigurationStaleness.beforeRefresh(context));
                assertNotNull(stale, key);
                assertTrue(stale.contains(component), stale);
            }
        }
    }

    @Test
    void aKeyNoRequirementReadsRefreshesInPlace() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            assertTrue(context.containsBean(MissingBean.class));
            assertTrue(context.containsBean(PackagedBean.class));
            // env cannot flip with an edit, and a key that only shares a prefix's first letters is not under it
            assertNull(ConfigurationStaleness.afterRefresh(context, ConfigurationChange.ofKeys(Set.of("staleness.unrelated", "staleness.missingx")),
                ConfigurationStaleness.beforeRefresh(context)));
        }
    }

    @Test
    void aBeanTheRunningContextFoundDisabledStillCounts() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            // asked for while running, the disabled reference is dropped from the context's own references
            assertFalse(context.containsBean(PresentBean.class));
            assertTrue(context.getBeanDefinitionReferences().stream().noneMatch(r -> r.getBeanType().equals(PresentBean.class)));
            String stale = ConfigurationStaleness.afterRefresh(context, ConfigurationChange.ofKeys(Set.of("staleness.present")),
                ConfigurationStaleness.beforeRefresh(context));
            assertNotNull(stale);
            assertTrue(stale.contains("PresentBean"), stale);
        }
    }

    @Test
    void aCustomConditionIsEvaluatedAgainAndRestartsOnlyWhenItFlips() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            assertFalse(context.containsBean(FlaggedBean.class));
            ConfigurationChange change = ConfigurationChange.ofKeys(Set.of("staleness.flag"));
            // the key changed but the condition answers the same
            assertNull(ConfigurationStaleness.afterRefresh(context, change, ConfigurationStaleness.beforeRefresh(context)));

            ConfigurationStaleness.Requirements before = ConfigurationStaleness.beforeRefresh(context);
            context.getEnvironment().addPropertySource(PropertySource.of("flag", Map.of("staleness.flag", true)));
            String stale = ConfigurationStaleness.afterRefresh(context, change, before);
            assertNotNull(stale);
            assertTrue(stale.contains("FlagCondition"), stale);
        }
    }

    @Test
    void withoutARunningContextNothingIsStale() {
        assertNull(ConfigurationStaleness.afterRefresh(null, ConfigurationChange.ofKeys(Set.of("staleness.missing")),
            ConfigurationStaleness.beforeRefresh(null)));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "staleness.present")
    static class PresentBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "staleness.value", value = "on")
    static class ValueBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "staleness.not-equals", notEquals = "off")
    static class NotEqualsBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "staleness.pattern", pattern = "a.*")
    static class PatternBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "staleness.default", value = "x", defaultValue = "x")
    static class DefaultValueBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(missingProperty = "staleness.missing")
    static class MissingBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(env = "staleness-env")
    @Requires(missingProperty = "staleness.repeated")
    static class RepeatedBean {
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Requires(missingProperty = "staleness.stereotype")
    @interface StalenessGated {
    }

    @Singleton
    @StalenessGated
    @Requires(property = "spec.name", value = SPEC)
    static class StereotypedBean {
    }

    static class Produced {
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    @Requires(missingProperty = "staleness.factory")
    static class StalenessFactory {
        @Bean
        Produced produced() {
            return new Produced();
        }
    }

    public static final class FlagCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            return context.getProperty("staleness.flag", Boolean.class).orElse(false);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(condition = FlagCondition.class)
    static class FlaggedBean {
    }

    @ConfigurationProperties("staleness.toggle")
    @Requires(property = "spec.name", value = SPEC)
    static class ToggleConfig {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(bean = ToggleConfig.class, beanProperty = "enabled", value = "true")
    static class BeanPropertyBean {
    }

    @EachProperty("staleness.entries")
    @Requires(property = "spec.name", value = SPEC)
    static class Entry {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(beans = Entry.class)
    static class BeansBean {
    }

    @EachProperty("staleness.others")
    @Requires(property = "spec.name", value = SPEC)
    static class Other {
    }

    @EachBean(Other.class)
    @Requires(property = "spec.name", value = SPEC)
    static class OtherClient {
        OtherClient(Other other) {
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(missingBeans = OtherClient.class)
    static class MissingBeansBean {
    }
}
