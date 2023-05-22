/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.writer;

import java.io.IOException;

/**
 * A component that accepts a {@link ClassWriterOutputVisitor} and writes classes to it.
 *
 * @since 3.5.2
 */
public interface ClassOutputWriter {
    /**
     * Accept a ClassWriterOutputVisitor to write this writer to disk.
     *
     * @param classWriterOutputVisitor The {@link ClassWriterOutputVisitor}
     * @throws IOException if there is an error writing to disk
     */
    void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException;
}
