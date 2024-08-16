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

import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

/**
 * Ordinary bean with AOP introduction.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class AopIntroductionProxySupportedBeanElementCreator extends DeclaredBeanElementCreator {

    AopIntroductionProxySupportedBeanElementCreator(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy) {
        super(classElement, visitorContext, isAopProxy);
    }

    @Override
    protected BeanDefinitionVisitor createBeanDefinitionVisitor() {
        if (classElement.isFinal()) {
            throw new ProcessingException(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.getName());
        }
        aopProxyVisitor = createIntroductionAopProxyWriter(classElement, visitorContext);
        aopProxyVisitor.visitTypeArguments(classElement.getAllTypeArguments());
        visitAnnotationMetadata(aopProxyVisitor, classElement.getAnnotationMetadata());
        beanDefinitionWriters.add(aopProxyVisitor);
        MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
        if (constructorElement != null) {
            aopProxyVisitor.visitBeanDefinitionConstructor(
                constructorElement,
                constructorElement.isPrivate(),
                visitorContext
            );
        } else {
            aopProxyVisitor.visitDefaultConstructor(
                AnnotationMetadata.EMPTY_METADATA,
                visitorContext
            );
        }
        return aopProxyVisitor;
    }

    @Override
    protected AopProxyWriter getAroundAopProxyVisitor(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        return aopProxyVisitor;
    }

    @Override
    protected boolean visitPropertyReadElement(BeanDefinitionVisitor visitor, PropertyElement propertyElement, MethodElement readElement) {
        if (intercept(visitor, readElement)) {
            return true;
        }
        return super.visitPropertyReadElement(visitor, propertyElement, readElement);
    }

    @Override
    protected boolean visitPropertyWriteElement(BeanDefinitionVisitor visitor, PropertyElement propertyElement, MethodElement writeElement) {
        if (intercept(visitor, writeElement)) {
            return true;
        }
        return super.visitPropertyWriteElement(visitor, propertyElement, writeElement);
    }

    @Override
    protected boolean visitMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (intercept(visitor, methodElement)) {
            return true;
        }
        return super.visitMethod(visitor, methodElement);
    }

    private boolean intercept(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        return !methodElement.isFinal() && visitIntrospectedMethod(visitor, classElement, methodElement);
    }

}
