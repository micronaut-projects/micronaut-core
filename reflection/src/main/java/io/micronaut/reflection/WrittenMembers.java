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
package io.micronaut.reflection;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import org.jspecify.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The members of an annotation the source actually wrote, read from the class file of the type declaring the
 * element it annotates.
 *
 * <p>An annotation instance cannot answer this. {@code AnnotationParser} completes the members it reads with the
 * defaults of the type before it builds the instance, so a member omitted and a member written as its default
 * answer alike, while the processors record only what the source writes. The class file keeps the distinction:
 * the {@code element_value_pairs} of a {@code RuntimeVisibleAnnotations} entry are the pairs the compiler emitted,
 * which are the pairs the source wrote.</p>
 *
 * <p>What cannot be read - a type with no class file to hand, a proxy, a class defined at runtime - answers that
 * it knows nothing, and the caller keeps the behaviour it had before: a member equal to its default is left out.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
final class WrittenMembers {

    /**
     * Nothing is known about the element, so nothing is reported as explicitly written.
     */
    private static final Map<String, Map<String, Set<String>>> UNKNOWN = Map.of();

    private static final ClassValue<Map<String, Map<String, Set<String>>>> BY_DECLARING_TYPE =
        new ClassValue<>() {
            @Override
            protected Map<String, Map<String, Set<String>>> computeValue(Class<?> type) {
                try {
                    return read(type);
                } catch (IOException | RuntimeException e) {
                    ClassUtils.REFLECTION_LOGGER.debug("Cannot read the class file of [{}], a member written as its "
                        + "default is not told from one left out", type.getName(), e);
                    return UNKNOWN;
                }
            }
        };

    private WrittenMembers() {
    }

    /**
     * The members of one annotation that the source wrote on one element.
     *
     * @param element        The annotated element
     * @param annotationType The annotation
     * @return The member names written, or {@code null} when the class file cannot say
     */
    @Nullable
    static Set<String> of(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        Class<?> declaring = declaringType(element);
        if (declaring == null) {
            return null;
        }
        Map<String, Map<String, Set<String>>> byElement = BY_DECLARING_TYPE.get(declaring);
        if (byElement.isEmpty()) {
            return null;
        }
        Map<String, Set<String>> byAnnotation = byElement.get(key(element));
        return byAnnotation == null ? null : byAnnotation.get(annotationType.getName());
    }

    @Nullable
    private static Class<?> declaringType(AnnotatedElement element) {
        if (element instanceof Class<?> type) {
            return type;
        }
        if (element instanceof Field field) {
            return field.getDeclaringClass();
        }
        if (element instanceof Executable executable) {
            return executable.getDeclaringClass();
        }
        if (element instanceof Parameter parameter) {
            return parameter.getDeclaringExecutable().getDeclaringClass();
        }
        return null;
    }

    /**
     * The key an element is filed under: the shapes below are the ones the class file distinguishes.
     */
    private static String key(AnnotatedElement element) {
        if (element instanceof Class<?>) {
            return "";
        }
        if (element instanceof Field field) {
            return "f " + field.getName();
        }
        if (element instanceof Method method) {
            return "m " + method.getName() + descriptor(method.getParameterTypes(), method.getReturnType());
        }
        if (element instanceof Constructor<?> constructor) {
            return "m <init>" + descriptor(constructor.getParameterTypes(), void.class);
        }
        if (element instanceof Parameter parameter) {
            Executable executable = parameter.getDeclaringExecutable();
            int index = 0;
            for (Parameter candidate : executable.getParameters()) {
                if (candidate.equals(parameter)) {
                    break;
                }
                index++;
            }
            return "p " + key(executable) + " " + index;
        }
        return "";
    }

    private static String descriptor(Class<?>[] parameterTypes, Class<?> returnType) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameterType : parameterTypes) {
            descriptor.append(typeDescriptor(parameterType));
        }
        return descriptor.append(')').append(typeDescriptor(returnType)).toString();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) {
            return "[" + typeDescriptor(type.getComponentType());
        }
        if (!type.isPrimitive()) {
            return "L" + type.getName().replace('.', '/') + ";";
        }
        if (type == void.class) {
            return "V";
        }
        if (type == boolean.class) {
            return "Z";
        }
        if (type == byte.class) {
            return "B";
        }
        if (type == char.class) {
            return "C";
        }
        if (type == short.class) {
            return "S";
        }
        if (type == int.class) {
            return "I";
        }
        if (type == long.class) {
            return "J";
        }
        if (type == float.class) {
            return "F";
        }
        return "D";
    }

    private static Map<String, Map<String, Set<String>>> read(Class<?> type) throws IOException {
        String resource = type.getName();
        int nested = resource.lastIndexOf('.');
        resource = (nested < 0 ? resource : resource.substring(nested + 1)) + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            if (stream == null) {
                return UNKNOWN;
            }
            return new Reader(new DataInputStream(stream)).parse();
        }
    }

    /**
     * Enough of the class file format to reach the annotation attributes: the constant pool for the strings they
     * name, and then the fields, the methods and the class itself, every other attribute skipped by its length.
     */
    private static final class Reader {

        private final DataInputStream in;
        private String[] strings = new String[0];
        private final Map<String, Map<String, Set<String>>> written = new HashMap<>();

        Reader(DataInputStream in) {
            this.in = in;
        }

        Map<String, Map<String, Set<String>>> parse() throws IOException {
            if (in.readInt() != 0xCAFEBABE) {
                return UNKNOWN;
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            constantPool();
            in.readUnsignedShort();
            in.readUnsignedShort();
            in.readUnsignedShort();
            skip(in.readUnsignedShort() * 2);
            members("f ");
            members("m ");
            attributes("");
            return written;
        }

        private void constantPool() throws IOException {
            int count = in.readUnsignedShort();
            strings = new String[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> strings[i] = in.readUTF();
                    case 7, 8, 16, 19, 20 -> skip(2);
                    case 15 -> skip(3);
                    case 5, 6 -> {
                        skip(8);
                        // a long or a double takes two entries
                        i++;
                    }
                    default -> skip(4);
                }
            }
        }

        private void members(String kind) throws IOException {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                in.readUnsignedShort();
                String name = strings[in.readUnsignedShort()];
                String descriptor = strings[in.readUnsignedShort()];
                attributes("f ".equals(kind) ? kind + name : kind + name + descriptor);
            }
        }

        private void attributes(String key) throws IOException {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                String name = strings[in.readUnsignedShort()];
                int length = in.readInt();
                switch (name == null ? "" : name) {
                    case "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations" -> annotations(key);
                    case "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations" -> {
                        int parameters = in.readUnsignedByte();
                        for (int parameter = 0; parameter < parameters; parameter++) {
                            annotations("p " + key + " " + parameter);
                        }
                    }
                    default -> skip(length);
                }
            }
        }

        private void annotations(String key) throws IOException {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                annotation(key);
            }
        }

        private void annotation(String key) throws IOException {
            String descriptor = strings[in.readUnsignedShort()];
            int pairs = in.readUnsignedShort();
            Set<String> members = new LinkedHashSet<>(Math.max(pairs, 1));
            for (int i = 0; i < pairs; i++) {
                members.add(strings[in.readUnsignedShort()]);
                value();
            }
            if (descriptor != null && descriptor.length() > 2) {
                String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
                written.computeIfAbsent(key, ignored -> new HashMap<>(4)).put(name, members);
            }
        }

        private void value() throws IOException {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case '@' -> {
                    // a nested annotation is read for its structure only: the caller asks about the members of
                    // the annotation on the element, not of the ones nested inside it
                    in.readUnsignedShort();
                    int pairs = in.readUnsignedShort();
                    for (int i = 0; i < pairs; i++) {
                        in.readUnsignedShort();
                        value();
                    }
                }
                case '[' -> {
                    int values = in.readUnsignedShort();
                    for (int i = 0; i < values; i++) {
                        value();
                    }
                }
                case 'e' -> skip(4);
                default -> skip(2);
            }
        }

        private void skip(int bytes) throws IOException {
            in.skipNBytes(bytes);
        }
    }
}
