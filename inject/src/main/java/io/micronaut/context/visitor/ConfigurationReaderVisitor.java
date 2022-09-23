/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.visitor;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The visitor adds Validated annotation if one of the parameters is a constraint or @Valid.
 *
 * @author Denis Stepanov
 * @since 3.7.0
 */
@Internal
public class ConfigurationReaderVisitor implements TypeElementVisitor<ConfigurationReader, Object> {

    private static final String ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice";

    private ConfigurationMetadataBuilder metadataBuilder = ConfigurationMetadataBuilder.INSTANCE;
    private ClassElement classElement;
    private String[] readPrefixes;
    private String[] writePrefixes;
    private Set<String> includes;
    private Set<String> excludes;

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        reset();
    }

    @Override
    public void visitClass(ClassElement classElement, VisitorContext context) {
        reset();

        if (!classElement.hasStereotype(ConfigurationReader.class)) {
            return;
        }

        ConfigurationMetadata configurationMetadata = metadataBuilder.visitProperties(classElement);
        if (configurationMetadata != null) {
            classElement.annotate(ConfigurationReader.class, (builder) -> builder.member(ConfigurationReader.PREFIX, configurationMetadata.getName()));
        }

        if (classElement.isInterface()) {
            classElement.annotate(ANN_CONFIGURATION_ADVICE);
        }
        if (classElement.hasStereotype(ValidationVisitor.ANN_REQUIRES_VALIDATION)) {
            classElement.annotate(Introspected.class);
        }

        this.classElement = classElement;
        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        readPrefixes = annotationMetadata.getValue(AccessorsStyle.class, "readPrefixes", String[].class)
            .orElse(new String[]{AccessorsStyle.DEFAULT_READ_PREFIX});
        writePrefixes = annotationMetadata.getValue(AccessorsStyle.class, "writePrefixes", String[].class)
            .orElse(new String[]{AccessorsStyle.DEFAULT_WRITE_PREFIX});
        includes = new HashSet<>(Arrays.asList(annotationMetadata.stringValues(ConfigurationReader.class, "includes")));
        excludes = new HashSet<>(Arrays.asList(annotationMetadata.stringValues(ConfigurationReader.class, "excludes")));
        // TODO: investigate why aliases aren't propagated
        includes.addAll(Arrays.asList(annotationMetadata.stringValues(ConfigurationProperties.class, "includes")));
        excludes.addAll(Arrays.asList(annotationMetadata.stringValues(ConfigurationProperties.class, "excludes")));

// field.isPrivate() || !field.isAccessible(classElement) && !field.getDeclaringType().hasDeclaredStereotype(ConfigurationReader.class)
        for (PropertyElement beanProperty : classElement.getBeanProperties()) {
            if (beanProperty.hasStereotype(ConfigurationBuilder.class)) {
                continue;
            } else {
                beanProperty.getWriteMember().ifPresent(memberElement -> {
                    String path = metadataBuilder.visitProperty(
                        memberElement.getOwningType(),
                        memberElement.getDeclaringType(),
                        beanProperty.getGenericType(),
                        beanProperty.getName(),
                        beanProperty.getDocumentation().orElse(null),
                        null
                    ).getPath();

                    beanProperty.annotate(Property.class, (builder) -> builder.member("name", path));
                });
            }
        }

    }

    private void reset() {
        readPrefixes = null;
        writePrefixes = null;
        includes = null;
        excludes = null;
        classElement = null;
    }

    public void visitMethod(MethodElement method, VisitorContext context) {
        if (classElement == null) {
            return;
        }
        if (method.isAbstract()) {
            visitAbstractMethod(method, context);
        } else {
//            visitPossibleGetterSetterMethod(method, context);
        }
    }

    private void visitAbstractMethod(MethodElement method, VisitorContext context) {
        String methodName = method.getName();
        if (!isGetter(methodName)) {
            context.fail("Only getter methods are allowed on @ConfigurationProperties interfaces: " + method + ". You can change the accessors using @AccessorsStyle annotation", classElement);
            return;
        }
        if (method.hasParameters()) {
            context.fail("Only zero argument getter methods are allowed on @ConfigurationProperties interfaces: " + method, method);
            return;
        }
        if ("void".equals(method.getReturnType().getName())) {
            context.fail("Getter methods must return a value @ConfigurationProperties interfaces: " + method, method);
            return;
        }

        final String propertyName = getPropertyNameForGetter(methodName);

        String path = metadataBuilder.visitProperty(
            classElement,
            classElement,
            method.getReturnType(),
            propertyName,
            method.getDocumentation().orElse(null),
            method.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue").orElse(null)
        ).getPath();

        method.annotate(Property.class, (builder) -> builder.member("name", path));

        method.annotate(ANN_CONFIGURATION_ADVICE, (annBuilder) -> {
            if (!method.getReturnType().isPrimitive() && method.getReturnType().hasStereotype(AnnotationUtil.SCOPE)) {
                annBuilder.member("bean", true);
            }
            if (method.hasStereotype(EachProperty.class)) {
                annBuilder.member("iterable", true);
            }

        }).getAnnotationMetadata();
    }

    private boolean shouldExclude(String propertyName) {
        if (!includes.isEmpty() && !includes.contains(propertyName)) {
            return true;
        }
        return !excludes.isEmpty() && excludes.contains(propertyName);
    }

    private boolean shouldExclude(MethodElement method) {
        return method.isStatic()
            || method.isPrivate()
            || method.hasDeclaredAnnotation(AnnotationUtil.INJECT)
            || method.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)
            || method.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT);
    }

    private boolean shouldExclude(FieldElement field) {
        if (field.isPrivate() || !field.isAccessible(classElement) && !field.getDeclaringType().hasDeclaredStereotype(ConfigurationReader.class)) {
            // Allow only to access protected / package protected fields with reflection
            // Don't include not accessible fields from a class without @ConfigurationReader
            return true;
        }
        return field.isFinal()
            || shouldExclude(field.getSimpleName())
            || field.hasStereotype(AnnotationUtil.INJECT)
            || field.hasStereotype(Value.class)
            || field.hasStereotype(Property.class);
    }

    private String getPropertyNameForGetter(String methodName) {
        return NameUtils.getPropertyNameForGetter(methodName, readPrefixes);
    }

    private String getPropertyNameForSetter(String methodName) {
        return NameUtils.getPropertyNameForSetter(methodName, writePrefixes);
    }

    private boolean isGetter(String methodName) {
        return NameUtils.isReaderName(methodName, readPrefixes);
    }

    private boolean isSetter(String methodName) {
        return NameUtils.isWriterName(methodName, writePrefixes);
    }

    private Optional<MethodElement> findSetterMethodFor(String propertyName) {
        String setterName = NameUtils.getterNameFor(propertyName, writePrefixes);
        return classElement.getEnclosedElements(ElementQuery.ALL_METHODS.named(setterName).onlyAccessible()).stream().findFirst();
    }

    private Optional<MethodElement> findGetterMethodFor(String propertyName) {
        String getterName = NameUtils.setterNameFor(propertyName, readPrefixes);
        return classElement.getEnclosedElements(ElementQuery.ALL_METHODS.named(getterName).onlyAccessible()).stream().findFirst();
    }

}
