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
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.micronaut.runtime.context.scope.Refreshable;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * <p>An {@link InfoSource} that retrieves values under the <i>info</i> key from configuration sources.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
@Refreshable
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.config.enabled", notEquals = StringUtils.FALSE)
public class ConfigurationInfoSource implements InfoSource {

    private final Environment environment;
    private final Supplier<MapPropertySource> supplier;

    /**
     * @param environment The {@link Environment}
     */
    public ConfigurationInfoSource(Environment environment) {
        this.environment = environment;
        this.supplier = SupplierUtil.memoized(this::retrieveConfigurationInfo);
    }

    @Override
    public Publisher<PropertySource> getSource() {
        return Flowable.just(supplier.get());
    }

    private MapPropertySource retrieveConfigurationInfo() {
        return new MapPropertySource(
            "info",
            environment.getProperty("info", Map.class).orElse(Collections.emptyMap())
        );
    }
}
