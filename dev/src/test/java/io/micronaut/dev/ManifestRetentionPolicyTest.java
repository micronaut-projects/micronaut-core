package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.context.reload.PolicyRetentionCriteria;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
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
            assertEquals(BeanRetentionPolicy.Decision.ABSTAIN, none.decide(dataSource));
            assertEquals(BeanRetentionPolicy.Decision.ABSTAIN, none.decide(pool));

            // a named type is retained, and no configuration prefix of its own releases it
            ManifestRetentionPolicy named = new ManifestRetentionPolicy(manifest(DataSource.class.getName()), getClass().getClassLoader());
            assertEquals(BeanRetentionPolicy.Decision.RETAIN, named.decide(dataSource));
            assertEquals(BeanRetentionPolicy.Decision.ABSTAIN, named.decide(pool));
            assertEquals(Set.of(), named.observedConfigurationPrefixes(dataSource));
        }
    }

    @Test
    void aPolicyRefusesWhatTheManifestNames() {
        ManifestRetentionPolicy named = new ManifestRetentionPolicy(manifest(DataSource.class.getName()), getClass().getClassLoader());
        BeanRetentionPolicy veto = registration -> DataSource.class.isAssignableFrom(registration.getBeanType())
            ? BeanRetentionPolicy.Decision.REFUSE : BeanRetentionPolicy.Decision.ABSTAIN;

        // named alone, the data source is retained
        DataSource kept = dataSource();
        try (ApplicationContext context = ApplicationContext.run()) {
            context.registerSingleton(DataSource.class, kept);
            Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) context).stopRetaining(
                new PolicyRetentionCriteria(List.of(named), prefix -> false, type -> false));
            assertTrue(retained.stream().anyMatch(registration -> registration.getBean() == kept));
        }

        // a policy that refuses it wins over the manifest
        DataSource refused = dataSource();
        try (ApplicationContext context = ApplicationContext.run()) {
            context.registerSingleton(DataSource.class, refused);
            Collection<BeanRegistration<?>> retained = ((DefaultBeanContext) context).stopRetaining(
                new PolicyRetentionCriteria(List.of(named, veto), prefix -> false, type -> false));
            assertFalse(retained.stream().anyMatch(registration -> registration.getBean() == refused));
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
