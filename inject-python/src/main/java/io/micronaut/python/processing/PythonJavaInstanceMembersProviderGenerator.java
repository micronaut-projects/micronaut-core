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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.util.PythonAnnotationTypes;
import io.micronaut.python.processing.util.PythonJavaTypes;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Computes member aliases from public Java APIs at compile time. Like the functional-interface
 * provider, covers imports, Java bases and hints, and one level of method return types. The
 * registered API class also covers its runtime implementations, without discovering their members.
 *
 * @since 5.3.0
 */
@Internal
final class PythonJavaInstanceMembersProviderGenerator {
    static final String PROVIDER = "io.micronaut.context.python.PythonJavaInstanceMembersProvider";
    private static final String CLASS_NAME_PREFIX = "PythonJavaInstanceMembers$";
    private static final Member BLOCKED = new Member("", false);
    private final VisitorContext context;
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<String, Integer> walked = new HashMap<>();

    PythonJavaInstanceMembersProviderGenerator(VisitorContext context) {
        this.context = context;
    }

    void referenceImport(String name) {
        context.getClassElement(name).ifPresent(type -> reference(type, 0));
    }

    void referenceAliasReceiver(String name) {
        context.getClassElement(name).ifPresent(type -> reference(type, 1));
    }

    void referencePythonType(ClassElement type) {
        type.getSuperType().filter(parent -> !parent.getName().equals(Object.class.getName()))
            .ifPresent(parent -> reference(parent, 0));
        type.getInterfaces().forEach(parent -> reference(parent, 0));
        for (MethodElement method : type.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            referenceSignature(method);
        }
        for (MethodElement constructor : type.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
            referenceSignature(constructor);
        }
        type.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared()).forEach(field -> reference(field.getType(), 0));
        type.getBeanProperties().forEach(property -> reference(property.getType(), 0));
    }

    private void referenceSignature(MethodElement method) {
        for (var parameter : method.getParameters()) {
            reference(parameter.getType(), 0);
        }
        reference(method.getReturnType(), 0);
    }

    private void reference(ClassElement referenced, int depth) {
        if (referenced.isPrimitive() || referenced.isVoid() || referenced.isArray() || PythonJavaTypes.isPythonClass(referenced)) {
            return;
        }
        ClassElement type = referenced.getRawClassElement();
        if (!accessible(type) || PythonAnnotationTypes.isAnnotationType(type)) {
            return;
        }
        Integer previous = walked.get(type.getName());
        if (previous != null && previous <= depth) {
            return;
        }
        walked.put(type.getName(), depth);
        List<MethodElement> methods = type.getEnclosedElements(ElementQuery.ALL_METHODS.filter(MethodElement::isPublic));
        Map<String, Member> members = members(methods);
        if (!members.isEmpty()) {
            entries.put(type.getName(), new Entry(type, members));
        }
        if (depth == 0) {
            for (MethodElement method : methods) {
                reference(method.getReturnType(), 1);
            }
        }
    }

    private static Map<String, Member> members(List<MethodElement> methods) {
        Set<String> staticNames = methods.stream().filter(MethodElement::isStatic)
            .map(MethodElement::getName).collect(Collectors.toSet());
        Map<String, Member> result = new TreeMap<>();
        for (MethodElement method : methods) {
            if (method.isStatic()) {
                continue;
            }
            String name = method.getName();
            String alias = snakeCase(name);
            if (!alias.equals(name)) {
                Member member = new Member(name, false);
                if (staticNames.contains(name)) {
                    member = BLOCKED;
                }
                add(result, alias, member);
            }
            String property = getterProperty(method);
            if (property != null) {
                add(result, property, new Member(name, true));
            }
        }
        return result;
    }

    private static void add(Map<String, Member> members, String alias, Member member) {
        members.merge(alias, member, (first, second) -> first.equals(second) ? first : BLOCKED);
    }

    private static @Nullable String getterProperty(MethodElement method) {
        String name = method.getName();
        if (method.getParameters().length != 0 || method.getReturnType().isVoid() || name.equals("getClass")) {
            return null;
        }
        int prefix = getterPrefix(method);
        return prefix > 0 && name.length() > prefix && Character.isUpperCase(name.charAt(prefix))
            ? snakeCase(name.substring(prefix)) : null;
    }

    private static int getterPrefix(MethodElement method) {
        if (method.getName().startsWith("get")) {
            return 3;
        }
        String returnType = method.getReturnType().getName();
        return method.getName().startsWith("is") && (returnType.equals("boolean") || returnType.equals(Boolean.class.getName())) ? 2 : 0;
    }

    private static String snakeCase(String name) {
        // Preserve the Python acronym boundary (HTTPStatus -> http_status) before the framework's
        // camel-case conversion. This is done once by the compiler, never by runtime host interop.
        return Arrays.stream(name.split("_", -1))
            .map(word -> NameUtils.underscoreSeparate(word.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2"))
                .toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("_"));
    }

    private static boolean accessible(ClassElement type) {
        ClassElement current = type;
        while (current != null) {
            if (!current.isPublic()) {
                return false;
            }
            current = current.getEnclosingType().orElse(null);
        }
        return true;
    }

    void generate(ClassElement origin) {
        if (entries.isEmpty()) {
            return;
        }
        ClassTypeDef entryType = ClassTypeDef.of(PROVIDER + ".Entry");
        ClassTypeDef memberType = ClassTypeDef.of(PROVIDER + ".Member");
        TypeDef memberList = TypeDef.parameterized(ClassTypeDef.of(List.class), memberType);
        TypeDef entryList = TypeDef.parameterized(ClassTypeDef.of(List.class), entryType);
        StringBuilder content = new StringBuilder();
        List<ExpressionDef> values = entries.values().stream().map(entry -> {
            content.append(entry.type().getName()).append(':').append(entry.members()).append('\n');
            List<ExpressionDef> aliases = entry.members().entrySet().stream().map(member -> memberType.instantiate(
                ExpressionDef.constant(member.getKey()), ExpressionDef.constant(member.getValue().name()),
                ExpressionDef.constant(member.getValue().property()))).map(ExpressionDef.class::cast).toList();
            return entryType.instantiate(ClassTypeDef.of(entry.type().getName().replace('$', '.')).getStaticField("class", TypeDef.CLASS),
                ClassTypeDef.of(List.class).invokeStatic("of", memberList, aliases));
        }).map(ExpressionDef.class::cast).toList();
        String className = origin.getPackageName() + '.' + CLASS_NAME_PREFIX + PythonAnnotationProcessor.contentHash(content.toString());
        ClassDef.ClassDefBuilder definition = ClassDef.builder(className)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(Internal.class)
            .addAnnotation(ClassTypeDef.of("jakarta.inject.Singleton"))
            .addSuperinterface(ClassTypeDef.of(PROVIDER))
            .addMethod(MethodDef.builder("entries")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(entryList)
                .build((aThis, params) -> ClassTypeDef.of(className).getStaticField("ENTRIES", entryList).returning()));
        List<ExpressionDef> factories = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String factory = "entry" + i;
            ExpressionDef value = values.get(i);
            definition.addMethod(MethodDef.builder(factory)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(entryType)
                .build((aThis, params) -> value.returning().doTry()
                    .doCatch(NoClassDefFoundError.class, exception -> ExpressionDef.nullValue().returning())));
            factories.add(ClassTypeDef.of(className).invokeStatic(factory, entryType));
        }
        definition.addField(FieldDef.builder("ENTRIES", entryList)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer(ClassTypeDef.of(PROVIDER).invokeStatic("available", entryList, factories))
            .build());
        SourceGenerators.findByLanguage(VisitorContext.Language.JAVA)
            .ifPresent(generator -> generator.write(definition.build(), context, origin));
        context.visitServiceDescriptor(PROVIDER, className, origin);
    }

    private record Entry(ClassElement type, Map<String, Member> members) {
    }

    private record Member(String name, boolean property) {
    }
}
