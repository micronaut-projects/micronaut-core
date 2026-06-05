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
package io.micronaut.context.beans.definition;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;

/**
 * Record-friendly accessor for {@link AnnotationMetadataProvider} implementations.
 *
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
sealed interface AnnotationMetadataAccessor extends AnnotationMetadataProvider permits BeanDefinitionInjectionPoint, MemberDefinition {

    /**
     * Returns the annotation metadata backing this provider.
     *
     * @return The annotation metadata backing this provider
     */
    AnnotationMetadata annotationMetadata();

    /**
     * Returns the annotation metadata associated with this element.
     *
     * @return The annotation metadata associated with this element
     */
    @Override
    default AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata();
    }
}
