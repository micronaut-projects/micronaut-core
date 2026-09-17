/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.util;

import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.python.processing.annotation.AnnotationMemberReflection;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Answers, for a Java type seen by the Python processor, whether it is an annotation type and whether
 * that annotation may be placed on other annotation types.
 *
 * <p>The Python processor and transformer used to answer these questions themselves by poking
 * {@code javax.lang.model} elements through interop with string comparisons. Both now call this class.</p>
 *
 * @since 5.2.0
 */
@Internal
public final class PythonAnnotationTypes {

    private static final String VALUE_MEMBER = "value";

    private static final String JAVA_LANG_ANNOTATION = "java.lang.annotation";
    private static final Set<ElementKind> NESTED_TYPE_KINDS = EnumSet.of(
        ElementKind.ANNOTATION_TYPE, ElementKind.INTERFACE, ElementKind.CLASS, ElementKind.ENUM
    );

    private PythonAnnotationTypes() {
    }

    /**
     * Whether the element is an annotation type.
     *
     * @param classElement The element
     * @return Whether it is an annotation type
     */
    public static boolean isAnnotationType(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return false;
        }
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            return typeElement.getKind() == ElementKind.ANNOTATION_TYPE;
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            return type.isAnnotation();
        }
        String packageName = classElement.getPackageName();
        if (packageName.startsWith(JAVA_LANG_ANNOTATION)) {
            return true;
        }
        AnnotationMetadata metadata = classElement.getAnnotationMetadata();
        return metadata.hasAnnotation(Retention.class.getName());
    }

    /**
     * Whether the annotation type may be applied to annotation types, which is how a decorator
     * generated for it learns that it may be applied to Python-defined annotations. Java permits an
     * annotation type without {@code @Target} (such as {@code jakarta.inject.Singleton}) on every
     * declaration but type parameters, annotation types included; only an explicit {@code @Target}
     * that leaves out {@code ANNOTATION_TYPE} excludes it.
     *
     * @param classElement The element
     * @return Whether the annotation may target annotation types
     */
    public static boolean targetsAnnotationType(@Nullable ClassElement classElement) {
        if (!isAnnotationType(classElement)) {
            return false;
        }
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            Target target = typeElement.getAnnotation(Target.class);
            return target == null || contains(target.value());
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            Target target = type.getAnnotation(Target.class);
            return target == null || contains(target.value());
        }
        AnnotationMetadata metadata = classElement.getAnnotationMetadata();
        return !metadata.hasAnnotation(Target.class.getName())
            || contains(metadata.enumValues(Target.class.getName(), AnnotationMetadata.VALUE_MEMBER, ElementType.class));
    }

    /**
     * The retention of the annotation type. Java keeps an annotation without {@code @Retention} in the
     * class file only ({@link RetentionPolicy#CLASS}), so that is the answer when the element declares
     * none.
     *
     * @param classElement The annotation element
     * @return The retention policy
     */
    public static RetentionPolicy retentionPolicy(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return RetentionPolicy.CLASS;
        }
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            Retention retention = typeElement.getAnnotation(Retention.class);
            return retention == null ? RetentionPolicy.CLASS : retention.value();
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            Retention retention = type.getAnnotation(Retention.class);
            return retention == null ? RetentionPolicy.CLASS : retention.value();
        }
        return classElement.getAnnotationMetadata()
            .enumValue(Retention.class.getName(), AnnotationMetadata.VALUE_MEMBER, RetentionPolicy.class)
            .orElse(RetentionPolicy.CLASS);
    }

    /**
     * Whether the annotation type may be placed on a declaration of the given kind. An annotation
     * without {@code @Target} may be placed on every declaration; an explicit {@code @Target} has to
     * name the declaration kind itself. {@link ElementType#TYPE_USE} is deliberately not treated as
     * permitting a declaration: a type-use annotation belongs to the type, not the member.
     *
     * @param classElement The annotation element
     * @param declaration The declaration kind ({@link ElementType#TYPE}, {@link ElementType#FIELD}, {@link ElementType#METHOD}, ...)
     * @return Whether the annotation may be placed on such a declaration
     */
    public static boolean targetsDeclaration(@Nullable ClassElement classElement, ElementType declaration) {
        if (classElement == null) {
            return false;
        }
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            Target target = typeElement.getAnnotation(Target.class);
            return target == null || contains(target.value(), declaration);
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            Target target = type.getAnnotation(Target.class);
            return target == null || contains(target.value(), declaration);
        }
        AnnotationMetadata metadata = classElement.getAnnotationMetadata();
        return !metadata.hasAnnotation(Target.class.getName())
            || contains(metadata.enumValues(Target.class.getName(), AnnotationMetadata.VALUE_MEMBER, ElementType.class), declaration);
    }

    /**
     * The names of the members of the annotation type that declare a default value, read from the annotation
     * type itself: the annotation metadata leaves out empty defaults, and the defaults of a Java annotation
     * type are not resolvable through the metadata builder of the Python visitor context.
     *
     * @param classElement The annotation element
     * @return The names of the members with a default
     */
    public static Set<String> defaultedMembers(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return Set.of();
        }
        Set<String> members = new HashSet<>();
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement member && member.getDefaultValue() != null) {
                    members.add(member.getSimpleName().toString());
                }
            }
            return members;
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            for (Method member : type.getDeclaredMethods()) {
                if (member.getDefaultValue() != null) {
                    members.add(member.getName());
                }
            }
            return members;
        }
        for (CharSequence member : classElement.getAnnotationMetadata().getDefaultValues(classElement.getName()).keySet()) {
            members.add(member.toString());
        }
        return members;
    }

    /**
     * The simple names of the annotation, interface, class and enum types nested in the element.
     *
     * @param classElement The element
     * @return The nested type simple names, in declaration order
     */
    public static List<String> nestedTypeSimpleNames(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (NESTED_TYPE_KINDS.contains(enclosed.getKind())) {
                    names.add(enclosed.getSimpleName().toString());
                }
            }
        } else if (classElement.getNativeType() instanceof Class<?> type) {
            for (Class<?> declared : type.getDeclaredClasses()) {
                names.add(declared.getSimpleName());
            }
        }
        return names;
    }

    /**
     * The declared return type names of the annotation's members (its methods), as the source
     * language names them, for example {@code example.Nested} or {@code example.Nested[]}.
     *
     * @param classElement The annotation element
     * @return The member return type names, in declaration order
     */
    public static List<String> memberReturnTypeNames(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.METHOD && enclosed instanceof ExecutableElement method) {
                    names.add(method.getReturnType().toString());
                }
            }
        } else if (classElement.getNativeType() instanceof Class<?> type) {
            // the bare parser sees a Java annotation as a loaded class, not as a javac element
            names.addAll(AnnotationMemberReflection.memberReturnTypeNames(type));
        }
        return names;
    }

    /**
     * The declared return type name of the annotation's {@code value} member, as the source language
     * names it (a wildcard {@code java.lang.Class}, {@code java.lang.String[]}), or {@code null} when
     * the annotation has no {@code value} member.
     *
     * @param classElement The annotation element
     * @return The return type name of {@code value}, or null
     */
    public static @Nullable String valueMemberTypeName(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return null;
        }
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.METHOD && enclosed instanceof ExecutableElement method
                    && method.getSimpleName().contentEquals(VALUE_MEMBER)) {
                    return method.getReturnType().toString();
                }
            }
            return null;
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            return AnnotationMemberReflection.valueMemberTypeName(type);
        }
        return null;
    }

    /**
     * The types nested in an annotation, in one answer: the declared nested types first, then the
     * nested types returned by the annotation's members, each once. The transformer used to walk
     * the members from Python, several host calls per member; the lookup callback resolves a nested
     * type by its binary or source name.
     *
     * @param classElement The annotation element
     * @param lookup Resolves a class element by name, or null
     * @return The nested types
     */
    public static List<NestedType> nestedTypes(@Nullable ClassElement classElement, Function<String, @Nullable Object> lookup) {
        if (classElement == null) {
            return List.of();
        }
        String parentName = classElement.getName();
        Map<String, NestedType> nested = new LinkedHashMap<>();
        for (String simpleName : nestedTypeSimpleNames(classElement)) {
            ClassElement element = resolve(lookup, parentName + '$' + simpleName);
            if (element == null) {
                element = resolve(lookup, parentName + '.' + simpleName);
            }
            addNestedType(nested, element);
        }
        for (MethodElement method : classElement.getMethods()) {
            ClassElement returnType = method.getReturnType();
            if (returnType.isArray()) {
                returnType = returnType.fromArray();
            }
            String returnTypeName = returnType.getName();
            if (returnTypeName.startsWith(parentName + '$') || returnTypeName.startsWith(parentName + '.')) {
                addNestedType(nested, returnType);
            }
        }
        return List.copyOf(nested.values());
    }

    private static void addNestedType(Map<String, NestedType> nested, @Nullable ClassElement element) {
        if (element == null) {
            return;
        }
        String name = element.getName();
        if (nested.containsKey(name)) {
            return;
        }
        int separator = Math.max(name.lastIndexOf('$'), name.lastIndexOf('.'));
        String simpleName = separator == -1 ? name : name.substring(separator + 1);
        boolean annotation = isAnnotationType(element);
        nested.put(name, new NestedType(element, name, simpleName, annotation, annotation ? repeatableContainerName(element) : null));
    }

    private static @Nullable ClassElement resolve(Function<String, @Nullable Object> lookup, String name) {
        return lookup.apply(name) instanceof ClassElement element ? element : null;
    }

    /**
     * The container annotation named by {@code @Repeatable} on the element, if any.
     *
     * @param classElement The annotation element
     * @return The container annotation's qualified name, or null
     */
    public static @Nullable String repeatableContainerName(@Nullable ClassElement classElement) {
        if (classElement == null) {
            return null;
        }
        AnnotationMetadata metadata = classElement.getAnnotationMetadata();
        if (metadata.hasAnnotation(Repeatable.class.getName())) {
            AnnotationClassValue<?> container = metadata.getValue(Repeatable.class.getName(), AnnotationMetadata.VALUE_MEMBER, AnnotationClassValue.class).orElse(null);
            if (container != null) {
                return container.getName();
            }
        }
        TypeElement typeElement = typeElement(classElement);
        if (typeElement != null) {
            for (AnnotationMirror mirror : typeElement.getAnnotationMirrors()) {
                if (!Repeatable.class.getName().equals(mirror.getAnnotationType().toString())) {
                    continue;
                }
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : mirror.getElementValues().entrySet()) {
                    if (AnnotationMetadata.VALUE_MEMBER.contentEquals(entry.getKey().getSimpleName())) {
                        Object value = entry.getValue().getValue();
                        if (value instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement container) {
                            return container.getQualifiedName().toString();
                        }
                        return value == null ? null : value.toString();
                    }
                }
            }
            return null;
        }
        if (classElement.getNativeType() instanceof Class<?> type) {
            Repeatable repeatable = type.getAnnotation(Repeatable.class);
            return repeatable == null ? null : repeatable.value().getName();
        }
        return null;
    }

    private static boolean contains(ElementType[] targets) {
        return contains(targets, ElementType.ANNOTATION_TYPE);
    }

    private static boolean contains(ElementType[] targets, ElementType expected) {
        for (ElementType target : targets) {
            if (target == expected) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable TypeElement typeElement(ClassElement classElement) {
        try {
            if (classElement.getNativeType() instanceof JavaNativeElement.Class nativeClass) {
                return nativeClass.element();
            }
        } catch (RuntimeException e) {
            // Not a Java-backed element (a Python element, or one without a native type).
        }
        return null;
    }

    /**
     * A type nested in an annotation, the way the generated decorator exposes it as a member.
     *
     * @param element The nested type
     * @param name The qualified name
     * @param simpleName The member name on the decorator
     * @param annotation Whether the nested type is itself an annotation
     * @param repeatableName The container annotation of a repeatable nested annotation, or null
     */
    public record NestedType(ClassElement element, String name, String simpleName, boolean annotation, @Nullable String repeatableName) {
    }
}
