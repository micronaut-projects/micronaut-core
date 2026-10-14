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
package io.micronaut.inject.utils;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Map;

/**
 * Writes JSON without a JSON library on the processor class path: the configuration metadata, the
 * configuration schemas, the reports and the generated sources that embed JSON literals.
 * <p>
 * The writer builds the document in memory, so no method throws; {@link #writeTo(Writer)} and
 * {@link #toString()} hand the result out. Members are separated automatically: a value written
 * into an array, or a {@link #name(String) name} written into an object, follows the previous one
 * with a comma. Every value of an object must be preceded by its name, and every {@code begin} must
 * be matched by its {@code end} before the document is complete; a misuse fails with an
 * {@link IllegalStateException} rather than producing malformed JSON.
 * <pre>{@code
 * String json = new JsonWriter()
 *     .beginObject()
 *     .name("name").value("timeout")
 *     .name("type").value("java.time.Duration")
 *     .name("tags").beginArray().value("io").value("client").endArray()
 *     .endObject()
 *     .toString();
 * }</pre>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class JsonWriter {

    private static final int INITIAL_DEPTH = 8;

    private final StringBuilder out;
    private final @Nullable String indent;
    private boolean[] objects = new boolean[INITIAL_DEPTH];
    private boolean[] nonEmpty = new boolean[INITIAL_DEPTH];
    private int depth;
    private boolean expectingValue;
    private boolean complete;

    /**
     * A writer producing compact JSON.
     */
    public JsonWriter() {
        this(null, 64);
    }

    private JsonWriter(@Nullable String indent, int capacity) {
        this.out = new StringBuilder(capacity);
        this.indent = indent;
    }

    /**
     * A writer producing indented JSON: each member on its own line, indented by the given string
     * per level, and a space after the colon of a name.
     *
     * @param indent The indentation of one level
     * @return The writer
     */
    public static JsonWriter indented(String indent) {
        return new JsonWriter(indent, 256);
    }

    /**
     * The JSON string literal of a value: quoted, with the quote, the backslash and the control
     * characters escaped; the literal {@code null} for {@code null}.
     *
     * @param value The value
     * @return The literal
     */
    public static String quote(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        appendQuoted(sb, value);
        return sb.toString();
    }

    /**
     * Appends the JSON string literal of a value, as {@link #quote(String)} renders it.
     *
     * @param sb    The builder
     * @param value The value
     */
    @SuppressWarnings("MagicNumber")
    public static void appendQuoted(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u00").append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 0xF, 16));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /**
     * Opens an object.
     *
     * @return This writer
     */
    public JsonWriter beginObject() {
        return begin(true, '{');
    }

    /**
     * Closes the innermost object.
     *
     * @return This writer
     */
    public JsonWriter endObject() {
        return end(true, '}');
    }

    /**
     * Opens an array.
     *
     * @return This writer
     */
    public JsonWriter beginArray() {
        return begin(false, '[');
    }

    /**
     * Closes the innermost array.
     *
     * @return This writer
     */
    public JsonWriter endArray() {
        return end(false, ']');
    }

    /**
     * Writes the name of the next member of the innermost object.
     *
     * @param name The name
     * @return This writer
     */
    public JsonWriter name(String name) {
        if (depth == 0 || !objects[depth - 1]) {
            throw new IllegalStateException("A name can only be written inside an object");
        }
        if (expectingValue) {
            throw new IllegalStateException("The value of the previous name is missing");
        }
        separate();
        appendQuoted(out, name);
        out.append(':');
        if (indent != null) {
            out.append(' ');
        }
        expectingValue = true;
        return this;
    }

    /**
     * Writes a string value, or {@code null}.
     *
     * @param value The value
     * @return This writer
     */
    public JsonWriter value(@Nullable String value) {
        beforeValue();
        if (value == null) {
            out.append("null");
        } else {
            appendQuoted(out, value);
        }
        return this;
    }

    /**
     * Writes a boolean value.
     *
     * @param value The value
     * @return This writer
     */
    public JsonWriter value(boolean value) {
        beforeValue();
        out.append(value);
        return this;
    }

    /**
     * Writes an integer value.
     *
     * @param value The value
     * @return This writer
     */
    public JsonWriter value(long value) {
        beforeValue();
        out.append(value);
        return this;
    }

    /**
     * Writes a floating point value.
     *
     * @param value The value
     * @return This writer
     */
    public JsonWriter value(double value) {
        beforeValue();
        out.append(value);
        return this;
    }

    /**
     * Writes a number, or {@code null}, as its {@link Number#toString()}.
     *
     * @param value The value
     * @return This writer
     */
    public JsonWriter value(@Nullable Number value) {
        beforeValue();
        out.append(value == null ? "null" : value.toString());
        return this;
    }

    /**
     * Writes the JSON of any value: a string, a boolean or a number as such; an {@link Iterable}
     * as an array and a {@link Map} as an object of its members; {@code null} as {@code null};
     * any other object as the string of its {@link Object#toString()}.
     *
     * @param value The value
     * @return This writer
     */
    public JsonWriter value(@Nullable Object value) {
        switch (value) {
            case null -> nullValue();
            case String s -> value(s);
            case Boolean b -> value(b.booleanValue());
            case Number n -> value(n);
            case Map<?, ?> map -> {
                beginObject();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    name(String.valueOf(entry.getKey())).value(entry.getValue());
                }
                endObject();
            }
            case Iterable<?> iterable -> {
                beginArray();
                for (Object element : iterable) {
                    value(element);
                }
                endArray();
            }
            default -> value(value.toString());
        }
        return this;
    }

    /**
     * Writes {@code null}.
     *
     * @return This writer
     */
    public JsonWriter nullValue() {
        beforeValue();
        out.append("null");
        return this;
    }

    /**
     * Writes a value already rendered as JSON: a number a caller formats itself, or a fragment
     * another writer produced.
     *
     * @param json The JSON of the value
     * @return This writer
     */
    public JsonWriter raw(String json) {
        beforeValue();
        out.append(json);
        return this;
    }

    /**
     * Writes string values, one member each, into the innermost array.
     *
     * @param values The values
     * @return This writer
     */
    public JsonWriter values(Iterable<String> values) {
        for (String value : values) {
            value(value);
        }
        return this;
    }

    /**
     * Whether the document is complete: a value was written and every container is closed.
     *
     * @return {@code true} when the document is complete
     */
    public boolean isComplete() {
        return complete && depth == 0;
    }

    /**
     * Writes the document.
     *
     * @param writer The writer
     * @throws IOException When the writer fails
     * @throws IllegalStateException When the document is not complete
     */
    public void writeTo(Writer writer) throws IOException {
        writer.write(toString());
    }

    /**
     * The document.
     *
     * @return The JSON
     * @throws IllegalStateException When the document is not complete
     */
    @Override
    public String toString() {
        if (!isComplete()) {
            throw new IllegalStateException("The JSON is not complete: " + (depth > 0 ? depth + " container(s) still open" : "no value was written"));
        }
        return out.toString();
    }

    private JsonWriter begin(boolean object, char bracket) {
        beforeValue();
        if (depth == objects.length) {
            objects = Arrays.copyOf(objects, depth * 2);
            nonEmpty = Arrays.copyOf(nonEmpty, depth * 2);
        }
        objects[depth] = object;
        nonEmpty[depth] = false;
        depth++;
        out.append(bracket);
        return this;
    }

    private JsonWriter end(boolean object, char bracket) {
        if (depth == 0 || objects[depth - 1] != object) {
            throw new IllegalStateException("No " + (object ? "object" : "array") + " is open");
        }
        if (expectingValue) {
            throw new IllegalStateException("The value of the previous name is missing");
        }
        depth--;
        if (indent != null && nonEmpty[depth]) {
            newline();
        }
        out.append(bracket);
        return this;
    }

    /**
     * Before a value: the separator an array member needs, and the checks that an object member
     * has its name and that the document holds one value.
     */
    private void beforeValue() {
        if (depth == 0) {
            if (complete) {
                throw new IllegalStateException("The document already holds a value");
            }
            complete = true;
            return;
        }
        if (objects[depth - 1]) {
            if (!expectingValue) {
                throw new IllegalStateException("A value inside an object needs a name");
            }
            expectingValue = false;
        } else {
            separate();
        }
    }

    /**
     * Before a member of the innermost container: the comma after the previous one, and the line
     * break and indentation of an indented document.
     */
    private void separate() {
        if (nonEmpty[depth - 1]) {
            out.append(',');
        }
        nonEmpty[depth - 1] = true;
        if (indent != null) {
            newline();
        }
    }

    private void newline() {
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(indent);
        }
    }
}
