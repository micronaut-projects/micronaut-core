/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.multipart;

import io.micronaut.core.io.buffer.ReadBuffer;

import java.io.InputStream;
import java.util.concurrent.Executor;

/**
 * A form field that is not a file upload.
 */
public final class CompletedAttribute extends CompletedPart {
    private final ReadBuffer readBuffer;

    private CompletedAttribute(FormFieldMetadata metadata, ReadBuffer readBuffer) {
        super(metadata);
        this.readBuffer = readBuffer;
    }

    /**
     * Create a new memory-backed attribute. Ownership of the data buffer transfers to the
     * attribute object. Closing the attribute object will close the memory.
     *
     * @param metadata The field metadata
     * @param readBuffer The attribute memory
     * @return The attribute
     */
    public static CompletedAttribute create(FormFieldMetadata metadata, ReadBuffer readBuffer) {
        return new CompletedAttribute(metadata, readBuffer);
    }

    @Override
    public boolean isInMemory() {
        return true;
    }

    @Override
    public long getSize() {
        return readBuffer.readable();
    }

    @Override
    public InputStream getInputStream() {
        return readBuffer.toInputStream();
    }

    @Override
    public ReadBuffer toReadBuffer() {
        return readBuffer.move();
    }

    @Override
    public CompletedPart moveResource() {
        return create(getMetadata(), toReadBuffer());
    }

    @Override
    public void closeAsync(Executor ioExecutor) {
        close();
    }

    @Override
    public void close() {
        readBuffer.close();
        closeTracker();
    }
}
