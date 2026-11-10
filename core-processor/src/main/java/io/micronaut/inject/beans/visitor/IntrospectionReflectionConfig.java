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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.ReflectionConfig;
import io.micronaut.core.graal.GraalReflectionConfigurer;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.writer.DispatchWriter;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.micronaut.inject.annotation.AnnotationMetadataGenUtils.createGetAnnotationMetadataMethodDef;

/**
 * The GraalVM reflection configuration of the members a generated introspection dispatches to reflectively: a
 * private field it reads or writes, a method it cannot call directly. A native image registers them through the
 * {@link GraalReflectionConfigurer} written beside the introspection, so an introspection reading a private field
 * works there without configuration of its own.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class IntrospectionReflectionConfig {

    private final Map<String, Members> byType = new LinkedHashMap<>();

    /**
     * Collects the members of the dispatch targets that dispatch reflectively.
     *
     * @param dispatchTargets The dispatch targets of the introspection
     * @return The configuration
     */
    static IntrospectionReflectionConfig of(List<DispatchWriter.DispatchTarget> dispatchTargets) {
        var config = new IntrospectionReflectionConfig();
        for (DispatchWriter.DispatchTarget target : dispatchTargets) {
            if (target instanceof DispatchWriter.FieldGetReflectionDispatchTarget fieldGet) {
                config.addField(fieldGet.getField());
            } else if (target instanceof DispatchWriter.FieldSetReflectionDispatchTarget fieldSet) {
                config.addField(fieldSet.getField());
            } else if (target instanceof DispatchWriter.MethodReflectionDispatchTarget method) {
                config.addMethod(method.getMethodElement());
            }
        }
        return config;
    }

    /**
     * @return Whether no member dispatches reflectively
     */
    boolean isEmpty() {
        return byType.isEmpty();
    }

    private void addField(FieldElement field) {
        members(field.getDeclaringType().getName()).fields.add(field.getName());
    }

    private void addMethod(MethodElement method) {
        // a Kotlin suspend function has a trailing Continuation parameter on the JVM
        ParameterElement[] parameters = method.isSuspend() ? method.getSuspendParameters() : method.getParameters();
        List<String> parameterTypes = new ArrayList<>(parameters.length);
        for (ParameterElement parameter : parameters) {
            parameterTypes.add(binaryName(parameter.getType()));
        }
        members(method.getDeclaringType().getName()).methods.add(new Signature(method.getName(), parameterTypes));
    }

    /**
     * The name {@link Class#forName(String)} loads a type by: an array is named by its descriptor,
     * {@code [Ljava.lang.String;} for {@code String[]} and {@code [[I} for {@code int[][]}.
     */
    static String binaryName(ClassElement type) {
        if (!type.isArray()) {
            return type.getName();
        }
        int dimensions = type.getArrayDimensions();
        ClassElement component = type;
        while (component.isArray()) {
            component = component.fromArray();
        }
        String element = component.isPrimitive() ? primitiveDescriptor(component.getName()) : "L" + component.getName() + ";";
        return "[".repeat(dimensions) + element;
    }

    private static String primitiveDescriptor(String primitive) {
        return switch (primitive) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> throw new IllegalArgumentException("Not a primitive type: " + primitive);
        };
    }

    private Members members(String typeName) {
        return byType.computeIfAbsent(typeName, name -> new Members());
    }

    /**
     * The configurer class, a service of {@link GraalReflectionConfigurer}.
     *
     * @param className The name of the class
     * @return The class
     */
    ClassDef build(String className) {
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        List<AnnotationValue<ReflectionConfig>> configs = new ArrayList<>(byType.size());
        byType.forEach((typeName, members) -> configs.add(members.build(typeName)));
        AnnotationValue<ReflectionConfig.ReflectionConfigList> list = AnnotationValue.builder(ReflectionConfig.ReflectionConfigList.class)
            .values(configs.toArray(new AnnotationValue[0]))
            .build();
        annotationMetadata.addAnnotation(list.getAnnotationName(), list.getValues(), RetentionPolicy.RUNTIME);

        ClassTypeDef thisType = ClassTypeDef.of(className);
        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(className)
            .synthetic()
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", GraalReflectionConfigurer.class.getName()).build())
            .addSuperinterface(ClassTypeDef.of(GraalReflectionConfigurer.class))
            .addMethod(createGetAnnotationMetadataMethodDef(thisType, annotationMetadata));
        Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
        Function<String, ExpressionDef> loadClassValueExpressionFn = AnnotationMetadataGenUtils.createLoadClassValueExpressionFn(thisType, loadTypeMethods);
        List<StatementDef> staticInit = new ArrayList<>();
        AnnotationMetadataGenUtils.addAnnotationDefaults(staticInit, annotationMetadata, loadClassValueExpressionFn);
        @Nullable FieldDef annotationMetadataField = AnnotationMetadataGenUtils.createAnnotationMetadataFieldAndInitialize(
            annotationMetadata,
            loadClassValueExpressionFn
        );
        loadTypeMethods.values().forEach(classDefBuilder::addMethod);
        if (annotationMetadataField != null) {
            classDefBuilder.addField(annotationMetadataField);
        }
        if (!staticInit.isEmpty()) {
            classDefBuilder.addStaticInitializer(StatementDef.multi(staticInit));
        }
        return classDefBuilder.build();
    }

    /**
     * A method by its name and the names of its parameter types.
     *
     * @param name           The name
     * @param parameterTypes The parameter types
     */
    private record Signature(String name, List<String> parameterTypes) {
    }

    /**
     * The members of a type dispatched to reflectively.
     */
    private static final class Members {
        private final Set<String> fields = new LinkedHashSet<>();
        private final Set<Signature> methods = new LinkedHashSet<>();

        @SuppressWarnings("unchecked")
        AnnotationValue<ReflectionConfig> build(String typeName) {
            AnnotationValueBuilder<ReflectionConfig> builder = AnnotationValue.builder(ReflectionConfig.class)
                .member("type", new AnnotationClassValue<>(typeName));
            if (!fields.isEmpty()) {
                builder.member("fields", fields.stream()
                    .map(name -> AnnotationValue.builder(ReflectionConfig.ReflectiveFieldConfig.class).member("name", name).build())
                    .toArray(AnnotationValue[]::new));
            }
            if (!methods.isEmpty()) {
                builder.member("methods", methods.stream()
                    .map(method -> AnnotationValue.builder(ReflectionConfig.ReflectiveMethodConfig.class)
                        .member("name", method.name())
                        .member("parameterTypes", method.parameterTypes().stream()
                            .map(AnnotationClassValue::new)
                            .toArray(AnnotationClassValue[]::new))
                        .build())
                    .toArray(AnnotationValue[]::new));
            }
            return builder.build();
        }
    }
}
