/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.util.ArrayUtils;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.integrator.spi.Integrator;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Configuration for JPA and Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(value = JpaConfiguration.PREFIX, primary = "default")
public class JpaConfiguration {
    public static final String PREFIX = "jpa";

    private final BootstrapServiceRegistry bootstrapServiceRegistry;

    private Map<String, Object> jpaProperties;
    private String[] packagesToScan;

    /**
     * @param applicationContext The application context
     * @param integrator         The {@link Integrator}
     */
    protected JpaConfiguration(ApplicationContext applicationContext,
                               @Nullable Integrator integrator) {
        ClassLoader classLoader = applicationContext.getClassLoader();
        BootstrapServiceRegistryBuilder bootstrapServiceRegistryBuilder =
            createBootstrapServiceRegistryBuilder(integrator, classLoader);

        this.bootstrapServiceRegistry = bootstrapServiceRegistryBuilder.build();
    }

    /**
     * Builds the standard service registry.
     *
     * @param additionalSettings Additional settings for the service registry
     * @return The standard service registry
     */
    @SuppressWarnings("WeakerAccess")
    public StandardServiceRegistry buildStandardServiceRegistry(@Nullable Map<String, Object> additionalSettings) {
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = createStandServiceRegistryBuilder(bootstrapServiceRegistry);

        if (jpaProperties != null) {
            standardServiceRegistryBuilder.applySettings(jpaProperties);
        }
        if (additionalSettings != null) {
            standardServiceRegistryBuilder.applySettings(additionalSettings);
        }
        return standardServiceRegistryBuilder.build();
    }

    /**
     * Sets the packages to scan.
     *
     * @param packagesToScan The packages to scan
     */
    public void setPackagesToScan(String... packagesToScan) {
        if (ArrayUtils.isNotEmpty(packagesToScan)) {
            this.packagesToScan = packagesToScan;
        }
    }

    /**
     * @return The packages to scan
     */
    public String[] getPackagesToScan() {
        return packagesToScan;
    }

    /**
     * Sets the JPA properties to be passed to the JPA implementation.
     *
     * @param jpaProperties The JPA properties
     */
    public final void setProperties(
        @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
            Map<String, Object> jpaProperties) {
        this.jpaProperties = jpaProperties;
    }

    /**
     * Creates the default {@link BootstrapServiceRegistryBuilder}.
     *
     * @param integrator  The integrator to use. Can be null
     * @param classLoader The class loade rto use
     * @return The BootstrapServiceRegistryBuilder
     */
    @SuppressWarnings("WeakerAccess")
    protected BootstrapServiceRegistryBuilder createBootstrapServiceRegistryBuilder(
        @Nullable Integrator integrator,
        ClassLoader classLoader) {
        BootstrapServiceRegistryBuilder bootstrapServiceRegistryBuilder = new BootstrapServiceRegistryBuilder();
        bootstrapServiceRegistryBuilder.applyClassLoader(classLoader);
        if (integrator != null) {
            bootstrapServiceRegistryBuilder.applyIntegrator(integrator);
        }
        return bootstrapServiceRegistryBuilder;
    }

    /**
     * Creates the standard service registry builder.
     *
     * @param bootstrapServiceRegistry The {@link BootstrapServiceRegistry} instance
     * @return The {@link StandardServiceRegistryBuilder} instance
     */
    @SuppressWarnings("WeakerAccess")
    protected StandardServiceRegistryBuilder createStandServiceRegistryBuilder(BootstrapServiceRegistry bootstrapServiceRegistry) {
        return new StandardServiceRegistryBuilder(
            bootstrapServiceRegistry
        );
    }
}
