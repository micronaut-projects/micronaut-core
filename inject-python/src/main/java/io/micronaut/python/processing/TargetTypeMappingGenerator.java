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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.graalvm.polyglot.Value;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a simple TargetTypeMapping bean for each Python stub that delegates
 * to the statically generated fromPolyglotValue(Value) method.
 */
@Experimental
public final class TargetTypeMappingGenerator implements TypeElementVisitor<Object, Object> {

    private static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";
    private static final String ASSIGNABLE_TARGET_TYPES = "assignableTargetTypes";
    private static final String OBJECT = Object.class.getName();
    private static final String VALUE_COERCIBLE = "io.micronaut.context.python.ValueCoercible";
    private static final String GENERATED_PROPERTY_MEMBERS = "io.micronaut.context.python.ValueCoercible$GeneratedPropertyMembers";
    private static final String GENERATED_PROPERTY_MEMBERS_CANONICAL = "io.micronaut.context.python.ValueCoercible.GeneratedPropertyMembers";
    private static final String POOLED_VALUE_COERCIBLE = "io.micronaut.context.python.PooledValueCoercible";
    private static final String PROXY_OBJECT = "org.graalvm.polyglot.proxy.ProxyObject";
    private static final String BOXED = "io.micronaut.core.graal.Boxed";
    private static final String TARGET_TYPE_MAPPING = "io.micronaut.context.python.TargetTypeMapping";

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        // Only generate for Java stubs created by PythonStubGenerator, which implement ValueCoercible
        if (!element.isAssignable("io.micronaut.context.python.ValueCoercible")) {
            return;
        }
        if (element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyStatic().named(FROM_POLYGLOT_VALUE).onlyAccessible(element)).isEmpty()) {
            return;
        }
        String mappingName = element.getName() + "TargetTypeMapping";
        ClassTypeDef thisType = ClassTypeDef.of(element.getName());
        ClassDef.ClassDefBuilder builder = ClassDef.builder(mappingName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(ClassTypeDef.of("jakarta.inject.Singleton"))
            .addSuperinterface(TypeDef.parameterized(
                ClassTypeDef.of(TARGET_TYPE_MAPPING), thisType
            ));

        builder.addMethod(MethodDef.builder("targetType")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.parameterized(Class.class, thisType))
            .build((aThis, params) -> thisType.getStaticField("class", TypeDef.CLASS).returning()));

        builder.addMethod(MethodDef.builder("convert")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterDef.of("value", TypeDef.of(Value.class)))
            .returns(thisType)
            .build((aThis, params) -> thisType.invokeStatic(FROM_POLYGLOT_VALUE, thisType, params.getFirst()).returning()));

        List<ClassElement> assignableTargetTypes = assignableTargetTypes(element);
        if (!assignableTargetTypes.isEmpty()) {
            TypeDef.Array classArray = TypeDef.Primitive.CLASS.array();
            builder.addMethod(MethodDef.builder(ASSIGNABLE_TARGET_TYPES)
                .addModifiers(Modifier.PUBLIC)
                .returns(classArray)
                .build((aThis, params) -> classArray.instantiate(
                    assignableTargetTypes.stream()
                        .map(targetType -> javaClassType(targetType).getStaticField("class", TypeDef.CLASS))
                        .toArray(ExpressionDef[]::new)
                ).returning()));
        }

        SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).ifPresent(sg -> sg.write(builder.build(), context, element));
        context.visitServiceDescriptor(TARGET_TYPE_MAPPING, mappingName, element);
    }

    private static List<ClassElement> assignableTargetTypes(ClassElement element) {
        Map<String, ClassElement> targetTypes = new LinkedHashMap<>();
        collectInterfaces(element, targetTypes);
        element.getSuperType().ifPresent(superType -> collectSuperTypes(superType, targetTypes));
        return new ArrayList<>(targetTypes.values());
    }

    private static void collectSuperTypes(ClassElement element, Map<String, ClassElement> targetTypes) {
        ClassElement rawElement = element.getRawClassElement();
        if (!addTargetType(rawElement, targetTypes)) {
            return;
        }
        collectInterfaces(rawElement, targetTypes);
        rawElement.getSuperType().ifPresent(superType -> collectSuperTypes(superType, targetTypes));
    }

    private static void collectInterfaces(ClassElement element, Map<String, ClassElement> targetTypes) {
        for (ClassElement anInterface : element.getInterfaces()) {
            ClassElement rawInterface = anInterface.getRawClassElement();
            if (!addTargetType(rawInterface, targetTypes)) {
                continue;
            }
            collectInterfaces(rawInterface, targetTypes);
        }
    }

    private static boolean addTargetType(ClassElement element, Map<String, ClassElement> targetTypes) {
        String name = element.getName();
        if (isSkippedTargetType(name)) {
            return false;
        }
        targetTypes.putIfAbsent(name, element);
        return true;
    }

    private static boolean isSkippedTargetType(String name) {
        return OBJECT.equals(name)
            || VALUE_COERCIBLE.equals(name)
            || GENERATED_PROPERTY_MEMBERS.equals(name)
            || GENERATED_PROPERTY_MEMBERS_CANONICAL.equals(name)
            || POOLED_VALUE_COERCIBLE.equals(name)
            || PROXY_OBJECT.equals(name)
            || BOXED.equals(name);
    }

    private static ClassTypeDef javaClassType(ClassElement element) {
        return ClassTypeDef.of(element.getName().replace('$', '.'), element.isInner());
    }
}
