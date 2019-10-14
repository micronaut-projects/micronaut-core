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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;

import java.lang.annotation.Annotation;

/**
 * Encapsulates all the possible configurations that might be defined in {@link Filter} annotation.
 *
 * @author svishnyakoff
 * @since 1.2.4
 */
@Internal
public final class FilterProperties {

    /**
     * Filter properties without any filter criteria. Filters build with the usage of this instance will be applied to all http clients.
     */
    public static final FilterProperties EMPTY_FILTER_PROPERTIES = new FilterProperties(
            new String[0],
            new HttpMethod[0],
            new String[0],
            new Class[0]
    );

    private final String[] patterns;
    private final HttpMethod[] methods;
    private final String[] serviceId;
    private final Class<? extends Annotation>[] stereotypes;

    /**
     * @param patterns          the patterns this filter should match
     * @param methods           the methods to match
     * @param serviceId         the serviceId to match
     * @param stereotypes the annotation markers to match
     */
    public FilterProperties(String[] patterns,
                            HttpMethod[] methods,
                            String[] serviceId,
                            Class<? extends Annotation>[] stereotypes) {
        this.patterns = patterns;
        this.methods = methods;
        this.serviceId = serviceId;
        this.stereotypes = stereotypes;
    }

    /**
     * @param properties additional properties you want to include
     * @return new {@link FilterProperties} that contains properties from current and given object.
     */
    public FilterProperties merge(FilterProperties properties) {
        return new FilterProperties(
                ArrayUtils.concat(patterns, properties.patterns),
                ArrayUtils.concat(methods, properties.methods),
                ArrayUtils.concat(serviceId, properties.serviceId),
                ArrayUtils.concat(stereotypes, properties.stereotypes)
        );
    }

    /**
     * @return The patterns this filter should match
     */
    public String[] getPatterns() {
        return this.patterns;
    }

    /**
     * @return The methods to match. Defaults to all
     */
    public HttpMethod[] getMethods() {
        return this.methods;
    }

    /**
     * The service identifiers this filter applies to. Equivalent to the {@code id()} of {@code io.micronaut.http.client.Client}.
     *
     * @return The service identifiers
     */
    public String[] getServiceId() {
        return this.serviceId;
    }

    /**
     * If provided, filter will be applied only to {@code io.micronaut.http.client.Client} that are marked
     * with one of provided annotations.
     *
     * @return Marker annotations
     */
    public Class<? extends Annotation>[] getStereotypes() {
        return this.stereotypes;
    }
}
