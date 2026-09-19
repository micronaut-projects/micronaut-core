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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.beans.definition.BeanDefinitionBuilder;
import io.micronaut.context.beans.definition.BeanDefinitionInjectionPoint;
import io.micronaut.context.beans.definition.ConstructorDefinition;
import io.micronaut.context.beans.definition.FieldDefinition;
import io.micronaut.context.beans.definition.MethodDefinition;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ConstructorModel;
import io.micronaut.context.python.runtime.model.InjectedMethodModel;
import io.micronaut.context.python.runtime.model.InjectionPointModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilder;
import io.micronaut.inject.writer.OriginatingElements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records what the shared bean definition analysis adds to a definition, as the model. Everything the model backends
 * cannot generate is reported as it is added, against the class, so that the compilation fails with the construct
 * named.
 *
 * @since 5.3.0
 */
@Internal
final class ModelBeanDefinitionBuilder implements ElementBeanDefinitionBuilder<BeanDefinitionModel> {

    // The writer's list: framework and JDK marker interfaces a bean is never looked up by
    private static final Set<String> IGNORED_EXPOSED_INTERFACES = Set.of(
        "java.lang.AutoCloseable", "io.micronaut.context.LifeCycle", "io.micronaut.core.order.Ordered", "java.io.Closeable",
        "io.micronaut.core.naming.Named", "io.micronaut.core.naming.Described", "java.lang.Record", "java.lang.Enum",
        "io.micronaut.core.util.Toggleable", "java.lang.Iterable", "java.io.Serializable");

    private final PythonMetadataModelBuilder modelBuilder;
    private final ClassElement classElement;
    private final ConstructorDefinition<ClassElement, MethodElement> constructorDefinition;
    private final OriginatingElements originatingElements;
    private final List<InjectedMethodModel> methods = new ArrayList<>();

    ModelBeanDefinitionBuilder(PythonMetadataModelBuilder modelBuilder, ClassElement classElement,
                               ConstructorDefinition<ClassElement, MethodElement> constructorDefinition) {
        this.modelBuilder = modelBuilder;
        this.classElement = classElement;
        this.constructorDefinition = constructorDefinition;
        this.originatingElements = OriginatingElements.of(classElement);
        if (constructorDefinition.requiresReflection()) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "a constructor that requires reflection");
        }
        if (constructorDefinition.annotationMetadata().hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)
            || classElement.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "an intercepted bean");
        }
        checkInjectScope(constructorDefinition.constructorElement());
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addExecutableMethod(MethodElement methodElement, boolean requiresReflection) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "an executable method (" + methodElement.getName() + ")");
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addMethodInjection(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        return add(methodDefinition, false, false);
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addFieldInjection(FieldDefinition<ClassElement, FieldElement> fieldDefinition) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a field injection point (" + fieldDefinition.fieldElement().getName() + ")");
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addPostConstruct(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        return add(methodDefinition, true, false);
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addPreDestroy(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        return add(methodDefinition, false, true);
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addFieldConfigurationBuilder(FieldElement fieldElement, AnnotationMetadata annotationMetadata, List<MethodDefinition<ClassElement, MethodElement>> builderMethods) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a configuration builder (" + fieldElement.getName() + ")");
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addMethodConfigurationBuilder(MethodElement methodElement, AnnotationMetadata annotationMetadata, List<MethodDefinition<ClassElement, MethodElement>> builderMethods) {
        throw PythonMetadataModelBuilder.unsupported(classElement, "a configuration builder (" + methodElement.getName() + ")");
    }

    private ModelBeanDefinitionBuilder add(MethodDefinition<ClassElement, MethodElement> methodDefinition, boolean postConstruct, boolean preDestroy) {
        MethodElement method = methodDefinition.methodElement();
        if (methodDefinition.requiresReflection()) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the method " + method.getName() + ", which requires reflection");
        }
        if (methodDefinition.isSetter() || methodDefinition.isOptional() || methodDefinition.booleanInjectionPoint() != null) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the property injection method " + method.getName());
        }
        if (method.getSuspendParameters().length != method.getParameters().length) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the suspending method " + method.getName());
        }
        checkInjectScope(method);
        List<InjectionPointModel> injectionPoints = new ArrayList<>();
        ParameterElement[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            injectionPoints.add(injectionPoint(methodDefinition.injectionPoints().get(i), parameters[i]));
        }
        methods.add(new InjectedMethodModel(modelBuilder.method(classElement, method),
            modelBuilder.annotationMetadata(classElement, methodDefinition.annotationMetadata()),
            List.copyOf(injectionPoints), methodDefinition.isOptional(), methodDefinition.isSetter(), postConstruct, preDestroy,
            InjectionPoint.isInjectionRequired(methodDefinition.annotationMetadata())));
        return this;
    }

    private InjectionPointModel injectionPoint(BeanDefinitionInjectionPoint<ClassElement> point, ParameterElement parameter) {
        ArgumentModel argument = modelBuilder.argument(classElement, parameter.getName(), parameter.getGenericType(), parameter.getAnnotationMetadata());
        ClassElement type = point.type();
        if (type.isAssignable(BeanResolutionContext.class)) {
            return new InjectionPointModel(InjectionPointModel.Kind.RESOLUTION_CONTEXT, argument, null, null, null, null);
        }
        if (type.isAssignable(BeanContext.class)) {
            return new InjectionPointModel(InjectionPointModel.Kind.BEAN_CONTEXT, argument, null, null, null, null);
        }
        if (type.getName().equals(ConversionService.class.getName()) || type.isAssignable("io.micronaut.context.ConfigurationPath")) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "injecting " + type.getName() + " into " + parameter.getName());
        }
        return switch (point) {
            case BeanDefinitionInjectionPoint.BeanInjectionPoint<ClassElement> ignored ->
                new InjectionPointModel(InjectionPointModel.Kind.BEAN, argument, null, null, null, null);
            case BeanDefinitionInjectionPoint.BeansInjectionPoint<ClassElement> beans ->
                new InjectionPointModel(InjectionPointModel.Kind.BEANS, argument, PythonMetadataModelBuilder.typeName(beans.beanType()), null, null, null);
            case BeanDefinitionInjectionPoint.OptionalBeanInjectionPoint<ClassElement> optional ->
                new InjectionPointModel(InjectionPointModel.Kind.OPTIONAL_BEAN, argument, PythonMetadataModelBuilder.typeName(optional.beanType()), null, null, null);
            case BeanDefinitionInjectionPoint.ValueInjectionPoint<ClassElement> value -> {
                if (value.hasExpression()) {
                    throw PythonMetadataModelBuilder.unsupported(classElement, "the evaluated expression injected into " + parameter.getName());
                }
                yield new InjectionPointModel(InjectionPointModel.Kind.VALUE, argument, null, null, null, value.value());
            }
            case BeanDefinitionInjectionPoint.PropertyInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the configuration property injected into " + parameter.getName());
            case BeanDefinitionInjectionPoint.ParameterInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the @Parameter " + parameter.getName());
            case BeanDefinitionInjectionPoint.MapOfBeansInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the map of beans injected into " + parameter.getName());
            case BeanDefinitionInjectionPoint.StreamOfBeansInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the stream of beans injected into " + parameter.getName());
            case BeanDefinitionInjectionPoint.BeanRegistrationInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the bean registration injected into " + parameter.getName());
            case BeanDefinitionInjectionPoint.BeanRegistrationsInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the bean registrations injected into " + parameter.getName());
        };
    }

    private void checkInjectScope(MethodElement method) {
        if (method.hasDeclaredAnnotation(InjectScope.class)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "@InjectScope on " + method.getName());
        }
        for (ParameterElement parameter : method.getParameters()) {
            if (parameter.hasDeclaredAnnotation(InjectScope.class)) {
                throw PythonMetadataModelBuilder.unsupported(classElement, "@InjectScope on " + parameter.getName());
            }
        }
    }

    @Override
    public List<BeanDefinitionModel> build() {
        MethodElement constructor = constructorDefinition.constructorElement();
        List<ArgumentModel> parameters = new ArrayList<>();
        List<InjectionPointModel> injectionPoints = new ArrayList<>();
        ParameterElement[] constructorParameters = constructor.getParameters();
        for (int i = 0; i < constructorParameters.length; i++) {
            parameters.add(modelBuilder.argument(classElement, constructorParameters[i].getName(), constructorParameters[i].getGenericType(),
                constructorParameters[i].getAnnotationMetadata()));
            injectionPoints.add(injectionPoint(constructorDefinition.injectionPoints().get(i), constructorParameters[i]));
        }
        ConstructorModel constructorModel = new ConstructorModel(modelBuilder.annotationMetadata(classElement, constructorDefinition.annotationMetadata()),
            List.copyOf(parameters), List.copyOf(injectionPoints));
        Map<String, List<ArgumentModel>> typeArguments = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ClassElement>> entry : classElement.getAllTypeArguments().entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<ArgumentModel> arguments = new ArrayList<>();
            for (Map.Entry<String, ClassElement> argument : entry.getValue().entrySet()) {
                arguments.add(modelBuilder.argument(classElement, argument.getKey(), argument.getValue(), argument.getValue().getAnnotationMetadata()));
            }
            typeArguments.put(entry.getKey(), List.copyOf(arguments));
        }
        String packageName = classElement.getPackageName();
        String definitionName = packageName + ".$" + classElement.getName().substring(packageName.isEmpty() ? 0 : packageName.length() + 1).replace('.', '$') + "$Definition";
        String[] declaredExposedTypes = classElement.getAnnotationMetadata().stringValues(Bean.class.getName(), "typed");
        List<String> exposedTypes;
        if (declaredExposedTypes.length != 0) {
            exposedTypes = List.of(declaredExposedTypes);
        } else {
            Set<String> collected = new LinkedHashSet<>();
            collectExposedTypes(collected, classElement, true, packageName);
            exposedTypes = List.copyOf(collected);
        }
        return List.of(new BeanDefinitionModel(definitionName, constructorModel, List.copyOf(methods),
            PythonMetadataModelBuilder.precalculatedInfo(classElement), exposedTypes, declaredExposedTypes.length != 0, typeArguments));
    }

    /**
     * The exposed types of a bean, as the writer collects them: the bean type, and the super types and interfaces
     * the definition can see, minus the framework's own marker interfaces.
     */
    private static void collectExposedTypes(Set<String> exposedTypeNames, ClassElement element, boolean rootType, String definitionPackage) {
        if (rootType || isAccessibleFrom(element, definitionPackage)) {
            String className = PythonMetadataModelBuilder.typeName(element);
            if (!exposedTypeNames.add(className) || IGNORED_EXPOSED_INTERFACES.contains(className)) {
                return;
            }
        }
        element.getSuperType().ifPresent(superType -> collectExposedTypes(exposedTypeNames, superType, false, definitionPackage));
        element.getInterfaces().forEach(iface -> collectExposedTypes(exposedTypeNames, iface, false, definitionPackage));
    }

    private static boolean isAccessibleFrom(ClassElement element, String definitionPackage) {
        if (element.isArray()) {
            return isAccessibleFrom(element.fromArray(), definitionPackage);
        }
        return element.isPublic() || (!element.isPrivate() && element.getPackageName().equals(definitionPackage));
    }

    @Override
    public Element[] getOriginatingElements() {
        return originatingElements.getOriginatingElements();
    }

    @Override
    public void addOriginatingElement(Element element) {
        originatingElements.addOriginatingElement(element);
    }
}
