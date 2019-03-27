/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Returns every {@link io.micronaut.core.version.annotation.Version} value in the application.
 * @since 1.1.0
 * @author Sergio del Amo
 */
@Singleton
public class VersionInfoSource implements InfoSource {

    private final Collection<VersionCollector> versionCollectors;
    private final String keyName;
    private final Supplier<MapPropertySource> supplier;

    /**
     *
     * @param versionCollectors Version collectors
     * @param keyName The key to be used for the info endpoint.
     */
    public VersionInfoSource(Collection<VersionCollector> versionCollectors,
                             @Value("${endpoints.info.api-version-key:api-versions}") String keyName) {
        this.versionCollectors = versionCollectors;
        this.keyName = keyName;
        this.supplier = SupplierUtil.memoized(this::retrieveVersionsInfo);
    }
    
    @Override
    public Publisher<PropertySource> getSource() {
        return Flowable.just(supplier.get());
    }

    private MapPropertySource retrieveVersionsInfo() {
        Set<String> versions = versionCollectors.stream()
                .map(VersionCollector::versions)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        return new MapPropertySource(keyName, Collections.singletonMap(keyName, versions));
    }
}
