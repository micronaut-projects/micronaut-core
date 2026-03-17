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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;

/**
 * Extends {@link BeanDefinitionVisitor} and adds access to the proxied type name.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ProxyingBeanDefinitionVisitor extends BeanDefinitionVisitor {

    /**
     * visitInterceptorTypes.
     *
     * @param interceptorBinding the interceptor binding
     */
    void visitInterceptorBinding(AnnotationValue<?>... interceptorBinding);

    /**
     * The visitor for introduction methods.
     *
     * @param beanType      The bean type
     * @param methodElement The method element
     * @since 5.0
     */
    void visitIntroductionMethod(TypedElement beanType, MethodElement methodElement);

    /**
     * The visitor for around methods.
     *
     * @param beanType      The bean type
     * @param methodElement The method element
     * @since 5.0
     */
    void visitAroundMethod(TypedElement beanType, MethodElement methodElement);
}
