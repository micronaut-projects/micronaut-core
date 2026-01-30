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

import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.ElementProxyBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Abstract shared functionality of the builder.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
abstract class AbstractBeanElementCreator<R> implements BeanDefinitionCreator<R> {

    protected final ClassElement classElement;
    protected final VisitorContext visitorContext;
    protected final ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory;

    protected AbstractBeanElementCreator(ClassElement classElement, VisitorContext visitorContext, ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory) {
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        this.beanDefinitionBuilderFactory = beanDefinitionBuilderFactory;
        checkPackage(classElement);
    }

    @Override
    public List<R> build() {
        return buildInternal();
    }

    /**
     * Build visitors.
     */
    protected abstract List<R> buildInternal();

    private void checkPackage(ClassElement classElement) {
        io.micronaut.inject.ast.PackageElement packageElement = classElement.getPackage();
        if (packageElement.isUnnamed()) {
            throw new ProcessingException(classElement, "Micronaut beans cannot be in the default package");
        }
    }

    public static AnnotationMetadata getElementAnnotationMetadata(MemberElement memberElement) {
        if (memberElement instanceof MethodElement methodElement) {
            return methodElement.getMethodAnnotationMetadata();
        }
        return memberElement.getAnnotationMetadata();
    }

    protected boolean visitIntrospectedMethod(ElementProxyBuilder<R> proxyBuilder, MethodElement methodElement) {

        final AnnotationMetadata resolvedTypeMetadata = classElement.getAnnotationMetadata();
        final boolean resolvedTypeMetadataIsAopProxyType = InterceptedMethodUtil.hasDeclaredAroundAdvice(resolvedTypeMetadata);

        if (methodElement.isAbstract()
            || resolvedTypeMetadataIsAopProxyType
            || InterceptedMethodUtil.hasDeclaredAroundAdvice(methodElement.getAnnotationMetadata())) {
            proxyBuilder.addProxyMethod(methodElement);
            return true;
        } else if (methodElement.hasDeclaredStereotype(Executable.class)) {
            proxyBuilder.beanDefinitionBuilder().addExecutableMethod(methodElement, methodElement.isReflectionRequired(classElement));
        }
        return false;
    }

}
