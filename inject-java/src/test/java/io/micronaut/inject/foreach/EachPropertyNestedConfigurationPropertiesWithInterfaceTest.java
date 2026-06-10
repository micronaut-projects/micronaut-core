package io.micronaut.inject.foreach;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EachPropertyNestedConfigurationPropertiesWithInterfaceTest {
    @Test
    void youCanNestEachPropertyWithConfigurationPropertiesInInterface() {
        final Map<String, Object> config = Map.of(
            "spec.name", "EachPropertyNestedConfigurationPropertiesWithInterfaceTest",
            "mtms.datasource.named.foo.timeout", "2s",
            "mtms.datasource.named.foo.drcp.enabled", StringUtils.TRUE,
            "mtms.datasource.named.bar.timeout", "1s",
            "mtms.datasource.named.bar.drcp.enabled", StringUtils.FALSE);
        try (ApplicationContext ctx = ApplicationContext.run(config)) {
            NamedPoolConfiguration foo = assertDoesNotThrow(() ->
                ctx.getBean(NamedPoolConfiguration.class, Qualifiers.byName("foo"))
            );
            assertNotNull(foo);
            assertEquals(Duration.ofSeconds(2), foo.getTimeout());
            assertNotNull(foo.getDrcpConfiguration());
            assertTrue(foo.getDrcpConfiguration().isEnabled());

            NamedPoolConfiguration bar = assertDoesNotThrow(() ->
                ctx.getBean(NamedPoolConfiguration.class, Qualifiers.byName("bar"))
            );
            assertNotNull(bar);
            assertEquals(Duration.ofSeconds(1), bar.getTimeout());
            assertNotNull(bar.getDrcpConfiguration());
            assertFalse(bar.getDrcpConfiguration().isEnabled());
        }
    }

    @Requires(property = "spec.name", value = "EachPropertyNestedConfigurationPropertiesWithInterfaceTest")
    @EachProperty("mtms.datasource.named")
    public interface NamedPoolConfiguration {
        Duration getTimeout();

        @Nullable
        NamedDrcpConfiguration getDrcpConfiguration();

        @ConfigurationProperties("drcp")
        interface NamedDrcpConfiguration extends Toggleable {
            @Override
            boolean isEnabled();
        }
    }
}
