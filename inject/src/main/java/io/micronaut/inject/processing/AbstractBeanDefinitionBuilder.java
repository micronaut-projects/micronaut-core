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

import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.RequiresValidation;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Abstract shared functionality of the builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
abstract class AbstractBeanDefinitionBuilder implements BeanDefinitionBuilder {

    public static final String ANN_VALIDATED = "io.micronaut.validation.Validated";
    protected static final String ANN_REQUIRES_VALIDATION = RequiresValidation.class.getName();

    protected final ClassElement classElement;
    protected final VisitorContext visitorContext;
    protected final List<BeanDefinitionVisitor> beanDefinitionWriters = new LinkedList<>();

    protected final AopHelper aopHelper;

    protected AbstractBeanDefinitionBuilder(ClassElement classElement, VisitorContext visitorContext) {
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        checkPackage(classElement);
        try {
            aopHelper = (AopHelper) ClassUtils.forName("io.micronaut.aop.writer.AopHelperImpl", getClass().getClassLoader()).get().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final Collection<BeanDefinitionVisitor> build() {
        buildInternal();
        return beanDefinitionWriters;
    }

    public abstract void buildInternal();

    private void checkPackage(ClassElement classElement) {
        io.micronaut.inject.ast.PackageElement packageElement = classElement.getPackage();
        if (packageElement.isUnnamed()) {
            throw new ProcessingException(classElement, "Micronaut beans cannot be in the default package");
        }
    }

    /**
     * Does the given metadata have AOP advice declared.
     *
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    @NextMajorVersion("Replace with InterceptedMethodUtil.hasAroundStereotype")
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
    @NextMajorVersion("Replace with InterceptedMethodUtil.hasDeclaredAroundAdvice")
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

    protected void visitAnnotationMetadata(BeanDefinitionVisitor writer, AnnotationMetadata annotationMetadata) {
        for (io.micronaut.core.annotation.AnnotationValue<Requires> annotation : annotationMetadata.getAnnotationValuesByType(Requires.class)) {
            annotation.stringValue(RequiresCondition.MEMBER_BEAN_PROPERTY)
                .ifPresent(beanProperty -> {
                    annotation.stringValue(RequiresCondition.MEMBER_BEAN)
                        .map(className -> visitorContext.getClassElement(className, visitorContext.getElementAnnotationMetadataFactory().readOnly()).get())
                        .ifPresent(classElement -> {
                            String requiredValue = annotation.stringValue().orElse(null);
                            String notEqualsValue = annotation.stringValue(RequiresCondition.MEMBER_NOT_EQUALS).orElse(null);
                            writer.visitAnnotationMemberPropertyInjectionPoint(classElement, beanProperty, requiredValue, notEqualsValue);
                        });
                });
        }
    }

    public static AnnotationMetadata getElementAnnotationMetadata(MemberElement methodElement) {
        // NOTE: if annotation processor modified the method's annotation
        // annotationUtils.getAnnotationMetadata(method) will return AnnotationMetadataHierarchy of both method+class metadata
        AnnotationMetadata annotationMetadata = methodElement.getAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            return annotationMetadata.getDeclaredMetadata();
        }
        return annotationMetadata;
    }

}
