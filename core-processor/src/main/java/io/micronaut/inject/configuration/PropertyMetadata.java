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

import io.micronaut.core.io.Writable;

import java.io.IOException;
import java.io.Writer;

/**
 * Metadata about a property.
 *
 * @author Graeme Rocher
 */
public class PropertyMetadata implements Writable {

    String type;
    String name;
    String description;
    String path;
    String defaultValue;
    String declaringType;

    /**
     * @return The type
     */
    public String getType() {
        return type;
    }

    /**
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The path
     */
    public String getPath() {
        return path;
    }

    /**
     * @return The default value
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * @return The declaring type
     */
    public String getDeclaringType() {
        return declaringType;
    }

    @Override
    public void writeTo(Writer out) throws IOException {
        out.write('{');
        ConfigurationMetadataBuilder.writeAttribute(out, "name", path);
        out.write(',');
        ConfigurationMetadataBuilder.writeAttribute(out, "type", type);
        out.write(',');
        ConfigurationMetadataBuilder.writeAttribute(out, "sourceType", declaringType);
        if (description != null) {
            out.write(',');
            ConfigurationMetadataBuilder.writeAttribute(out, "description", description);
        }
        if (defaultValue != null) {
            out.write(',');
            ConfigurationMetadataBuilder.writeAttribute(out, "defaultValue", defaultValue);
        }
        out.write('}');
    }

    @Override
    public String toString() {
        return "PropertyMetadata{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", path='" + path + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                ", declaringType='" + declaringType + '\'' +
                '}';
    }
}
