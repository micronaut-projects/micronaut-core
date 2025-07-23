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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
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
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderDefinition;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderPropertyDefinition;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * The visitor adds Validated annotation if one of the parameters is a constraint or @Valid.
 *
 * @author Denis Stepanov
 * @since 3.7.0
 */
@Internal
public final class ConfigurationReaderVisitor implements TypeElementVisitor<ConfigurationReader, Object> {

    private static final List<String> CONSTRUCTOR_PARAMETERS_INJECTION_ANN =
        Arrays.asList(Property.class.getName(), Value.class.getName(), Parameter.class.getName(), AnnotationUtil.QUALIFIER, AnnotationUtil.INJECT);

    private static final String ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice";

    private ConfigurationMetadataBuilder metadataBuilder = new ConfigurationMetadataBuilder();

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (metadataBuilder.hasMetadata()) {
            metadataBuilder.finish();
            ServiceLoader<ConfigurationMetadataWriter> writers = ServiceLoader.load(ConfigurationMetadataWriter.class, getClass().getClassLoader());
            try {
                for (ConfigurationMetadataWriter writer : writers) {
                    try {
                        writer.write(metadataBuilder, visitorContext);
                    } catch (IOException e) {
                        visitorContext.warn("Error occurred writing configuration metadata: " + e.getMessage(), null);
                    }
                }
            } catch (ServiceConfigurationError e) {
                visitorContext.warn("Unable to load ConfigurationMetadataWriter due to : " + e.getMessage(), null);
            }
        }
        metadataBuilder = new ConfigurationMetadataBuilder();
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement classElement, VisitorContext context) {

        if (!classElement.hasStereotype(ConfigurationReader.class)) {
            return;
        }

        ConfigurationMetadata configurationMetadata = metadataBuilder.visitProperties(classElement);
        String prefix = configurationMetadata.getName();
        classElement.annotate(ConfigurationReader.class, builder -> builder.member(ConfigurationReader.PREFIX, prefix));

        boolean anInterface = classElement.isInterface();
        if (anInterface) {
            classElement.annotate(ANN_CONFIGURATION_ADVICE);
        }
        if (classElement.hasStereotype(RequiresValidation.class)) {
            classElement.annotate(Introspected.class);
        }

        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        String[] readPrefixes = annotationMetadata.getValue(AccessorsStyle.class, "readPrefixes", String[].class)
            .orElse(new String[]{AccessorsStyle.DEFAULT_READ_PREFIX});

        classElement.getPrimaryConstructor()
            .ifPresent(constructorElement -> applyConfigurationInjectionIfNecessary(classElement, constructorElement, context));

        Set<MemberElement> processed = new HashSet<>();

        classElement.getBeanProperties()
            .forEach(propertyElement -> {
                if (propertyElement.hasStereotype(ConfigurationBuilder.class)) {
                    Optional<MethodElement> readMethod = propertyElement.getReadMethod();
                    Optional<FieldElement> field = propertyElement.getField();
                    // Exclude / ignore shouldn't affect builders
                    if (readMethod.isPresent()) {
                        MethodElement methodElement = readMethod.get();
                        visitConfigurationBuilder(
                            prefix,
                            ConfigurationBuilderDefinition.of(classElement, methodElement.withAnnotationMetadata(propertyElement.getAnnotationMetadata()), context),
                            context
                        );
                    } else if (field.isPresent()) {
                        FieldElement fieldElement = field.get();
                        if (fieldElement.isAccessible(classElement)) {
                            visitConfigurationBuilder(
                                prefix,
                                ConfigurationBuilderDefinition.of(classElement, fieldElement, context),
                                context
                            );
                        }
                        processed.add(fieldElement);
                    }
                } else if (!propertyElement.isExcluded()) {
                    if (notProcessed(propertyElement.getName(), propertyElement.getDeclaringType())
                        && (propertyElement.getWriteMethod().isPresent() || anInterface || propertyElement.getField().isPresent())
                        && !propertyElement.getType().hasStereotype(ConfigurationProperties.class)) {
                        String doc = getPropertyDocs(propertyElement, anInterface);
                        metadataBuilder.visitProperty(
                            propertyElement.getOwningType(),
                            propertyElement.getDeclaringType(),
                            propertyElement.getType(),
                            propertyElement.getName(),
                            doc,
                            propertyElement.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue").orElse(null)
                        );
                        propertyElement.getField().ifPresent(processed::add);
                    }
                }
            });

        classElement.getMethods().forEach(methodElement -> {
            if (!methodElement.isStatic() && isInjectPointMethod(methodElement)) {
                applyConfigurationInjectionIfNecessary(classElement, methodElement, context);
            }
            if (methodElement.isAbstract()) {
                visitAbstractMethod(methodElement, readPrefixes, context);
            }
        });
        classElement.getFields().forEach(fieldElement -> {
            if (!fieldElement.isStatic() && fieldElement.isAccessible(classElement) && !processed.contains(fieldElement)) {
                if (fieldElement.hasStereotype(ConfigurationBuilder.class)) {
                    visitConfigurationBuilder(
                        prefix,
                        ConfigurationBuilderDefinition.of(classElement, fieldElement, context),
                        context
                    );
                }
            }
        });
    }

    private String getPropertyDocs(PropertyElement propertyElement, boolean anInterface) {
        String doc = propertyElement.getDocumentation().orElse(null);
        Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
        if (writeMethod.isPresent()) {
            Optional<String> documentation = writeMethod.get().getDocumentation();
            if (documentation.isPresent()) {
                doc = documentation.get();
            }
        } else if (anInterface) {
            Optional<MethodElement> readMethod = propertyElement.getReadMethod();
            if (readMethod.isPresent()) {
                Optional<String> documentation = readMethod.get().getDocumentation();
                if (documentation.isPresent()) {
                    doc = documentation.get();
                }
            }
        }
        return doc;
    }

    private boolean notProcessed(String prop, ClassElement declaringType) {
        return metadataBuilder.getProperties().stream().noneMatch(p -> p.getName().equals(prop) && p.getDeclaringType().equals(declaringType.getName()));
    }

    private void visitConfigurationBuilder(String prefix, ConfigurationBuilderDefinition builderDefinition, VisitorContext visitorContext) {
        String configurationPrefix = metadataBuilder.visitBuilder(prefix, builderDefinition.builderElement(), builderDefinition.builderType()).getName();
        if (!configurationPrefix.isEmpty()) {
            configurationPrefix += ".";
        }

        for (ConfigurationBuilderPropertyDefinition methodDefinition : builderDefinition.elements()) {
            MethodElement method = methodDefinition.method();
            metadataBuilder.visitProperty(
                method.getOwningType(),
                method.getDeclaringType(),
                methodDefinition.type(),
                configurationPrefix + methodDefinition.name(),
                methodDefinition.path(),
                null,
                null
            );
        }
    }

    private boolean isInjectPointMethod(MemberElement memberElement) {
        return memberElement.hasDeclaredStereotype(AnnotationUtil.INJECT) || memberElement.hasDeclaredStereotype(ConfigurationInject.class);
    }

    private void applyConfigurationInjectionIfNecessary(ClassElement classElement,
                                                        MethodElement constructor,
                                                        VisitorContext visitorContext) {
        if (!classElement.isRecord() && !constructor.hasAnnotation(ConfigurationInject.class)) {
            return;
        }
        if (classElement.isRecord()) {
            final List<PropertyElement> beanProperties = constructor
                .getDeclaringType()
                .getBeanProperties();
            final ParameterElement[] parameters = constructor.getParameters();
            if (beanProperties.size() == parameters.length) {
                for (int i = 0; i < parameters.length; i++) {
                    ParameterElement parameter = parameters[i];
                    final PropertyElement bp = beanProperties.get(i);
                    if (CONSTRUCTOR_PARAMETERS_INJECTION_ANN.stream().noneMatch(bp::hasStereotype)) {
                        processConfigurationInjectParameter(constructor.getDeclaringType(), parameter, visitorContext);
                    }
                }
                return;
            }
        }
        processConfigurationInject(constructor, visitorContext);
    }

    private void processConfigurationInject(MethodElement injectMethod, VisitorContext visitorContext) {
        for (ParameterElement parameter : injectMethod.getParameters()) {
            if (CONSTRUCTOR_PARAMETERS_INJECTION_ANN.stream().noneMatch(parameter::hasStereotype)) {
                processConfigurationInjectParameter(injectMethod.getDeclaringType(), parameter, visitorContext);
            }
        }
    }

    private void processConfigurationInjectParameter(ClassElement declaringType,
                                                     ParameterElement parameter,
                                                     VisitorContext visitorContext) {
        if (ConfigurationReaderVisitor.isPropertyParameter(parameter, visitorContext)) {
            PropertyMetadata pm = metadataBuilder.getProperties().stream().filter(p -> p.getName().equals(parameter.getName()) && p.getDeclaringType().equals(declaringType.getName())).findFirst().orElse(null);
            if (pm == null) {
                pm = metadataBuilder.visitProperty(
                    parameter.getMethodElement().getOwningType(),
                    parameter.getMethodElement().getDeclaringType(),
                    parameter.getGenericType(),
                    parameter.getName(),
                    parameter.getDocumentation().orElse(null),
                    parameter.stringValue(Bindable.class, "defaultValue").orElse(null)
                );
            }
            String path = pm.getPath();
            parameter.annotate(Property.class, (builder) -> builder.member("name", path));
        }
    }

    public static boolean isPropertyParameter(ParameterElement parameter, VisitorContext visitorContext) {
        ClassElement genericType = parameter.getGenericType();
        return isPropertyParameter(genericType, visitorContext);
    }

    private static boolean isPropertyParameter(ClassElement genericType, VisitorContext visitorContext) {
        if (genericType.isOptional() || genericType.isContainerType() || isProvider(genericType)) {
            ClassElement finalParameterType = genericType;
            genericType = genericType.getOptionalValueType().or(finalParameterType::getFirstTypeArgument).orElse(genericType);
            // Get the class with type annotations
            genericType = visitorContext.getClassElement(genericType.getCanonicalName()).orElse(genericType);
        } else if (genericType.isAssignable(Map.class)) {
            ClassElement t = genericType.getTypeArguments().get("V");
            if (t != null) {
                genericType = t;
            }
        }
        return !genericType.hasStereotype(AnnotationUtil.SCOPE) && !genericType.hasStereotype(Bean.class);
    }

    private static boolean isProvider(ClassElement genericType) {
        String name = genericType.getName();
        for (String type : DefaultArgument.PROVIDER_TYPES) {
            if (name.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private void visitAbstractMethod(MethodElement method, String[] readPrefixes, VisitorContext context) {
        String methodName = method.getName();
        if (!isGetter(methodName, readPrefixes)) {
            context.fail("Only getter methods are allowed on @ConfigurationProperties interfaces: " + method + ". You can change the accessors using @AccessorsStyle annotation", method.getOwningType());
            return;
        }
        if (method.hasParameters()) {
            context.fail("Only zero argument getter methods are allowed on @ConfigurationProperties interfaces: " + method, method);
            return;
        }
        if (method.getReturnType().isVoid()) {
            context.fail("Getter methods must return a value @ConfigurationProperties interfaces: " + method, method);
            return;
        }

        boolean isPropertyParameter = isPropertyParameter(method.getGenericReturnType(), context);
        final String propertyName = getPropertyNameForGetter(methodName, readPrefixes);
        if (isPropertyParameter) {
            PropertyMetadata pm = metadataBuilder.getProperties().stream().filter(p -> p.getName().equals(propertyName) && p.getDeclaringType().equals(method.getOwningType().getName())).findFirst().orElse(null);
            if (pm == null) {
                pm = metadataBuilder.visitProperty(
                    method.getOwningType(),
                    method.getOwningType(), // interface methods don't inherit the prefix
                    method.getReturnType(),
                    propertyName,
                    method.getDocumentation().orElse(null),
                    method.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue").orElse(null)
                );
            }
            String path = pm.getPath();
            method.annotate(Property.class, builder -> builder.member("name", path));
        }

        method.annotate(ANN_CONFIGURATION_ADVICE, annBuilder -> {

            if (!isPropertyParameter) {
                annBuilder.member("bean", true);
            }
            if (method.hasStereotype(EachProperty.class)) {
                annBuilder.member("iterable", true);
            }
        });
    }

    private String getPropertyNameForGetter(String methodName, String[] readPrefixes) {
        return NameUtils.getPropertyNameForGetter(methodName, readPrefixes);
    }

    private boolean isGetter(String methodName, String[] readPrefixes) {
        return NameUtils.isReaderName(methodName, readPrefixes);
    }

}
