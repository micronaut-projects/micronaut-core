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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A {@link ConfigurationMetadataWriter} that writes per-class JSON Schema (Draft 2020-12)
 * for each {@code @ConfigurationProperties} / {@code @EachProperty} annotated type.
 */
public final class JsonSchemaConfigurationMetadataWriter implements ConfigurationMetadataWriter {

    private static final String SCHEMAS_DIR = "micronaut-configuration-schemas";
    private static final String ATTR_MIN = "minimum";
    private static final String ATTR_MAX = "maximum";
    private static final String ATTR_CONST = "const";
    private static final String ATTR_FORMAT = "format";
    private static final String ATTR_PATTERN = "pattern";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_ENUM = "enum";
    private static final String ATTR_PROPERTIES = "properties";
    private static final String JV_NOT_NULL = "jakarta.validation.constraints.NotNull";
    private static final String JV_NOT_BLANK = "jakarta.validation.constraints.NotBlank";
    private static final String JV_ASSERT_TRUE = "jakarta.validation.constraints.AssertTrue";
    private static final String JV_ASSERT_FALSE = "jakarta.validation.constraints.AssertFalse";
    private static final String JV_EMAIL = "jakarta.validation.constraints.Email";
    private static final String JV_PATTERN = "jakarta.validation.constraints.Pattern";
    private static final String JV_SIZE = "jakarta.validation.constraints.Size";
    private static final String JV_NOT_EMPTY = "jakarta.validation.constraints.NotEmpty";
    private static final String ATTR_MIN_PROPERTIES = "minProperties";
    private static final String ATTR_MAX_PROPERTIES = "maxProperties";
    private static final String JV_MIN = "jakarta.validation.constraints.Min";
    private static final String JV_MAX = "jakarta.validation.constraints.Max";
    private static final String JV_DIGITS = "jakarta.validation.constraints.Digits";
    private static final String JV_NEGATIVE = "jakarta.validation.constraints.Negative";
    private static final String JV_NEGATIVE_OR_ZERO = "jakarta.validation.constraints.NegativeOrZero";
    private static final String ATTR_EXCLUSIVE_MINIMUM = "exclusiveMinimum";
    private static final String ATTR_EXCLUSIVE_MAXIMUM = "exclusiveMaximum";
    private static final String JV_POSITIVE = "jakarta.validation.constraints.Positive";
    private static final String JV_POSITIVE_OR_ZERO = "jakarta.validation.constraints.PositiveOrZero";
    private static final String OBJECT = "object";
    private static final String DEFAULT = "default";
    private static final String ARRAY = "array";
    private static final String ATTR_MIN_ITEMS = "minItems";
    private static final String URL_JSON_SCHEMA = "https://json-schema.org/draft/2020-12/schema";
    private static final String STRING = "string";
    private static final String DURATION = "duration";
    private static final String ATTR_ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String BOOLEAN = "boolean";

    @Override
    public void write(ConfigurationMetadataBuilder metadataBuilder, ClassWriterOutputVisitor outputVisitor) throws IOException {
        final List<ConfigurationMetadata> configs = metadataBuilder.getConfigurations();
        if (configs.isEmpty()) {
            return;
        }
        // We need VisitorContext for richer type info where available.
        final VisitorContext vc;
        if (outputVisitor instanceof VisitorContext) {
            vc = (VisitorContext) outputVisitor;
        } else {
            vc = null;
        }

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
                                @Nullable VisitorContext vc,
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
        str(out, URL_JSON_SCHEMA);
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
        attr(out, ATTR_TYPE);
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
            attr(out, ATTR_TYPE);
            str(out, OBJECT);
            comma(out);
            attr(out, ATTR_MIN_PROPERTIES);
            out.write("1");
            comma(out);
            emitAdditionalPropertiesRef(out);
            // defs entry schema
            emitEntryDefs(cm, basePrefix, allProps, vc, out, true, vc != null ? vc.getClassElement(cm.getType()).orElse(null) : null);
        } else if (isEachList) {
            // type: array; minItems:1; items: $ref $defs.Entry
            attr(out, ATTR_TYPE);
            str(out, ARRAY);
            comma(out);
            attr(out, ATTR_MIN_ITEMS);
            out.write("1");
            comma(out);
            attr(out, "items");
            refEntry(out);
            emitEntryDefs(cm, basePrefix, allProps, vc, out, false, vc != null ? vc.getClassElement(cm.getType()).orElse(null) : null);
        } else {
            // Plain configuration object
            attr(out, ATTR_TYPE);
            str(out, OBJECT);
            comma(out);
            // properties: object
            attr(out, ATTR_PROPERTIES);
            ClassElement classElement = vc != null ? vc.getClassElement(cm.getType()).orElse(null) : null;
            Set<String> required = writePropertiesObject(out, cm, basePrefix, allProps, vc, /*containerMode*/ null, classElement);
            emitRequired(out, required);
            // keep additionalProperties default (omitted) or explicitly true
        }
        out.write('}');
    }

    private void emitRequired(Writer out, Set<String> required) throws IOException {
        if (!required.isEmpty()) {
            comma(out);
            attr(out, "required");
            out.write('[');
            Iterator<String> r = required.iterator();
            while (r.hasNext()) {
                str(out, r.next());
                if (r.hasNext()) {
                    out.write(',');
                }
            }
            out.write(']');
        }
    }

    private void emitEntryDefs(ConfigurationMetadata cm,
                               String basePrefix,
                               List<PropertyMetadata> allProps,
                               @Nullable VisitorContext vc,
                               Writer out,
                               boolean mapMode,
                               @Nullable ClassElement classElement) throws IOException {
        comma(out);
        attr(out, "$defs");
        out.write('{');
        attr(out, "Entry");
        out.write('{');
        attr(out, ATTR_TYPE);
        str(out, OBJECT);
        comma(out);
        attr(out, ATTR_PROPERTIES);
        Set<String> required = writePropertiesObject(out, cm, basePrefix, allProps, vc, mapMode ? ContainerMode.MAP : ContainerMode.LIST, classElement);
        emitRequired(out, required);
        out.write('}');
        out.write('}');
    }

    private enum ContainerMode {
        MAP,
        LIST
    }

    private Set<String> writePropertiesObject(Writer out,
                                              ConfigurationMetadata cm,
                                              String basePrefix,
                                              List<PropertyMetadata> allProps,
                                              @Nullable VisitorContext vc,
                                              @Nullable ContainerMode containerMode,
                                              @Nullable ClassElement classElement) throws IOException {
        // Build nested property tree from matching properties
        Map<String, Object> tree = new LinkedHashMap<>();
        Set<String> required = new java.util.LinkedHashSet<>();
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
        if (tree.isEmpty()) {
            for (PropertyMetadata pm : allProps) {
                if (pm.getDeclaringType().equals(cm.getType())) {
                    tree.put(pm.getName(), pm);
                }
            }
        }
        // Serialize properties from the tree
        emitProperties(out, vc, classElement, tree, required);
        return required;
    }

    private void emitProperties(Writer out, @Nullable VisitorContext vc, @Nullable ClassElement classElement, Map<String, Object> tree, Set<String> required) throws IOException {
        out.write('{');
        Iterator<Map.Entry<String, Object>> it = tree.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> e = it.next();
            String key = e.getKey();
            attr(out, key);
            writeSchemaNode(out, e.getValue(), vc, classElement, key, required);
            if (it.hasNext()) {
                out.write(',');
            }
        }
        out.write('}');
    }

    @SuppressWarnings("unchecked")
    private void writeSchemaNode(Writer out, Object node, @Nullable VisitorContext vc, @Nullable ClassElement classElement, @Nullable String currentKey, Set<String> requiredOut) throws IOException {
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
                    attr(out, DEFAULT);
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
            if (vc != null) {
                if (classElement != null) {
                    PropertyElement pe = findProperty(classElement, currentKey, pm);
                    if (pe != null) {
                        applyValidationConstraints(out, pe, currentKey, requiredOut);
                        if (!wroteDefault) {
                            String defaultValue = pe.stringValue(Bindable.class, "defaultValue").orElse(null);
                            if (defaultValue != null) {
                                Object aDefault = coerceDefault(defaultValue, pm.getType());
                                if (aDefault != null) {
                                    out.write(',');
                                    attr(out, DEFAULT);
                                    writeJsonValue(out, aDefault);
                                }
                            } else {
                                String constantName = "DEFAULT_" + NameUtils.environmentName(pm.getName());
                                FieldElement constantField = classElement.getEnclosedElement(ElementQuery.ALL_FIELDS.named(constantName).onlyStatic()).orElse(null);
                                if (constantField != null) {
                                    Object constantValue = constantField.getConstantValue();
                                    if (constantValue != null) {
                                        out.write(',');
                                        attr(out, DEFAULT);
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
                                // ignored
                            }
                        }
                    }
                }
            }
            out.write('}');
        } else if (node instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) node;
            out.write('{');
            attr(out, ATTR_TYPE);
            str(out, OBJECT);
            out.write(',');
            attr(out, ATTR_PROPERTIES);
            emitProperties(out, vc, classElement, m, requiredOut);
            out.write('}');
        } else {
            // Should not happen; write permissive schema
            out.write("{\"type\":\"object\"}");
        }
    }

    @Nullable
    private static PropertyElement findProperty(ClassElement classElement, @Nullable String currentKey, PropertyMetadata pm) {
        return classElement.getBeanProperties()
            .stream().filter(p -> p.getName().equals(currentKey) || p.getName().equals(pm.getName()))
            .findFirst().orElse(null);
    }

    private void writeTypeForProperty(Writer out, PropertyMetadata pm, @Nullable VisitorContext vc) throws IOException {
        String fqcn = pm.getType();
        // Try to refine via VisitorContext (generics, enums)
        ClassElement ce = (vc != null) ? vc.getClassElement(pm.getDeclaringType()).orElse(null) : null;
        PropertyElement pe = null;
        if (ce != null) {
            pe = findProperty(ce, null, pm);
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
                out.write(ATTR_TYPE);
                out.write('"');
                out.write(':');
                str(out, ARRAY);
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
                        writeChildTypeSchema(out, item);
                    } else {
                        writeChildTypeName(out, "java.lang.String");
                    }
                }
                return;
            }
            // Map
            if (t.isAssignable(Map.class)) {
                out.write('"');
                out.write(ATTR_TYPE);
                out.write('"');
                out.write(':');
                str(out, OBJECT);
                out.write(',');
                attr(out, ATTR_ADDITIONAL_PROPERTIES);
                ClassElement v = t.getTypeArguments().get("V");
                if (v == null) {
                    v = t;
                }
                writeChildTypeSchema(out, v);
                return;
            }
            // Plain type
            writeSimpleTypeSchema(out, t);
            return;
        }
        // Fallback: map simple by name
        writeSimpleTypeName(out, fqcn);
    }

    private void writeChildTypeSchema(Writer out, ClassElement t) throws IOException {
        out.write('{');
        writeSimpleTypeSchema(out, t);
        out.write('}');
    }

    private void writeChildTypeName(Writer out, String fqcn) throws IOException {
        out.write('{');
        writeSimpleTypeName(out, fqcn);
        out.write('}');
    }

    private void writeSimpleTypeSchema(Writer out, ClassElement t) throws IOException {
        // Enum
        if (t.isEnum()) {
            attr(out, ATTR_TYPE);
            str(out, STRING);
            out.write(',');
            attr(out, ATTR_ENUM);
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
            attr(out, ATTR_TYPE);
            str(out, STRING);
            out.write(',');
            attr(out, ATTR_FORMAT);
            str(out, "uri");
            return;
        }
        if ("java.time.Duration".equals(n)) {
            attr(out, ATTR_TYPE);
            str(out, STRING);
            out.write(',');
            attr(out, ATTR_FORMAT);
            str(out, DURATION);
            return;
        }
        // Basic primitives/wrappers/strings
        writeSimpleTypeName(out, n);
    }

    private void writeSimpleTypeName(Writer out, String fqcn) throws IOException {
        String type = switch (fqcn) {
            case BOOLEAN, "java.lang.Boolean" -> BOOLEAN;
            case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short",
                 "java.lang.Integer", "java.lang.Long", "java.math.BigInteger" -> "integer";
            case "float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" ->
                "number";
            default -> STRING;
        };
        attr(out, ATTR_TYPE);
        str(out, type);
    }

    private @Nullable Object coerceDefault(String value, String typeName) {
        try {
            return switch (typeName) {
                case BOOLEAN, "java.lang.Boolean" -> Boolean.parseBoolean(value);
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
                out.write(b ? StringUtils.TRUE : StringUtils.FALSE);
                return;
            }
            case Number number -> {
                out.write(number.toString());
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
        attr(out, ATTR_ADDITIONAL_PROPERTIES);
        refEntry(out);
    }

    private static List<String> splitOnDot(String rel) {
        if (rel.indexOf('.') < 0) {
            return List.of(rel);
        }
        List<String> parts = new ArrayList<>();
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

    private void applyValidationConstraints(Writer out,
                                            PropertyElement pe,
                                            @Nullable String currentKey,
                                            Set<String> requiredOut) throws IOException {
        BiFunction<String, String, @Nullable String> sval = (ann, member) ->
            pe.stringValue(ann, member).orElseGet(() -> {
                List<AnnotationValue<Annotation>> values = pe.getDeclaredAnnotationValuesByName(ann);
                if (!values.isEmpty()) {
                    return values.getFirst().stringValue(member).orElse(null);
                }
                return null;
            }
        );

        // Collect NotNull -> required
        if ((hasValidationAnnotation(pe, JV_NOT_NULL) || hasValidationAnnotation(pe, JV_NOT_BLANK)) && currentKey != null) {
            requiredOut.add(currentKey);
        }
        // Null -> const null? skip (rare); users should use @Nullable to allow nulls
        // AssertTrue/False -> const
        if (hasValidationAnnotation(pe, JV_ASSERT_TRUE)) {
            comma(out);
            attr(out, ATTR_CONST);
            out.write(StringUtils.TRUE);
        }
        if (hasValidationAnnotation(pe, JV_ASSERT_FALSE)) {
            comma(out);
            attr(out, ATTR_CONST);
            out.write(StringUtils.FALSE);
        }
        // Email
        if (hasValidationAnnotation(pe, JV_EMAIL)) {
            comma(out);
            attr(out, ATTR_FORMAT);
            str(out, "email");
        }
        // Pattern
        String pattern = sval.apply(JV_PATTERN, "regexp");
        if (pattern != null && !pattern.isEmpty()) {
            comma(out);
            attr(out, ATTR_PATTERN);
            str(out, pattern);
        }
        // Size
        Integer sizeMinBox = intValue(pe, JV_SIZE, "min");
        Integer sizeMaxBox = intValue(pe, JV_SIZE, "max");
        int sizeMin = sizeMinBox == null ? -1 : sizeMinBox;
        int sizeMax = sizeMaxBox == null ? -1 : sizeMaxBox;
        ClassElement t = pe.getType();
        boolean isString = String.class.getName().equals(t.getName());
        boolean isArray = t.isArray() || t.isIterable();
        boolean isMap = t.isAssignable(java.util.Map.class);
        if (isString) {
            if (hasValidationAnnotation(pe, JV_NOT_BLANK) || hasValidationAnnotation(pe, JV_NOT_EMPTY)) {
                sizeMin = Math.max(sizeMin, 1);
            }
            if (sizeMin >= 0) {
                comma(out);
                attr(out, "minLength");
                out.write(Integer.toString(sizeMin));
            }
            if (sizeMax >= 0) {
                comma(out);
                attr(out, "maxLength");
                out.write(Integer.toString(sizeMax));
            }
        } else if (isArray) {
            if (hasValidationAnnotation(pe, JV_NOT_EMPTY)) {
                sizeMin = Math.max(sizeMin, 1);
            }
            if (sizeMin >= 0) {
                comma(out);
                attr(out, ATTR_MIN_ITEMS);
                out.write(Integer.toString(sizeMin));
            }
            if (sizeMax >= 0) {
                comma(out);
                attr(out, "maxItems");
                out.write(Integer.toString(sizeMax));
            }
        } else if (isMap) {
            if (hasValidationAnnotation(pe, JV_NOT_EMPTY)) {
                sizeMin = Math.max(sizeMin, 1);
            }
            if (sizeMin >= 0) {
                comma(out);
                attr(out, ATTR_MIN_PROPERTIES);
                out.write(Integer.toString(sizeMin));
            }
            if (sizeMax >= 0) {
                comma(out);
                attr(out, ATTR_MAX_PROPERTIES);
                out.write(Integer.toString(sizeMax));
            }
        }
        // Min/Max
        Long min = longValue(pe, JV_MIN, AnnotationMetadata.VALUE_MEMBER);
        Long max = longValue(pe, JV_MAX, AnnotationMetadata.VALUE_MEMBER);
        if (min != null) {
            comma(out);
            attr(out, ATTR_MIN);
            out.write(Long.toString(min));
        }
        if (max != null) {
            comma(out);
            attr(out, ATTR_MAX);
            out.write(Long.toString(max));
        }
        // DecimalMin/DecimalMax
        String dmin = sval.apply("jakarta.validation.constraints.DecimalMin", AnnotationMetadata.VALUE_MEMBER);
        Boolean dminInc = booleanValue(pe, "jakarta.validation.constraints.DecimalMin", "inclusive");
        if (dmin != null) {
            comma(out);
            if (dminInc == null || dminInc) {
                attr(out, ATTR_MIN);
            } else {
                attr(out, ATTR_EXCLUSIVE_MINIMUM);
            }
            out.write(dmin);
        }
        String dmax = sval.apply("jakarta.validation.constraints.DecimalMax", AnnotationMetadata.VALUE_MEMBER);
        Boolean dmaxInc = booleanValue(pe, "jakarta.validation.constraints.DecimalMax", "inclusive");
        if (dmax != null) {
            comma(out);
            if (dmaxInc == null || dmaxInc) {
                attr(out, ATTR_MAX);
            } else {
                attr(out, ATTR_EXCLUSIVE_MAXIMUM);
            }
            out.write(dmax);
        }
        // Positive / Negative variants
        if (hasValidationAnnotation(pe, JV_POSITIVE)) {
            comma(out);
            attr(out, ATTR_EXCLUSIVE_MINIMUM);
            out.write("0");
        }
        if (hasValidationAnnotation(pe, JV_POSITIVE_OR_ZERO)) {
            comma(out);
            attr(out, ATTR_MIN);
            out.write("0");
        }
        if (hasValidationAnnotation(pe, JV_NEGATIVE)) {
            comma(out);
            attr(out, ATTR_EXCLUSIVE_MAXIMUM);
            out.write("0");
        }
        if (hasValidationAnnotation(pe, JV_NEGATIVE_OR_ZERO)) {
            comma(out);
            attr(out, ATTR_MAX);
            out.write("0");
        }
        // Digits -> regex
        Integer intDigits = intValue(pe, JV_DIGITS, "integer");
        Integer fracDigits = intValue(pe, JV_DIGITS, "fraction");
        if (intDigits != null || fracDigits != null) {
            StringBuilder re = new StringBuilder("^");
            int id = intDigits != null ? intDigits : 0;
            int fd = fracDigits != null ? fracDigits : 0;
            if (id > 0) {
                re.append("\\d{1,").append(id).append("}");
            } else {
                re.append("\\d+");
            }
            if (fd > 0) {
                re.append("(\\.\\d{1,").append(fd).append("})?");
            }
            re.append("$");
            comma(out);
            attr(out, ATTR_PATTERN);
            str(out, re.toString());
        }
    }

    @Nullable
    private static Boolean booleanValue(PropertyElement pe, String ann, String member) {
        return pe.booleanValue(ann, member).orElseGet(() -> {
                List<AnnotationValue<Annotation>> values = pe.getDeclaredAnnotationValuesByName(ann);
                if (!values.isEmpty()) {
                    return values.getFirst().booleanValue(member).orElse(null);
                }
                return null;
            }
        );
    }

    @Nullable
    private static Long longValue(PropertyElement pe, String ann, String member) {
        OptionalLong opt = pe.longValue(ann, member);
        if (opt.isPresent()) {
            return opt.getAsLong();
        } else {
            List<AnnotationValue<Annotation>> values = pe.getDeclaredAnnotationValuesByName(ann);
            if (!values.isEmpty()) {
                OptionalLong optionalInt = values.getFirst().longValue(member);
                if (optionalInt.isPresent()) {
                    return optionalInt.getAsLong();
                }
            }
            return null;
        }
    }

    @Nullable
    private static Integer intValue(PropertyElement pe, String ann, String member) {
        OptionalInt opt = pe.intValue(ann, member);
        if (opt.isPresent()) {
            return opt.getAsInt();
        } else {
            List<AnnotationValue<Annotation>> values = pe.getDeclaredAnnotationValuesByName(ann);
            if (!values.isEmpty()) {
                OptionalInt optionalInt = values.getFirst().intValue(member);
                if (optionalInt.isPresent()) {
                    return optionalInt.getAsInt();
                }
            }
            return null;
        }
    }

    private static boolean hasValidationAnnotation(PropertyElement pe, String ann) {
        List<AnnotationValue<Annotation>> values = pe.getDeclaredAnnotationValuesByName(ann);
        return !values.isEmpty();
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
