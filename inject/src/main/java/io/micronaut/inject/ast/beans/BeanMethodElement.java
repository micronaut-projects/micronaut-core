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
package io.micronaut.inject.ast.beans;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.MethodElement;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents a configurable bean method.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface BeanMethodElement extends MethodElement {
    /**
     * Make the method executable.
     *
     * @return This bean method
     */
    default @NonNull
    BeanMethodElement executable() {
        annotate(Executable.class);
        return this;
    }

    /**
     * Make the method injected.
     *
     * @return This bean method
     */
    default @NonNull
    BeanMethodElement inject() {
        if (hasAnnotation(AnnotationUtil.PRE_DESTROY)) {
            throw new IllegalStateException("Cannot inject a method annotated with @PreDestroy");
        }
        if (hasAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            throw new IllegalStateException("Cannot inject a method annotated with @PostConstruct");
        }
        annotate(AnnotationUtil.INJECT);
        return this;
    }

    /**
     * Make the method a {@link jakarta.annotation.PreDestroy} hook.
     *
     * @return This bean method
     */
    default @NonNull
    BeanMethodElement preDestroy() {
        if (hasAnnotation(AnnotationUtil.INJECT)) {
            throw new IllegalStateException("Cannot make a method annotated with @Inject a @PreDestroy handler");
        }
        if (hasAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            throw new IllegalStateException("Cannot make a method annotated with @PostConstruct a @PreDestroy handler");
        }
        annotate(AnnotationUtil.PRE_DESTROY);
        return this;
    }

    /**
     * Make the method a {@link jakarta.annotation.PostConstruct} hook.
     *
     * @return This bean method
     */
    default @NonNull
    BeanMethodElement postConstruct() {
        if (hasAnnotation(AnnotationUtil.INJECT)) {
            throw new IllegalStateException("Cannot make a method annotated with @Inject a @PostConstruct handler");
        }
        if (hasAnnotation(AnnotationUtil.PRE_DESTROY)) {
            throw new IllegalStateException("Cannot make a method annotated with @PreDestroy a @PostConstruct handler");
        }
        annotate(AnnotationUtil.POST_CONSTRUCT);
        return this;
    }

    /**
     * Process the bean parameters.
     *
     * @param parameterConsumer The parameter consumer
     * @return This bean method
     */
    default @NonNull
    BeanMethodElement withParameters(@NonNull Consumer<BeanParameterElement[]> parameterConsumer) {
        Objects.requireNonNull(parameterConsumer, "The parameter consumer cannot be null");
        parameterConsumer.accept(getParameters());
        return this;
    }

    @NonNull
    @Override
    BeanParameterElement[] getParameters();
}
