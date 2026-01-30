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

import io.micronaut.context.bean.definition.builder.Builder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.ElementProxyBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordinary bean with AOP introduction.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class AopIntroductionProxySupportedBeanElementCreator<R> extends DeclaredBeanElementCreator<R> {

    private final ElementProxyBuilder<R> introductionProxyBuilder;

    AopIntroductionProxySupportedBeanElementCreator(ClassElement classElement,
                                                    VisitorContext visitorContext,
                                                    boolean isAopProxy,
                                                    ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilder) {
        super(classElement, visitorContext, isAopProxy, beanDefinitionBuilder);
        if (classElement.isFinal()) {
            throw new ProcessingException(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement.getName());
        }
        introductionProxyBuilder = beanDefinitionBuilderFactory.introductionProxy(classElement);
    }

    @Override
    public List<R> build() {
        ElementBeanDefinitionBuilder<R> beanDefinitionBuilder = createBeanDefinitionBuilder();
        build(beanDefinitionBuilder);
        List<R> result = new ArrayList<>(introductionProxyBuilder.build());
        for (Builder<List<R>> additionalBuilder : additionalBuilders) {
            result.addAll(additionalBuilder.build());
        }
        return result;
    }

    @Override
    protected ElementBeanDefinitionBuilder<R> createBeanDefinitionBuilder() {
        return introductionProxyBuilder.beanDefinitionBuilder();
    }

    @Override
    protected ElementProxyBuilder<R> getAopProxyBuilder(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, @Nullable MethodElement methodElement) {
        return introductionProxyBuilder;
    }

    @Override
    protected boolean visitPropertyReadElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement, MethodElement readElement) {
        if (intercept(readElement)) {
            return true;
        }
        return super.visitPropertyReadElement(beanDefinitionBuilder, propertyElement, readElement);
    }

    @Override
    protected boolean visitPropertyWriteElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement, MethodElement writeElement) {
        if (intercept(writeElement)) {
            return true;
        }
        return super.visitPropertyWriteElement(beanDefinitionBuilder, propertyElement, writeElement);
    }

    @Override
    protected boolean visitMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        if (intercept(methodElement)) {
            return true;
        }
        return super.visitMethod(beanDefinitionBuilder, methodElement);
    }

    private boolean intercept(MethodElement methodElement) {
        return !methodElement.isFinal() && visitIntrospectedMethod(introductionProxyBuilder, methodElement);
    }

}
