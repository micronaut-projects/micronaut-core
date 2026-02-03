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
package io.micronaut.inject.configuration;

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;

import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ConfigurationMetadataWriter} that writes per-class JSON Schema (Draft 2020-12)
 * for each {@code @ConfigurationProperties} / {@code @EachProperty} annotated type.
 */
public final class JsonSchemaConfigurationMetadataWriter implements ConfigurationMetadataWriter {

    private static final String SCHEMAS_DIR = "micronaut-configuration-schemas";

    @Override
    public void write(ConfigurationMetadataBuilder metadataBuilder, ClassWriterOutputVisitor outputVisitor) throws IOException {
        final List<ConfigurationMetadata> configs = metadataBuilder.getConfigurations();
        if (configs.isEmpty()) {
            return;
        }
        // We need VisitorContext for richer type info where available.
        final VisitorContext vc = (outputVisitor instanceof VisitorContext visitorContext) ? visitorContext : null;

        // Build quick index of properties by path for efficient filtering
        final List<PropertyMetadata> props = metadataBuilder.getProperties();

        Set<String> seen = new HashSet<>();
        for (ConfigurationMetadata cm : configs) {
            final String fqcn = cm.getType();
            if (!seen.add(fqcn)) {
                continue;
            }
            final String fileName = SCHEMAS_DIR + "/" + fqcn + ".json";
            Optional<GeneratedFile> fileOpt = outputVisitor.visitMetaInfFile(fileName, metadataBuilder.getOriginatingElements());
            if (fileOpt.isEmpty()) {
                continue;
            }

            try (Writer out = fileOpt.get().openWriter()) {
                writeSchemaFor(cm, props, vc, out);
            }
        }
    }

    private void writeSchemaFor(ConfigurationMetadata cm,
                                List<PropertyMetadata> allProps,
                                @org.jspecify.annotations.Nullable VisitorContext vc,
                                Writer out) throws IOException {
        // Determine prefix and whether this is EachProperty
        String fullPrefix = cm.getName(); // may contain .* or [*]
        boolean isEachMap = fullPrefix.endsWith(".*");
        boolean isEachList = fullPrefix.endsWith("[*]");
        String basePrefix = fullPrefix;
        if (isEachMap) {
            basePrefix = fullPrefix.substring(0, fullPrefix.length() - 2);
        }
        if (isEachList) {
            basePrefix = fullPrefix.substring(0, fullPrefix.length() - 3);
        }

        // JSON begin
        out.write('{');
        attr(out, "$schema");
        str(out, "https://json-schema.org/draft/2020-12/schema");
        comma(out);
        attr(out, "$id");
        str(out, "urn:micronaut:config:" + cm.getType());
        comma(out);
        attr(out, "title");
        str(out, cm.getType());
        comma(out);
        if (cm.getDescription() != null) {
            attr(out, "description");
            str(out, cm.getDescription());
            comma(out);
        }
        // Vendor extension at root
        attr(out, "x-micronaut");
        out.write('{');
        attr(out, "prefix");
        str(out, basePrefix);
        comma(out);
        attr(out, "type");
        str(out, cm.getType());
        comma(out);
        boolean isEach = isEachMap || isEachList;
        attr(out, "kind");
        str(out, isEach ? "each-property" : "configuration-properties");
        if (isEach) {
            comma(out);
            attr(out, "container");
            str(out, isEachMap ? "map" : "list");
        }
        out.write('}');
        comma(out);

        // Root schema shape
        if (isEachMap) {
            // type: object; minProperties:1; additionalProperties: $ref $defs.Entry
            attr(out, "type");
            str(out, "object");
            comma(out);
            attr(out, "minProperties");
            out.write("1");
            comma(out);
            emitAdditionalPropertiesRef(out);
            // defs entry schema
            emitEntryDefs(cm, basePrefix, allProps, vc, out, true);
        } else if (isEachList) {
            // type: array; minItems:1; items: $ref $defs.Entry
            attr(out, "type");
            str(out, "array");
            comma(out);
            attr(out, "minItems");
            out.write("1");
            comma(out);
            attr(out, "items");
            refEntry(out);
            emitEntryDefs(cm, basePrefix, allProps, vc, out, false);
        } else {
            // Plain configuration object
            attr(out, "type");
            str(out, "object");
            comma(out);
            // properties: object
            attr(out, "properties");
            writePropertiesObject(out, cm, basePrefix, allProps, vc, /*containerMode*/ null);
            // keep additionalProperties default (omitted) or explicitly true
        }
        out.write('}');
    }

    private void emitEntryDefs(ConfigurationMetadata cm,
                               String basePrefix,
                               List<PropertyMetadata> allProps,
                               @org.jspecify.annotations.Nullable VisitorContext vc,
                               Writer out,
                               boolean mapMode) throws IOException {
        comma(out);
        attr(out, "$defs");
        out.write('{');
        attr(out, "Entry");
        out.write('{');
        attr(out, "type");
        str(out, "object");
        comma(out);
        attr(out, "properties");
        writePropertiesObject(out, cm, basePrefix, allProps, vc, mapMode ? ContainerMode.MAP : ContainerMode.LIST);
        out.write('}');
        out.write('}');
    }

    private enum ContainerMode {
        MAP,
        LIST
    }

    private void writePropertiesObject(Writer out,
                                       ConfigurationMetadata cm,
                                       String basePrefix,
                                       List<PropertyMetadata> allProps,
                                       @org.jspecify.annotations.Nullable VisitorContext vc,
                                       @org.jspecify.annotations.Nullable ContainerMode containerMode) throws IOException {
        ClassElement classElement = vc != null ? vc.getClassElement(cm.type).orElse(null) : null;
        // Build nested property tree from matching properties
        Map<String, Object> tree = new LinkedHashMap<>();
        for (PropertyMetadata pm : allProps) {
            String path = pm.getPath();
            String matchPrefix = basePrefix + ".";
            if (containerMode == ContainerMode.MAP) {
                matchPrefix = basePrefix + ".*.";
            }
            if (containerMode == ContainerMode.LIST) {
                matchPrefix = basePrefix + "[*].";
            }
            if (!path.startsWith(matchPrefix)) {
                continue;
            }
            String rel = path.substring(matchPrefix.length());
            if (rel.isEmpty()) {
                continue;
            }
            // Split into segments
            List<String> segs = splitOnDot(rel);
            Map<String, Object> cursor = tree;
            for (int i = 0; i < segs.size(); i++) {
                String seg = segs.get(i);
                boolean last = (i == segs.size() - 1);
                if (last) {
                    // Leaf: store PropertyMetadata
                    cursor.put(seg, pm);
                } else {
                    Object n = cursor.get(seg);
                    if (!(n instanceof Map)) {
                        n = new LinkedHashMap<String, Object>();
                        cursor.put(seg, n);
                    }
                    //noinspection unchecked
                    cursor = (Map<String, Object>) n;
                }
            }
        }
        // Serialize properties from the tree
        out.write('{');
        Iterator<Map.Entry<String, Object>> it = tree.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            attr(out, e.getKey());
            writeSchemaNode(out, e.getValue(), cm, vc, classElement);
            if (it.hasNext()) {
                out.write(',');
            }
        }
        out.write('}');
    }

    @SuppressWarnings("unchecked")
    private void writeSchemaNode(Writer out, Object node, ConfigurationMetadata cm, @Nullable VisitorContext vc, @Nullable ClassElement classElement) throws IOException {
        if (node instanceof PropertyMetadata pm) {
            // Leaf property schema
            out.write('{');
            // type mapping (best effort)
            writeTypeForProperty(out, pm, vc);
            // description
            if (pm.getDescription() != null) {
                out.write(',');
                attr(out, "description");
                str(out, pm.getDescription());
            }
            // default
            boolean wroteDefault;
            if (pm.getDefaultValue() != null) {
                Object coerced = coerceDefault(pm.getDefaultValue(), pm.getType());
                if (coerced != null) {
                    out.write(',');
                    attr(out, "default");
                    writeJsonValue(out, coerced);
                    wroteDefault = true;
                } else {
                    wroteDefault = false;
                }
            } else {
                wroteDefault = false;
            }
            // vendor ext per property
            out.write(',');
            attr(out, "x-micronaut-javaType");
            str(out, pm.getType());
            out.write(',');
            attr(out, "x-micronaut-sourceType");
            str(out, pm.getDeclaringType());
            out.write(',');
            attr(out, "x-micronaut-path");
            str(out, pm.getPath());
            // deprecated (if resolvable)
            if (classElement != null) {
                List<PropertyElement> beanProperties =
                    classElement.getBeanProperties(PropertyElementQuery.of(classElement).includes(Set.of(pm.getName())));
                if (!beanProperties.isEmpty()) {
                    PropertyElement pe = beanProperties.getFirst();
                    if (!wroteDefault) {
                        String defaultValue = pe.stringValue(Bindable.class, "defaultValue").orElse(null);
                        if (defaultValue != null) {
                            Object aDefault = coerceDefault(defaultValue, pm.getType());
                            if (aDefault != null) {
                                out.write(',');
                                attr(out, "default");
                                writeJsonValue(out, aDefault);
                            }
                        } else {
                            String constantName = "DEFAULT_" + NameUtils.environmentName(pm.getName());
                            FieldElement constantField = classElement.getEnclosedElement(ElementQuery.ALL_FIELDS.named(constantName).onlyStatic())
                                .orElse(null);
                            if (constantField != null) {
                                Object constantValue = constantField.getConstantValue();
                                if (constantValue != null) {
                                    out.write(',');
                                    attr(out, "default");
                                    writeJsonValue(out, constantValue);
                                }
                            }
                        }
                    }
                    if (pe.hasStereotype(Deprecated.class)) {
                        try {
                            out.write(',');
                            attr(out, "deprecated");
                            out.write("true");
                        } catch (IOException ignored) {
                            // ignore
                        }
                    }
                }
            }
            out.write('}');
        } else if (node instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) node;
            out.write('{');
            attr(out, "type");
            str(out, "object");
            out.write(',');
            attr(out, "properties");
            out.write('{');
            Iterator<Map.Entry<String, Object>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                attr(out, e.getKey());
                writeSchemaNode(out, e.getValue(), cm, vc, classElement);
                if (it.hasNext()) {
                    out.write(',');
                }
            }
            out.write('}');
            out.write('}');
        } else {
            // Should not happen; write permissive schema
            out.write("{\"type\":\"object\"}");
        }
    }

    private void writeTypeForProperty(Writer out, PropertyMetadata pm, @org.jspecify.annotations.Nullable VisitorContext vc) throws IOException {
        String fqcn = pm.getType();
        // Try to refine via VisitorContext (generics, enums)
        ClassElement ce = (vc != null) ? vc.getClassElement(pm.getDeclaringType()).orElse(null) : null;
        PropertyElement pe = null;
        if (ce != null) {
            pe = ce.getBeanProperties().stream().filter(p -> p.getName().equals(pm.getName())).findFirst().orElse(null);
        }
        if (pe != null) {
            // Optional
            ClassElement t = pe.getGenericType();
            if (t.isOptional()) {
                t = t.getOptionalValueType().orElse(t);
            }
            // Collection/Array
            if (t.isArray() || t.isIterable()) {
                out.write('"');
                out.write("type");
                out.write('"');
                out.write(':');
                str(out, "array");
                out.write(',');
                attr(out, "items");
                if (t.isArray()) {
                    String n = t.getName();
                    while (n.endsWith("[]")) {
                        n = n.substring(0, n.length() - 2);
                    }
                    writeChildTypeName(out, n);
                } else {
                    ClassElement item = t.getFirstTypeArgument().orElse(null);
                    if (item != null) {
                        writeChildTypeSchema(out, item, vc);
                    } else {
                        writeChildTypeName(out, "java.lang.String");
                    }
                }
                return;
            }
            // Map
            if (t.isAssignable(Map.class)) {
                out.write('"');
                out.write("type");
                out.write('"');
                out.write(':');
                str(out, "object");
                out.write(',');
                attr(out, "additionalProperties");
                ClassElement v = t.getTypeArguments().get("V");
                if (v == null) {
                    v = t;
                }
                writeChildTypeSchema(out, v, vc);
                return;
            }
            // Plain type
            writeSimpleTypeSchema(out, t, vc);
            return;
        }
        // Fallback: map simple by name
        writeSimpleTypeName(out, fqcn);
    }

    private void writeChildTypeSchema(Writer out, ClassElement t, @org.jspecify.annotations.Nullable VisitorContext vc) throws IOException {
        out.write('{');
        writeSimpleTypeSchema(out, t, vc);
        out.write('}');
    }

    private void writeChildTypeName(Writer out, String fqcn) throws IOException {
        out.write('{');
        writeSimpleTypeName(out, fqcn);
        out.write('}');
    }

    private void writeSimpleTypeSchema(Writer out, ClassElement t, @org.jspecify.annotations.Nullable VisitorContext vc) throws IOException {
        // Enum
        if (t.isEnum()) {
            attr(out, "type");
            str(out, "string");
            out.write(',');
            attr(out, "enum");
            out.write('[');
            List<String> values;
            if (t instanceof io.micronaut.inject.ast.EnumElement ee) {
                values = ee.values();
            } else {
                values = Collections.emptyList();
            }
            for (int i = 0; i < values.size(); i++) {
                str(out, values.get(i));
                if (i + 1 < values.size()) {
                    out.write(',');
                }
            }
            out.write(']');
            return;
        }
        // URI/URL
        String n = t.getName();
        if ("java.net.URI".equals(n) || "java.net.URL".equals(n)) {
            attr(out, "type");
            str(out, "string");
            out.write(',');
            attr(out, "format");
            str(out, "uri");
            return;
        }
        if ("java.time.Duration".equals(n)) {
            attr(out, "type");
            str(out, "string");
            out.write(',');
            attr(out, "format");
            str(out, "duration");
            return;
        }
        // Basic primitives/wrappers/strings
        writeSimpleTypeName(out, n);
    }

    private void writeSimpleTypeName(Writer out, String fqcn) throws IOException {
        String type = switch (fqcn) {
            case "boolean", "java.lang.Boolean" -> "boolean";
            case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short",
                 "java.lang.Integer", "java.lang.Long", "java.math.BigInteger" -> "integer";
            case "float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" ->
                "number";
            default -> "string";
        };
        attr(out, "type");
        str(out, type);
    }

    private @org.jspecify.annotations.Nullable Object coerceDefault(String value, String typeName) {
        try {
            return switch (typeName) {
                case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(value);
                case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short",
                     "java.lang.Integer", "java.lang.Long", "java.math.BigInteger" ->
                    Long.parseLong(value);
                case "float", "double", "java.lang.Float", "java.lang.Double",
                     "java.math.BigDecimal" -> Double.parseDouble(value);
                default -> value; // string/enum/uri fall back to string
            };
        } catch (Exception e) {
            return null;
        }
    }

    private void writeJsonValue(Writer out, Object v) throws IOException {
        switch (v) {
            case String s -> {
                str(out, s);
                return;
            }
            case Boolean b -> {
                out.write(b ? "true" : "false");
                return;
            }
            case Number number -> {
                out.write(v.toString());
                return;
            }
            default -> {
                // no-oip
            }
        }
        // fallback to string
        str(out, String.valueOf(v));
    }

    private void emitAdditionalPropertiesRef(Writer out) throws IOException {
        attr(out, "additionalProperties");
        refEntry(out);
    }

    private static List<String> splitOnDot(String rel) {
        if (rel.indexOf('.') < 0) {
            return java.util.List.of(rel);
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        int start = 0;
        for (int i = 0; i < rel.length(); i++) {
            if (rel.charAt(i) == '.') {
                parts.add(rel.substring(start, i));
                start = i + 1;
            }
        }
        if (start <= rel.length()) {
            parts.add(rel.substring(start));
        }
        return parts;
    }

    private void refEntry(Writer out) throws IOException {
        out.write('{');
        attr(out, "$ref");
        str(out, "#/$defs/Entry");
        out.write('}');
    }

    // JSON writing helpers
    private void attr(Writer out, String name) throws IOException {
        out.write('"');
        out.write(name);
        out.write('"');
        out.write(':');
    }

    private void str(Writer out, String s) throws IOException {
        out.write(ConfigurationMetadataBuilder.quote(s));
    }

    private void comma(Writer out) throws IOException {
        out.write(',');
    }
}
