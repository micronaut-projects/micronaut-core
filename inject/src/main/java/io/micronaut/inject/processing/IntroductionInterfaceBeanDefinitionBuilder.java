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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Introduction interface proxy builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
final class IntroductionInterfaceBeanDefinitionBuilder extends AbstractBeanDefinitionBuilder {

    private final String factoryBeanDefinitionName;

    IntroductionInterfaceBeanDefinitionBuilder(ClassElement classElement, VisitorContext visitorContext, String factoryBeanDefinitionName) {
        super(classElement, visitorContext);
        this.factoryBeanDefinitionName = factoryBeanDefinitionName;
    }

    @Override
    public void buildInternal() {
        BeanDefinitionVisitor aopProxyWriter = aopHelper.createIntroductionAopProxyWriter(classElement, visitorContext);
        aopProxyWriter.visitTypeArguments(classElement.getAllTypeArguments());

        // Because we add validated interceptor in some cases, this needs to run before the constructor visit
        if (classElement.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            if (ConfigurationReaderBeanDefinitionBuilder.isConfigurationProperties(classElement)) {
                // Configuration beans are validated at the startup and don't require validation advice
                aopProxyWriter.setValidated(true);
            } else {
                for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(am -> am.hasAnnotation(ANN_REQUIRES_VALIDATION)))) {
                    methodElement.annotate(AbstractBeanDefinitionBuilder.ANN_VALIDATED);
                }
            }
        }

        MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
        if (constructorElement != null) {
            aopProxyWriter.visitBeanDefinitionConstructor(constructorElement, constructorElement.isReflectionRequired(), visitorContext);
        } else {
            aopProxyWriter.visitDefaultConstructor(classElement, visitorContext);
        }
        if (factoryBeanDefinitionName != null) {
            aopProxyWriter.visitSuperBeanDefinitionFactory(factoryBeanDefinitionName);
        }

        // The introduction will include overridden methods* (find(List) <- find(Iterable)*) but ordinary class introduction doesn't
        // Because of the caching we need to process declared methods first
        Set<MethodElement> processed = new HashSet<>();
        List<MethodElement> declaredEnclosedElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeHiddenElements().includeOverriddenMethods().onlyDeclared());
        for (MethodElement methodElement : declaredEnclosedElements) {
            aopHelper.visitIntrospectedMethod(aopProxyWriter, classElement, methodElement);
            processed.add(methodElement);
        }
        List<MethodElement> otherEnclosedElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeHiddenElements().includeOverriddenMethods());
        for (MethodElement methodElement : otherEnclosedElements) {
            if (processed.contains(methodElement)) {
                continue;
            }
            aopHelper.visitIntrospectedMethod(aopProxyWriter, classElement, methodElement);
        }

        beanDefinitionWriters.add(aopProxyWriter);
    }

}
