/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.env;

import io.micronaut.context.LifeCycle;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathAnnotationScanner;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>The current application environment. The environment represents the loaded configuration of the application
 * for a current list of active environment names.</p>
 * <p>
 * <p>The active environment names can be obtained from the {@link #getActiveNames()} method and are established from one
 * of the following sources:
 * </p>
 * <ul>
 *     <li>Environment names passed to the {@link io.micronaut.context.ApplicationContext#run(String...)} method</li>
 *     <li>The value of the {@link #ENVIRONMENTS_PROPERTY} system property</li>
 *     <li>The value of the {@link #ENVIRONMENTS_ENV} environment variable</li>
 *     <li>The class that started the application</li>
 * </ul>
 *
 * <p>When establishing the environment name from the class the started the application Micronaut will inspect the stacktrace. If JUnit or Spock are
 * featured in the stacktrace the {@link #TEST} environment is included. When running from Android the {@link #ANDROID} environment is included.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Environment extends PropertyResolver, LifeCycle<Environment>, ConversionService<Environment>, ResourceLoader {

    /**
     * Constant for the the name micronaut.
     */
    String MICRONAUT = "micronaut";

    /**
     * The test environment.
     */
    String TEST = "test";

    /**
     * The development environment.
     */
    String DEVELOPMENT = "dev";

    /**
     * The android environment.
     */
    String ANDROID = "android";

    /**
     * The cli environment.
     */
    String CLI = "cli";

    /**
     * The cloud environment.
     */
    String CLOUD = "cloud";

    /**
     * The application is executing as a function.
     */
    String FUNCTION = "function";

    /**
     * The default bootstrap name.
     */
    String BOOTSTRAP_NAME_PROPERTY = "micronaut.bootstrap.name";
    /**
     * Whether the bootstrap context is enabled.
     */
    String BOOTSTRAP_CONTEXT_PROPERTY = "micronaut.bootstrap.context";
    /**
     * The default bootstrap name.
     */
    String CLOUD_PLATFORM_PROPERTY = "micronaut.cloud.platform";

    /**
     * The property that stores additional environments.
     */
    String ENVIRONMENTS_PROPERTY = "micronaut.environments";

    /**
     * The environment key that stores additional environments.
     */
    String ENVIRONMENTS_ENV = "MICRONAUT_ENVIRONMENTS";

    /**
     * The default bootstrap config name.
     */
    String BOOTSTRAP_NAME = "bootstrap";

    /**
     * The default application name.
     */
    String DEFAULT_NAME = "application";

    /**
     * Cloud provider google compute instance.
     */
    String GOOGLE_COMPUTE = "gcp";

    /**
     * Cloud provider for google app engine.
     */
    String GAE = "gae";

    /**
     * Cloud provider amazon ec2.
     */
    String AMAZON_EC2 = "ec2";

    /**
     * Cloud provider Microsoft Azure.
     */
    String AZURE = "azure";

    /**
     * Cloud provider Oracle Cloud.
     */
    String ORACLE_CLOUD = "oraclecloud";

    /**
     * Cloud provider Digital Ocean.
     */

    String DIGITAL_OCEAN = "digitalocean";
    /**
     * Cloud or non cloud provider on bare metal (unknown).
     */
    String BARE_METAL = "baremetal";

    /**
     * Cloud provider IBM cloud.
     */
    String IBM = "ibm";

    /**
     * Running on Kubernetes.
     */
    String KUBERNETES = "k8s";

    /**
     * Running on Cloud Foundry.
     */
    String CLOUD_FOUNDRY = "pcf";

    /**
     * Running on Heroku.
     */
    String HEROKU = "heroku";

    /**
     * The key used to load additional property sources.
     */
    String PROPERTY_SOURCES_KEY = "micronaut.config.files";

    /**
     * The host name environment variable.
     */
    String HOSTNAME = "HOSTNAME";

    /**
     * Property for whether to deduce environments.
     */
    String DEDUCE_ENVIRONMENT_PROPERTY = "micronaut.env.deduction";

    /**
     * Environment key for whether to deduce environments.
     */
    String DEDUCE_ENVIRONMENT_ENV = "MICRONAUT_ENV_DEDUCTION";

    /**
     * Should respect the order as provided.
     *
     * @return The active environment names
     */
    Set<String> getActiveNames();

    /**
     * @return The active property sources
     */
    Collection<PropertySource> getPropertySources();

    /**
     * Adds a property source to this environment.
     *
     * @param propertySource The property source
     * @return This environment
     */
    Environment addPropertySource(PropertySource propertySource);

    /**
     * Removes a property source from this environment.
     * @param propertySource The property source
     * @return This environment
     */
    Environment removePropertySource(PropertySource propertySource);

    /**
     * Add an application package. Application packages are candidates for scanning for tools that need it (such as JPA
     * or GORM).
     *
     * @param pkg The package to add
     * @return This environment
     */
    Environment addPackage(String pkg);

    /**
     * Exclude configurations by name.
     *
     * @param names The names of the configuration
     * @return This environment
     */
    Environment addConfigurationExcludes(String... names);

    /**
     * Exclude configurations by name.
     *
     * @param names The names of the configuration
     * @return This environment
     */
    Environment addConfigurationIncludes(String... names);

    /**
     * @return The application packages
     */
    Collection<String> getPackages();

    /**
     * @return The placeholder resolver
     */
    PropertyPlaceholderResolver getPlaceholderResolver();

    /**
     * Refresh the environment from the list of {@link PropertySource} instances and return a diff of the changes.
     *
     * @return The values that changed
     */
    Map<String, Object> refreshAndDiff();

    /**
     * Add a property source for the given map.
     *
     * @param name   The name
     * @param values The values
     * @return This environment
     */
    default Environment addPropertySource(String name, @Nullable Map<String, ? super Object> values) {
        if (StringUtils.isNotEmpty(name) && CollectionUtils.isNotEmpty(values)) {
            return addPropertySource(PropertySource.of(name, values));
        }
        return this;
    }

    /**
     * Add an application package. Application packages are candidates for scanning for tools that need it (such as JPA
     * or GORM).
     *
     * @param pkg The package to add
     * @return This environment
     */
    default Environment addPackage(Package pkg) {
        addPackage(pkg.getName());
        return this;
    }

    /**
     * Scan the current environment for classes annotated with the given annotation. Use with care, repeated
     * invocations should be avoided for performance reasons.
     *
     * @param annotation The annotation to scan
     * @return The classes
     */
    default Stream<Class> scan(Class<? extends Annotation> annotation) {
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner(getClassLoader());
        return scanner.scan(annotation, getPackages());
    }

    /**
     * Scan the current environment for classes annotated with the given annotation. Use with care, repeated
     * invocations should be avoided for performance reasons.
     *
     * @param annotation The annotation to scan
     * @param packages   The packages to scan
     * @return The classes
     */
    default Stream<Class> scan(Class<? extends Annotation> annotation, String... packages) {
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner(getClassLoader());
        return scanner.scan(annotation, Arrays.asList(packages));
    }

    /**
     * @return The class loader for the environment
     */
    default ClassLoader getClassLoader() {
        return Environment.class.getClassLoader();
    }

    /**
     * Check whether the given class is present within this environment.
     *
     * @param className The class name
     * @return True if it is
     */
    default boolean isPresent(String className) {
        return ClassUtils.isPresent(className, getClassLoader());
    }

    /**
     * Whether the current environment includes the given configuration.
     *
     * @param configuration The configuration
     * @return True if it does
     */
    boolean isActive(BeanConfiguration configuration);

    /**
     * Obtains the {@link PropertySourceLoader} instances.
     *
     * @return A collection of {@link PropertySourceLoader}
     */
    Collection<PropertySourceLoader> getPropertySourceLoaders();
}
