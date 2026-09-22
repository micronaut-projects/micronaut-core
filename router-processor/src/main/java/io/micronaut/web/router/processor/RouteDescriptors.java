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
package io.micronaut.web.router.processor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.spi.ControllerRoute;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the versioned descriptor of a route plan, the durable per-origin output of the
 * route compiler: a JSON document with the identity, the fingerprint and the compatibility
 * versions of the plan, and for each slot its descriptor and its lowered template. A tool that
 * links the plans of an application reads the descriptors without loading application classes.
 *
 * <pre>{@code
 * {
 *   "format": "micronaut-route-plan/1",
 *   "abiVersion": 1,
 *   "inputProfile": "micronaut-raw-path/1",
 *   "selectionPolicy": "micronaut/1",
 *   "id": "controller:example.PetController",
 *   "class": "example.$PetController$RoutePlan",
 *   "fingerprint": "...",
 *   "owners": ["example.PetController"],
 *   "slots": [{"key": "...", "method": "GET", "engine": "micronaut", "template": "/pets/{id}", ...}]
 * }
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RouteDescriptors {

    /**
     * The format of the descriptors this version writes and reads.
     */
    public static final String FORMAT = "micronaut-route-plan/1";

    private RouteDescriptors() {
    }

    /**
     * @param plan A plan
     * @return Its descriptor
     */
    public static String write(CompiledRoutePlan plan) {
        StringBuilder json = new StringBuilder(256 + 256 * plan.slots().size());
        json.append("{\n");
        field(json, "format", FORMAT).append(",\n");
        json.append("  \"abiVersion\": ").append(RoutePlan.ABI_VERSION).append(",\n");
        field(json, "inputProfile", RoutePlan.INPUT_PROFILE).append(",\n");
        field(json, "selectionPolicy", RoutePlan.SELECTION_POLICY).append(",\n");
        field(json, "id", plan.id()).append(",\n");
        field(json, "class", plan.className()).append(",\n");
        field(json, "fingerprint", plan.fingerprint()).append(",\n");
        json.append("  \"owners\": ");
        writeStrings(json, plan.owners().toArray(String[]::new));
        json.append(",\n  \"slots\": [");
        for (int i = 0; i < plan.slots().size(); i++) {
            json.append(i == 0 ? "\n" : ",\n");
            slot(json, plan.slots().get(i), plan.lowered().get(i));
        }
        json.append(plan.slots().isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return json.toString();
    }

    private static void slot(StringBuilder json, RouteSlot slot, LoweredTemplate lowered) {
        json.append("    {\"key\": ");
        writeString(json, slot.key());
        json.append(", \"method\": ");
        writeString(json, slot.httpMethodName());
        json.append(", \"engine\": ");
        writeString(json, slot.template().engineId());
        json.append(", \"template\": ");
        writeString(json, slot.template().expression());
        json.append(", \"engineVersion\": ");
        writeString(json, slot.engineVersion());
        json.append(", \"requiredPrefix\": ");
        writeString(json, slot.requiredPrefix());
        json.append(", \"rawLength\": ").append(slot.rawLength());
        json.append(", \"pathVariableCount\": ").append(slot.pathVariableCount());
        if (slot.patternVariableCount() != 0) {
            // written only when there are such variables: a descriptor without it has none
            json.append(", \"patternVariableCount\": ").append(slot.patternVariableCount());
        }
        json.append(", \"captures\": ");
        writeStrings(json, slot.captures());
        json.append(", \"compiled\": ").append(slot.compiled());
        if (slot.compiled()) {
            json.append(", \"segments\": [");
            List<LoweredTemplate.Segment> segments = lowered.segments();
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) {
                    json.append(", ");
                }
                LoweredTemplate.Segment segment = segments.get(i);
                LoweredTemplate.VariableRule rule = segment.rule();
                if (rule != null) {
                    json.append("{\"variable\": ");
                    writeString(json, rule.ownerType() + '#' + rule.methodName());
                    json.append('}');
                } else {
                    json.append("{\"literal\": ");
                    writeString(json, String.valueOf(segment.literal()));
                    json.append('}');
                }
            }
            json.append(']');
        } else {
            json.append(", \"fallbackReason\": ");
            writeString(json, String.valueOf(slot.fallbackReason()));
        }
        ControllerRoute controller = slot.controller();
        if (controller != null) {
            json.append(", \"controller\": {\"owner\": ");
            writeString(json, controller.ownerType());
            json.append(", \"method\": ");
            writeString(json, controller.methodName());
            json.append(", \"argumentTypes\": ");
            writeStrings(json, controller.argumentTypes());
            json.append(", \"declaringTypeTarget\": ").append(controller.declaringTypeTarget());
            json.append(", \"consumes\": ");
            writeStrings(json, controller.consumes());
            json.append(", \"produces\": ");
            writeStrings(json, controller.produces());
            json.append(", \"implicitHead\": ").append(controller.implicitHead());
            json.append(", \"port\": ").append(controller.port()).append('}');
        }
        json.append('}');
    }

    private static StringBuilder field(StringBuilder json, String name, String value) {
        json.append("  \"").append(name).append("\": ");
        writeString(json, value);
        return json;
    }

    private static void writeStrings(StringBuilder json, String @Nullable [] values) {
        if (values == null) {
            json.append("null");
            return;
        }
        json.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                json.append(", ");
            }
            writeString(json, values[i]);
        }
        json.append(']');
    }

    private static void writeString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }

    /**
     * Read a descriptor.
     *
     * @param json The descriptor
     * @return The plan it describes
     * @throws IllegalArgumentException if the descriptor is not valid, of another format, or its fingerprint does not match its slots
     */
    public static Descriptor read(String json) {
        Map<String, @Nullable Object> root = object(new Parser(json).document());
        if (!FORMAT.equals(root.get("format"))) {
            throw new IllegalArgumentException("Not a route plan descriptor of the format " + FORMAT + ": " + root.get("format"));
        }
        List<RouteSlot> slots = new ArrayList<>();
        for (Object element : list(root, "slots")) {
            Map<String, @Nullable Object> slot = object(element);
            Object controllerValue = slot.get("controller");
            Map<String, @Nullable Object> controller = controllerValue == null ? null : object(controllerValue);
            slots.add(new RouteSlot(
                string(slot, "key"),
                string(slot, "method"),
                RouteTemplate.of(string(slot, "engine"), string(slot, "template")),
                string(slot, "engineVersion"),
                string(slot, "requiredPrefix"),
                number(slot, "rawLength"),
                number(slot, "pathVariableCount"),
                slot.get("patternVariableCount") == null ? 0 : number(slot, "patternVariableCount"),
                strings(slot, "captures"),
                bool(slot, "compiled"),
                (String) slot.get("fallbackReason"),
                controller == null ? null : new ControllerRoute(
                    string(controller, "owner"),
                    string(controller, "method"),
                    strings(controller, "argumentTypes"),
                    bool(controller, "declaringTypeTarget"),
                    nullableStrings(controller.get("consumes")),
                    nullableStrings(controller.get("produces")),
                    bool(controller, "implicitHead"),
                    number(controller, "port")
                )
            ));
        }
        Descriptor descriptor = new Descriptor(
            string(root, "id"),
            string(root, "class"),
            number(root, "abiVersion"),
            string(root, "inputProfile"),
            string(root, "selectionPolicy"),
            string(root, "fingerprint"),
            List.of(strings(root, "owners")),
            List.copyOf(slots)
        );
        if (!RoutePlan.fingerprint(slots.toArray(RouteSlot[]::new)).equals(descriptor.fingerprint())) {
            throw new IllegalArgumentException("The fingerprint of the route plan descriptor " + descriptor.id() + " does not match its slots");
        }
        return descriptor;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, @Nullable Object> object(@Nullable Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Invalid route plan descriptor: not an object: " + value);
        }
        return (Map<String, @Nullable Object>) value;
    }

    private static List<?> list(Map<String, @Nullable Object> object, String name) {
        if (!(object.get(name) instanceof List<?> list)) {
            throw new IllegalArgumentException("Invalid route plan descriptor: no array " + name);
        }
        return list;
    }

    private static String string(Map<String, @Nullable Object> object, String name) {
        if (!(object.get(name) instanceof String value)) {
            throw new IllegalArgumentException("Invalid route plan descriptor: no string " + name);
        }
        return value;
    }

    private static int number(Map<String, @Nullable Object> object, String name) {
        if (!(object.get(name) instanceof Number value)) {
            throw new IllegalArgumentException("Invalid route plan descriptor: no number " + name);
        }
        return value.intValue();
    }

    private static boolean bool(Map<String, @Nullable Object> object, String name) {
        if (!(object.get(name) instanceof Boolean value)) {
            throw new IllegalArgumentException("Invalid route plan descriptor: no boolean " + name);
        }
        return value;
    }

    private static String[] strings(Map<String, @Nullable Object> object, String name) {
        String[] values = nullableStrings(object.get(name));
        if (values == null) {
            throw new IllegalArgumentException("Invalid route plan descriptor: no array " + name);
        }
        return values;
    }

    private static String @Nullable [] nullableStrings(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Invalid route plan descriptor: not an array: " + value);
        }
        String[] strings = new String[list.size()];
        for (int i = 0; i < strings.length; i++) {
            if (!(list.get(i) instanceof String string)) {
                throw new IllegalArgumentException("Invalid route plan descriptor: not a string: " + list.get(i));
            }
            strings[i] = string;
        }
        return strings;
    }

    /**
     * A plan read from its descriptor.
     *
     * @param id              The identity
     * @param className       The name of the generated class
     * @param abiVersion      The ABI version
     * @param inputProfile    The input profile
     * @param selectionPolicy The selection policy
     * @param fingerprint     The fingerprint
     * @param owners          The controller types
     * @param slots           The slots
     */
    public record Descriptor(String id,
                             String className,
                             int abiVersion,
                             String inputProfile,
                             String selectionPolicy,
                             String fingerprint,
                             List<String> owners,
                             List<RouteSlot> slots) {
    }

    /**
     * A parser of the JSON the writer writes: objects, arrays, strings, integers, booleans and null.
     */
    private static final class Parser {
        private final String json;
        private int i;

        Parser(String json) {
            this.json = json;
        }

        @Nullable Object document() {
            Object value = value();
            whitespace();
            if (i != json.length()) {
                throw error("unexpected content");
            }
            return value;
        }

        private @Nullable Object value() {
            whitespace();
            if (i >= json.length()) {
                throw error("unexpected end");
            }
            char c = json.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> object = new LinkedHashMap<>();
            i++;
            whitespace();
            if (peek() == '}') {
                i++;
                return object;
            }
            while (true) {
                whitespace();
                String name = string();
                whitespace();
                expect(':');
                object.put(name, value());
                whitespace();
                if (peek() == ',') {
                    i++;
                } else {
                    expect('}');
                    return object;
                }
            }
        }

        private List<@Nullable Object> array() {
            List<@Nullable Object> array = new ArrayList<>();
            i++;
            whitespace();
            if (peek() == ']') {
                i++;
                return array;
            }
            while (true) {
                array.add(value());
                whitespace();
                if (peek() == ',') {
                    i++;
                } else {
                    expect(']');
                    return array;
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (true) {
                if (i >= json.length()) {
                    throw error("unterminated string");
                }
                char c = json.charAt(i++);
                if (c == '"') {
                    return value.toString();
                }
                if (c == '\\') {
                    char e = json.charAt(i++);
                    switch (e) {
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            value.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> value.append(e);
                    }
                } else {
                    value.append(c);
                }
            }
        }

        private @Nullable Object literal(String text, @Nullable Object value) {
            if (!json.startsWith(text, i)) {
                throw error("unexpected value");
            }
            i += text.length();
            return value;
        }

        private Number number() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            while (i < json.length() && Character.isDigit(json.charAt(i))) {
                i++;
            }
            if (start == i) {
                throw error("unexpected character");
            }
            return Long.parseLong(json.substring(start, i));
        }

        private char peek() {
            return i < json.length() ? json.charAt(i) : '\0';
        }

        private void expect(char c) {
            if (peek() != c) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        private void whitespace() {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid route plan descriptor at " + i + ": " + message);
        }
    }
}
