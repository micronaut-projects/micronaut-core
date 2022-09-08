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
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.ConfigurationUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.OriginatingElements;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory bean builder.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
final class FactoryBeanDefinitionBuilder extends DeclaredBeanDefinitionBuilder {

    private final AtomicInteger factoryMethodIndex = new AtomicInteger();

    FactoryBeanDefinitionBuilder(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy) {
        super(classElement, visitorContext, isAopProxy);
    }

    @Override
    protected boolean visitMethod(BeanDefinitionVisitor visitor, MethodElement methodElement) {
        if (methodElement.hasDeclaredStereotype(Bean.class.getName(), AnnotationUtil.SCOPE)) {
            visitBeanFactoryElement(visitor, methodElement.getGenericReturnType(), methodElement);
            return true;
        }
        return super.visitMethod(visitor, methodElement);
    }

    @Override
    protected boolean visitField(BeanDefinitionVisitor visitor, FieldElement fieldElement) {
        if (fieldElement.hasDeclaredStereotype(Bean.class.getName())) {
            if (!fieldElement.isAccessible(classElement)) {
                throw new ProcessingException(fieldElement, "Beans produced from fields cannot be private");
            }
            visitBeanFactoryElement(visitor, fieldElement.getType(), fieldElement);
            return true;
        }
        return super.visitField(visitor, fieldElement);
    }

    @Override
    protected boolean visitPropertyReadElement(BeanDefinitionVisitor visitor, PropertyElement propertyElement, MethodElement readElement) {
        if (readElement.hasDeclaredStereotype(Bean.class.getName())) {
            visitBeanFactoryElement(visitor, readElement.getGenericReturnType(), readElement);
            return true;
        }
        return super.visitPropertyReadElement(visitor, propertyElement, readElement);
    }

    @Override
    protected boolean visitPropertyWriteElement(BeanDefinitionVisitor visitor, PropertyElement propertyElement, MethodElement writeElement) {
        if (writeElement.hasDeclaredStereotype(Bean.class.getName())) {
            return true;
        }
        return super.visitPropertyWriteElement(visitor, propertyElement, writeElement);
    }

    void visitBeanFactoryElement(BeanDefinitionVisitor visitor, ClassElement producedType, MemberElement producingElement) {
        if (producedType.isPrimitive()) {
            BeanDefinitionWriter producedBeanDefinitionWriter = new BeanDefinitionWriter(producingElement,
                OriginatingElements.of(producingElement),
                visitorContext,
                factoryMethodIndex.getAndIncrement()
            );
            buildProducedBeanDefinition(producedBeanDefinitionWriter, producedType, producingElement, producingElement.getAnnotationMetadata());
        } else {
            AnnotationMetadata producedTypeAnnotationMetadata = createProducedTypeAnnotationMetadata(producedType, producingElement);
            producedType = producedType.withAnnotationMetadata(producedTypeAnnotationMetadata);
            AnnotationMetadata producingElementAnnotationMetadata = createProducingElementAnnotationMetadata(producedTypeAnnotationMetadata);
            producingElement = producingElement.withAnnotationMetadata(producingElementAnnotationMetadata);

            BeanDefinitionWriter producedBeanDefinitionWriter = new BeanDefinitionWriter(
                producingElement,
                OriginatingElements.of(producingElement),
                visitorContext,
                factoryMethodIndex.getAndIncrement()
            );

//            producingElement = producingElement.withAnnotationMetadata(producedTypeAnnotationMetadata);
            buildProducedBeanDefinition(producedBeanDefinitionWriter, producedType, producingElement, producedType.getAnnotationMetadata());

            if (producingElement instanceof MethodElement) {
                MethodElement methodElement = (MethodElement) producingElement;
                if (isAopProxy && visitAopMethod(visitor, methodElement)) {
                    return;
                }
                visitExecutableMethod(visitor, methodElement);
            }
        }
    }

    private AnnotationMetadata createProducingElementAnnotationMetadata(AnnotationMetadata producedAnnotationMetadata) {
        MutableAnnotationMetadata factoryClassAnnotationMetadata = MutableAnnotationMetadata.of(classElement.getAnnotationMetadata());

        boolean modifiedFactoryClassAnnotationMetadata = false;
        if (classElement.hasStereotype(AnnotationUtil.QUALIFIER)) {
            // Don't propagate any qualifiers to the factories
            for (String qualifier : classElement.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER)) {
                if (!producedAnnotationMetadata.hasStereotype(qualifier)) {
                    factoryClassAnnotationMetadata.removeAnnotation(qualifier);
                    modifiedFactoryClassAnnotationMetadata = true;
                }
            }
        }
        if (modifiedFactoryClassAnnotationMetadata) {
            return new AnnotationMetadataHierarchy(factoryClassAnnotationMetadata, producedAnnotationMetadata);
        }
        return new AnnotationMetadataHierarchy(classElement, producedAnnotationMetadata);
    }

    private AnnotationMetadata createProducedTypeAnnotationMetadata(ClassElement producedType, MemberElement producingElement) {
        // Original logic is to combine producing element annotation metadata (method or field) with the produced type's annotation metadata
        MutableAnnotationMetadata producedAnnotationMetadata = new AnnotationMetadataHierarchy(
            producedType.getAnnotationMetadata(),
            getElementAnnotationMetadata(producingElement)
        ).merge();
        AnnotationMetadata producedTypeAnnotationMetadata = producedType.getAnnotationMetadata();
        AnnotationMetadata elementAnnotationMetadata = getElementAnnotationMetadata(producingElement);

        cleanupScopeAndQualifierAnnotations(producedAnnotationMetadata, producedTypeAnnotationMetadata, elementAnnotationMetadata);
        return producedAnnotationMetadata;
    }

    private void buildProducedBeanDefinition(BeanDefinitionWriter producedBeanDefinitionWriter,
                                             ClassElement producedType,
                                             MemberElement producingElement,
                                             AnnotationMetadata producedAnnotationMetadata) {

        visitAnnotationMetadata(producedBeanDefinitionWriter, producedAnnotationMetadata);
        producedBeanDefinitionWriter.visitTypeArguments(producedType.getAllTypeArguments());

        beanDefinitionWriters.add(producedBeanDefinitionWriter);

        if (producedType.hasStereotype(EachProperty.class)) {
            producedType.annotate(ConfigurationReader.class, builder -> builder.member(ConfigurationReader.PREFIX, ConfigurationUtils.getRequiredTypePath(producedType)));
        }

        if (producingElement instanceof MethodElement) {
            producedBeanDefinitionWriter.visitBeanFactoryMethod(classElement, (MethodElement) producingElement);
        } else {
            producedBeanDefinitionWriter.visitBeanFactoryField(classElement, (FieldElement) producingElement);
        }

        if (hasAroundStereotype(producedAnnotationMetadata) && !producedType.isAssignable("io.micronaut.aop.Interceptor")) {
            if (producedType.isArray()) {
                throw new ProcessingException(producingElement, "Cannot apply AOP advice to arrays");
            }
            if (producedType.isPrimitive()) {
                throw new ProcessingException(producingElement, "Cannot apply AOP advice to primitive beans");
            }
            if (producedType.isFinal()) {
                throw new ProcessingException(producingElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + producedType.getName());
            }

            MethodElement constructorElement = producedType.getPrimaryConstructor().orElse(null);
            if (!producedType.isInterface() && constructorElement != null && constructorElement.getParameters().length > 0) {
                final String proxyTargetMode = producedAnnotationMetadata.stringValue(AnnotationUtil.ANN_AROUND, "proxyTargetMode").orElse("ERROR");
                switch (proxyTargetMode) {
                    case "ALLOW":
                        allowProxyConstruction(constructorElement);
                        break;
                    case "WARN":
                        allowProxyConstruction(constructorElement);
                        visitorContext.warn("The produced type of a @Factory method has constructor arguments and is proxied. " +
                            "This can lead to unexpected behaviour. See the javadoc for Around.ProxyTargetConstructorMode for more information: " + producingElement.getName(), producingElement);
                        break;
                    case "ERROR":
                    default:
                        throw new ProcessingException(producingElement, "The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor. " +
                            "Proxying types with constructor arguments can lead to unexpected behaviour. See the javadoc for for Around.ProxyTargetConstructorMode for more information and possible solutions: " + producingElement.getName());
                }
            }

            BeanDefinitionVisitor aopProxyWriter = aopHelper.createAroundAopProxyWriter(producedBeanDefinitionWriter, producedAnnotationMetadata, visitorContext, true);
            if (constructorElement != null) {
                aopProxyWriter.visitBeanDefinitionConstructor(constructorElement, constructorElement.isReflectionRequired(), visitorContext);
            } else {
                aopProxyWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, visitorContext);
            }
            aopProxyWriter.visitSuperBeanDefinitionFactory(producedBeanDefinitionWriter.getBeanDefinitionName());
            aopProxyWriter.visitTypeArguments(producedType.getAllTypeArguments());
            beanDefinitionWriters.add(aopProxyWriter);

            producedType.getEnclosedElements(ElementQuery.ALL_METHODS)
                .stream()
                .filter(m -> m.isPublic() && !m.isFinal() && !m.isStatic())
                .forEach(methodElement -> aopHelper.visitAroundMethod(aopProxyWriter, methodElement.getDeclaringType(), methodElement));

        } else if (producedAnnotationMetadata.hasStereotype(Executable.class)) {
            if (producedType.isArray()) {
                throw new ProcessingException(producingElement, "Using '@Executable' is not allowed on array type beans");
            }
            if (producedType.isPrimitive()) {
                throw new ProcessingException(producingElement, "Using '@Executable' is not allowed on primitive type beans");
            }
            producedType.getEnclosedElements(ElementQuery.ALL_METHODS)
                .forEach(methodElement -> producedBeanDefinitionWriter.visitExecutableMethod(methodElement.getDeclaringType(), methodElement, visitorContext));
        }

        if (producedAnnotationMetadata.isPresent(Bean.class, "preDestroy")) {
            if (producedType.isArray()) {
                throw new ProcessingException(producingElement, "Using 'preDestroy' is not allowed on array type beans");
            }
            if (producedType.isPrimitive()) {
                throw new ProcessingException(producingElement, "Using 'preDestroy' is not allowed on primitive type beans");
            }

            producedType.getValue(Bean.class, "preDestroy", String.class).ifPresent(destroyMethodName -> {
                if (StringUtils.isNotEmpty(destroyMethodName)) {
                    final Optional<MethodElement> destroyMethod = producedType.getEnclosedElement(ElementQuery.ALL_METHODS.onlyAccessible(classElement)
                        .onlyInstance()
                        .named(destroyMethodName)
                        .filter((e) -> !e.hasParameters()));
                    if (destroyMethod.isPresent()) {
                        MethodElement destroyMethodElement = destroyMethod.get();
                        producedBeanDefinitionWriter.visitPreDestroyMethod(producedType, destroyMethodElement, false, visitorContext);
                    } else {
                        throw new ProcessingException(producingElement, "@Bean method defines a preDestroy method that does not exist or is not public: " + destroyMethodName);
                    }
                }
            });
        }
    }

    private void allowProxyConstruction(MethodElement constructor) {
        final ParameterElement[] parameters = constructor.getParameters();
        for (ParameterElement parameter : parameters) {
            if (parameter.isPrimitive() && !parameter.isArray()) {
                final String name = parameter.getType().getName();
                if ("boolean".equals(name)) {
                    parameter.annotate(Value.class, (builder) -> builder.value(false));
                } else {
                    parameter.annotate(Value.class, (builder) -> builder.value(0));
                }
            } else {
                // allow null
                parameter.annotate(AnnotationUtil.NULLABLE);
                parameter.removeAnnotation(AnnotationUtil.NON_NULL);
            }
        }
    }

    private void cleanupScopeAndQualifierAnnotations(MutableAnnotationMetadata producedAnnotationMetadata, AnnotationMetadata producedTypeAnnotationMetadata, AnnotationMetadata producingElementAnnotationMetadata) {
        // If the producing element defines a scope don't inherit it from the type
        if (producingElementAnnotationMetadata.hasStereotype(AnnotationUtil.SCOPE) || producingElementAnnotationMetadata.hasStereotype(AnnotationUtil.QUALIFIER)) {
            // The producing element is declaring the scope then we should remove the scope defined by the type
            for (String scope : producedTypeAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)) {
                if (!producingElementAnnotationMetadata.hasStereotype(scope)) {
                    producedAnnotationMetadata.removeAnnotation(scope);
                }
            }
            // Remove any qualifier coming from the type
            for (String qualifier : producedTypeAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER)) {
                if (!producingElementAnnotationMetadata.hasStereotype(qualifier)) {
                    producedAnnotationMetadata.removeAnnotation(qualifier);
                }
            }
        }
    }

}
