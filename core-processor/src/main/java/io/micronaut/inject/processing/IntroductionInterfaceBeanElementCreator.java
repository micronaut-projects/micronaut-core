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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Introduction interface proxy builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class IntroductionInterfaceBeanElementCreator extends AbstractBeanElementCreator {

    IntroductionInterfaceBeanElementCreator(ClassElement classElement, VisitorContext visitorContext) {
        super(classElement, visitorContext);
    }

    @Override
    public void buildInternal() {
        BeanDefinitionVisitor aopProxyWriter = createIntroductionAopProxyWriter(classElement, visitorContext);
        aopProxyWriter.visitTypeArguments(classElement.getAllTypeArguments());

        // Because we add validated interceptor in some cases, this needs to run before the constructor visit
        if (classElement.hasAnnotation(ANN_REQUIRES_VALIDATION)) {
            if (ConfigurationReaderBeanElementCreator.isConfigurationProperties(classElement)) {
                // Configuration beans are validated at the startup and don't require validation advice
                aopProxyWriter.setValidated(true);
            } else {
                for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(am -> am.hasAnnotation(ANN_REQUIRES_VALIDATION)))) {
                    methodElement.annotate(AbstractBeanElementCreator.ANN_VALIDATED);
                }
            }
        }

        MethodElement constructorElement = classElement.getPrimaryConstructor().orElse(null);
        if (constructorElement != null) {
            aopProxyWriter.visitBeanDefinitionConstructor(constructorElement, constructorElement.isReflectionRequired(), visitorContext);
        } else {
            aopProxyWriter.visitDefaultConstructor(classElement, visitorContext);
        }

        // The introduction will include overridden methods* (find(List) <- find(Iterable)*) but ordinary class introduction doesn't
        // Because of the caching we need to process declared methods first
        List<MethodElement> allMethods = new ArrayList<>(classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeOverriddenMethods()));
        List<MethodElement> methods = new ArrayList<>(allMethods);
        List<MethodElement> nonAbstractMethods = methods.stream().filter(m -> !m.isAbstract()).toList();
        // Remove abstract methods overridden by non-abstract ones
        methods.removeIf(method -> method.isAbstract() && nonAbstractMethods.stream().anyMatch(nonAbstractMethod -> nonAbstractMethod.overrides(method)));
        // Remove non-abstract methods without explicit around advice
        methods.removeIf(method -> !method.isAbstract() && !InterceptedMethodUtil.hasDeclaredAroundAdvice(method.getAnnotationMetadata()));
        Collections.reverse(methods); // reverse to process hierarchy starting from declared methods
        for (MethodElement methodElement : methods) {
            visitIntrospectedMethod(aopProxyWriter, classElement, methodElement);
        }
        List<PropertyElement> beanProperties = classElement.getSyntheticBeanProperties();
        for (PropertyElement beanProperty : beanProperties) {
            handlePropertyMethod(aopProxyWriter, methods, beanProperty.getReadMethod().orElse(null));
            handlePropertyMethod(aopProxyWriter, methods, beanProperty.getWriteMethod().orElse(null));
        }
        beanDefinitionWriters.add(aopProxyWriter);
    }

    private void handlePropertyMethod(BeanDefinitionVisitor aopProxyWriter, List<MethodElement> methods, MethodElement method) {
        if (method != null && method.isAbstract() && !methods.contains(method)) {
            visitIntrospectedMethod(
                aopProxyWriter,
                this.classElement,
                method
            );
        }
    }

}
