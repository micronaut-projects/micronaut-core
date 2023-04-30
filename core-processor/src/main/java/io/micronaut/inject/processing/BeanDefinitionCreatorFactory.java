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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;

/**
 * Bean definition builder factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public abstract class BeanDefinitionCreatorFactory {

    @NonNull
    public static BeanDefinitionCreator produce(ClassElement classElement, VisitorContext visitorContext) {
        boolean isAbstract = classElement.isAbstract();
        boolean isIntroduction = isIntroduction(classElement);
        if (ConfigurationReaderBeanElementCreator.isConfigurationProperties(classElement)) {
            if (classElement.isInterface()) {
                return new IntroductionInterfaceBeanElementCreator(classElement, visitorContext);
            }
            return new ConfigurationReaderBeanElementCreator(classElement, visitorContext);
        }
        boolean aopProxyType = !isAbstract && isAopProxyType(classElement);
        if (!isAbstract && classElement.hasStereotype(Factory.class)) {
            return new FactoryBeanElementCreator(classElement, visitorContext, aopProxyType);
        }
        if (aopProxyType) {
            if (isIntroduction) {
                return new AopIntroductionProxySupportedBeanElementCreator(classElement, visitorContext, true);
            }
            return new DeclaredBeanElementCreator(classElement, visitorContext, true);
        }
        if (isIntroduction) {
            if (classElement.isInterface()) {
                return new IntroductionInterfaceBeanElementCreator(classElement, visitorContext);
            }
            return new AopIntroductionProxySupportedBeanElementCreator(classElement, visitorContext, false);
        }
        // NOTE: In Micronaut 3 abstract classes are allowed to be beans, but are not pickup to be beans just by having methods or fields with @Inject
        if (isDeclaredBean(classElement) || (!isAbstract && (containsInjectMethod(classElement) || containsInjectField(classElement)))) {
            if (classElement.hasStereotype("groovy.lang.Singleton")) {
                throw new ProcessingException(classElement, "Class annotated with groovy.lang.Singleton instead of jakarta.inject.Singleton. Import jakarta.inject.Singleton to use Micronaut Dependency Injection.");
            }
            if (classElement.isEnum()) {
                throw new ProcessingException(classElement, "Enum types cannot be defined as beans");
            }
            return new DeclaredBeanElementCreator(classElement, visitorContext, false);
        }
        return Collections::emptyList;
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
            classElement.getPrimaryConstructor().map(constructor -> constructor.hasStereotype(AnnotationUtil.INJECT)).orElse(false);
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

}
