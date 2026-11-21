/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <p>An {@link io.micronaut.management.endpoint.info.InfoSource} that retrieves info from build properties.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.build.enabled", notEquals = StringUtils.FALSE)
public class BuildInfoSource implements PropertiesInfoSource {

    private static final String EXTENSION = ".properties";

    private final String buildPropertiesPath;
    private final ResourceResolver resourceResolver;
    private final @Nullable Environment environment;
    private final Supplier<Optional<PropertySource>> supplier;

    /**
     * Creates a source that only reads properties files. Use
     * {@link #BuildInfoSource(ResourceResolver, String, Environment)} to also read the other formats the application
     * can load configuration from.
     *
     * @param resourceResolver A {@link ResourceResolver}
     * @param buildPropertiesPath The path for the build properties file
     */
    public BuildInfoSource(ResourceResolver resourceResolver, String buildPropertiesPath) {
        this(resourceResolver, buildPropertiesPath, null);
    }

    /**
     * @param resourceResolver A {@link ResourceResolver}
     * @param buildPropertiesPath The path for the build properties file, which may use any format a {@link PropertySourceLoader} of the environment supports
     * @param environment The {@link Environment} whose {@link PropertySourceLoader}s read the file, or {@code null} to only read properties files
     * @since 5.3.0
     */
    @Inject
    public BuildInfoSource(
        ResourceResolver resourceResolver,
        @Value("${endpoints.info.build.location:META-INF/build-info.properties}") String buildPropertiesPath,
        @Nullable Environment environment) {

        this.resourceResolver = resourceResolver;
        this.environment = environment;
        this.supplier = SupplierUtil.memoized(this::retrieveBuildInfo);
        this.buildPropertiesPath = buildPropertiesPath;
    }

    @Override
    public Publisher<PropertySource> getSource() {
        Optional<PropertySource> propertySource = supplier.get();
        return propertySource.map(Publishers::just).orElseGet(Publishers::empty);
    }

    private Optional<PropertySource> retrieveBuildInfo() {
        Collection<PropertySourceLoader> loaders = environment != null ? environment.getPropertySourceLoaders() : List.of(new PropertiesPropertySourceLoader());
        return retrievePropertySource(buildPropertiesPath, ClassPathResourceLoader.PREFIX, EXTENSION, resourceResolver, loaders);
    }
}
