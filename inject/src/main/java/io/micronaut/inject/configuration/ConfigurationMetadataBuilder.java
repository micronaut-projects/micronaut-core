/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.configuration;

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>A builder for producing metadata for the available {@link io.micronaut.context.annotation.ConfigurationProperties}.</p>
 * <p>
 * <p>This data can then be subsequently written to a format readable by IDEs
 * (like spring-configuration-metadata.json for example).</p>
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class ConfigurationMetadataBuilder<T> {

    private final List<PropertyMetadata> properties = new ArrayList<>();
    private final List<ConfigurationMetadata> configurations = new ArrayList<>();

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
     * @param type        The type of the {@link io.micronaut.context.annotation.ConfigurationProperties}
     * @param description A description
     * @return This {@link ConfigurationMetadata}
     */
    public ConfigurationMetadata visitProperties(T type,
                                                 @Nullable String description) {

        AnnotationMetadata annotationMetadata = getAnnotationMetadata(type);
        return visitProperties(type, description, annotationMetadata);
    }

    /**
     * Visit a {@link io.micronaut.context.annotation.ConfigurationProperties} class.
     *
     * @param type        The type of the {@link io.micronaut.context.annotation.ConfigurationProperties}
     * @param description A description
     * @param annotationMetadata the annotation metadata
     * @return This {@link ConfigurationMetadata}
     */
    public ConfigurationMetadata visitProperties(T type, @Nullable String description, @NonNull AnnotationMetadata annotationMetadata) {
        String path = buildTypePath(type, type, annotationMetadata);
        ConfigurationMetadata configurationMetadata = new ConfigurationMetadata();
        configurationMetadata.name = NameUtils.hyphenate(path, true);
        configurationMetadata.type = getTypeString(type);
        configurationMetadata.description = description;
        configurationMetadata.includes = CollectionUtils.setOf(annotationMetadata.stringValues(ConfigurationReader.class, "includes"));
        configurationMetadata.excludes = CollectionUtils.setOf(annotationMetadata.stringValues(ConfigurationReader.class, "excludes"));
        this.configurations.add(configurationMetadata);
        return configurationMetadata;
    }

    /**
     * Visit a configuration property.
     *
     * @param owningType    The type that owns the property
     * @param declaringType The declaring type of the property
     * @param propertyType  The property type
     * @param name          The property name
     * @param description   A description for the property
     * @param defaultValue  The default value of the property (only used for constant values such as strings, numbers,
     *                      enums etc.)
     * @return This property metadata
     */
    public PropertyMetadata visitProperty(T owningType,
                                          T declaringType,
                                          String propertyType,
                                          String name,
                                          @Nullable String description,
                                          @Nullable String defaultValue) {

        PropertyMetadata metadata = new PropertyMetadata();
        metadata.declaringType = getTypeString(declaringType);
        metadata.name = name;
        metadata.path = NameUtils.hyphenate(buildPropertyPath(owningType, declaringType, name), true);
        metadata.type = propertyType;
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

    /**
     * <p>Build a property path for the given declaring type and property name.</p>
     * <p>
     * <p>For {@link io.micronaut.context.annotation.ConfigurationProperties} that path is a property is
     * established by looking at the value of the {@link io.micronaut.context.annotation.ConfigurationProperties} and
     * then calculating the path based on the inheritance tree.</p>
     * <p>
     * <p>For example consider the following classes:</p>
     * <p>
     * <pre><code>
     *  {@literal @}ConfigurationProperties("parent")
     *   public class ParentProperties {
     *      String foo;
     *   }
     *
     *  {@literal @}ConfigurationProperties("child")
     *   public class ChildProperties extends ParentProperties {
     *      String bar;
     *   }
     * </code></pre>
     * <p>
     * <p>The path of the property {@code foo} will be "parent.foo" whilst the path of the property {@code bar} will
     * be "parent.child.bar" factoring in the class hierarchy</p>
     * <p>
     * <p>Inner classes hierarchies are also taken into account</p>
     *
     * @param owningType    The owning type
     * @param declaringType The declaring type
     * @param propertyName  The property name
     * @return The property path
     */
    protected abstract String buildPropertyPath(T owningType, T declaringType, String propertyName);

    /**
     * Variation of {@link #buildPropertyPath(Object, Object, String)} for types.
     *
     * @param owningType    The owning type
     * @param declaringType The type
     * @return The type path
     */
    protected abstract String buildTypePath(T owningType, T declaringType);

    /**
     * Variation of {@link #buildPropertyPath(Object, Object, String)} for types.
     *
     * @param owningType    The owning type
     * @param declaringType The type
     * @param annotationMetadata The annotation metadata
     * @return The type path
     */
    protected abstract String buildTypePath(T owningType, T declaringType, AnnotationMetadata annotationMetadata);

    /**
     * Convert the given type to a string.
     *
     * @param type The type
     * @return The string
     */
    protected abstract String getTypeString(T type);

    /**
     * @param type The type
     * @return The annotation metadata for the type
     */
    protected abstract AnnotationMetadata getAnnotationMetadata(T type);

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
