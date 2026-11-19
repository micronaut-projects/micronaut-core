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

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Bean definition builder factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public abstract class BeanDefinitionCreatorFactory {

    public static <R> List<R> produce(ClassElement classElement,
                                      ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilder,
                                      VisitorContext visitorContext) {
        return produceInternal(classElement, beanDefinitionBuilder, visitorContext);
    }

    /**
     * Whether {@link #produce(ClassElement, ElementBeanDefinitionBuilderFactory, VisitorContext)} may write a bean
     * definition for the class: configuration, a factory, advised or introduced, a class declared a bean by
     * {@link #isDeclaredBeanInMetadata(AnnotationMetadata)}, or a concrete class that is executable, qualified or has
     * an injected constructor, method or field. It is the decision {@code produce} itself takes, without the
     * validation {@code produce} does afterwards: {@code false} means {@code produce} writes nothing for the class,
     * {@code true} that it writes a definition unless it rejects the class.
     *
     * @param classElement The class element
     * @return Whether a bean definition may be produced for the class
     * @since 5.3.0
     */
    public static boolean mayProduce(ClassElement classElement) {
        return selectCreator(classElement) != Creator.NONE;
    }

    private static <R> List<R> produceInternal(ClassElement classElement, ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilder, VisitorContext visitorContext) {
        BeanInjectionUtils.validateBeanConstructor(classElement);
        return switch (selectCreator(classElement)) {
            case CONFIGURATION_INTERFACE, INTRODUCTION_INTERFACE ->
                new IntroductionInterfaceBeanElementCreator<>(classElement, visitorContext, beanDefinitionBuilder).build();
            case CONFIGURATION ->
                new ConfigurationReaderBeanElementCreator<>(classElement, visitorContext, beanDefinitionBuilder).build();
            case FACTORY ->
                new FactoryBeanElementCreator<>(classElement, visitorContext, isAopProxyType(classElement), beanDefinitionBuilder).build();
            case AOP_PROXY_INTRODUCTION ->
                new AopIntroductionProxySupportedBeanElementCreator<>(classElement, visitorContext, true, beanDefinitionBuilder).build();
            case AOP_PROXY ->
                new DeclaredBeanElementCreator<>(classElement, visitorContext, true, beanDefinitionBuilder).build();
            case INTRODUCTION ->
                new AopIntroductionProxySupportedBeanElementCreator<>(classElement, visitorContext, false, beanDefinitionBuilder).build();
            case DECLARED -> {
                if (classElement.hasStereotype("groovy.lang.Singleton")) {
                    throw new ProcessingException(classElement, "Class annotated with groovy.lang.Singleton instead of jakarta.inject.Singleton. Import jakarta.inject.Singleton to use Micronaut Dependency Injection.");
                }
                if (classElement.isEnum()) {
                    throw new ProcessingException(classElement, "Enum types cannot be defined as beans");
                }
                yield new DeclaredBeanElementCreator<>(classElement, visitorContext, false, beanDefinitionBuilder).build();
            }
            case NONE -> List.of();
        };
    }

    private static Creator selectCreator(ClassElement classElement) {
        boolean isAbstract = classElement.isAbstract();
        // An interceptor declares @InterceptorBinding(kind = INTRODUCTION) to state which advice it implements,
        // not to request advice for itself. Without the same guard that isAopProxyType applies, the interceptor is
        // compiled into its own introduction proxy that carries the target's lifecycle bindings, and resolving
        // interceptors for that proxy finds the proxy again and recurses until the stack is exhausted.
        boolean isIntroduction = !classElement.isAssignable(Interceptor.class) && isIntroduction(classElement);
        if (ConfigurationReaderBeanElementCreator.isConfigurationProperties(classElement)) {
            return classElement.isInterface() ? Creator.CONFIGURATION_INTERFACE : Creator.CONFIGURATION;
        }
        boolean aopProxyType = !isAbstract && isAopProxyType(classElement);
        if (!isAbstract && classElement.hasStereotype(Factory.class)) {
            return Creator.FACTORY;
        }
        if (aopProxyType) {
            return isIntroduction ? Creator.AOP_PROXY_INTRODUCTION : Creator.AOP_PROXY;
        }
        if (isIntroduction) {
            return classElement.isInterface() ? Creator.INTRODUCTION_INTERFACE : Creator.INTRODUCTION;
        }
        if (classElement.isInterface()) {
            return Creator.NONE;
        }
        // NOTE: In Micronaut 3 abstract classes are allowed to be beans, but are not pickup to be beans just by having methods or fields with @Inject
        if (isDeclaredBean(classElement) || (!isAbstract && (containsInjectMethod(classElement) || containsInjectField(classElement)))) {
            return Creator.DECLARED;
        }
        return Creator.NONE;
    }

    private static boolean isDeclaredBean(ClassElement classElement) {
        if (isDeclaredBeanInMetadata(classElement.getAnnotationMetadata())) {
            return true;
        }
        if (classElement.isAbstract()) {
            return false;
        }
        return classElement.hasStereotype(Executable.class) ||
            classElement.hasStereotype(AnnotationUtil.QUALIFIER) ||
            BeanInjectionUtils.findBeanConstructor(classElement)
                .map(constructor -> constructor.hasStereotype(AnnotationUtil.INJECT))
                .orElse(false);
    }

    private static boolean containsInjectMethod(ClassElement classElement) {
        return classElement.getEnclosedElement(
            ElementQuery.ALL_METHODS.onlyConcrete()
                .onlyDeclared()
                .annotated(annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.INJECT))
        ).isPresent();
    }

    private static boolean containsInjectField(ClassElement classElement) {
        return classElement.getEnclosedElement(
            ElementQuery.ALL_FIELDS
                .onlyDeclared()
                .annotated(BeanDefinitionCreatorFactory::containsInjectPoint)
        ).isPresent();
    }

    private static boolean containsInjectPoint(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || annotationMetadata.hasStereotype(Value.class)
            || annotationMetadata.hasStereotype(Property.class);
    }

    private static boolean isAopProxyType(ClassElement classElement) {
        return !classElement.isAssignable(Interceptor.class) && InterceptedMethodUtil.hasAroundStereotype(classElement.getAnnotationMetadata());
    }

    public static boolean isDeclaredBeanInMetadata(AnnotationMetadata concreteClassMetadata) {
        return concreteClassMetadata.hasDeclaredStereotype(Bean.class) ||
            concreteClassMetadata.hasStereotype(AnnotationUtil.SCOPE) ||
            concreteClassMetadata.hasStereotype(DefaultScope.class);
    }

    public static boolean isIntroduction(AnnotationMetadata metadata) {
        return InterceptedMethodUtil.hasIntroductionStereotype(metadata);
    }

    /**
     * The creator {@link #produce(ClassElement, ElementBeanDefinitionBuilderFactory, VisitorContext)} hands a class to.
     */
    private enum Creator {
        CONFIGURATION_INTERFACE,
        CONFIGURATION,
        FACTORY,
        AOP_PROXY_INTRODUCTION,
        AOP_PROXY,
        INTRODUCTION_INTERFACE,
        INTRODUCTION,
        DECLARED,
        NONE
    }

}
