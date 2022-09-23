package io.micronaut.inject.processing.gen;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ConfigurationPropertiesBeanBuilder extends SimpleBeanBuilder {

    protected ConfigurationPropertiesBeanBuilder(ClassElement classElement, VisitorContext visitorContext) {
        super(classElement, visitorContext, false);
    }

    public static boolean isConfigurationProperties(ClassElement classElement) {
        return classElement.hasStereotype(ConfigurationReader.class);
    }

    @Override
    protected boolean visitAopMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        return false;
    }

    @Override
    protected boolean processAsProperties() {
        return true;
    }

    @Override
    protected boolean visitProperty(BeanDefinitionVisitor visitor, PropertyElement propertyElement) {
        if (propertyElement.hasStereotype(ConfigurationBuilder.class)) {
            if (propertyElement.getReadMethod().isPresent()) {
                MethodElement methodElement = propertyElement.getReadMethod().get();
                ClassElement builderType = methodElement.getReturnType();
                visitor.visitConfigBuilderMethod(
                    builderType,
                    methodElement.getName(),
                    propertyElement.getAnnotationMetadata(),
                    null,
                    builderType.isInterface()
                );
                visitConfigurationBuilder(visitor, propertyElement, builderType);
                return true;
            }
            if (propertyElement.getField().isPresent()) {
                FieldElement fieldElement = propertyElement.getField().get();
                if (fieldElement.isAccessible(classElement)) {
                    ClassElement builderType = fieldElement.getType();
                    visitor.visitConfigBuilderField(
                        builderType,
                        fieldElement.getName(),
                        fieldElement.getAnnotationMetadata(),
                        metadataBuilder,
                        builderType.isInterface()
                    );
                    visitConfigurationBuilder(visitor, propertyElement, builderType);
                    return true;
                }
                throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
            }
        } else if (!propertyElement.isExcluded()) {
            if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.METHOD && propertyElement.getWriteMethod().isPresent()) {
                visitor.setValidated(visitor.isValidated() || propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION));
                MethodElement methodElement = propertyElement.getWriteMethod().get();
                ParameterElement parameter = methodElement.getParameters()[0];
                AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                    propertyElement,
                    parameter
                ).merge();
                annotationMetadata = calculatePath(propertyElement, methodElement, annotationMetadata);
                methodElement.replaceAnnotations(annotationMetadata);
                parameter.replaceAnnotations(annotationMetadata);
                visitor.visitSetterValue(methodElement.getDeclaringType(), methodElement, annotationMetadata, methodElement.isReflectionRequired(classElement), true);
                return true;
            }
            if (propertyElement.getWriteAccessKind() == PropertyElement.AccessKind.FIELD && propertyElement.getField().isPresent()) {
                visitor.setValidated(visitor.isValidated() || propertyElement.hasAnnotation(ANN_REQUIRES_VALIDATION));
                FieldElement fieldElement = propertyElement.getField().get();
                AnnotationMetadata annotationMetadata = propertyElement.getAnnotationMetadata();
                if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                    annotationMetadata = ((AnnotationMetadataHierarchy) annotationMetadata).merge();
                } else {
                    // Just a copy
                    annotationMetadata = new AnnotationMetadataHierarchy(annotationMetadata).merge();
                }
                annotationMetadata = calculatePath(propertyElement, fieldElement, annotationMetadata);
                visitor.visitFieldValue(fieldElement.getDeclaringType(), fieldElement, annotationMetadata,true, fieldElement.isReflectionRequired(classElement));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean visitField(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        if (fieldElement.hasStereotype(ConfigurationBuilder.class) && !fieldElement.isAccessible(classElement)) {
            throw new ProcessingException(fieldElement, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
        }
        return super.visitField(visitor, fieldElement);
    }

    private AnnotationMetadata calculatePath(PropertyElement propertyElement, MemberElement writeMember, AnnotationMetadata annotationMetadata) {
        String path = metadataBuilder.visitProperty(
            writeMember.getOwningType(),
            writeMember.getDeclaringType(),
            propertyElement.getGenericType(),
            propertyElement.getName(),
            propertyElement.getDocumentation().orElse(null),
            null
        ).getPath();
        return visitorContext.getAnnotationMetadataBuilder().annotate(annotationMetadata, AnnotationValue.builder(Property.class).member("name", path).build());
    }

    @Override
    protected boolean isInjectPointMethod(MethodElement methodElement) {
        return super.isInjectPointMethod(methodElement) || methodElement.hasDeclaredStereotype(ConfigurationInject.class);
    }

    private void visitConfigurationBuilder(BeanDefinitionVisitor visitor, MemberElement builderElement, ClassElement builderType) {
        try {
            Boolean allowZeroArgs = builderElement.booleanValue(ConfigurationBuilder.class, "allowZeroArgs").orElse(false);
            List<String> prefixes = Arrays.asList(builderElement.getValue(AccessorsStyle.class, "writePrefixes", String[].class).orElse(new String[]{AccessorsStyle.DEFAULT_WRITE_PREFIX}));
            String configurationPrefix = builderElement.stringValue(ConfigurationBuilder.class).map(v -> v + ".").orElse("");
            Set<String> includes = CollectionUtils.setOf(builderElement.stringValues(ConfigurationBuilder.class, "includes"));
            Set<String> excludes = CollectionUtils.setOf(builderElement.stringValues(ConfigurationBuilder.class, "excludes"));

            builderType.getEnclosedElements(ElementQuery.ALL_METHODS)
                .stream()
                .filter(methodElement -> {
                    if (methodElement.hasStereotype(Deprecated.class) || !methodElement.isPublic()) {
                        return false;
                    }
                    int paramCount = methodElement.getParameters().length;
                    if (!((paramCount > 0 && paramCount < 3) || allowZeroArgs && paramCount == 0)) {
                        return false;
                    }
                    return isPrefixedWith(methodElement, prefixes);
                }).forEach(methodElement -> {
                    String methodName = methodElement.getSimpleName();
                    String prefix = getMethodPrefix(prefixes, methodName);
                    String propertyName = NameUtils.decapitalize(methodName.substring(prefix.length()));
                    if (shouldExclude(includes, excludes, propertyName)) {
                        return;
                    }
                    ParameterElement[] params = methodElement.getParameters();
                    int paramCount = params.length;
                    if (paramCount < 2) {
                        ParameterElement parameterElement = paramCount == 1 ? params[0] : null;
                        ClassElement parameterElementType = parameterElement == null ? null : parameterElement.getGenericType();

                        PropertyMetadata metadata = metadataBuilder.visitProperty(
                            classElement,
                            builderElement.getDeclaringType(),
                            methodElement.getReturnType().getGenericType(),
                            configurationPrefix + propertyName,
                            null,
                            null
                        );

                        visitor.visitConfigBuilderMethod(
                            prefix,
                            methodElement.getReturnType(),
                            methodName,
                            parameterElementType,
                            parameterElementType != null ? parameterElementType.getTypeArguments() : null,
                            metadata.getPath()
                        );
                    } else if (paramCount == 2) {
                        // check the params are a long and a TimeUnit
                        ParameterElement first = params[0];
                        ParameterElement second = params[1];
                        ClassElement firstParamType = first.getType();
                        ClassElement secondParamType = second.getType();

                        if (firstParamType.getSimpleName().equals("long") && secondParamType.isAssignable(TimeUnit.class)) {
                            PropertyMetadata metadata = metadataBuilder.visitProperty(
                                classElement,
                                methodElement.getDeclaringType(),
                                visitorContext.getClassElement(Duration.class.getName()).get(),
                                configurationPrefix + propertyName,
                                null,
                                null
                            );

                            visitor.visitConfigBuilderDurationMethod(
                                prefix,
                                methodElement.getReturnType(),
                                methodName,
                                metadata.getPath()
                            );
                        }
                    }
                });
        } finally {
            visitor.visitConfigBuilderEnd();
        }
    }

    private boolean shouldExclude(Set<String> includes, Set<String> excludes, String propertyName) {
        if (!includes.isEmpty() && !includes.contains(propertyName)) {
            return true;
        }
        return !excludes.isEmpty() && excludes.contains(propertyName);
    }

    private boolean isPrefixedWith(MemberElement enclosedElement, List<String> prefixes) {
        String name = enclosedElement.getSimpleName();
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String getMethodPrefix(List<String> prefixes, String methodName) {
        for (String prefix : prefixes) {
            if (methodName.startsWith(prefix)) {
                return prefix;
            }
        }
        return methodName;
    }

    private Optional<MethodElement> findGetterMethodFor(FieldElement field) {
        return classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAccessible()
                .named(NameUtils.getterNameFor(field.getSimpleName())))
            .stream()
            .findFirst();
    }

}
