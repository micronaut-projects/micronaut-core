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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Allows remapping of annotations from one annotation set to another at compilation time.
 * Similar to the {@link AnnotationMapper} interface with the following differences:
 *
 * <ul>
 *     <li>Can be applied to a whole package of annotations.</li>
 *     <li>The original annotation being mapped is not retained in the metadata.</li>
 * </ul>
 *
 * <p>Useful for supporting multiple annotation sets that reside in different package namespaces, however are largely
 * similar in function, for example {@code javax.annotation.Nullable} and {@code edu.umd.cs.findbugs.annotations.Nullable}. One can
 * remap these to a single annotation internally at compilation time.</p>
 *
 * @author graemerocher
 * @since 1.2.0
 */
public interface AnnotationRemapper {

    /**
     * @return The package name of the annotation.
     */
    @Nonnull String getPackageName();

    /**
     * The map method will be called for each instances of the annotation returned via this method.
     *
     * @param annotation The annotation values
     * @param visitorContext The context that is being visited
     * @return A list of zero or many annotations and values to map to
     */
    @Nonnull List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext);

}
