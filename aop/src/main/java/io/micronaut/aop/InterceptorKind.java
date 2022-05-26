/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.aop;

import java.lang.annotation.Annotation;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Enum representing different interceptors kinds.
 *
 * @author graemerocher
 * @since 2.4.0
 */
public enum InterceptorKind {
    /**
     * Around advice interception.
     *
     * @see Around
     */
    AROUND(Around.class),
    /**
     * Around advice interception.
     *
     * @see AroundConstruct
     * @since 3.0.0
     */
    AROUND_CONSTRUCT(AroundConstruct.class),
    /**
     * Introduction advice interception.
     *
     * @see Introduction
     */
    INTRODUCTION(Introduction.class),
    /**
     * Post construct interception.
     *
     * @see PostConstruct
     * @since 3.0.0
     */
    POST_CONSTRUCT(PostConstruct.class),
    /**
     * PreDestroy interception.
     *
     * @see PreDestroy
     * @since 3.0.0
     */
    PRE_DESTROY(PreDestroy.class);

    private final Class<? extends Annotation> annotationType;

    /**
     * @param annotationType The annotation type
     */
    InterceptorKind(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    /**
     * @return The associated annotation type
     */
    public Class<? extends Annotation> getAnnotationType() {
        return this.annotationType;
    }

    /**
     * @return The associated annotation name
     */
    public String getAnnotationName() {
        return this.annotationType.getName();
    }
}
