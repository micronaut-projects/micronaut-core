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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bean definition builder factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public abstract class BeanDefinitionBuilderFactory {

    @NonNull
    public static BeanDefinitionBuilder produce(ClassElement classElement, VisitorContext visitorContext) {
        boolean isAbstract = classElement.isAbstract();
        boolean isIntroduction = classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION);
        if (ConfigurationReaderBeanDefinitionBuilder.isConfigurationProperties(classElement)) {
            if (classElement.isInterface()) {
                return new IntroductionInterfaceBeanDefinitionBuilder(classElement, visitorContext, null);
            }
            return new ConfigurationReaderBeanDefinitionBuilder(classElement, visitorContext);
        }
        boolean aopProxyType = !isAbstract && isAopProxyType(classElement);
        if (!isAbstract && classElement.hasStereotype(Factory.class)) {
            return new FactoryBeanDefinitionBuilder(classElement, visitorContext, aopProxyType);
        }
        if (aopProxyType) {
            if (isIntroduction) {
                return new AopIntroductionProxySupportedBeanDefinitionBuilder(classElement, visitorContext, true);
            }
            return new DeclaredBeanDefinitionBuilder(classElement, visitorContext, true);
        }
        if (isIntroduction) {
            if (classElement.isInterface()) {
                return new IntroductionInterfaceBeanDefinitionBuilder(classElement, visitorContext, null);
            }
            return new AopIntroductionProxySupportedBeanDefinitionBuilder(classElement, visitorContext, false);
        }
        // NOTE: In Micronaut 3 abstract classes are allowed to be beans, but are not pickup to be beans just by having methods or fields with @Inject
        if (isDeclaredBean(classElement) || (!isAbstract && (containsInjectMethod(classElement) || containsInjectField(classElement)))) {
            if (classElement.hasStereotype("groovy.lang.Singleton")) {
                throw new ProcessingException(classElement, "Class annotated with groovy.lang.Singleton instead of jakarta.inject.Singleton. Import jakarta.inject.Singleton to use Micronaut Dependency Injection.");
            }
            return new DeclaredBeanDefinitionBuilder(classElement, visitorContext, false);
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
                .annotated(BeanDefinitionBuilderFactory::containsInjectPoint)
        ).isPresent();
    }

    private static boolean containsInjectPoint(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || annotationMetadata.hasStereotype(Value.class)
            || annotationMetadata.hasStereotype(Property.class);
    }

    private static boolean isAopProxyType(ClassElement classElement) {
        return !classElement.isAssignable("io.micronaut.aop.Interceptor") && hasAroundStereotype(classElement.getAnnotationMetadata());
    }

    public static boolean isDeclaredBeanInMetadata(AnnotationMetadata concreteClassMetadata) {
        return concreteClassMetadata.hasDeclaredStereotype(Bean.class) ||
            concreteClassMetadata.hasStereotype(AnnotationUtil.SCOPE) ||
            concreteClassMetadata.hasStereotype(DefaultScope.class);
    }

    /**
     * Does the given metadata have AOP advice declared.
     *
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    protected static boolean hasAroundStereotype(@Nullable AnnotationMetadata annotationMetadata) {
        return hasAround(annotationMetadata,
            annMetadata -> annMetadata.hasStereotype(AnnotationUtil.ANN_AROUND),
            annMetdata -> annMetdata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING));
    }

    /**
     * Does the given metadata have declared AOP advice.
     *
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    protected static boolean hasDeclaredAroundAdvice(@Nullable AnnotationMetadata annotationMetadata) {
        return hasAround(annotationMetadata,
            annMetadata -> annMetadata.hasDeclaredStereotype(AnnotationUtil.ANN_AROUND),
            annMetdata -> annMetdata.getDeclaredAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING));
    }

    private static boolean hasAround(@Nullable AnnotationMetadata annotationMetadata,
                                     @NonNull Predicate<AnnotationMetadata> hasFunction,
                                     @NonNull Function<AnnotationMetadata, List<AnnotationValue<Annotation>>> interceptorBindingsFunction) {
        if (annotationMetadata == null) {
            return false;
        }
        if (hasFunction.test(annotationMetadata)) {
            return true;
        } else if (annotationMetadata.hasDeclaredStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            return interceptorBindingsFunction.apply(annotationMetadata)
                .stream().anyMatch(av ->
                    av.stringValue("kind").orElse("AROUND").equals("AROUND")
                );
        }

        return false;
    }

}
