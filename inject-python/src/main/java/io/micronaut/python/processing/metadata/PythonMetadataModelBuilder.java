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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.ArgumentModel;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.IntrospectionModel;
import io.micronaut.context.python.runtime.model.MethodModel;
import io.micronaut.context.python.runtime.model.PropertyIndexModel;
import io.micronaut.context.python.runtime.model.PropertyModel;
import io.micronaut.context.python.runtime.model.PythonMetadataModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.annotation.PythonAnnotationMetadataModels;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Captures the resolved model of a Python class once every visitor has contributed to it. Bean definitions are
 * derived by the same {@link BeanDefinitionCreatorFactory} analysis the compiler backend uses, through a builder that
 * records instead of emitting; introspections by the same property query the introspection visitor uses. What the
 * model backends cannot generate yet is a compilation error naming the construct, never a silent fallback.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonMetadataModelBuilder {

    private static final String PREFIX = "Python metadata model backend: ";

    private final VisitorContext visitorContext;

    /**
     * @param visitorContext The visitor context
     */
    public PythonMetadataModelBuilder(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    /**
     * @return The visitor context
     */
    VisitorContext visitorContext() {
        return visitorContext;
    }

    /**
     * Builds the model of a class.
     *
     * @param classElement The class
     * @return The model, or empty when the class needs neither a definition nor an introspection
     */
    public Optional<PythonMetadataModel> build(ClassElement classElement) {
        BeanDefinitionModel beanDefinition = beanDefinition(classElement);
        IntrospectionModel introspection = introspection(classElement);
        if (beanDefinition == null && introspection == null) {
            return Optional.empty();
        }
        AnnotationMetadataModel annotationMetadata = annotationMetadata(classElement, classElement.getAnnotationMetadata());
        return Optional.of(new PythonMetadataModel(PythonMetadataModel.FORMAT_VERSION, Objects.requireNonNullElse(VersionUtils.MICRONAUT_VERSION, "unknown"), classElement.getName(),
            new ClassModel(classElement.getName(), annotationMetadata, beanDefinition, introspection)));
    }

    private @Nullable BeanDefinitionModel beanDefinition(ClassElement classElement) {
        ModelBeanDefinitionBuilderFactory factory = new ModelBeanDefinitionBuilderFactory(this, classElement);
        List<BeanDefinitionModel> definitions = BeanDefinitionCreatorFactory.produce(classElement, factory, visitorContext);
        if (definitions.isEmpty()) {
            return null;
        }
        if (definitions.size() > 1) {
            throw unsupported(classElement, "a class producing several bean definitions");
        }
        return definitions.getFirst();
    }

    private @Nullable IntrospectionModel introspection(ClassElement classElement) {
        if (!classElement.hasStereotype(Introspected.class)) {
            return null;
        }
        AnnotationValue<Introspected> introspected = classElement.getAnnotation(Introspected.class);
        if (introspected == null) {
            return null;
        }
        if (classElement.isEnum()) {
            throw unsupported(classElement, "an introspected enum");
        }
        for (String member : new String[]{"classes", "classNames", "packages", "builder", "targetPackage", "constructors", "members"}) {
            if (introspected.contains(member) && !isDefault(introspected, member)) {
                Object value = introspected.getValues().get(member);
                String shown = value instanceof Object[] array ? java.util.Arrays.deepToString(array) : String.valueOf(value);
                throw unsupported(classElement, "@Introspected(" + member + " = " + shown + ")");
            }
        }
        boolean metadata = introspected.booleanValue("annotationMetadata").orElse(true);
        boolean ignoreSettersWithDifferingType = introspected.booleanValue("ignoreSettersWithDifferingType").orElse(true);
        PropertyElementQuery query = PropertyElementQuery.of(classElement).ignoreSettersWithDifferingType(ignoreSettersWithDifferingType);
        AnnotationValue<?>[] indexedAnnotations = introspected.get("indexed", AnnotationValue[].class, new AnnotationValue[0]);
        List<PropertyModel> properties = new ArrayList<>();
        List<PropertyIndexModel> indexes = new ArrayList<>();
        for (PropertyElement property : classElement.getBeanProperties(query)) {
            if (property.isExcluded()) {
                continue;
            }
            for (AnnotationValue<?> indexedAnnotation : indexedAnnotations) {
                String annotationName = indexedAnnotation.get("annotation", String.class).orElse(null);
                if (annotationName != null && property.hasStereotype(annotationName)) {
                    String value = indexedAnnotation.get("member", String.class)
                        .flatMap(m -> property.getValue(annotationName, m, String.class)).orElse(null);
                    indexes.add(new PropertyIndexModel(annotationName, value, properties.size()));
                }
            }
            AnnotationMetadata propertyMetadata = metadata ? merge(property) : AnnotationMetadata.EMPTY_METADATA;
            MethodModel read = member(classElement, property.getReadMember().orElse(null), property);
            MethodModel write = member(classElement, property.getWriteMember().orElse(null), property);
            ClassElement type = property.getGenericType();
            if (property.getReadType().filter(t -> !t.equals(type)).isPresent() || property.getWriteType().filter(t -> !t.equals(type)).isPresent()) {
                throw unsupported(classElement, "the property " + property.getName() + " read or written as another type than it is declared with");
            }
            properties.add(new PropertyModel(property.getName(), argument(classElement, property.getName(), type, propertyMetadata), read, write, property.isReadOnly()));
        }
        for (MethodElement method : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().annotated(am -> am.hasStereotype(Executable.class)))) {
            throw unsupported(classElement, "an executable method (" + method.getName() + ") of an introspected class");
        }
        MethodElement constructor = classElement.getPrimaryConstructor().orElse(null);
        MethodElement defaultConstructor = classElement.getDefaultConstructor().orElse(null);
        MethodElement instantiating = constructor != null && ArrayUtils.isNotEmpty(constructor.getParameters()) ? constructor : defaultConstructor;
        if (instantiating == null) {
            throw unsupported(classElement, "an introspected class without a constructor");
        }
        if (instantiating.isStatic()) {
            throw unsupported(classElement, "a static creator method");
        }
        List<ArgumentModel> constructorArguments = new ArrayList<>();
        for (ParameterElement parameter : instantiating.getParameters()) {
            constructorArguments.add(argument(classElement, parameter.getName(), parameter.getGenericType(), parameter.getAnnotationMetadata()));
        }
        String packageName = classElement.getPackageName();
        String introspectionName = packageName + ".$" + classElement.getName().substring(packageName.isEmpty() ? 0 : packageName.length() + 1).replace('.', '$') + "$Introspection";
        if (introspectionName.length() > 240) {
            throw unsupported(classElement, "an introspection name longer than 240 characters");
        }
        return new IntrospectionModel(introspectionName, annotationMetadata(classElement, instantiating.getAnnotationMetadata()),
            List.copyOf(constructorArguments), List.copyOf(properties), List.copyOf(indexes));
    }

    private static boolean isDefault(AnnotationValue<Introspected> introspected, String member) {
        return isDefaultValue(introspected.getValues().get(member));
    }

    private static boolean isDefaultValue(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        if (value instanceof String string) {
            return string.isEmpty();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof AnnotationValue<?> nested) {
            return nested.getValues().values().stream().allMatch(PythonMetadataModelBuilder::isDefaultValue);
        }
        if (value instanceof Object[] array) {
            return java.util.Arrays.stream(array).allMatch(PythonMetadataModelBuilder::isDefaultValue);
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        return false;
    }

    private @Nullable MethodModel member(ClassElement classElement, @Nullable MemberElement member, PropertyElement property) {
        if (member == null) {
            return null;
        }
        if (!(member instanceof MethodElement method)) {
            throw unsupported(classElement, "the field-backed property " + property.getName());
        }
        return method(classElement, method);
    }

    /**
     * The model of a method invoked by generated code.
     *
     * @param classElement The class the model is built for
     * @param method       The method
     * @return The model
     */
    MethodModel method(ClassElement classElement, MethodElement method) {
        if (method.isReflectionRequired(classElement)) {
            throw unsupported(classElement, "the method " + method.getName() + ", which is not accessible without reflection");
        }
        List<ArgumentModel> parameters = new ArrayList<>();
        for (ParameterElement parameter : method.getParameters()) {
            parameters.add(argument(classElement, parameter.getName(), parameter.getGenericType(), parameter.getAnnotationMetadata()));
        }
        return new MethodModel(method.getDeclaringType().getName(), method.getName(),
            argument(classElement, method.getName(), method.getGenericReturnType(), AnnotationMetadata.EMPTY_METADATA),
            List.copyOf(parameters), annotationMetadata(classElement, method.getMethodAnnotationMetadata().getAnnotationMetadata()), method.isStatic());
    }

    /**
     * The model of a typed, named and annotated argument.
     *
     * @param classElement       The class the model is built for
     * @param name               The name
     * @param type               The type
     * @param annotationMetadata The annotation metadata
     * @return The model
     */
    ArgumentModel argument(ClassElement classElement, String name, TypedElement type, AnnotationMetadata annotationMetadata) {
        ClassElement resolved = type.getType();
        if (type instanceof GenericPlaceholderElement placeholder) {
            resolved = placeholder.getResolved().orElseThrow(() -> unsupported(classElement, "the unresolved type variable " + placeholder.getVariableName() + " of " + name));
        }
        if (resolved instanceof WildcardElement || resolved.isTypeVariable() || resolved instanceof GenericPlaceholderElement) {
            throw unsupported(classElement, "the generic type of " + name + " (" + resolved.getName() + ")");
        }
        List<ArgumentModel> typeArguments = new ArrayList<>();
        for (Map.Entry<String, ClassElement> entry : resolved.getTypeArguments().entrySet()) {
            typeArguments.add(argument(classElement, entry.getKey(), entry.getValue(), entry.getValue().getAnnotationMetadata()));
        }
        return new ArgumentModel(name, typeName(resolved), annotationMetadata(classElement, annotationMetadata), List.copyOf(typeArguments));
    }

    /**
     * The precalculated information of a bean.
     *
     * @param classElement The bean class
     * @return The scope, singleton, primary and container decisions
     */
    static io.micronaut.context.python.runtime.model.PrecalculatedInfoModel precalculatedInfo(ClassElement classElement) {
        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        String scope = annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).orElse(null);
        boolean singleton;
        if (scope != null) {
            singleton = scope.equals(Singleton.class.getName()) || scope.equals(Context.class.getName());
        } else {
            singleton = annotationMetadata.stringValue(DefaultScope.class)
                .map(t -> t.equals(Singleton.class.getName()) || t.equals(Context.class.getName())).orElse(false);
        }
        boolean iterable = annotationMetadata.hasDeclaredStereotype(EachProperty.class) || annotationMetadata.hasDeclaredStereotype(EachBean.class);
        return new io.micronaut.context.python.runtime.model.PrecalculatedInfoModel(scope, classElement.isAbstract(), iterable, singleton,
            annotationMetadata.hasDeclaredStereotype(Primary.class), false, classElement.isArray() || classElement.isContainerType());
    }

    /**
     * Exports annotation metadata, reporting unsupported values against the class.
     *
     * @param classElement       The class the model is built for
     * @param annotationMetadata The metadata
     * @return The model
     */
    AnnotationMetadataModel annotationMetadata(ClassElement classElement, AnnotationMetadata annotationMetadata) {
        try {
            return PythonAnnotationMetadataModels.export(annotationMetadata);
        } catch (IllegalArgumentException e) {
            throw unsupported(classElement, e.getMessage());
        }
    }

    private static AnnotationMetadata merge(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof io.micronaut.inject.annotation.AnnotationMetadataHierarchy hierarchy) {
            return hierarchy.merge();
        }
        return annotationMetadata;
    }

    /**
     * The model type name of a class element.
     *
     * @param type The type
     * @return The name
     */
    static String typeName(ClassElement type) {
        if (type.isArray()) {
            return typeName(type.fromArray()) + "[]";
        }
        return type.getName();
    }

    /**
     * The diagnostic of a construct the model backends do not support.
     *
     * @param element   The element
     * @param construct What is not supported
     * @return The exception to throw
     */
    static ProcessingException unsupported(ClassElement element, @Nullable String construct) {
        return new ProcessingException(element, PREFIX + construct + " is not supported yet; compile " + element.getName()
            + " with " + PythonMetadataBackend.BACKEND_OPTION + "=compiler or exclude it through " + PythonMetadataBackend.TYPES_OPTION);
    }
}
