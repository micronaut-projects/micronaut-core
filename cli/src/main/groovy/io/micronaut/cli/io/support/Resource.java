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
package io.micronaut.cli.io.support;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/**
 * Interface for a resource descriptor that abstracts from the actual
 * type of underlying resource, such as a file or class path resource.
 *
 * <p>An InputStream can be opened for every resource if it exists in
 * physical form, but a URL or File handle can just be returned for
 * certain resources. The actual behavior is implementation-specific.
 *
 * @author Juergen Hoeller
 * @see #getURL()
 * @see #getURI()
 * @see #getFile()
 * @since 28.12.2003
 */
public interface Resource {

    /**
     * An input stream.
     *
     * @return The input stream
     * @throws IOException if there is an error
     */
    InputStream getInputStream() throws IOException;

    /**
     * Return whether this resource actually exists in physical form.
     * <p>This method performs a definitive existence check, whereas the
     * existence of a <code>Resource</code> handle only guarantees a
     * valid descriptor handle.
     *
     * @return Whether the resource exists
     */
    boolean exists();

    /**
     * Return whether the contents of this resource can be read,
     * e.g. via {@link #getInputStream()} or {@link #getFile()}.
     * <p>Will be <code>true</code> for typical resource descriptors;
     * note that actual content reading may still fail when attempted.
     * However, a value of <code>false</code> is a definitive indication
     * that the resource content cannot be read.
     *
     * @return Where the contents can be read
     * @see #getInputStream()
     */
    boolean isReadable();

    /**
     * @return A URL handle for this resource.
     * @throws java.io.IOException if the resource cannot be resolved as URL,
     *                             i.e. if the resource is not available as descriptor
     */
    URL getURL() throws IOException;

    /**
     * @return A URI handle for this resource.
     * @throws IOException if the resource cannot be resolved as URI,
     *                     i.e. if the resource is not available as descriptor
     */
    URI getURI() throws IOException;

    /**
     * @return A File handle for this resource.
     * @throws IOException if the resource cannot be resolved as absolute
     *                     file path, i.e. if the resource is not available in a file system
     */
    File getFile() throws IOException;

    /**
     * Determine the content length for this resource.
     *
     * @return The content length
     * @throws IOException if the resource cannot be resolved
     *                     (in the file system or as some other known physical resource type)
     */
    long contentLength() throws IOException;

    /**
     * Determine the last-modified timestamp for this resource.
     *
     * @return The last-modified timestamp
     * @throws IOException if the resource cannot be resolved
     *                     (in the file system or as some other known physical resource type)
     */
    long lastModified() throws IOException;

    /**
     * Determine a filename for this resource, i.e. typically the last part of the path: for example, "myfile.txt".
     * <p>Returns <code>null</code> if this type of resource does not have a filename.
     *
     * @return The filename for this resource
     */
    String getFilename();

    /**
     * Return a description for this resource, to be used for error output when working with the resource.
     * <p>Implementations are also encouraged to return this value
     * from their <code>toString</code> method.
     *
     * @return The description for this resouce
     * @see java.lang.Object#toString()
     */
    String getDescription();

    /**
     * Creates a new resource relative to this one.
     *
     * @param relativePath The relative path
     * @return The new resource
     */
    Resource createRelative(String relativePath);
}
