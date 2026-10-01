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
package io.micronaut.dev.test.report;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A small JSON writer, so the report needs no JSON library on the launch classpath.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class Json {

    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;

    private final StringBuilder out = new StringBuilder();
    // whether the next value or member needs a comma before it
    private boolean comma;

    Json beginObject() {
        separate();
        out.append('{');
        comma = false;
        return this;
    }

    Json endObject() {
        out.append('}');
        comma = true;
        return this;
    }

    Json beginArray() {
        separate();
        out.append('[');
        comma = false;
        return this;
    }

    Json endArray() {
        out.append(']');
        comma = true;
        return this;
    }

    Json name(String name) {
        separate();
        string(name);
        out.append(':');
        comma = false;
        return this;
    }

    Json value(@Nullable String value) {
        separate();
        if (value == null) {
            out.append("null");
        } else {
            string(value);
        }
        comma = true;
        return this;
    }

    Json value(long value) {
        separate();
        out.append(value);
        comma = true;
        return this;
    }

    Json value(boolean value) {
        separate();
        out.append(value);
        comma = true;
        return this;
    }

    Json field(String name, @Nullable String value) {
        return name(name).value(value);
    }

    Json field(String name, long value) {
        return name(name).value(value);
    }

    Json field(String name, boolean value) {
        return name(name).value(value);
    }

    /**
     * Appends JSON written by another writer as a value.
     */
    Json raw(String json) {
        separate();
        out.append(json);
        comma = true;
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    private void separate() {
        if (comma) {
            out.append(',');
            comma = false;
        }
    }

    /**
     * Writes a string, escaping what JSON requires and what would end a script element or open a comment in HTML,
     * so the JSON can be embedded in a page as it is; an unpaired surrogate becomes a replacement character.
     */
    private void string(String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<', '>', '&', LINE_SEPARATOR, PARAGRAPH_SEPARATOR -> unicode(c);
                default -> {
                    if (c < 0x20) {
                        unicode(c);
                    } else if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                        out.append(c).append(value.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        unicode((char) 0xFFFD);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private void unicode(char c) {
        out.append("\\u").append(String.format(Locale.ROOT, "%04x", (int) c));
    }
}
