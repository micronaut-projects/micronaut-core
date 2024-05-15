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
package io.micronaut.inject;

<<<<<<< HEAD
=======
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Autowired;
import io.micronaut.core.annotation.AnnotationMetadata;
>>>>>>> 605ceac06f (initial implementation, method injection not yet working)
import io.micronaut.core.annotation.AnnotationMetadataProvider;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;

/**
 * An injection point as a point in a class definition where dependency injection is required.
 *
 * @author Graeme Rocher
 * @since 1.0
 * @param <T> the bean type
 */
public interface InjectionPoint<T> extends AnnotationMetadataProvider {

    /**
     * @return The bean that declares this injection point
     */
    @NonNull BeanDefinition<T> getDeclaringBean();

<<<<<<< HEAD
=======
    /**
     * @return The qualifier of the bean that declares this injection point
     * @since 4.5.0
     */
    @Nullable
    default Qualifier<T> getDeclaringBeanQualifier() {
        return getDeclaringBean().getDeclaredQualifier();
    }

    /**
     * Check whether injection is required for the given metadata.
     * @param annotationMetadata The annotation metadata.
     * @return True if injection is required.
     * @since 4.5.0
     */
    static boolean isInjectionRequired(AnnotationMetadata annotationMetadata) {
        return annotationMetadata != null && annotationMetadata
            .booleanValue(AnnotationUtil.INJECT, Autowired.MEMBER_REQUIRED)
            .orElse(true);
    }

>>>>>>> 605ceac06f (initial implementation, method injection not yet working)
}
