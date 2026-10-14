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
package io.micronaut.core.annotation;

import io.micronaut.core.convert.ConversionService;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The conversions of a raw value to a basic type: a string, a number, a boolean or a character.
 *
 * <p>Each method checks for the form the value most likely has and returns it directly, and only otherwise
 * delegates to the {@link ConversionService}. The methods that return an object answer {@code null} for a
 * {@code null} value; the ones that return a primitive answer zero or {@code false}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public final class AnnotationConversionUtils {

    private AnnotationConversionUtils() {
    }

    /**
     * Converts a value to the given type, with the conversion service.
     *
     * @param value             The value
     * @param type              The type
     * @param conversionService The conversion service
     * @param <T>               The type
     * @return The converted value, null when the value is null
     * @throws IllegalArgumentException When the value cannot be converted
     */
    public static <T> @Nullable T convert(@Nullable Object value, Class<T> type, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return conversionService.convert(value, type).orElseThrow(() -> new IllegalArgumentException(
            "Cannot convert the value [" + value + "] to " + type.getTypeName()));
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a string
     */
    public static @Nullable String toString(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof AnnotationClassValue<?> classValue) {
            return classValue.getName();
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        return convert(value, String.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as an {@link Integer}
     */
    public static @Nullable Integer toInteger(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return convert(value, Integer.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as an {@code int}, zero for null
     */
    public static int toInt(@Nullable Object value, ConversionService conversionService) {
        Integer integer = toInteger(value, conversionService);
        return integer == null ? 0 : integer;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Long}
     */
    public static @Nullable Long toLongObject(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return convert(value, Long.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code long}, zero for null
     */
    public static long toLong(@Nullable Object value, ConversionService conversionService) {
        Long number = toLongObject(value, conversionService);
        return number == null ? 0L : number;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Short}
     */
    public static @Nullable Short toShortObject(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Short number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.shortValue();
        }
        return convert(value, Short.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code short}, zero for null
     */
    public static short toShort(@Nullable Object value, ConversionService conversionService) {
        Short number = toShortObject(value, conversionService);
        return number == null ? 0 : number;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Byte}
     */
    public static @Nullable Byte toByteObject(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Byte number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.byteValue();
        }
        return convert(value, Byte.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code byte}, zero for null
     */
    public static byte toByte(@Nullable Object value, ConversionService conversionService) {
        Byte number = toByteObject(value, conversionService);
        return number == null ? 0 : number;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Float}
     */
    public static @Nullable Float toFloatObject(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Float number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return convert(value, Float.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code float}, zero for null
     */
    public static float toFloat(@Nullable Object value, ConversionService conversionService) {
        Float number = toFloatObject(value, conversionService);
        return number == null ? 0f : number;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Double}
     */
    public static @Nullable Double toDoubleObject(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return convert(value, Double.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code double}, zero for null
     */
    public static double toDouble(@Nullable Object value, ConversionService conversionService) {
        Double number = toDoubleObject(value, conversionService);
        return number == null ? 0d : number;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Boolean}
     */
    public static @Nullable Boolean toBooleanObject(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return convert(value, Boolean.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code boolean}, false for null
     */
    public static boolean toBoolean(@Nullable Object value, ConversionService conversionService) {
        Boolean bool = toBooleanObject(value, conversionService);
        return bool != null && bool;
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@link Character}
     */
    public static @Nullable Character toCharacter(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Character character) {
            return character;
        }
        return convert(value, Character.class, conversionService);
    }

    /**
     * @param value             The value
     * @param conversionService The conversion service
     * @return The value as a {@code char}, zero for null
     */
    public static char toChar(@Nullable Object value, ConversionService conversionService) {
        Character character = toCharacter(value, conversionService);
        return character == null ? 0 : character;
    }

    /**
     * The elements of an array, a collection or a single value.
     *
     * @param value The value
     * @return The elements
     */
    private static List<Object> elements(Object value) {
        if (value instanceof Object[] array) {
            return List.of(array);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof CharSequence sequence && !(value instanceof AnnotationClassValue<?>)) {
            // a comma separated list, as the conversion service reads a string as an array
            String string = sequence.toString();
            return string.isEmpty() ? List.of() : new ArrayList<>(List.of((Object[]) string.split(",")));
        }
        return List.of(value);
    }

    /**
     * Converts a value to a {@code String[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static String @Nullable [] toStringArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof String[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        String[] result = new String[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toString(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to an {@code int[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static int @Nullable [] toIntArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof int[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        int[] result = new int[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toInt(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code long[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static long @Nullable [] toLongArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof long[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        long[] result = new long[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toLong(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code short[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static short @Nullable [] toShortArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof short[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        short[] result = new short[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toShort(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code byte[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static byte @Nullable [] toByteArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        byte[] result = new byte[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toByte(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code float[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static float @Nullable [] toFloatArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof float[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        float[] result = new float[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toFloat(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code double[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static double @Nullable [] toDoubleArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof double[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        double[] result = new double[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toDouble(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code boolean[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static boolean @Nullable [] toBooleanArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof boolean[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        boolean[] result = new boolean[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toBoolean(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to a {@code char[]}: an array of that type is returned as it is, an array or
     * a collection is converted element by element, and a single value is an array of one.
     *
     * @param value             The value
     * @param conversionService The conversion service
     * @return The array, null for null
     */
    public static char @Nullable [] toCharArray(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof char[] array) {
            return array;
        }
        List<Object> elements = elements(value);
        char[] result = new char[elements.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = toChar(elements.get(i), conversionService);
        }
        return result;
    }

    /**
     * Converts a value to an enum constant, by name: a constant is read by its name, and so is a string. The
     * constants are the ones of the enum type, which the generated code supplies, so that nothing is looked up
     * reflectively.
     *
     * @param value     The value, a constant or its name
     * @param constants The constants of the enum type
     * @param <E>       The enum type
     * @return The constant, null for null
     * @throws IllegalArgumentException When the enum type has no constant of that name
     */
    public static <E extends Enum<E>> @Nullable E toEnum(@Nullable Object value, E[] constants) {
        if (value == null) {
            return null;
        }
        String name = value instanceof Enum<?> constant ? constant.name() : value.toString();
        for (E constant : constants) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("No enum constant [" + name + "]");
    }

    /**
     * Converts a value to an array of enum constants: an array, or a collection, is converted element by element,
     * and a single value is an array of one.
     *
     * @param value     The value
     * @param constants The constants of the enum type
     * @param <E>       The enum type
     * @return The constants, null for null
     */
    public static <E extends Enum<E>> E @Nullable [] toEnumArray(@Nullable Object value, E[] constants) {
        if (value == null) {
            return null;
        }
        List<Object> elements = elements(value);
        // the constants make an array of the right type, of the size needed
        E[] result = Arrays.copyOf(constants, elements.size());
        for (int i = 0; i < result.length; i++) {
            result[i] = toEnum(elements.get(i), constants);
        }
        return result;
    }

    /**
     * The raw value of a member: the given one, or the default of the annotation type.
     *
     * @param values   The given values
     * @param defaults The defaults
     * @param name     The member name
     * @return The raw value, null when there is none
     */
    public static @Nullable Object member(Map<? extends CharSequence, ?> values, Map<CharSequence, Object> defaults, String name) {
        Object value = values.get(name);
        return value != null ? value : defaults.get(name);
    }

    /**
     * @param value             The raw value: a class, an {@link AnnotationClassValue} or a class name
     * @param conversionService The conversion service, loading a class by its name
     * @return The class, null when absent
     */
    public static @Nullable Class<?> toClass(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Class<?> type) {
            return type;
        }
        if (value instanceof AnnotationClassValue<?> classValue) {
            Class<?> type = classValue.getType().orElse(null);
            return type != null ? type : convert(classValue.getName(), Class.class, conversionService);
        }
        if (value instanceof CharSequence name) {
            return convert(name.toString(), Class.class, conversionService);
        }
        throw new IllegalArgumentException("Not a class: " + value);
    }

    /**
     * @param value             The raw value: an array or a collection of what {@link #toClass} reads, or one of it
     * @param conversionService The conversion service
     * @return The classes, null when absent
     */
    public static Class<?> @Nullable [] toClasses(@Nullable Object value, ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (value instanceof Class<?>[] classes) {
            return classes;
        }
        List<Object> elements = elements(value);
        Class<?>[] classes = new Class<?>[elements.size()];
        for (int i = 0; i < classes.length; i++) {
            classes[i] = toClass(elements.get(i), conversionService);
        }
        return classes;
    }

    /**
     * Finds the builder of a nested annotation type, for a generated class to keep and pass to {@link #toAnnotation}.
     *
     * @param type The annotation type
     * @param <T>  The annotation type
     * @return The builder, null when none is registered
     */
    public static <T extends Annotation> @Nullable AnnotationBuilder<T> annotationBuilder(Class<T> type) {
        return AnnotationBuilderRegistry.shared().find(type).orElse(null);
    }

    /**
     * Reads a nested annotation from an instance, an {@link AnnotationValue} or a map of its members, through the
     * builder registered for its type, or the conversion service when there is none. With a builder, the annotation
     * is always one the builder created, which is an {@link AnnotationValueProvider}.
     *
     * @param value             The raw value
     * @param type              The annotation type
     * @param builder           The builder of the annotation type, from {@link #annotationBuilder}, null when there is none
     * @param conversionService The conversion service
     * @param <T>               The annotation type
     * @return The annotation, null when absent
     */
    public static <T extends Annotation> @Nullable T toAnnotation(@Nullable Object value,
                                                                  Class<T> type,
                                                                  @Nullable AnnotationBuilder<T> builder,
                                                                  ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            if (builder == null || value instanceof AnnotationValueProvider) {
                return type.cast(value);
            }
            // an annotation of the JVM is built again, so that the annotation answers its annotation value
            value = AnnotationValue.of(type.cast(value));
        }
        if (builder != null) {
            if (value instanceof AnnotationValue<?> annotationValue) {
                return builder.build(annotationValue, conversionService);
            }
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked") Map<CharSequence, Object> members = (Map<CharSequence, Object>) map;
                return builder.build(members, conversionService);
            }
        }
        return convert(value, type, conversionService);
    }

    /**
     * Reads the nested annotations of an array member: an array or a collection of what {@link #toAnnotation}
     * reads, or one of it.
     *
     * @param value             The raw value
     * @param type              The annotation type
     * @param builder           The builder of the annotation type, from {@link #annotationBuilder}, null when there is none
     * @param conversionService The conversion service
     * @param <T>               The annotation type
     * @return The annotations, null when absent
     */
    public static <T extends Annotation> @Nullable List<T> toAnnotations(@Nullable Object value,
                                                                         Class<T> type,
                                                                         @Nullable AnnotationBuilder<T> builder,
                                                                         ConversionService conversionService) {
        if (value == null) {
            return null;
        }
        List<Object> elements = elements(value);
        List<T> annotations = new ArrayList<>(elements.size());
        for (Object element : elements) {
            annotations.add(toAnnotation(element, type, builder, conversionService));
        }
        return annotations;
    }

    /**
     * The class members in the form the annotation metadata records them.
     *
     * @param types The classes
     * @return The class values
     */
    public static AnnotationClassValue<?>[] toClassValues(Class<?>[] types) {
        AnnotationClassValue<?>[] values = new AnnotationClassValue<?>[types.length];
        for (int i = 0; i < types.length; i++) {
            values[i] = new AnnotationClassValue<>(types[i]);
        }
        return values;
    }

    /**
     * The enum members in the form the annotation metadata records them: the names of the constants.
     *
     * @param constants The constants
     * @return The names
     */
    public static String[] toEnumNames(Enum<?>[] constants) {
        String[] names = new String[constants.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = constants[i].name();
        }
        return names;
    }

    /**
     * The nested annotations in the form the annotation metadata records them: the annotation value of each, which
     * the annotations a builder created provide.
     *
     * @param annotations The annotations
     * @return The annotation values
     */
    public static AnnotationValue<?>[] toAnnotationValues(Annotation[] annotations) {
        AnnotationValue<?>[] values = new AnnotationValue<?>[annotations.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = annotations[i] instanceof AnnotationValueProvider<?> provider
                ? provider.annotationValue()
                : AnnotationValue.of(annotations[i]);
        }
        return values;
    }
}
