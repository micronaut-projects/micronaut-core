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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Adds several annotations to a route or a group at once, see
 * {@link HttpRouteSpec#annotate(Consumer)} and {@link HttpRouteGroup#annotate(Consumer)}. The
 * annotations are added in order, as if each was given with {@code annotate(...)}: a later
 * annotation of a type replaces an earlier one of the same type.
 *
 * <pre>{@code
 * routes.GET("/reports", reportsHandler).annotate(annotations -> annotations
 *     .add(Audited.class)
 *     .add(Version.class, version -> version.value("2"))
 *     .add(AnnotationValue.builder(RequiresRole.class).value("admin").build()));
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface RouteAnnotations permits DefaultRouteAnnotations {

    /**
     * Add an annotation, see {@link HttpRouteSpec#annotate(AnnotationValue)}.
     *
     * @param annotation The annotation
     * @return These annotations
     */
    RouteAnnotations add(AnnotationValue<?> annotation);

    /**
     * Add an annotation without members.
     *
     * @param annotationType The type of the annotation
     * @return These annotations
     */
    default RouteAnnotations add(Class<? extends Annotation> annotationType) {
        return add(AnnotationValue.builder(Objects.requireNonNull(annotationType, "annotationType")).build());
    }

    /**
     * Add an annotation with members: {@code add(Version.class, version -> version.value("2"))}.
     *
     * @param annotationType The type of the annotation
     * @param members        Sets the members of the annotation
     * @param <A>            The type of the annotation
     * @return These annotations
     */
    default <A extends Annotation> RouteAnnotations add(Class<A> annotationType, Consumer<AnnotationValueBuilder<A>> members) {
        Objects.requireNonNull(members, "members");
        AnnotationValueBuilder<A> builder = AnnotationValue.builder(Objects.requireNonNull(annotationType, "annotationType"));
        members.accept(builder);
        return add(builder.build());
    }
}
