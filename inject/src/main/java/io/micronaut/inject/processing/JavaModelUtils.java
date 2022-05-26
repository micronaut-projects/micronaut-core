/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.TypedElement;
import org.objectweb.asm.Type;

import javax.lang.model.element.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility methods for Java model handling.
 *
 * @author graemerocher
 * @since 1.0
 */
public class JavaModelUtils {

    public static final Map<String, String> NAME_TO_TYPE_MAP = new HashMap<>();
    private static final ElementKind RECORD_KIND = ReflectionUtils.findDeclaredField(ElementKind.class, "RECORD").flatMap(field -> {
        try {
            return Optional.of((ElementKind) field.get(ElementKind.class));
        } catch (IllegalAccessException e) {
            return Optional.empty();
        }
    }).orElse(null);
    private static final ElementKind RECORD_COMPONENT_KIND = ReflectionUtils.findDeclaredField(ElementKind.class, "RECORD_COMPONENT").flatMap(field -> {
        try {
            return Optional.of((ElementKind) field.get(ElementKind.class));
        } catch (IllegalAccessException e) {
            return Optional.empty();
        }
    }).orElse(null);

    static {
        JavaModelUtils.NAME_TO_TYPE_MAP.put("void", "V");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("boolean", "Z");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("char", "C");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("int", "I");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("byte", "B");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("long", "J");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("double", "D");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("float", "F");
        JavaModelUtils.NAME_TO_TYPE_MAP.put("short", "S");
    }

    /**
     * The Java APT throws an internal exception {code com.sun.tools.javac.code.Symbol$CompletionFailure} if a class is missing from the classpath and {@link Element#getKind()} is called. This method
     * handles exceptions when calling the getKind() method to avoid this scenario and should be used instead of {@link Element#getKind()}.
     *
     * @param element The element
     * @return The kind if it is resolvable
     */
    public static Optional<ElementKind> resolveKind(Element element) {
        if (element != null) {

            try {
                final ElementKind kind = element.getKind();
                return Optional.of(kind);
            } catch (Exception e) {
                // ignore and fall through to empty
            }
        }
        return Optional.empty();
    }

    /**
     * The Java APT throws an internal exception {code com.sun.tools.javac.code.Symbol$CompletionFailure} if a class is missing from the classpath and {@link Element#getKind()} is called. This method
     * handles exceptions when calling the getKind() method to avoid this scenario and should be used instead of {@link Element#getKind()}.
     *
     * @param element The element
     * @param expected The expected kind
     * @return The kind if it is resolvable and matches the expected kind
     */
    public static Optional<ElementKind> resolveKind(Element element, ElementKind expected) {
        final Optional<ElementKind> elementKind = resolveKind(element);
        if (elementKind.isPresent() && elementKind.get() == expected) {
            return elementKind;
        }
        return Optional.empty();
    }

    /**
     * Whether the given element is an interface.
     *
     * @param element The element
     * @return True if it is
     */
    public static boolean isInterface(Element element) {
        return resolveKind(element, ElementKind.INTERFACE).isPresent();
    }

    /**
     * Whether the given element is an interface.
     *
     * @param element The element
     * @return True if it is
     */
    public static boolean isRecord(Element element) {
        return resolveKind(element, RECORD_KIND).isPresent();
    }

    /**
     * Whether the given element is a class.
     *
     * @param element The element
     * @return True if it is
     */
    public static boolean isClass(Element element) {
        return resolveKind(element, ElementKind.CLASS).isPresent();
    }

    /**
     * Whether the given element is an enum.
     *
     * @param element The element
     * @return True if it is
     */
    public static boolean isEnum(Element element) {
        return resolveKind(element, ElementKind.ENUM).isPresent();
    }

    /**
     * Whether the given element is a class or interface.
     *
     * @param element The element
     * @return True if it is
     */
    public static boolean isClassOrInterface(Element element) {
        return isInterface(element) || isClass(element);
    }

    /**
     * Get the class name for the given type element. Handles {@link NestingKind}.
     *
     * @param typeElement The type element
     * @return The class name
     */
    public static String getClassName(TypeElement typeElement) {
        Name qualifiedName = typeElement.getQualifiedName();
        NestingKind nestingKind;
        try {
            nestingKind = typeElement.getNestingKind();
            if (nestingKind == NestingKind.MEMBER) {
                TypeElement enclosingElement = typeElement;
                StringBuilder builder = new StringBuilder();
                while (nestingKind == NestingKind.MEMBER) {
                    builder.insert(0, '$').insert(1, enclosingElement.getSimpleName());
                    Element enclosing = enclosingElement.getEnclosingElement();

                    if (enclosing instanceof TypeElement) {
                        enclosingElement = (TypeElement) enclosing;
                        nestingKind = enclosingElement.getNestingKind();
                    } else {
                        break;
                    }
                }
                Name enclosingName = enclosingElement.getQualifiedName();
                return enclosingName.toString() + builder;
            } else {
                return qualifiedName.toString();
            }
        } catch (RuntimeException e) {
            return qualifiedName.toString();
        }
    }

    /**
     * Get the class name for the given type element without the package. Handles {@link NestingKind}.
     *
     * @param typeElement The type element
     * @return The class name
     */
    public static String getClassNameWithoutPackage(TypeElement typeElement) {
        NestingKind nestingKind;
        try {
            nestingKind = typeElement.getNestingKind();
            if (nestingKind == NestingKind.MEMBER) {
                TypeElement enclosingElement = typeElement;
                StringBuilder builder = new StringBuilder();
                while (nestingKind == NestingKind.MEMBER) {
                    builder.insert(0, '$').insert(1, enclosingElement.getSimpleName());
                    Element enclosing = enclosingElement.getEnclosingElement();

                    if (enclosing instanceof TypeElement) {
                        enclosingElement = (TypeElement) enclosing;
                        nestingKind = enclosingElement.getNestingKind();
                    } else {
                        break;
                    }
                }
                Name enclosingName = enclosingElement.getSimpleName();
                return enclosingName.toString() + builder;
            } else {
                return typeElement.getSimpleName().toString();
            }
        } catch (RuntimeException e) {
            return typeElement.getSimpleName().toString();
        }
    }

    public static String getPackageName(TypeElement typeElement) {
        Element enclosingElement = typeElement.getEnclosingElement();
        while (enclosingElement != null && enclosingElement.getKind() != ElementKind.PACKAGE) {
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        if (enclosingElement == null) {
            return StringUtils.EMPTY_STRING;
        } else if (enclosingElement instanceof PackageElement) {
            return ((PackageElement) enclosingElement).getQualifiedName().toString();
        } else {
            return enclosingElement.toString();
        }
    }

    /**
     * Get the array class name for the given type element. Handles {@link NestingKind}.
     *
     * @param typeElement The type element
     * @return The class name
     */
    public static String getClassArrayName(TypeElement typeElement) {
        return "[L" + getClassName(typeElement) + ";";
    }

    /**
     * Return whether this is a record or a component of a record.
     * @param e The element
     * @return True if it is
     */
    public static boolean isRecordOrRecordComponent(Element e) {
        return isRecord(e) || isRecordComponent(e);
    }

    /**
     * Return whether this is a component of a record.
     * @param e The element
     * @return True if it is
     */
    public static boolean isRecordComponent(Element e) {
        return resolveKind(e, RECORD_COMPONENT_KIND).isPresent();
    }

    /**
     * Return the type reference for a class.
     *
     * @param type The type
     * @return The {@link Type}
     */
    public static Type getTypeReference(TypedElement type) {
        ClassElement classElement = type.getType();
        if (type.isPrimitive()) {
            String internalName = NAME_TO_TYPE_MAP.get(classElement.getName());
            if (type.isArray()) {
                StringBuilder name = new StringBuilder(internalName);
                for (int i = 0; i < type.getArrayDimensions(); i++) {
                    name.insert(0, "[");
                }
                return Type.getObjectType(name.toString());
            } else {
                return Type.getType(internalName);
            }
        } else {
            Object nativeType = type.getNativeType();
            if (nativeType instanceof Class) {
                Class<?> t = (Class<?>) nativeType;
                return Type.getType(t);
            } else {
                String internalName = type.getType().getName().replace('.', '/');
                if (internalName.isEmpty()) {
                    return Type.getType(Object.class);
                }
                if (type.isArray()) {
                    StringBuilder name = new StringBuilder(internalName);
                    name.insert(0, "L");
                    for (int i = 0; i < type.getArrayDimensions(); i++) {
                        name.insert(0, "[");
                    }
                    name.append(";");
                    return Type.getObjectType(name.toString());
                } else {
                    return Type.getObjectType(internalName);
                }
            }
        }
    }

    /**
     * Return the type reference for a class.
     *
     * @param type The type
     * @return The {@link Type}
     */
    public static String getClassname(TypedElement type) {
        return getTypeReference(type).getClassName();
    }
}
