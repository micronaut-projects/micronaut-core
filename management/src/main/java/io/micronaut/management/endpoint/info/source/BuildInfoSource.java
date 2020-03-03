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
package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
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
    private static final String PREFIX = "classpath:";

    private final String buildPropertiesPath;
    private final ResourceResolver resourceResolver;
    private final Supplier<Optional<PropertySource>> supplier;

    /**
     * @param resourceResolver    A {@link ResourceResolver}
     * @param buildPropertiesPath The build properties path
     */
    public BuildInfoSource(
        ResourceResolver resourceResolver,
        @Value("${endpoints.info.build.location:META-INF/build-info.properties}") String buildPropertiesPath) {

        this.resourceResolver = resourceResolver;
        this.supplier = SupplierUtil.memoized(this::retrieveBuildInfo);
        this.buildPropertiesPath = buildPropertiesPath;
    }

    @Override
    public Publisher<PropertySource> getSource() {
        Optional<PropertySource> propertySource = supplier.get();
        return propertySource.map(Flowable::just).orElse(Flowable.empty());
    }

    private Optional<PropertySource> retrieveBuildInfo() {
        return retrievePropertiesPropertySource(buildPropertiesPath, PREFIX, EXTENSION, resourceResolver);
    }
}
