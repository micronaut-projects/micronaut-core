package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestRetentionPolicyTest {

    @TempDir
    Path project;

    @Test
    void anUnnamedDataSourceIsNotRetained() {
        try (ApplicationContext context = ApplicationContext.run()) {
            context.registerSingleton(DataSource.class, dataSource());
            context.registerSingleton(RetainedPool.class, new RetainedPool());
            BeanRegistration<DataSource> dataSource = context.getBeanRegistration(DataSource.class, null);
            BeanRegistration<RetainedPool> pool = context.getBeanRegistration(RetainedPool.class, null);

            // nothing named: nothing is retained, a data source no more than anything else, whatever the module declares
            ManifestRetentionPolicy none = new ManifestRetentionPolicy(manifest(null), getClass().getClassLoader());
            assertFalse(none.retain(dataSource));
            assertFalse(none.retain(pool));

            // a named type is retained, and no configuration prefix of its own releases it
            ManifestRetentionPolicy named = new ManifestRetentionPolicy(manifest(DataSource.class.getName()), getClass().getClassLoader());
            assertTrue(named.retain(dataSource));
            assertFalse(named.retain(pool));
            assertEquals(Set.of(), named.observedConfigurationPrefixes(dataSource));
        }
    }

    private DevManifest manifest(String retain) {
        return DevManifest.of(project, properties(retain));
    }

    private static Properties properties(String retain) {
        Properties properties = new Properties();
        properties.setProperty("micronaut.dev.main-class", "app.Application");
        properties.setProperty("micronaut.dev.reloadable", "build/classes");
        if (retain != null) {
            properties.setProperty("micronaut.dev.retain", retain);
        }
        return properties;
    }

    private static DataSource dataSource() {
        return (DataSource) Proxy.newProxyInstance(ManifestRetentionPolicyTest.class.getClassLoader(), new Class<?>[] {DataSource.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "DataSource";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
