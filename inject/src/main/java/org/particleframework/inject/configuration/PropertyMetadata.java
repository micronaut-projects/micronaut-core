/*
 * Copyright 2018 original authors
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
package org.particleframework.inject.configuration;

import org.particleframework.core.io.Writable;

import java.io.IOException;
import java.io.Writer;

import static org.particleframework.inject.configuration.ConfigurationMetadataBuilder.writeAttribute;

/**
 * Metadata about a property
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

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPath() {
        return path;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDeclaringType() {
        return declaringType;
    }

    @Override
    public void writeTo(Writer out) throws IOException {
        out.write('{');
        writeAttribute(out, "name", path);
        out.write(',');
        writeAttribute(out, "type", type);
        out.write(',');
        writeAttribute(out, "sourceType", declaringType);
        if(description != null) {
            out.write(',');
            writeAttribute(out, "description", description);
        }
        if(defaultValue != null) {
            out.write(',');
            writeAttribute(out, "defaultValue", defaultValue);
        }
        out.write('}');
    }


}
