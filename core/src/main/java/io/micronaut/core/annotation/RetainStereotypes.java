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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Retains the annotations composed by the annotated annotation on its {@link AnnotationValue} in the
 * {@link AnnotationMetadata} of every element the annotation is applied to, in addition to flattening them into the
 * element's stereotypes.</p>
 *
 * <p>{@link AnnotationMetadata} normally indexes stereotypes by name, which loses the association between an
 * individual stereotype occurrence and the annotation that introduced it: two annotations that each compose a
 * repeatable annotation contribute two indistinguishable occurrences. When this annotation is present — directly
 * or through the annotated annotation's own stereotypes — the composing annotations are also kept as a tree under
 * the composing annotation, with member overrides declared through
 * {@code io.micronaut.context.annotation.AliasFor} already applied, and are read back with
 * {@link AnnotationValue#getStereotypes()}:</p>
 *
 * <pre class="code">
 * &#064;RetainStereotypes
 * &#064;Size(min = 5)
 * &#064;interface Username {
 *     &#064;AliasFor(annotation = Size.class, member = "min")
 *     int min() default 5;
 * }
 *
 * // for an element annotated &#064;Username(min = 3)
 * metadata.getAnnotation(Username.class).getStereotypes(); // [ &#064;Size(min = 3) ]
 * </pre>
 *
 * <p>Retention costs generated code proportional to the size of the composition tree at every use site, so it is
 * opt-in and has no cost for annotations that do not declare it.</p>
 *
 * @since 5.2.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface RetainStereotypes {
}
