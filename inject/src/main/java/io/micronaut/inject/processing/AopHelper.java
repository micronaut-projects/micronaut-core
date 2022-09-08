/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.processing;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * AOP helper to connect Inject module with AOP.
 */
@NextMajorVersion("Correct dependency graph")
public interface AopHelper {

    BeanDefinitionVisitor visitAdaptedMethod(ClassElement classElement,
                                             MethodElement sourceMethod,
                                             AtomicInteger adaptedMethodIndex,
                                             VisitorContext visitorContext);

    BeanDefinitionVisitor createIntroductionAopProxyWriter(ClassElement typeElement,
                                                           VisitorContext visitorContext);

    BeanDefinitionVisitor createAroundAopProxyWriter(BeanDefinitionVisitor existingWriter,
                                                     AnnotationMetadata producedAnnotationMetadata,
                                                     VisitorContext visitorContext,
                                                     boolean forceProxyTarget);

    boolean visitIntrospectedMethod(BeanDefinitionVisitor visitor, ClassElement typeElement, MethodElement methodElement);

    void visitAroundMethod(BeanDefinitionVisitor existingWriter, TypedElement beanType, MethodElement methodElement);
}
