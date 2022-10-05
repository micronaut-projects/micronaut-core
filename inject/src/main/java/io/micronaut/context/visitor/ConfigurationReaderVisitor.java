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

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.RequiresValidation;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * The visitor adds Validated annotation if one of the parameters is a constraint or @Valid.
 *
 * @author Denis Stepanov
 * @since 3.7.0
 */
@Internal
public class ConfigurationReaderVisitor implements TypeElementVisitor<ConfigurationReader, Object> {

    private static final String ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice";

    private final ConfigurationMetadataBuilder metadataBuilder = ConfigurationMetadataBuilder.INSTANCE;
    private String[] readPrefixes;

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
        if (classElement.hasStereotype(RequiresValidation.class)) {
            classElement.annotate(Introspected.class);
        }

        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        readPrefixes = annotationMetadata.getValue(AccessorsStyle.class, "readPrefixes", String[].class)
            .orElse(new String[]{AccessorsStyle.DEFAULT_READ_PREFIX});
    }

    private void reset() {
        readPrefixes = null;
    }

    @Override
    public void visitMethod(MethodElement method, VisitorContext context) {
        if (method.isAbstract()) {
            visitAbstractMethod(method, context);
        }
    }

    private void visitAbstractMethod(MethodElement method, VisitorContext context) {
        String methodName = method.getName();
        if (!isGetter(methodName)) {
            context.fail("Only getter methods are allowed on @ConfigurationProperties interfaces: " + method + ". You can change the accessors using @AccessorsStyle annotation", method.getOwningType());
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
            method.getOwningType(),
            method.getOwningType(), // interface methods don't inherit the prefix
            method.getReturnType(),
            propertyName,
            method.getDocumentation().orElse(null),
            method.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue").orElse(null)
        ).getPath();

        method.annotate(Property.class, builder -> builder.member("name", path));

        method.annotate(ANN_CONFIGURATION_ADVICE, annBuilder -> {
            if (!method.getReturnType().isPrimitive() && method.getReturnType().hasStereotype(AnnotationUtil.SCOPE)) {
                annBuilder.member("bean", true);
            }
            if (method.hasStereotype(EachProperty.class)) {
                annBuilder.member("iterable", true);
            }
        });
    }

    private String getPropertyNameForGetter(String methodName) {
        return NameUtils.getPropertyNameForGetter(methodName, readPrefixes);
    }

    private boolean isGetter(String methodName) {
        return NameUtils.isReaderName(methodName, readPrefixes);
    }

}
