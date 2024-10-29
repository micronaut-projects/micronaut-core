/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.TypeDef;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The expressions utils.
 *
 * @author Denis Stepanov
 * @since 4.8
 */
@Internal
public final class ExpressionsUtils {

    private static final ClassTypeDef MAP_TYPE = ClassTypeDef.of(Map.class);
    private static final ClassTypeDef MAP_ENTRY_TYPE = ClassTypeDef.of(Map.Entry.class);
    private static final ClassTypeDef LIST_TYPE = ClassTypeDef.of(List.class);

    /**
     * Create a map of a string key expression.
     *
     * @param map             The map
     * @param skipEmpty       Should skip empty value entry
     * @param empty           Replace the empty entry value with
     * @param objAsExpression The object to expression mapper
     * @param <T>             The value type
     * @return The expression
     */
    public static <T> ExpressionDef stringMapOf(@NonNull
                                                Map<? extends CharSequence, T> map,
                                                boolean skipEmpty,
                                                @Nullable T empty,
                                                @NonNull Function<T, ExpressionDef> objAsExpression) {
        return stringMapOf(map, skipEmpty, empty, null, objAsExpression);
    }

    /**
     * Create a map of a string key expression.
     *
     * @param map             The map
     * @param skipEmpty       Should skip empty value entry
     * @param empty           Replace the empty entry value with
     * @param valuePredicate  The value predicate
     * @param objAsExpression The object to expression mapper
     * @param <T>             The value type
     * @return The expression
     */
    public static <T> ExpressionDef stringMapOf(@NonNull
                                                Map<? extends CharSequence, T> map,
                                                boolean skipEmpty,
                                                @Nullable T empty,
                                                @Nullable
                                                Predicate<T> valuePredicate,
                                                @NonNull Function<T, ExpressionDef> objAsExpression) {
        Set<? extends Map.Entry<String, T>> entrySet = map != null ? map.entrySet()
            .stream()
            .filter(e -> !skipEmpty || (e.getKey() != null) && (valuePredicate == null || valuePredicate.test(e.getValue())))
            .map(e -> e.getValue() == null && empty != null ? new AbstractMap.SimpleEntry<>(e.getKey().toString(), empty) : new AbstractMap.SimpleEntry<>(e.getKey().toString(), e.getValue()))
            .collect(Collectors.toCollection(() -> new TreeSet<>(Map.Entry.comparingByKey()))) : null;
        if (entrySet == null || entrySet.isEmpty()) {
            return MAP_TYPE.invokeStatic("of", MAP_TYPE);
        }
        if (entrySet.size() < 11) {
            List<TypeDef> parameterTypes = new ArrayList<>(entrySet.size());
            List<ExpressionDef> values = new ArrayList<>(entrySet.size());
            for (Map.Entry<String, T> entry : entrySet) {
                parameterTypes.add(TypeDef.OBJECT);
                parameterTypes.add(TypeDef.OBJECT);
                values.add(ExpressionDef.constant(entry.getKey()));
                values.add(objAsExpression.apply(entry.getValue()));
            }
            return MAP_TYPE.invokeStatic("of", parameterTypes, MAP_TYPE, values);
        }
        return MAP_TYPE.invokeStatic("ofEntries",
            List.of(MAP_ENTRY_TYPE.array()),
            MAP_TYPE,
            MAP_ENTRY_TYPE
                .array()
                .instantiate(
                    entrySet.stream().map(e ->
                        mapEntry(
                            ExpressionDef.constant(e.getKey()),
                            objAsExpression.apply(e.getValue())
                        )
                    ).toList()
                )
        );
    }

    /**
     * The map entry expression.
     *
     * @param key   The key
     * @param value The value
     * @return the expression
     */
    public static ExpressionDef mapEntry(ExpressionDef key, ExpressionDef value) {
        return MAP_TYPE.invokeStatic(
            "entry",
            List.of(TypeDef.OBJECT, TypeDef.OBJECT),
            MAP_ENTRY_TYPE,
            key,
            value
        );
    }

    /**
     * The list of string expression.
     * @param strings The strings
     * @return the expression
     */
    public static ExpressionDef listOfString(List<String> strings) {
        return listOf(strings.stream().<ExpressionDef>map(ExpressionDef::constant).toList());
    }

    /**
     * The list of expression.
     * @param values The values
     * @return the expression
     */
    public static ExpressionDef listOf(List<ExpressionDef> values) {
        if (values != null) {
            values = values.stream().filter(Objects::nonNull).toList();
        }
        if (values == null || values.isEmpty()) {
            return LIST_TYPE.invokeStatic("of", LIST_TYPE);
        }
        if (values.size() < 11) {
            List<TypeDef> parameterTypes = new ArrayList<>(values.size());
            for (ExpressionDef ignore : values) {
                parameterTypes.add(TypeDef.OBJECT);
            }
            return LIST_TYPE.invokeStatic("of", parameterTypes, LIST_TYPE, values);
        } else {
            return LIST_TYPE.invokeStatic("of", List.of(TypeDef.OBJECT.array()), LIST_TYPE,
                TypeDef.OBJECT.array().instantiate(values)
            );
        }
    }

}
