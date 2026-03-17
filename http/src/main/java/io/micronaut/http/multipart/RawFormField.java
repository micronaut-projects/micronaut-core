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

import io.micronaut.http.body.CloseableByteBody;

import java.io.Closeable;

/**
 * A raw, streamed form field. Note that processing of other form fields may be stalled until you
 * consume the {@link #byteBody}!
 */
public final class RawFormField implements Closeable {
    private final FormFieldMetadata metadata;
    private final CloseableByteBody byteBody;

    /**
     * @param metadata The field metadata provided by the user
     * @param byteBody The field bytes
     */
    public RawFormField(FormFieldMetadata metadata, CloseableByteBody byteBody) {
        this.metadata = metadata;
        this.byteBody = byteBody;
    }

    @Override
    public void close() {
        byteBody.close();
    }

    /**
     * The field metadata provided by the user.
     *
     * @return The field metadata
     */
    public FormFieldMetadata metadata() {
        return metadata;
    }

    /**
     * The field bytes.
     *
     * @return The field bytes
     */
    public CloseableByteBody byteBody() {
        return byteBody;
    }

    @Override
    public String toString() {
        return "RawFormField[" +
            "metadata=" + metadata + ", " +
            "byteBody=" + byteBody + ']';
    }
}
