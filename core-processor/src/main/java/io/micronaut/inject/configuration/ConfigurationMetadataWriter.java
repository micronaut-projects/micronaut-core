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

import io.micronaut.inject.writer.ClassWriterOutputVisitor;

import java.io.IOException;

/**
 * An interface for classes that write {@link io.micronaut.context.annotation.ConfigurationProperties} metadata.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConfigurationMetadataWriter {

    /**
     * An interface for classes that can write metadata produced by a {@link ConfigurationMetadataBuilder}.
     *
     * @param metadataBuilder          The metadata builder
     * @param classWriterOutputVisitor The class output visitor
     * @throws IOException If an error occurred writing output
     */
    void write(ConfigurationMetadataBuilder metadataBuilder, ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException;
}
