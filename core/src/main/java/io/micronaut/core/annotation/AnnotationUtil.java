/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.core.annotation;

import java.lang.annotation.*;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;

/**
 * Internal utility methods for annotations. For Internal and framework use only. Do not use in application code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AnnotationUtil {

    /**
     * Constant for Kotlin metadata.
     */
    public static final String KOTLIN_METADATA = "kotlin.Metadata";

    public static final List<String> INTERNAL_ANNOTATION_NAMES = Arrays.asList(
        Retention.class.getName(),
        "kotlin.annotation.Retention",
        Inherited.class.getName(),
        SuppressWarnings.class.getName(),
        Override.class.getName(),
        Repeatable.class.getName(),
        Documented.class.getName(),
        "kotlin.annotation.MustBeDocumented",
        Target.class.getName(),
        "kotlin.annotation.Target",
        KOTLIN_METADATA
    );

    /**
     * Constant indicating an zero annotation.
     */
    public static final Annotation[] ZERO_ANNOTATIONS = new Annotation[0];

    /**
     * Constant indicating an zero annotation.
     */
    public static final AnnotatedElement[] ZERO_ANNOTATED_ELEMENTS = new AnnotatedElement[0];
    /**
     * An empty re-usable element.
     */
    public static final AnnotatedElement EMPTY_ANNOTATED_ELEMENT = new AnnotatedElement() {
        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return ZERO_ANNOTATIONS;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return ZERO_ANNOTATIONS;
        }
    };

}
