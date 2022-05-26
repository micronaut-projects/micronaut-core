/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.tree;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable class representing a json node. Json nodes can be either scalar (string, number, boolean, null) or
 * containers (object, array).
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
public abstract class JsonNode {
    JsonNode() {
    }

    /**
     * @return The singleton node representing {@code null}.
     */
    @NonNull
    public static JsonNode nullNode() {
        return JsonNull.INSTANCE;
    }

    /**
     * @param nodes The nodes in this array. Must not be modified after this method is called.
     * @return The immutable array node.
     */
    @NonNull
    public static JsonNode createArrayNode(@NonNull List<JsonNode> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        return new JsonArray(nodes);
    }

    /**
     * @param nodes The nodes in this object. Must not be modified after this method is called.
     * @return The immutable array node.
     */
    @NonNull
    public static JsonNode createObjectNode(Map<String, JsonNode> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        return new JsonObject(nodes);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given boolean value.
     */
    @NonNull
    public static JsonNode createBooleanNode(boolean value) {
        return JsonBoolean.valueOf(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given string value.
     */
    @NonNull
    public static JsonNode createStringNode(@NonNull String value) {
        Objects.requireNonNull(value, "value");
        return new JsonString(value);
    }

    /**
     * Hidden, so that we don't have to check that the number type is supported.
     *
     * @param value The raw numeric value.
     * @return The number node.
     */
    @Internal
    public static JsonNode createNumberNodeImpl(Number value) {
        Objects.requireNonNull(value, "value");
        return new JsonNumber(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public static JsonNode createNumberNode(int value) {
        return createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public static JsonNode createNumberNode(long value) {
        return createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public static JsonNode createNumberNode(@NonNull BigDecimal value) {
        return createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public static JsonNode createNumberNode(float value) {
        return createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public static JsonNode createNumberNode(double value) {
        return createNumberNodeImpl(value);
    }

    /**
     * @param value The value of the node.
     * @return A json node representing the given numeric value.
     */
    @NonNull
    public static JsonNode createNumberNode(@NonNull BigInteger value) {
        return createNumberNodeImpl(value);
    }

    /**
     * @return {@code true} iff this is a number node.
     */
    public boolean isNumber() {
        return false;
    }

    /**
     * @return The raw numeric value of this node. Always full precision.
     * @throws IllegalStateException if this is not a number node.
     */
    @NonNull
    public Number getNumberValue() {
        throw new IllegalStateException("Not a number");
    }

    /**
     * @return The value of this number node, converted to {@code int}. May lose precision.
     * @throws IllegalStateException if this is not a number node.
     */
    public final int getIntValue() {
        return getNumberValue().intValue();
    }

    /**
     * @return The value of this number node, converted to {@code long}. May lose precision.
     * @throws IllegalStateException if this is not a number node.
     */
    public final long getLongValue() {
        return getNumberValue().longValue();
    }

    /**
     * @return The value of this number node, converted to {@code float}. May lose precision.
     * @throws IllegalStateException if this is not a number node.
     */
    public final float getFloatValue() {
        return getNumberValue().floatValue();
    }

    /**
     * @return The value of this number node, converted to {@code double}. May lose precision.
     * @throws IllegalStateException if this is not a number node.
     */
    public final double getDoubleValue() {
        return getNumberValue().doubleValue();
    }

    /**
     * @return The value of this number node, converted to {@link BigInteger}. May lose the decimal part.
     * @throws IllegalStateException if this is not a number node.
     */
    @NonNull
    public final BigInteger getBigIntegerValue() {
        Number numberValue = getNumberValue();
        if (numberValue instanceof BigInteger) {
            return (BigInteger) numberValue;
        } else if (numberValue instanceof BigDecimal) {
            return ((BigDecimal) numberValue).toBigInteger();
        } else {
            return BigInteger.valueOf(numberValue.longValue());
        }
    }

    /**
     * @return The value of this number node, converted to {@link BigDecimal}.
     * @throws IllegalStateException if this is not a number node.
     */
    @NonNull
    public final BigDecimal getBigDecimalValue() {
        Number numberValue = getNumberValue();
        if (numberValue instanceof BigInteger) {
            return new BigDecimal((BigInteger) numberValue);
        } else if (numberValue instanceof BigDecimal) {
            return (BigDecimal) numberValue;
        } else if (numberValue instanceof Long) {
            return BigDecimal.valueOf(numberValue.longValue());
        } else {
            // all other types, including the int types, fit into double
            return BigDecimal.valueOf(numberValue.doubleValue());
        }
    }

    /**
     * @return {@code true} iff this is a string node.
     */
    public boolean isString() {
        return false;
    }

    /**
     * @return The value of this string node.
     * @throws IllegalStateException if this is not a string node.
     */
    @NonNull
    public String getStringValue() {
        throw new IllegalStateException("Not a string");
    }

    /**
     * Attempt to coerce this node to a string.
     *
     * @return The coerced string value.
     * @throws IllegalStateException if this node is not a scalar value
     */
    @NonNull
    public String coerceStringValue() {
        throw new IllegalStateException("Not a scalar value");
    }

    /**
     * @return {@code true} iff this is a boolean node.
     */
    public boolean isBoolean() {
        return false;
    }

    /**
     * @return The value of this boolean node.
     * @throws IllegalStateException if this is not a boolean node.
     */
    public boolean getBooleanValue() {
        throw new IllegalStateException("Not a boolean");
    }

    /**
     * @return {@code true} iff this is the null node.
     */
    public boolean isNull() {
        return false;
    }

    /**
     * @return The number of immediate children of this node, or {@code 0} if this is not a container node.
     */
    public abstract int size();

    /**
     * @return An {@link Iterable} of all values of this array or object node.
     * @throws IllegalStateException if this is not a container node.
     */
    @NonNull
    public abstract Iterable<JsonNode> values();

    /**
     * @return An {@link Iterable} of all entries of this object node.
     * @throws IllegalStateException if this is not an object node.
     */
    @NonNull
    public abstract Iterable<Map.Entry<String, JsonNode>> entries();

    /**
     * @return {@code true} iff this node is a value node (string, number, boolean, null).
     */
    public boolean isValueNode() {
        return false;
    }

    /**
     * @return {@code true} iff this node is a container node (array or object).
     */
    public boolean isContainerNode() {
        return false;
    }

    /**
     * @return {@code true} iff this node is an array node.
     */
    public boolean isArray() {
        return false;
    }

    /**
     * @return {@code true} iff this node is an object node.
     */
    public boolean isObject() {
        return false;
    }

    /**
     * @param fieldName The field name.
     * @return The field with the given name, or {@code null} if there is no such field or this is not an object.
     */
    @Nullable
    public abstract JsonNode get(@NonNull String fieldName);

    /**
     * @param index The index into this array.
     * @return The field at the given index, or {@code null} if there is no such field or this is not an array.
     */
    @Nullable
    public abstract JsonNode get(int index);
}
