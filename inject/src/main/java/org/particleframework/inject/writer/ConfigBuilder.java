/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.writer;

import org.objectweb.asm.Type;
import org.particleframework.core.annotation.Internal;

import javax.annotation.concurrent.Immutable;

/**
 * Stores data to be used when visiting a configuration builder method
 *
 * @author James Kleeh
 * @since 1.0
 */
@Immutable
@Internal
public class ConfigBuilder {

    private String name;
    private final Type type;
    private boolean invokeMethod;

    /**
     * Constructs a config builder
     *
     * @param type The builder type
     */
    public ConfigBuilder(Object type) {
        this.type = AbstractClassFileWriter.getTypeReference(type);
    }

    public ConfigBuilder forField(String field) {
        this.name = field;
        return this;
    }

    public ConfigBuilder forMethod(String field) {
        this.invokeMethod = true;
        this.name = field;
        return this;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isInvokeMethod() {
        return invokeMethod;
    }
}
