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

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.utils.JsonWriter;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
        final TypeResolver vc = new TypeResolver(outputVisitor instanceof VisitorContext visitorContext ? visitorContext : null);

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

            JsonWriter json = new JsonWriter();
            writeSchemaFor(cm, props, vc, json);
            try (Writer out = fileOpt.get().openWriter()) {
                json.writeTo(out);
            }
        }
    }

    private void writeSchemaFor(ConfigurationMetadata cm,
                                List<PropertyMetadata> allProps,
                                TypeResolver vc,
                                JsonWriter out) {
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
        out.beginObject();
        out.name("$schema").value(URL_JSON_SCHEMA);
        out.name("$id").value("urn:micronaut:config:" + cm.getType());
        out.name("title").value(cm.getType());
        if (cm.getDescription() != null) {
            out.name("description").value(cm.getDescription());
        }
        // Vendor extension at root
        out.name("x-micronaut");
        out.beginObject();
        out.name("prefix").value(basePrefix);
        out.name(ATTR_TYPE).value(cm.getType());
        boolean isEach = isEachMap || isEachList;
        out.name("kind").value(isEach ? "each-property" : "configuration-properties");
        if (isEach) {
            out.name("container").value(isEachMap ? "map" : "list");
        }
        out.endObject();

        // Root schema shape
        if (isEachMap) {
            // type: object; minProperties:1; additionalProperties: $ref $defs.Entry
            out.name(ATTR_TYPE).value(OBJECT);
            out.name(ATTR_MIN_PROPERTIES).value(1);
            emitAdditionalPropertiesRef(out);
            // defs entry schema
            emitEntryDefs(cm, basePrefix, allProps, vc, out, true, vc.resolve(cm.getType()));
        } else if (isEachList) {
            // type: array; minItems:1; items: $ref $defs.Entry
            out.name(ATTR_TYPE).value(ARRAY);
            out.name(ATTR_MIN_ITEMS).value(1);
            out.name("items");
            refEntry(out);
            emitEntryDefs(cm, basePrefix, allProps, vc, out, false, vc.resolve(cm.getType()));
        } else {
            // Plain configuration object
            out.name(ATTR_TYPE).value(OBJECT);
            // properties: object
            out.name(ATTR_PROPERTIES);
            ClassElement classElement = vc.resolve(cm.getType());
            Set<String> required = writePropertiesObject(out, cm, basePrefix, allProps, vc, /*containerMode*/ null, classElement);
            emitRequired(out, required);
            // keep additionalProperties default (omitted) or explicitly true
        }
        out.endObject();
    }

    private void emitRequired(JsonWriter out, Set<String> required) {
        if (!required.isEmpty()) {
            out.name("required").beginArray().values(required).endArray();
        }
    }

    private void emitEntryDefs(ConfigurationMetadata cm,
                               String basePrefix,
                               List<PropertyMetadata> allProps,
                               TypeResolver vc,
                               JsonWriter out,
                               boolean mapMode,
                               @Nullable ClassElement classElement) {
        out.name("$defs");
        out.beginObject();
        out.name("Entry");
        out.beginObject();
        out.name(ATTR_TYPE).value(OBJECT);
        out.name(ATTR_PROPERTIES);
        Set<String> required = writePropertiesObject(out, cm, basePrefix, allProps, vc, mapMode ? ContainerMode.MAP : ContainerMode.LIST, classElement);
        emitRequired(out, required);
        out.endObject();
        out.endObject();
    }

    private enum ContainerMode {
        MAP,
        LIST
    }

    private Set<String> writePropertiesObject(JsonWriter out,
                                              ConfigurationMetadata cm,
                                              String basePrefix,
                                              List<PropertyMetadata> allProps,
                                              TypeResolver vc,
                                              @Nullable ContainerMode containerMode,
                                              @Nullable ClassElement classElement) {
        // Build nested property tree from matching properties
        Map<String, Object> tree = new LinkedHashMap<>();
        Set<String> required = new java.util.LinkedHashSet<>();
        final String matchPrefix;
        if (containerMode == ContainerMode.MAP) {
            matchPrefix = basePrefix + ".*.";
        } else if (containerMode == ContainerMode.LIST) {
            matchPrefix = basePrefix + "[*].";
        } else {
            matchPrefix = basePrefix + ".";
        }
        for (PropertyMetadata pm : allProps) {
            String path = pm.getPath();
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

    private void emitProperties(JsonWriter out, TypeResolver vc, @Nullable ClassElement classElement, Map<String, Object> tree, Set<String> required) {
        out.beginObject();
        for (Map.Entry<String, Object> e : tree.entrySet()) {
            out.name(e.getKey());
            writeSchemaNode(out, e.getValue(), vc, classElement, e.getKey(), required);
        }
        out.endObject();
    }

    @SuppressWarnings("unchecked")
    private void writeSchemaNode(JsonWriter out, Object node, TypeResolver vc, @Nullable ClassElement classElement, @Nullable String currentKey, Set<String> requiredOut) {
        if (node instanceof PropertyMetadata pm) {
            // Leaf property schema
            out.beginObject();
            // type mapping (best effort)
            writeTypeForProperty(out, pm, vc);
            // description
            if (pm.getDescription() != null) {
                out.name("description").value(pm.getDescription());
            }
            // default
            boolean wroteDefault;
            if (pm.getDefaultValue() != null) {
                Object coerced = coerceDefault(pm.getDefaultValue(), pm.getType());
                if (coerced != null) {
                    out.name(DEFAULT).value(coerced);
                    wroteDefault = true;
                } else {
                    wroteDefault = false;
                }
            } else {
                wroteDefault = false;
            }
            // vendor ext per property
            out.name("x-micronaut-javaType").value(pm.getType());
            out.name("x-micronaut-sourceType").value(pm.getDeclaringType());
            out.name("x-micronaut-path").value(pm.getPath());
            if (vc.hasVisitorContext()) {
                if (classElement != null) {
                    PropertyElement pe = findProperty(vc, classElement, currentKey, pm);
                    if (pe != null) {
                        applyValidationConstraints(out, pe, currentKey, requiredOut);
                        if (!wroteDefault) {
                            String defaultValue = pe.stringValue(Bindable.class, "defaultValue").orElse(null);
                            if (defaultValue != null) {
                                Object aDefault = coerceDefault(defaultValue, pm.getType());
                                if (aDefault != null) {
                                    out.name(DEFAULT).value(aDefault);
                                }
                            } else {
                                String constantName = "DEFAULT_" + NameUtils.environmentName(pm.getName());
                                FieldElement constantField = classElement.getEnclosedElement(ElementQuery.ALL_FIELDS.named(constantName).onlyStatic()).orElse(null);
                                if (constantField != null) {
                                    Object constantValue = constantField.getConstantValue();
                                    if (constantValue != null) {
                                        out.name(DEFAULT).value(constantValue);
                                    }
                                }
                            }
                        }
                        if (pe.hasStereotype(Deprecated.class)) {
                            out.name("deprecated").value(true);
                        }
                    }
                }
            }
            out.endObject();
        } else if (node instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) node;
            out.beginObject();
            out.name(ATTR_TYPE).value(OBJECT);
            out.name(ATTR_PROPERTIES);
            emitProperties(out, vc, classElement, m, requiredOut);
            out.endObject();
        } else {
            // Should not happen; write permissive schema
            out.beginObject().name(ATTR_TYPE).value(OBJECT).endObject();
        }
    }

    @Nullable
    private static PropertyElement findProperty(TypeResolver vc, ClassElement classElement, @Nullable String currentKey, PropertyMetadata pm) {
        return vc.beanProperties(classElement)
            .stream().filter(p -> p.getName().equals(currentKey) || p.getName().equals(pm.getName()))
            .findFirst().orElse(null);
    }

    private void writeTypeForProperty(JsonWriter out, PropertyMetadata pm, TypeResolver vc) {
        String fqcn = pm.getType();
        // Try to refine via VisitorContext (generics, enums)
        ClassElement ce = vc.resolve(pm.getDeclaringType());
        PropertyElement pe = null;
        if (ce != null) {
            pe = findProperty(vc, ce, null, pm);
        }
        if (pe != null) {
            // Optional
            ClassElement t = pe.getGenericType();
            if (t.isOptional()) {
                t = t.getOptionalValueType().orElse(t);
            }
            // Collection/Array
            if (t.isArray() || t.isIterable()) {
                out.name(ATTR_TYPE).value(ARRAY);
                out.name("items");
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
                out.name(ATTR_TYPE).value(OBJECT);
                out.name(ATTR_ADDITIONAL_PROPERTIES);
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

    private void writeChildTypeSchema(JsonWriter out, ClassElement t) {
        out.beginObject();
        writeSimpleTypeSchema(out, t);
        out.endObject();
    }

    private void writeChildTypeName(JsonWriter out, String fqcn) {
        out.beginObject();
        writeSimpleTypeName(out, fqcn);
        out.endObject();
    }

    private void writeSimpleTypeSchema(JsonWriter out, ClassElement t) {
        // Enum
        if (t.isEnum()) {
            out.name(ATTR_TYPE).value(STRING);
            List<String> values = t instanceof io.micronaut.inject.ast.EnumElement ee ? ee.values() : Collections.emptyList();
            out.name(ATTR_ENUM).beginArray().values(values).endArray();
            return;
        }
        // URI/URL
        String n = t.getName();
        if ("java.net.URI".equals(n) || "java.net.URL".equals(n)) {
            out.name(ATTR_TYPE).value(STRING);
            out.name(ATTR_FORMAT).value("uri");
            return;
        }
        if ("java.time.Duration".equals(n)) {
            out.name(ATTR_TYPE).value(STRING);
            out.name(ATTR_FORMAT).value(DURATION);
            return;
        }
        // Basic primitives/wrappers/strings
        writeSimpleTypeName(out, n);
    }

    private void writeSimpleTypeName(JsonWriter out, String fqcn) {
        String type = switch (fqcn) {
            case BOOLEAN, "java.lang.Boolean" -> BOOLEAN;
            case "byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short",
                 "java.lang.Integer", "java.lang.Long", "java.math.BigInteger" -> "integer";
            case "float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" ->
                "number";
            default -> STRING;
        };
        out.name(ATTR_TYPE).value(type);
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

    private void emitAdditionalPropertiesRef(JsonWriter out) {
        out.name(ATTR_ADDITIONAL_PROPERTIES);
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

    private void refEntry(JsonWriter out) {
        out.beginObject();
        out.name("$ref").value("#/$defs/Entry");
        out.endObject();
    }

    private void applyValidationConstraints(JsonWriter out,
                                            PropertyElement pe,
                                            @Nullable String currentKey,
                                            Set<String> requiredOut) {
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
            out.name(ATTR_CONST).value(true);
        }
        if (hasValidationAnnotation(pe, JV_ASSERT_FALSE)) {
            out.name(ATTR_CONST).value(false);
        }
        // Email
        if (hasValidationAnnotation(pe, JV_EMAIL)) {
            out.name(ATTR_FORMAT).value("email");
        }
        // Pattern
        String pattern = sval.apply(JV_PATTERN, "regexp");
        if (pattern != null && !pattern.isEmpty()) {
            out.name(ATTR_PATTERN).value(pattern);
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
                out.name("minLength").value(sizeMin);
            }
            if (sizeMax >= 0) {
                out.name("maxLength").value(sizeMax);
            }
        } else if (isArray) {
            if (hasValidationAnnotation(pe, JV_NOT_EMPTY)) {
                sizeMin = Math.max(sizeMin, 1);
            }
            if (sizeMin >= 0) {
                out.name(ATTR_MIN_ITEMS).value(sizeMin);
            }
            if (sizeMax >= 0) {
                out.name("maxItems").value(sizeMax);
            }
        } else if (isMap) {
            if (hasValidationAnnotation(pe, JV_NOT_EMPTY)) {
                sizeMin = Math.max(sizeMin, 1);
            }
            if (sizeMin >= 0) {
                out.name(ATTR_MIN_PROPERTIES).value(sizeMin);
            }
            if (sizeMax >= 0) {
                out.name(ATTR_MAX_PROPERTIES).value(sizeMax);
            }
        }
        // Min/Max
        Long min = longValue(pe, JV_MIN, AnnotationMetadata.VALUE_MEMBER);
        Long max = longValue(pe, JV_MAX, AnnotationMetadata.VALUE_MEMBER);
        if (min != null) {
            out.name(ATTR_MIN).value(min);
        }
        if (max != null) {
            out.name(ATTR_MAX).value(max);
        }
        // DecimalMin/DecimalMax
        String dmin = sval.apply("jakarta.validation.constraints.DecimalMin", AnnotationMetadata.VALUE_MEMBER);
        Boolean dminInc = booleanValue(pe, "jakarta.validation.constraints.DecimalMin", "inclusive");
        if (dmin != null) {
            out.name(dminInc == null || dminInc ? ATTR_MIN : ATTR_EXCLUSIVE_MINIMUM).raw(dmin);
        }
        String dmax = sval.apply("jakarta.validation.constraints.DecimalMax", AnnotationMetadata.VALUE_MEMBER);
        Boolean dmaxInc = booleanValue(pe, "jakarta.validation.constraints.DecimalMax", "inclusive");
        if (dmax != null) {
            out.name(dmaxInc == null || dmaxInc ? ATTR_MAX : ATTR_EXCLUSIVE_MAXIMUM).raw(dmax);
        }
        // Positive / Negative variants
        if (hasValidationAnnotation(pe, JV_POSITIVE)) {
            out.name(ATTR_EXCLUSIVE_MINIMUM).value(0);
        }
        if (hasValidationAnnotation(pe, JV_POSITIVE_OR_ZERO)) {
            out.name(ATTR_MIN).value(0);
        }
        if (hasValidationAnnotation(pe, JV_NEGATIVE)) {
            out.name(ATTR_EXCLUSIVE_MAXIMUM).value(0);
        }
        if (hasValidationAnnotation(pe, JV_NEGATIVE_OR_ZERO)) {
            out.name(ATTR_MAX).value(0);
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
            out.name(ATTR_PATTERN).value(re.toString());
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

    /**
     * Resolves class elements by name and caches them for the duration of one write, so that
     * a configuration class and its bean properties are resolved once instead of once per property.
     */
    private static final class TypeResolver {

        private final @Nullable VisitorContext visitorContext;
        private final Map<String, Optional<ClassElement>> resolved = new HashMap<>();
        private final Map<String, List<PropertyElement>> properties = new HashMap<>();

        TypeResolver(@Nullable VisitorContext visitorContext) {
            this.visitorContext = visitorContext;
        }

        boolean hasVisitorContext() {
            return visitorContext != null;
        }

        @Nullable
        ClassElement resolve(String name) {
            if (visitorContext == null) {
                return null;
            }
            return resolved.computeIfAbsent(name, visitorContext::getClassElement).orElse(null);
        }

        List<PropertyElement> beanProperties(ClassElement classElement) {
            return properties.computeIfAbsent(classElement.getName(), n -> classElement.getBeanProperties(
                PropertyElementQuery.of(classElement).visibility(BeanProperties.Visibility.ANY)));
        }
    }
}
