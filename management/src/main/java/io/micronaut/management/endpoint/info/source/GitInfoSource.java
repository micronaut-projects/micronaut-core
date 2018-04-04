/*
 * Copyright 2018 original authors
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
import io.micronaut.context.env.*;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.cli.Option;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

/**
 * <p>An {@link InfoSource} that retrieves info from Git properties. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.git.enabled", notEquals = "false")
public class GitInfoSource implements PropertiesInfoSource {

    private static final String EXTENSION = ".properties";
    private static final String PREFIX = "classpath:";

    @Value("${endpoints.info.git.location:git.properties}")
    private String gitPropertiesPath;

    private ResourceResolver resourceResolver;
    private final Supplier<Optional<PropertySource>> supplier;

    public GitInfoSource(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.supplier = SupplierUtil.memoized(this::retrieveGitInfo);
    }

    @Override
    public Publisher<PropertySource> getSource() {
        Optional<PropertySource> propertySource = supplier.get();
        return propertySource.map(Flowable::just).orElse(Flowable.empty());
    }

    private Optional<PropertySource> retrieveGitInfo() {
        return retrievePropertiesPropertySource(gitPropertiesPath, PREFIX, EXTENSION, resourceResolver);
    }
}

