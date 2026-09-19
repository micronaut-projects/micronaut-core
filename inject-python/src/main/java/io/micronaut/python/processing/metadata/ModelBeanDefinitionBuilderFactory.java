/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing.metadata;

import io.micronaut.context.beans.definition.ConstructorDefinition;
import io.micronaut.context.beans.definition.FieldDefinition;
import io.micronaut.context.beans.definition.MethodDefinition;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilder;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.processing.definition.ElementProxyBuilder;
import io.micronaut.inject.utils.BeanInjectionUtils;
import org.jspecify.annotations.Nullable;

/**
 * The factory the shared bean definition analysis drives when the model backends are selected: it answers with a
 * recording builder for a constructor-instantiated bean, and with a diagnostic for the bean kinds the model cannot
 * describe yet.
 *
 * @since 5.3.0
 */
@Internal
final class ModelBeanDefinitionBuilderFactory implements ElementBeanDefinitionBuilderFactory<BeanDefinitionModel> {

    private final PythonMetadataModelBuilder modelBuilder;
    private final ClassElement classElement;

    ModelBeanDefinitionBuilderFactory(PythonMetadataModelBuilder modelBuilder, ClassElement classElement) {
        this.modelBuilder = modelBuilder;
        this.classElement = classElement;
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> ofType(ClassElement classElement) {
        MethodElement constructorElement = BeanInjectionUtils.findBeanConstructor(classElement).orElse(null);
        if (constructorElement == null) {
            constructorElement = MethodElement.of(classElement, AnnotationMetadata.EMPTY_METADATA, classElement, classElement, "<init>");
        }
        return constructor(BeanInjectionUtils.createConstructorDefinition(constructorElement, modelBuilder.visitorContext()));
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> factoryMethod(MethodElement methodElement) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a factory method (" + methodElement.getName() + ")");
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> factoryField(FieldElement fieldElement) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a factory field (" + fieldElement.getName() + ")");
    }

    @Override
    public ElementProxyBuilder<BeanDefinitionModel> aroundProxy(ClassElement classElement, AnnotationMetadata annotationMetadata,
                                                                ElementBeanDefinitionBuilder<BeanDefinitionModel> targetBeanDefinitionBuilder) {
        throw PythonMetadataModelBuilder.unsupported(this.classElement, "an AOP around proxy");
    }

    @Override
    public ElementProxyBuilder<BeanDefinitionModel> introductionProxy(ClassElement target) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "an AOP introduction proxy");
    }

    @Override
    public ElementProxyBuilder<BeanDefinitionModel> introductionProxy(String proxyName, AnnotationMetadata proxyAnnotationMetadata) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "an AOP introduction proxy (" + proxyName + ")");
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> constructor(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition) {
        return new ModelBeanDefinitionBuilder(modelBuilder, classElement, constructorDefinition);
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> constructor(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                                                         @Nullable String beanDefinitionName,
                                                                         @Nullable AnnotationMetadata annotationMetadata) {
        if (beanDefinitionName != null || annotationMetadata != null) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "an associated bean with its own name or metadata");
        }
        return constructor(constructorDefinition);
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> factoryMethod(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a factory method (" + methodDefinition.methodElement().getName() + ")");
    }

    @Override
    public ElementBeanDefinitionBuilder<BeanDefinitionModel> factoryField(FieldDefinition<ClassElement, FieldElement> fieldDefinition) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a factory field (" + fieldDefinition.fieldElement().getName() + ")");
    }
}
