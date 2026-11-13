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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NullMarked;

/**
 * An annotation remapper that recognizes repeatable containers whose repeated annotation types do not declare
 * {@link java.lang.annotation.Repeatable}. Registered as an {@link AnnotationRemapper}, it can inspect application
 * annotations through the visitor context instead of declaring their names before compilation.
 *
 * <p>The metadata builder processes each contained occurrence through the usual annotation pipeline and stores
 * the occurrences as repeatable annotations. The annotation type's own {@code @Repeatable} takes precedence.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface RepeatableAnnotationRemapper extends AnnotationRemapper {

    /**
     * Whether an annotation is the repeatable container of the annotation type held in its {@code value} member.
     * Called for containers holding a non-empty array of annotations of one type, when that type does not declare
     * {@code @Repeatable}. Implementations should decide from the annotation types, not the occurrence's values,
     * and recognize one container per repeated annotation type.
     *
     * @param annotation The candidate container
     * @param visitorContext The visitor context
     * @return Whether to process the contained annotations as repeated occurrences
     * @since 5.3.0
     */
    boolean isRepeatableAnnotationContainer(AnnotationValue<?> annotation, VisitorContext visitorContext);
}
