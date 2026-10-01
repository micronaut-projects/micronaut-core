/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.processing.definition;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.sourcegen.model.ObjectDef;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Aggregates the generated {@link ObjectDef} together with service metadata.
 *
 * @param objectDef           The generated object definition
 * @param serviceClass        The service to be registered
 * @param originatingElements The originating elements
 * @param serviceContent      The content of the {@code META-INF/micronaut} entry of the service, or {@code null}
 *                            to leave the entry empty
 * @author Denis Stepanov
 * @since 5.1.0
 */
public record OutputObjectDef(ObjectDef objectDef,
                              @Nullable Class<?> serviceClass,
                              OriginatingElements originatingElements,
                              @Internal byte @Nullable [] serviceContent) {

    /**
     * The canonical constructor, which takes the content of the service entry. Internal: the Micronaut processors
     * create it with that content. Other processors use the three-argument constructor.
     *
     * @param objectDef           The generated object definition
     * @param serviceClass        The service to be registered
     * @param originatingElements The originating elements
     * @param serviceContent      The content of the {@code META-INF/micronaut} entry of the service, or {@code null}
     *                            to leave the entry empty
     */
    @Internal
    public OutputObjectDef {
    }

    /**
     * An object definition whose service entry is empty.
     *
     * @param objectDef           The generated object definition
     * @param serviceClass        The service to be registered
     * @param originatingElements The originating elements
     */
    public OutputObjectDef(ObjectDef objectDef, @Nullable Class<?> serviceClass, OriginatingElements originatingElements) {
        this(objectDef, serviceClass, originatingElements, null);
    }

    // The generated methods would compare and print the content array by identity

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof OutputObjectDef that
            && objectDef.equals(that.objectDef)
            && Objects.equals(serviceClass, that.serviceClass)
            && originatingElements.equals(that.originatingElements)
            && Arrays.equals(serviceContent, that.serviceContent);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(objectDef, serviceClass, originatingElements) + Arrays.hashCode(serviceContent);
    }

    @Override
    public String toString() {
        return "OutputObjectDef[objectDef=" + objectDef
            + ", serviceClass=" + serviceClass
            + ", originatingElements=" + originatingElements
            + ", serviceContent=" + Arrays.toString(serviceContent) + ']';
    }
}
