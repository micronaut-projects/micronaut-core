package io.micronaut.inject.processing.gen;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.BeanPropertiesConfiguration;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.time.Duration;
import java.util.Optional;
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
                visitor.visitFieldValue(fieldElement.getDeclaringType(), fieldElement, annotationMetadata, true, fieldElement.isReflectionRequired(classElement));
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
    protected boolean isInjectPointMethod(MemberElement memberElement) {
        return super.isInjectPointMethod(memberElement) || memberElement.hasDeclaredStereotype(ConfigurationInject.class);
    }

    private void visitConfigurationBuilder(BeanDefinitionVisitor visitor,
                                           MemberElement builderElement,
                                           ClassElement builderType) {
        try {
            String configurationPrefix = builderElement.stringValue(ConfigurationBuilder.class).map(v -> v + ".").orElse("");
            builderType.getBeanProperties(BeanPropertiesConfiguration.of(builderElement))
                .stream()
                .filter(propertyElement -> {
                    if (propertyElement.isExcluded()) {
                        return false;
                    }
                    Optional<MethodElement> writeMethod = propertyElement.getWriteMethod();
                    if (!writeMethod.isPresent()) {
                        return false;
                    }
                    MethodElement methodElement = writeMethod.get();
                    if (methodElement.hasStereotype(Deprecated.class) || !methodElement.isPublic()) {
                        return false;
                    }
                    return methodElement.getParameters().length <= 2;
                }).forEach(propertyElement -> {
                    MethodElement methodElement = propertyElement.getWriteMethod().get();
                    String propertyName = propertyElement.getName();
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
                            propertyName,
                            methodElement.getReturnType(),
                            methodElement.getSimpleName(),
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
                                propertyName,
                                methodElement.getReturnType(),
                                methodElement.getSimpleName(),
                                metadata.getPath()
                            );
                        }
                    }
                });
        } finally {
            visitor.visitConfigBuilderEnd();
        }
    }

}
