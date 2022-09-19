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

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.writer.OriginatingElements;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>A builder for producing metadata for the available {@link io.micronaut.context.annotation.ConfigurationProperties}.</p>
 *
 * <p>This data can then be subsequently written to a format readable by IDEs
 * (like spring-configuration-metadata.json for example).</p>
 *
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @since 1.0
 */
public class ConfigurationMetadataBuilder {

    public static final ConfigurationMetadataBuilder INSTANCE = new ConfigurationMetadataBuilder();

    private final OriginatingElements originatingElements = OriginatingElements.of();
    private final List<PropertyMetadata> properties = new ArrayList<>();
    private final List<ConfigurationMetadata> configurations = new ArrayList<>();

    /**
     * @return The originating elements for the builder.
     */
    public @NonNull Element[] getOriginatingElements() {
        return originatingElements.getOriginatingElements();
    }

    /**
     * @return The properties
     */
    public List<PropertyMetadata> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    /**
     * @return The configurations
     */
    public List<ConfigurationMetadata> getConfigurations() {
        return Collections.unmodifiableList(configurations);
    }

    /**
     * @return Whether any metadata is present
     */
    public boolean hasMetadata() {
        return !properties.isEmpty() || !configurations.isEmpty();
    }

    /**
     * Visit a {@link io.micronaut.context.annotation.ConfigurationProperties} class.
     *
     * @param classElement The type of the {@link io.micronaut.context.annotation.ConfigurationProperties}
     * @return This {@link ConfigurationMetadata}
     */
    public ConfigurationMetadata visitProperties(ClassElement classElement) {
        String path = buildTypePath(classElement, classElement);
        ConfigurationMetadata configurationMetadata = new ConfigurationMetadata();
        configurationMetadata.name = NameUtils.hyphenate(path, true);
        configurationMetadata.type = classElement.getType().getName();
        configurationMetadata.description = classElement.getDocumentation().orElse(null);
        configurationMetadata.includes = CollectionUtils.setOf(classElement.stringValues(ConfigurationReader.class, "includes"));
        configurationMetadata.excludes = CollectionUtils.setOf(classElement.stringValues(ConfigurationReader.class, "excludes"));
        this.configurations.add(configurationMetadata);
        return configurationMetadata;
    }

    /**
     * Visit a configuration property.
     *
     * @param owningType    The type that owns the property
     * @param declaringType The declaring type of the property
     * @param name          The property name
     * @param description   A description for the property
     * @param defaultValue  The default value of the property (only used for constant values such as strings, numbers,
     *                      enums etc.)
     * @return This property metadata
     */
    public PropertyMetadata visitProperty(ClassElement owningType,
                                          ClassElement declaringType,
                                          ClassElement propertyType,
                                          String name,
                                          @Nullable String description,
                                          @Nullable String defaultValue) {

        PropertyMetadata metadata = new PropertyMetadata();
        metadata.declaringType = declaringType.getName();
        metadata.name = name;
        metadata.path = NameUtils.hyphenate(buildPropertyPath(owningType, declaringType, name), true);
        metadata.type = propertyType.getType().getName();
        metadata.description = description;
        metadata.defaultValue = defaultValue;
        properties.add(metadata);
        return metadata;
    }

    /**
     * Visit a configuration property on the last declared properties instance.
     *
     * @param propertyType The property type
     * @param name         The property name
     * @param description  A description for the property
     * @param defaultValue The default value of the property (only used for constant values such as strings, numbers,
     *                     enums etc.)
     * @return This property metadata or null if no existing configuration is active
     */
    public PropertyMetadata visitProperty(String propertyType,
                                          String name,
                                          @Nullable String description,
                                          @Nullable String defaultValue) {

        if (!configurations.isEmpty()) {
            ConfigurationMetadata last = configurations.get(configurations.size() - 1);
            PropertyMetadata metadata = new PropertyMetadata();
            metadata.declaringType = last.type;
            metadata.name = name;
            metadata.path = NameUtils.hyphenate(last.name + "." + name, true);
            metadata.type = propertyType;
            metadata.description = description;
            metadata.defaultValue = defaultValue;
            properties.add(metadata);
            return metadata;
        }
        return null;
    }

    private String buildPropertyPath(ClassElement owningType, ClassElement declaringType, String propertyName) {
        // We assume owning and declaringType classes are already processed and correct prefix is calculated
        return getPath(owningType, declaringType) + "." + propertyName;
    }

    private String getPath(ClassElement owningType, ClassElement declaringType) {
        originatingElements.addOriginatingElement(owningType);
        originatingElements.addOriginatingElement(declaringType);
        return declaringType.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX)
            .orElseGet(() -> getRequiredOwningTypePath(owningType));
    }

    private static String getRequiredOwningTypePath(ClassElement owningType) {
        return owningType.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX)
            .orElseThrow(() -> new IllegalStateException("Prefix is required for " + owningType));
    }

    private String buildTypePath(ClassElement owningType, ClassElement declaringType) {
        originatingElements.addOriginatingElement(owningType);
        originatingElements.addOriginatingElement(declaringType);

        String path = getPath(owningType);
        path = prependSuperclasses(declaringType, path);
        if (owningType.isInner()) {
            // We assume outer classes are already processed and correct prefix is calculated
            ClassElement enclosingType = owningType.getEnclosingType().get();
            String parentPrefix = enclosingType.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse("");
            return combinePaths(parentPrefix, path);
        }
        return path;
    }

    private String combinePaths(String p1, String p2) {
        if (StringUtils.isNotEmpty(p1) && StringUtils.isNotEmpty(p2)) {
            return p1 + "." + p2;
        }
        if (StringUtils.isNotEmpty(p1)) {
            return p1;
        }
        return p2;
    }

    public static String getPath(AnnotationMetadata annotationMetadata) {
        String prefix = annotationMetadata.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
        if (annotationMetadata.hasDeclaredAnnotation(EachProperty.class)) {
            Objects.requireNonNull(prefix);
            if (annotationMetadata.booleanValue(EachProperty.class, "list").orElse(false)) {
                return prefix + "[*]";
            } else {
                return prefix + ".*";
            }
        }
        if (prefix == null) {
            return "";
        }
        return prefix;
    }

    private String prependSuperclasses(ClassElement declaringType, String path) {
        if (declaringType.isInterface()) {
            ClassElement superInterface = resolveSuperInterface(declaringType);
            while (superInterface != null) {
                Optional<String> parentConfig = superInterface.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX);
                if (parentConfig.isPresent()) {
                    path = combinePaths(parentConfig.get(), path);
                }
                superInterface = resolveSuperInterface(superInterface);
            }
        } else {
            Optional<ClassElement> optionalSuperType = declaringType.getSuperType();
            while (optionalSuperType.isPresent()) {
                ClassElement superType = optionalSuperType.get();
                Optional<String> parentConfig = superType.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX);
                if (parentConfig.isPresent()) {
                    path = combinePaths(parentConfig.get(), path);
                }
                optionalSuperType = superType.getSuperType();
            }
        }
        return path;
    }

    private ClassElement resolveSuperInterface(ClassElement declaringType) {
        return declaringType.getInterfaces().stream().filter(tm -> tm.hasStereotype(ConfigurationReader.class)).findFirst().orElse(null);
    }

    /**
     * Quote a string.
     *
     * @param string The string to quote
     * @return The quoted string
     */
    @SuppressWarnings("MagicNumber")
    static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char c = 0;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    //                if (b == '<') {
                    sb.append('\\');
                    //                }
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Write a quoted attribute with a value to a writer.
     *
     * @param out   The out writer
     * @param name  The name of the attribute
     * @param value The value
     * @throws IOException If an error occurred writing output
     */
    static void writeAttribute(Writer out, String name, String value) throws IOException {
        out.write('"');
        out.write(name);
        out.write("\":");
        out.write(quote(value));
    }
}
