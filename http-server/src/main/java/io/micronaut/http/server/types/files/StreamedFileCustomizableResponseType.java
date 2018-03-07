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
package io.micronaut.http.server.types.files;

import io.micronaut.http.server.types.CustomizableResponseTypeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;

/**
 * A special type for streaming an {@link InputStream} representing
 * a file or resource.
 *
 * @author James Kleeh
 */
public class StreamedFileCustomizableResponseType implements FileCustomizableResponseType {

    private final String name;
    private final long lastModified;
    private final InputStream inputStream;
    private final long length;

    public StreamedFileCustomizableResponseType(InputStream inputStream, String name) {
        this(inputStream, name, Instant.now().toEpochMilli());
    }

    public StreamedFileCustomizableResponseType(InputStream inputStream, String name, long lastModified) {
        this(inputStream, name, lastModified, -1);
    }

    public StreamedFileCustomizableResponseType(InputStream inputStream, String name, long lastModified, long contentLength) {
        this.name = name;
        this.lastModified = lastModified;
        this.inputStream = inputStream;
        this.length = contentLength;
    }

    public StreamedFileCustomizableResponseType(URL url) {
        String path = url.getPath();
        int idx = path.lastIndexOf(File.separatorChar);
        if (idx > -1) {
            this.name = path.substring(idx+1);
        } else {
            this.name = path;
        }
        try {
            URLConnection con = url.openConnection();
            this.lastModified = con.getLastModified();
            this.inputStream = con.getInputStream();
            this.length = con.getContentLengthLong();
        } catch (IOException e) {
            throw new CustomizableResponseTypeException("Could not open a connection to the URL: " + path, e);
        }
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public String getName() {
        return name;
    }

    public InputStream getInputStream() {
        return inputStream;
    }
}
