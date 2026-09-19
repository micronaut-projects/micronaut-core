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
package io.micronaut.context.python.runtime;

import io.micronaut.core.annotation.Internal;
import org.objectweb.asm.Type;

import java.lang.reflect.Array;
import java.util.Map;

/**
 * The type names of the model: binary class names, with {@code []} suffixes for arrays, and how they map to classes
 * and to JVM descriptors.
 *
 * @since 5.3.0
 */
@Internal
public final class ModelTypes {

    private static final Map<String, Class<?>> PRIMITIVES = Map.of(
        "boolean", boolean.class, "byte", byte.class, "short", short.class, "int", int.class, "long", long.class,
        "float", float.class, "double", double.class, "char", char.class, "void", void.class);
    private static final Map<String, Type> PRIMITIVE_TYPES = Map.of(
        "boolean", Type.BOOLEAN_TYPE, "byte", Type.BYTE_TYPE, "short", Type.SHORT_TYPE, "int", Type.INT_TYPE, "long", Type.LONG_TYPE,
        "float", Type.FLOAT_TYPE, "double", Type.DOUBLE_TYPE, "char", Type.CHAR_TYPE, "void", Type.VOID_TYPE);

    private ModelTypes() {
    }

    /**
     * Resolves a type name.
     *
     * @param typeName    The type name
     * @param classLoader The class loader
     * @return The class
     * @throws ClassNotFoundException If the class cannot be loaded
     */
    public static Class<?> resolve(String typeName, ClassLoader classLoader) throws ClassNotFoundException {
        if (typeName.endsWith("[]")) {
            return Array.newInstance(resolve(typeName.substring(0, typeName.length() - 2), classLoader), 0).getClass();
        }
        Class<?> primitive = PRIMITIVES.get(typeName);
        if (primitive != null) {
            return primitive;
        }
        return Class.forName(typeName, false, classLoader);
    }

    /**
     * The ASM type of a type name.
     *
     * @param typeName The type name
     * @return The type
     */
    public static Type type(String typeName) {
        if (typeName.endsWith("[]")) {
            return Type.getType("[" + type(typeName.substring(0, typeName.length() - 2)).getDescriptor());
        }
        Type primitive = PRIMITIVE_TYPES.get(typeName);
        if (primitive != null) {
            return primitive;
        }
        return Type.getObjectType(typeName.replace('.', '/'));
    }

    /**
     * The type name of a class.
     *
     * @param type The class
     * @return The type name
     */
    public static String nameOf(Class<?> type) {
        if (type.isArray()) {
            return nameOf(type.getComponentType()) + "[]";
        }
        return type.getName();
    }

    /**
     * @param typeName The type name
     * @return Whether the type is primitive
     */
    public static boolean isPrimitive(String typeName) {
        return PRIMITIVES.containsKey(typeName);
    }
}
