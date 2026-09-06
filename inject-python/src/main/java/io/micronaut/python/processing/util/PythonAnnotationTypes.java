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
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
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
            for (Method method : type.getDeclaredMethods()) {
                names.add(method.getReturnType().getName());
            }
        }
        return names;
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
        for (ElementType target : targets) {
            if (target == ElementType.ANNOTATION_TYPE) {
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
