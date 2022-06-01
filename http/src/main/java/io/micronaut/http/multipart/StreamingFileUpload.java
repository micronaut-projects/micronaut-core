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
package io.micronaut.http.multipart;

import org.reactivestreams.Publisher;

import java.io.File;
import java.io.OutputStream;

/**
 * <p>Represents a part of a {@link io.micronaut.http.MediaType#MULTIPART_FORM_DATA} request.</p>
 *
 * <p>The {@code StreamingFileUpload} may be incomplete when first received, in which case the consumer can subscribe
 * to the file upload to process the data a chunk at a time.</p>
 *
 * <p>The {@link #transferTo(String)} method can be used whether the upload is complete or not. If it is not complete
 * the framework will automatically subscribe to the upload and transfer the data chunk by chunk in a non-blocking
 * manner</p>
 *
 * <p>All I/O operation return a {@link Publisher} that runs on the the configured I/O
 * {@link java.util.concurrent.ExecutorService}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface StreamingFileUpload extends FileUpload, Publisher<PartData> {

    /**
     * <p>A convenience method to write this uploaded item to disk.</p>
     *
     * <p>This method will return a no-op {@link Publisher} if called multiple times for the same location</p>
     *
     * @param location the name of the file to which the stream will be written. The file is created relative to
     *                 the location as specified in the {@code MultipartConfiguration}
     * @return A {@link Publisher} that outputs whether the transfer was successful
     * @deprecated Use {@link #transferTo(File)} or {@link #transferTo(OutputStream)} instead.
     */
    @Deprecated
    Publisher<Boolean> transferTo(String location);

    /**
     * <p>A convenience method to write this uploaded item to disk.</p>
     *
     * <p>This method will return a no-op {@link Publisher} if called multiple times for the same location</p>
     *
     * @param destination the destination of the file to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    Publisher<Boolean> transferTo(File destination);

    /**
     * <p>A convenience method to write this uploaded item the provided output stream.</p>
     *
     * @param outputStream the destination to which the stream will be written.
     * @return A {@link Publisher} that outputs whether the transfer was successful
     * @since 3.1.0
     */
    default Publisher<Boolean> transferTo(OutputStream outputStream) {
        throw new UnsupportedOperationException("StreamingFileUpload doesn't support transferTo OutputStream");
    }

    /**
     * Deletes the underlying storage for a file item, including deleting any associated temporary disk file.
     *
     * @return A {@link Publisher} that outputs whether the delete was successful
     */
    Publisher<Boolean> delete();

}
