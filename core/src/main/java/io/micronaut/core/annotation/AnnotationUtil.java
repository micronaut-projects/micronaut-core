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
package io.micronaut.core.annotation;

import io.micronaut.core.util.StringUtils;

import java.lang.annotation.*;
import java.lang.reflect.AnnotatedElement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal utility methods for annotations. For Internal and framework use only. Do not use in application code.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AnnotationUtil {

    /**
     * Constant for Kotlin metadata.
     */
    public static final String KOTLIN_METADATA = "kotlin.Metadata";

    public static final List<String> INTERNAL_ANNOTATION_NAMES = Arrays.asList(
        Retention.class.getName(),
        "javax.annotation.meta.TypeQualifier",
        "javax.annotation.meta.TypeQualifierNickname",
        "kotlin.annotation.Retention",
        Inherited.class.getName(),
        SuppressWarnings.class.getName(),
        Override.class.getName(),
        Repeatable.class.getName(),
        Documented.class.getName(),
        "kotlin.annotation.MustBeDocumented",
        Target.class.getName(),
        "kotlin.annotation.Target",
        KOTLIN_METADATA
    );

    /**
     * Packages excludes from stereotype processing.
     */
    public static final List<String> STEREOTYPE_EXCLUDES = Arrays.asList(
            "javax.annotation",
            "edu.umd.cs.findbugs.annotations"
    );

    /**
     * Constant indicating an zero annotation.
     */
    public static final Annotation[] ZERO_ANNOTATIONS = new Annotation[0];

    /**
     * Constant indicating an zero annotation.
     */
    public static final AnnotatedElement[] ZERO_ANNOTATED_ELEMENTS = new AnnotatedElement[0];

    /**
     * An empty re-usable element.
     */
    public static final AnnotatedElement EMPTY_ANNOTATED_ELEMENT = new AnnotatedElement() {
        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return ZERO_ANNOTATIONS;
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return ZERO_ANNOTATIONS;
        }
    };

    /**
     * Simple Annotation name used for nullable.
     */
    public static final String NULLABLE = "javax.annotation.Nullable";

    /**
     * Simple Annotation name used for non-null.
     */
    public static final String NON_NULL = "javax.annotation.Nonnull";

    private static final Map<Integer, List<String>> INTERN_LIST_POOL = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> INTERN_MAP_POOL = new ConcurrentHashMap<>();

    /**
     * Converts the given objects into a set of potentially cached and interned strings contained within an internal pool of lists. See {@link String#intern()}.
     *
     * <p>This method serves the purpose of reducing memory footprint by pooling common lists of annotations in compiled {@link AnnotationMetadata}</p>
     *
     * @param objects The objects
     * @return A unmodifiable, pooled set of strings
     */
    @SuppressWarnings({"unused"})
    @UsedByGeneratedCode
    public static List<String> internListOf(Object... objects) {
        if (objects == null || objects.length == 0) {
            return Collections.emptyList();
        }

        Integer hash = Arrays.hashCode(objects);
        return INTERN_LIST_POOL.computeIfAbsent(hash, integer -> StringUtils.internListOf(objects));
    }


    /**
     * Converts the given objects into a map of potentially cached and interned strings where the keys and values are alternating entries in the passed array. See {@link String#intern()}.
     *
     * <p>The values stored at even number positions will be converted to strings and interned.</p>
     *
     * @param values The objects
     * @return An unmodifiable set of strings
     * @see io.micronaut.core.util.CollectionUtils#mapOf(Object...)
     */
    @SuppressWarnings("unused")
    @UsedByGeneratedCode
    public static Map<String, Object> internMapOf(Object... values) {
        if (values == null || values.length == 0) {
            return Collections.emptyMap();
        }
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        // if the length is 2 then only a single annotation is defined, so tried use internal pool
        if (len == 2) {
            Object value = values[1];
            if (value == Collections.EMPTY_MAP) {
                String key = values[0].toString();
                return INTERN_MAP_POOL.computeIfAbsent(key, s ->
                        Collections.singletonMap(s, Collections.emptyMap())
                );
            } else {
                return Collections.singletonMap(
                        values[0].toString(),
                        value
                );
            }

        } else {
            return StringUtils.internMapOf(values);
        }
    }

    /**
     * Calculates the hash code of annotation values.
     *
     * @param values The map to calculate values' hash code
     * @return The hash code
     */
    @SuppressWarnings("MagicNumber")
    public static int calculateHashCode(Map<? extends CharSequence, Object> values) {
        int hashCode = 0;

        for (Map.Entry<? extends CharSequence, Object> member : values.entrySet()) {
            Object value = member.getValue();

            int nameHashCode = member.getKey().hashCode();

            int valueHashCode =
                !value.getClass().isArray() ? value.hashCode() :
                    value.getClass() == boolean[].class ? Arrays.hashCode((boolean[]) value) :
                        value.getClass() == byte[].class ? Arrays.hashCode((byte[]) value) :
                            value.getClass() == char[].class ? Arrays.hashCode((char[]) value) :
                                value.getClass() == double[].class ? Arrays.hashCode((double[]) value) :
                                    value.getClass() == float[].class ? Arrays.hashCode((float[]) value) :
                                        value.getClass() == int[].class ? Arrays.hashCode((int[]) value) :
                                            value.getClass() == long[].class ? Arrays.hashCode(
                                                (long[]) value
                                            ) :
                                                value.getClass() == short[].class ? Arrays
                                                    .hashCode((short[]) value) :
                                                    Arrays.hashCode((Object[]) value);

            hashCode += 127 * nameHashCode ^ valueHashCode;
        }

        return hashCode;
    }

    /**
     * Computes whether 2 annotation values are equal.
     *
     * @param o1 One object
     * @param o2 Another object
     * @return Whether both objects are equal
     */
    public static boolean areEqual(Object o1, Object o2) {
        return
            !o1.getClass().isArray() ? o1.equals(o2) :
                o1.getClass() == boolean[].class ? Arrays.equals((boolean[]) o1, (boolean[]) o2) :
                    o1.getClass() == byte[].class ? Arrays.equals((byte[]) o1, (byte[]) o2) :
                        o1.getClass() == char[].class ? Arrays.equals((char[]) o1, (char[]) o2) :
                            o1.getClass() == double[].class ? Arrays.equals(
                                (double[]) o1,
                                (double[]) o2
                            ) :
                                o1.getClass() == float[].class ? Arrays.equals(
                                    (float[]) o1,
                                    (float[]) o2
                                ) :
                                    o1.getClass() == int[].class ? Arrays.equals(
                                        (int[]) o1,
                                        (int[]) o2
                                    ) :
                                        o1.getClass() == long[].class ? Arrays.equals(
                                            (long[]) o1,
                                            (long[]) o2
                                        ) :
                                            o1.getClass() == short[].class ? Arrays.equals(
                                                (short[]) o1,
                                                (short[]) o2
                                            ) :
                                                Arrays.equals(
                                                    (Object[]) o1,
                                                    (Object[]) o2
                                                );
    }
}
