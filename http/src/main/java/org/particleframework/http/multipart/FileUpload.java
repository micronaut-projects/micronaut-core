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
package org.particleframework.http.multipart;

import org.particleframework.http.MediaType;
import org.reactivestreams.Publisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * <p>Represents a part of a {@link org.particleframework.http.MediaType#MULTIPART_FORM_DATA} request</p>
 *
 * <p>The <tt>FileUpload</tt> may be incomplete when first received, in which case the consumer can subscribe to the file upload to obtain the final completed upload.</p>
 *
 * <p>Note that if {@link #isComplete()} is false and this is a partial file upload then the various methods to obtain the data within the upload return only the data for the current chunk.</p>
 *
 * <p>The {@link #transferTo(String)} method can be used whether the upload is complete or not. If it is not complete the framework will automatically subscribe to the
 * upload and transfer the data chunk by chunk in a non-blocking manner</p>
 *
 * <p>All I/O operation return a {@link Publisher} that runs on the the configured I/O {@link java.util.concurrent.ExecutorService}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface FileUpload extends Publisher<FileUpload> {
    /**
     * Gets the content of this part as an <tt>InputStream</tt>
     *
     * @return The content of this part as an <tt>InputStream</tt>
     * @throws IOException If an error occurs in retrieving the contet
     * as an <tt>InputStream</tt>
     */
    InputStream getInputStream() throws IOException;

    /**
     * Gets the content of this part as a <tt>byte[]</tt>
     *
     * @return The content of this part as a <tt>byte[]</tt>
     * @throws IOException If an error occurs in retrieving the content
     * as a <tt>byte[]</tt>
     */
    byte[] getBytes() throws IOException;

    /**
     * Gets the content of this part as a <tt>ByteBuffer</tt>
     *
     * @return The content of this part as a <tt>ByteBuffer</tt>
     * @throws IOException If an error occurs in retrieving the content
     * as a <tt>ByteBuffer</tt>
     */
    ByteBuffer getByteBuffer() throws IOException;

    /**
     * Gets the content type of this part.
     *
     * @return The content type of this part.
     */
    Optional<MediaType> getContentType();

    /**
     * Gets the name of this part
     *
     * @return The name of this part as a <tt>String</tt>
     */
    String getName();

    /**
     * Gets the name of this part
     *
     * @return The name of this part as a <tt>String</tt>
     */
    String getFilename();

    /**
     * Returns the size of the part.
     *
     * @return a <code>long</code> specifying the size of this part, in bytes.
     */
    long getSize();

    /**
     * Returns whether the {@link FileUpload} has been fully uploaded or is in a partial state
     *
     * @return A <code>boolean</code> value of <code>true</code> if the part is fully uploaded
     */
    boolean isComplete();

    /**
     * <p>A convenience method to write this uploaded item to disk.</p>
     *
     * <p>This method will return a no-op {@link Publisher} if called multiple times for the same location</p>
     *
     * @param location the name of the file to which the stream will be
     * written. The file is created relative to the location as
     * specified in the <tt>MultipartConfiguration</tt>
     *
     *
     *
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    Publisher<Boolean> transferTo(String location);

    /**
     * <p>A convenience method to write this uploaded item to disk.</p>
     *
     * <p>This method will return a no-op {@link Publisher} if called multiple times for the same location</p>
     *
     * @param destination the destination of the file to which the stream will be
     * written.
     *
     *
     * @return A {@link Publisher} that outputs whether the transfer was successful
     */
    Publisher<Boolean> transferTo(File destination);

    /**
     * Deletes the underlying storage for a file item, including deleting any
     * associated temporary disk file.
     */
    Publisher<Boolean> delete();

}
