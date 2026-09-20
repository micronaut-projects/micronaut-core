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
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.beans.definition.BeanDefinitionBuilder;
import io.micronaut.context.beans.definition.BeanDefinitionInjectionPoint;
import io.micronaut.context.beans.definition.ConstructorDefinition;
import io.micronaut.context.beans.definition.FieldDefinition;
import io.micronaut.context.beans.definition.MethodDefinition;
import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ConstructorModel;
import io.micronaut.context.python.runtime.model.ExecutableMethodModel;
import io.micronaut.context.python.runtime.model.FactoryMethodModel;
import io.micronaut.context.python.runtime.model.InjectedMethodModel;
import io.micronaut.context.python.runtime.model.InjectionPointModel;
import io.micronaut.context.python.runtime.model.MethodModel;
import io.micronaut.context.python.runtime.model.PropertyGuardModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilder;
import io.micronaut.inject.writer.OriginatingElements;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ClassElement beanTypeElement;
    private final @Nullable ConstructorDefinition<ClassElement, MethodElement> constructorDefinition;
    private final @Nullable MethodDefinition<ClassElement, MethodElement> factoryMethodDefinition;
    private final String definitionName;
    private final OriginatingElements originatingElements;
    private final List<InjectedMethodModel> methods = new ArrayList<>();
    private final List<ExecutableMethodModel> executableMethods = new ArrayList<>();
    private final Set<String> executableKeys = new LinkedHashSet<>();
    private boolean validated;
    private boolean postConstructValidation;

    /**
     * A definition instantiated by the constructor of the class.
     */
    ModelBeanDefinitionBuilder(PythonMetadataModelBuilder modelBuilder, ClassElement classElement,
                               ConstructorDefinition<ClassElement, MethodElement> constructorDefinition) {
        this.modelBuilder = modelBuilder;
        this.classElement = classElement;
        this.beanTypeElement = classElement;
        this.constructorDefinition = constructorDefinition;
        this.factoryMethodDefinition = null;
        this.definitionName = definitionPrefix(classElement) + "$Definition";
        this.originatingElements = OriginatingElements.of(classElement);
        autoApplyNamedToBeanProducingElement(classElement);
        checkNotIterable(classElement.getAnnotationMetadata());
        if (constructorDefinition.requiresReflection()) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "a constructor that requires reflection");
        }
        if (constructorDefinition.annotationMetadata().hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)
            || classElement.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "an intercepted bean");
        }
        checkInjectScope(constructorDefinition.constructorElement());
    }

    /**
     * A definition instantiated by invoking a factory method of the class on the factory bean.
     */
    ModelBeanDefinitionBuilder(PythonMetadataModelBuilder modelBuilder, ClassElement classElement,
                               MethodDefinition<ClassElement, MethodElement> factoryMethodDefinition, int uniqueIdentifier) {
        this.modelBuilder = modelBuilder;
        this.classElement = classElement;
        this.constructorDefinition = null;
        this.factoryMethodDefinition = factoryMethodDefinition;
        MethodElement method = factoryMethodDefinition.methodElement();
        this.beanTypeElement = method.getGenericReturnType();
        // The writer's name: $Factory$Method<n>$Definition
        this.definitionName = definitionPrefix(method.getOwningType()) + "$" + NameUtils.capitalize(method.getName()) + uniqueIdentifier + "$Definition";
        this.originatingElements = OriginatingElements.of(method);
        autoApplyNamedToBeanProducingElement(method);
        checkNotIterable(method.getAnnotationMetadata());
        String construct = "the factory method " + method.getName();
        if (factoryMethodDefinition.requiresReflection() || method.isReflectionRequired(classElement)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, construct + ", which requires reflection");
        }
        if (method.getSuspendParameters().length != method.getParameters().length) {
            throw PythonMetadataModelBuilder.unsupported(classElement, construct + ", which suspends");
        }
        if (beanTypeElement.isPrimitive() || beanTypeElement.isArray() || beanTypeElement.isContainerType()) {
            throw PythonMetadataModelBuilder.unsupported(classElement, construct + ", which produces a primitive, array or container type");
        }
        if (beanTypeElement.isTypeVariable() || beanTypeElement.isGenericPlaceholder()) {
            throw PythonMetadataModelBuilder.unsupported(classElement, construct + ", which produces a type variable");
        }
        if (method.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS) || beanTypeElement.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, construct + ", which produces an intercepted bean");
        }
        if (classElement.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "an intercepted factory");
        }
        checkInjectScope(method);
    }

    /**
     * An iterable bean produces one definition per configuration entry or per bean of another type, resolved through
     * the configuration path of the resolution context. The model does not describe that yet.
     */
    private void checkNotIterable(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasDeclaredStereotype(EachProperty.class)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "an iterable bean (@EachProperty)");
        }
        if (annotationMetadata.hasDeclaredStereotype(EachBean.class)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "an iterable bean (@EachBean)");
        }
    }

    private static String definitionPrefix(ClassElement classElement) {
        String packageName = classElement.getPackageName();
        return packageName + ".$" + classElement.getName().substring(packageName.isEmpty() ? 0 : packageName.length() + 1).replace('.', '$');
    }

    @Override
    public BeanDefinitionBuilder<ClassElement, MethodElement, FieldElement, List<BeanDefinitionModel>> addExecutableMethod(MethodElement methodElement, boolean requiresReflection) {
        if (requiresReflection || methodElement.isReflectionRequired(beanTypeElement)) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the executable method " + methodElement.getName() + ", which requires reflection");
        }
        if (methodElement.getSuspendParameters().length != methodElement.getParameters().length) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the suspending executable method " + methodElement.getName());
        }
        MethodModel method = modelBuilder.method(classElement, beanTypeElement, methodElement);
        String key = method.declaringType() + "." + method.name() + method.parameters().stream().map(ArgumentModel::typeName).toList();
        if (!executableKeys.add(key)) {
            return this;
        }
        AnnotationMetadata annotationMetadata = methodElement.getTargetAnnotationMetadata();
        boolean hierarchy = false;
        if (annotationMetadata instanceof AnnotationMetadataHierarchy h) {
            if (h.size() != 2) {
                throw PythonMetadataModelBuilder.unsupported(classElement, "the annotation metadata hierarchy of " + methodElement.getName());
            }
            if (h.getRootMetadata().equals(methodElement.getOwningType())) {
                hierarchy = true;
                annotationMetadata = h.getDeclaredMetadata();
            }
        }
        ClassElement returnType = methodElement.getGenericReturnType();
        executableMethods.add(new ExecutableMethodModel(method,
            modelBuilder.argument(classElement, methodElement.getName(), returnType, returnType.getTypeAnnotationMetadata().getAnnotationMetadata()),
            modelBuilder.annotationMetadata(classElement, annotationMetadata), hierarchy,
            methodElement.isTrue(Executable.class, Executable.MEMBER_PROCESS_ON_STARTUP), methodElement.isAbstract()));
        return this;
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
        if (methodDefinition.booleanInjectionPoint() != null) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the configuration builder property " + method.getName());
        }
        if (methodDefinition.isSetter() && method.getParameters().length != 1) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the property setter " + method.getName() + ", which does not take exactly one value");
        }
        if (method.getSuspendParameters().length != method.getParameters().length) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the suspending method " + method.getName());
        }
        checkInjectScope(method);
        autoApplyNamedToParameters(method);
        applyValidation(methodDefinition.annotationMetadata(), methodDefinition.injectionPoints());
        List<InjectionPointModel> injectionPoints = new ArrayList<>();
        ParameterElement[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            InjectionPointModel point = injectionPoint(methodDefinition.injectionPoints().get(i), parameters[i]);
            if (methodDefinition.isSetter()) {
                point = setterPoint(method, point, parameters[i]);
            }
            injectionPoints.add(point);
        }
        methods.add(new InjectedMethodModel(modelBuilder.method(classElement, beanTypeElement, method),
            modelBuilder.annotationMetadata(classElement, methodDefinition.annotationMetadata()),
            List.copyOf(injectionPoints), methodDefinition.isOptional(), methodDefinition.isSetter(), postConstruct, preDestroy,
            InjectionPoint.isInjectionRequired(methodDefinition.annotationMetadata()), guard(methodDefinition)));
        return this;
    }

    /**
     * The writer's validation decision for a member: a member that declares validation and has a validated injection
     * point makes the definition validated, and validates the whole bean once constructed when it binds configuration
     * or when a bean dependency (which may be injected as null) carries the constraint.
     */
    private void applyValidation(AnnotationMetadata annotationMetadata, List<BeanDefinitionInjectionPoint<ClassElement>> injectionPoints) {
        if (!annotationMetadata.hasDeclaredAnnotation(RequiresValidation.class)) {
            return;
        }
        List<BeanDefinitionInjectionPoint<ClassElement>> validatedPoints = injectionPoints.stream().filter(point -> {
            if (!point.annotationMetadata().hasDeclaredAnnotation(RequiresValidation.class)) {
                return false;
            }
            return !(point instanceof BeanDefinitionInjectionPoint.BeanInjectionPoint<ClassElement>) || point.type().isNullable();
        }).toList();
        if (validatedPoints.isEmpty()) {
            return;
        }
        validated = true;
        boolean configurationProperties = classElement.getAnnotationMetadata().hasStereotype(ConfigurationReader.class);
        if (configurationProperties || validatedPoints.stream().anyMatch(point ->
            point instanceof BeanDefinitionInjectionPoint.BeanInjectionPoint<ClassElement>
                || point instanceof BeanDefinitionInjectionPoint.OptionalBeanInjectionPoint<ClassElement>
                || !isValueType(point.annotationMetadata()))) {
            postConstructValidation = true;
        }
    }

    private static boolean isValueType(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredStereotype(Value.class) || annotationMetadata.hasDeclaredStereotype(Property.class);
    }

    /**
     * A setter resolves its value the same way an ordinary method argument does, with one addition: the writer also
     * reads the command line property of a configuration that declares a cli prefix.
     */
    private InjectionPointModel setterPoint(MethodElement method, InjectionPointModel point, ParameterElement parameter) {
        return switch (point.kind()) {
            case PROPERTY -> new InjectionPointModel(point.kind(), point.argument(), point.beanTypeName(), point.propertyName(),
                point.propertyPath(), point.value(), cliProperty(parameter.getName()));
            case VALUE -> point;
            default -> throw PythonMetadataModelBuilder.unsupported(classElement,
                "the property setter " + method.getName() + ", which injects " + point.kind().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        };
    }

    /**
     * The property an optional injection is guarded by, as the writer computes the check: the {@code @Property} name,
     * asked for as a prefix when the value holds several values, plus the command line property when one applies.
     */
    private @Nullable PropertyGuardModel guard(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        if (!methodDefinition.isOptional()) {
            return null;
        }
        MethodElement method = methodDefinition.methodElement();
        String property = methodDefinition.annotationMetadata().stringValue(Property.class, "name").orElse(null);
        if (property == null) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "the optional injection method " + method.getName() + ", which has no @Property name");
        }
        ClassElement type = method.getParameters()[0].getGenericType();
        boolean multiValue = type.isAssignable(Map.class) || type.isAssignable(Collection.class) || type.hasStereotype(ConfigurationReader.class);
        return new PropertyGuardModel(property, multiValue, cliProperty(""));
    }

    /**
     * The command line property of a configuration property name, or null when the class declares no cli prefix.
     */
    private @Nullable String cliProperty(String propertyName) {
        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        if (!annotationMetadata.hasStereotype(ConfigurationReader.class) || !annotationMetadata.isPresent(ConfigurationProperties.class, "cliPrefix")) {
            return null;
        }
        return annotationMetadata.stringValue(ConfigurationProperties.class, "cliPrefix").map(prefix -> prefix + propertyName).orElse(null);
    }

    private InjectionPointModel injectionPoint(BeanDefinitionInjectionPoint<ClassElement> point, ParameterElement parameter) {
        ArgumentModel argument = modelBuilder.argument(classElement, parameter.getName(), parameter.getGenericType(), parameter.getAnnotationMetadata());
        ClassElement type = point.type();
        if (type.isAssignable(BeanResolutionContext.class)) {
            return new InjectionPointModel(InjectionPointModel.Kind.RESOLUTION_CONTEXT, argument, null, null, null, null, null);
        }
        if (type.isAssignable(BeanContext.class)) {
            return new InjectionPointModel(InjectionPointModel.Kind.BEAN_CONTEXT, argument, null, null, null, null, null);
        }
        if (type.getName().equals(ConversionService.class.getName()) || type.isAssignable("io.micronaut.context.ConfigurationPath")) {
            throw PythonMetadataModelBuilder.unsupported(classElement, "injecting " + type.getName() + " into " + parameter.getName());
        }
        return switch (point) {
            case BeanDefinitionInjectionPoint.BeanInjectionPoint<ClassElement> ignored ->
                new InjectionPointModel(InjectionPointModel.Kind.BEAN, argument, null, null, null, null, null);
            case BeanDefinitionInjectionPoint.BeansInjectionPoint<ClassElement> beans ->
                new InjectionPointModel(InjectionPointModel.Kind.BEANS, argument, PythonMetadataModelBuilder.typeName(beans.beanType()), null, null, null, null);
            case BeanDefinitionInjectionPoint.OptionalBeanInjectionPoint<ClassElement> optional ->
                new InjectionPointModel(InjectionPointModel.Kind.OPTIONAL_BEAN, argument, PythonMetadataModelBuilder.typeName(optional.beanType()), null, null, null, null);
            case BeanDefinitionInjectionPoint.ValueInjectionPoint<ClassElement> value -> {
                if (value.hasExpression()) {
                    throw PythonMetadataModelBuilder.unsupported(classElement, "the evaluated expression injected into " + parameter.getName());
                }
                yield new InjectionPointModel(InjectionPointModel.Kind.VALUE, argument, null, null, null, value.value(), null);
            }
            case BeanDefinitionInjectionPoint.PropertyInjectionPoint<ClassElement> property ->
                new InjectionPointModel(InjectionPointModel.Kind.PROPERTY, argument, null, property.propertyName(), property.propertyPath(), null, null);
            case BeanDefinitionInjectionPoint.ParameterInjectionPoint<ClassElement> ignored ->
                throw PythonMetadataModelBuilder.unsupported(classElement, "the @Parameter " + parameter.getName());
            case BeanDefinitionInjectionPoint.MapOfBeansInjectionPoint<ClassElement> map ->
                new InjectionPointModel(InjectionPointModel.Kind.MAP_OF_BEANS, argument, PythonMetadataModelBuilder.typeName(map.beanType()), null, null, null, null);
            case BeanDefinitionInjectionPoint.StreamOfBeansInjectionPoint<ClassElement> stream ->
                new InjectionPointModel(InjectionPointModel.Kind.STREAM_OF_BEANS, argument, PythonMetadataModelBuilder.typeName(stream.beanType()), null, null, null, null);
            case BeanDefinitionInjectionPoint.BeanRegistrationInjectionPoint<ClassElement> registration ->
                new InjectionPointModel(InjectionPointModel.Kind.BEAN_REGISTRATION, argument, PythonMetadataModelBuilder.typeName(registration.beanType()), null, null, null, null);
            case BeanDefinitionInjectionPoint.BeanRegistrationsInjectionPoint<ClassElement> registrations ->
                new InjectionPointModel(InjectionPointModel.Kind.BEAN_REGISTRATIONS, argument, PythonMetadataModelBuilder.typeName(registrations.beanType()), null, null, null, null);
        };
    }

    /**
     * The writer's implicit qualifier: a {@code @Named} without a value takes the name of what it annotates, the
     * decapitalized simple name of a class or the name (or property name) of a method, field or parameter. The writer
     * annotates the element in its constructor, before it reads any metadata; the model backends do the same so that
     * the exported metadata carries the same value.
     */
    private static void autoApplyNamed(Element element) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        if (!annotationMetadata.hasAnnotation(AnnotationUtil.NAMED) && !annotationMetadata.hasStereotype(AnnotationUtil.NAMED)) {
            return;
        }
        if (element.stringValue(AnnotationUtil.NAMED).isPresent()) {
            return;
        }
        String name;
        if (element instanceof ClassElement) {
            name = NameUtils.decapitalize(element.getSimpleName());
        } else if (element instanceof MethodElement && NameUtils.isGetterName(element.getName())) {
            name = NameUtils.getPropertyNameForGetter(element.getName());
        } else {
            name = element.getName();
        }
        element.annotate(AnnotationUtil.NAMED, builder -> builder.value(name));
    }

    private static void autoApplyNamedToParameters(MethodElement method) {
        for (ParameterElement parameter : method.getParameters()) {
            autoApplyNamed(parameter);
        }
    }

    private static void autoApplyNamedToBeanProducingElement(Element element) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        if (annotationMetadata.hasAnnotation(EachProperty.class) || annotationMetadata.hasAnnotation(EachBean.class)) {
            return;
        }
        autoApplyNamed(element);
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
        MethodElement producer;
        List<BeanDefinitionInjectionPoint<ClassElement>> producerInjectionPoints;
        AnnotationMetadata producerMetadata;
        if (factoryMethodDefinition != null) {
            producer = factoryMethodDefinition.methodElement();
            producerInjectionPoints = factoryMethodDefinition.injectionPoints();
            producerMetadata = factoryMethodDefinition.annotationMetadata();
        } else {
            ConstructorDefinition<ClassElement, MethodElement> constructor = Objects.requireNonNull(constructorDefinition);
            producer = constructor.constructorElement();
            producerInjectionPoints = constructor.injectionPoints();
            producerMetadata = constructor.annotationMetadata();
        }
        autoApplyNamedToParameters(producer);
        List<ArgumentModel> parameters = new ArrayList<>();
        List<InjectionPointModel> injectionPoints = new ArrayList<>();
        ParameterElement[] producerParameters = producer.getParameters();
        for (int i = 0; i < producerParameters.length; i++) {
            parameters.add(modelBuilder.argument(classElement, producerParameters[i].getName(), producerParameters[i].getGenericType(),
                producerParameters[i].getAnnotationMetadata()));
            injectionPoints.add(injectionPoint(producerInjectionPoints.get(i), producerParameters[i]));
        }
        applyValidation(producerMetadata, producerInjectionPoints);
        ConstructorModel constructorModel = new ConstructorModel(modelBuilder.annotationMetadata(classElement, producerMetadata),
            List.copyOf(parameters), List.copyOf(injectionPoints));
        Map<String, List<ArgumentModel>> typeArguments = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ClassElement>> entry : beanTypeElement.getAllTypeArguments().entrySet()) {
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
        // The writer reads the declared exposed types from the producing element: the class, or the factory method
        AnnotationMetadata producingAnnotationMetadata = factoryMethodDefinition != null
            ? factoryMethodDefinition.methodElement().getMethodAnnotationMetadata() : classElement.getAnnotationMetadata();
        String[] declaredExposedTypes = producingAnnotationMetadata.stringValues(Bean.class.getName(), "typed");
        List<String> exposedTypes;
        if (declaredExposedTypes.length != 0) {
            exposedTypes = List.of(declaredExposedTypes);
        } else {
            Set<String> collected = new LinkedHashSet<>();
            collectExposedTypes(collected, beanTypeElement, true, packageName);
            exposedTypes = List.copyOf(collected);
        }
        if (factoryMethodDefinition == null) {
            return List.of(new BeanDefinitionModel(definitionName, classElement.getName(), null, null, null, constructorModel, List.copyOf(methods),
                List.copyOf(executableMethods), PythonMetadataModelBuilder.precalculatedInfo(classElement), exposedTypes,
                declaredExposedTypes.length != 0, typeArguments, validated, postConstructValidation));
        }
        MethodElement method = factoryMethodDefinition.methodElement();
        // The writer's definition metadata is the method's target metadata: the factory class layer under the method's own
        AnnotationMetadata definitionMetadata = method.getTargetAnnotationMetadata();
        AnnotationMetadataModel rootMetadata = null;
        AnnotationMetadataModel declaredMetadata;
        if (definitionMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            if (hierarchy.size() != 2) {
                throw PythonMetadataModelBuilder.unsupported(classElement, "the annotation metadata hierarchy of the factory method " + method.getName());
            }
            rootMetadata = modelBuilder.annotationMetadata(classElement, hierarchy.getRootMetadata());
            declaredMetadata = modelBuilder.annotationMetadata(classElement, hierarchy.getDeclaredMetadata());
        } else {
            declaredMetadata = modelBuilder.annotationMetadata(classElement, definitionMetadata);
        }
        FactoryMethodModel factory = new FactoryMethodModel(method.getOwningType().getName(), method.getName(),
            modelBuilder.argument(classElement, method.getName(), method.getReturnType(), AnnotationMetadata.EMPTY_METADATA), method.isStatic());
        return List.of(new BeanDefinitionModel(definitionName, PythonMetadataModelBuilder.typeName(beanTypeElement), factory, declaredMetadata, rootMetadata,
            constructorModel, List.copyOf(methods), List.copyOf(executableMethods),
            PythonMetadataModelBuilder.precalculatedInfo(definitionMetadata, method.getDeclaredMetadata(), false, beanTypeElement), exposedTypes,
            declaredExposedTypes.length != 0, typeArguments, validated, postConstructValidation));
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
