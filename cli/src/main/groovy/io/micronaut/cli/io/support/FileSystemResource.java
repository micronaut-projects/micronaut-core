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
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;

/**
 * Based on Spring FileSystemResource implementation.
 *
 * @author Juergen Hoeller
 * @author Graeme Rocher
 * @see java.io.File
 * @since 28.12.2003
 */
public class FileSystemResource implements Resource {

    private final File file;
    private final String path;

    /**
     * Create a new FileSystemResource from a File handle.
     *
     * @param file a File handle
     */
    public FileSystemResource(File file) {
        assertNotNull(file, "File must not be null");
        this.file = file;
        path = ResourceUtils.cleanPath(file.getPath());
    }

    /**
     * Create a new FileSystemResource from a file path.
     *
     * @param path a file path
     */
    public FileSystemResource(String path) {
        assertNotNull(path, "Path must not be null");
        file = new File(path);
        this.path = ResourceUtils.cleanPath(path);
    }

    /**
     * @return The file path for this resource.
     */
    public final String getPath() {
        return path;
    }

    /**
     * This implementation returns whether the underlying file exists.
     *
     * @see java.io.File#exists()
     */
    @Override
    public boolean exists() {
        return file.exists();
    }

    /**
     * This implementation checks whether the underlying file is marked as readable
     * (and corresponds to an actual file with content, not to a directory).
     *
     * @see java.io.File#canRead()
     * @see java.io.File#isDirectory()
     */
    @Override
    public boolean isReadable() {
        return file.canRead() && !file.isDirectory();
    }

    /**
     * This implementation opens a FileInputStream for the underlying file.
     *
     * @see java.io.FileInputStream
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(file.toPath());
    }

    /**
     * This implementation returns a URL for the underlying file.
     *
     * @see java.io.File#toURI()
     */
    @Override
    public URL getURL() throws IOException {
        return file.toURI().toURL();
    }

    /**
     * This implementation returns a URI for the underlying file.
     *
     * @see java.io.File#toURI()
     */
    @Override
    public URI getURI() throws IOException {
        return file.toURI();
    }

    /**
     * This implementation returns the underlying File reference.
     */
    @Override
    public File getFile() {
        return file;
    }

    /**
     * This implementation returns the underlying File's length.
     */
    @Override
    public long contentLength() throws IOException {
        return file.length();
    }

    @Override
    public long lastModified() throws IOException {
        return file.lastModified();
    }

    /**
     * This implementation returns the name of the file.
     *
     * @see java.io.File#getName()
     */
    @Override
    public String getFilename() {
        return file.getName();
    }

    /**
     * This implementation returns a description that includes the absolute
     * path of the file.
     *
     * @see java.io.File#getAbsolutePath()
     */
    @Override
    public String getDescription() {
        return "file [" + file.getAbsolutePath() + "]";
    }

    /**
     * This implementation creates a FileSystemResource, applying the given path
     * relative to the path of the underlying file of this resource descriptor.
     */
    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = ResourceUtils.applyRelativePath(path, relativePath);
        return new FileSystemResource(pathToUse);
    }

    // implementation of WritableResource

    /**
     * This implementation checks whether the underlying file is marked as writable
     * (and corresponds to an actual file with content, not to a directory).
     *
     * @return Whether the file is writable
     * @see java.io.File#canWrite()
     * @see java.io.File#isDirectory()
     */
    public boolean isWritable() {
        return file.canWrite() && !file.isDirectory();
    }

    /**
     * This implementation opens a FileOutputStream for the underlying file.
     *
     * @return The FileOutputStream
     * @throws IOException if there is an error
     * @see java.io.FileOutputStream
     */
    public OutputStream getOutputStream() throws IOException {
        return Files.newOutputStream(file.toPath());
    }

    /**
     * This implementation compares the underlying File references.
     */
    @Override
    public boolean equals(Object obj) {
        return (obj == this ||
            (obj instanceof FileSystemResource && path.equals(((FileSystemResource) obj).path)));
    }

    /**
     * This implementation returns the hash code of the underlying File reference.
     */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /**
     * Assert that an object is not null.
     *
     * @param object  The object to check
     * @param message The error message thrown if the object is null
     */
    protected void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
