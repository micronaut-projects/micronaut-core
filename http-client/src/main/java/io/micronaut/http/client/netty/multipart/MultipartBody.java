/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.netty.multipart;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.MultipartException;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A builder class to generate a list {@link InterfaceHttpData} to build a Netty multipart request.
 *
 * @author James Kleeh
 * @since 1.0
 */
public final class MultipartBody {

    private final List<Part> parts;

    /**
     * Initialize with all the parts.
     *
     * @param parts The List of all parts to be sent in the body of Netty multipart request, such a File, String, Bytes etc.
     */
    private MultipartBody(List<Part> parts) {
        this.parts = parts;
    }

    /**
     * Create a list of {@link InterfaceHttpData} to build Netty multipart request.
     *
     * @param request Associated request
     * @param factory The factory used to create the {@link InterfaceHttpData}
     * @return List of {@link InterfaceHttpData} objects to build Netty multipart request
     */
    public List<InterfaceHttpData> getData(HttpRequest request, HttpDataFactory factory) {
        List<InterfaceHttpData> data = new ArrayList<>(parts.size());
        for (Part part : parts) {
            data.add(part.getData(request, factory));
        }
        return data;
    }

    /**
     * @return A Builder to build MultipartBody.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class to add different {@link Part} to {@link MultipartBody}.
     */
    public static final class Builder {

        /**
         * List of all parts.
         */
        private List<Part> parts = new ArrayList<>();

        /**
         * Construct a builder.
         */
        private Builder() {
        }

        /**
         * Add a file object to MultipartBody.
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param file The file object to copy the content to {@link io.micronaut.http.multipart.FileUpload}
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, File file) {
            return addPart(name, file.getName(), file);
        }

        /**
         * Add a file object to MultipartBody.
         *
         * @param name     Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param file     The file object to copy the content to {@link io.micronaut.http.multipart.FileUpload}
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String filename, File file) {
            return addFilePart(new FilePart(name, filename, file));
        }

        /**
         * Add a file object to MultipartBody.
         *
         * @param name        Name of the parameter for file object to be passed in multipart request
         * @param filename    Name of the file
         * @param contentType File content of type {@link MediaType}, possible values could be "text/plain", "application/json" etc
         * @param file        The file object to copy the content to {@link io.micronaut.http.multipart.FileUpload}
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String filename, MediaType contentType, File file) {
            return addFilePart(new FilePart(name, filename, contentType, file));
        }

        /**
         * Add bytes data to MultipartBody.
         *
         * @param name     Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param data     A byte Array (byte[]) representing the contents of the file
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String filename, byte[] data) {
            return addFilePart(new BytePart(name, filename, data));
        }

        /**
         * Add bytes data to MultipartBody.
         *
         * @param name        Name of the parameter for file object to be passed in multipart request
         * @param filename    Name of the file
         * @param contentType The content type of File, possible values could be "text/plain", "application/json" etc
         * @param data        A byte Array (byte[]) representing the contents of the file
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String filename, MediaType contentType, byte[] data) {
            return addFilePart(new BytePart(name, filename, contentType, data));
        }

        /**
         * Add a InputStream data to MultipartBody.
         *
         * @param name          Name of the parameter for file object to be passed in multipart request
         * @param filename      Name of the file
         * @param data          An {@link InputStream} data value representing the content of file object
         * @param contentLength The size of the content to pass to {@link HttpDataFactory} in order to create
         *                      {@link io.netty.handler.codec.http.multipart.FileUpload} object
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String filename, InputStream data, long contentLength) {
            return addFilePart(new InputStreamPart(name, filename, data, contentLength));
        }

        /**
         * Add a InputStream data to MultipartBody.
         *
         * @param name          Name of the parameter for file object to be passed in multipart request
         * @param filename      Name of the file
         * @param contentType   The content type of File, possible values could be "text/plain", "application/json" etc
         * @param data          An {@link InputStream} data value representing the content of file object
         * @param contentLength The size of the content to pass to {@link HttpDataFactory} in order to create
         *                      {@link io.netty.handler.codec.http.multipart.FileUpload} object
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
            return addFilePart(new InputStreamPart(name, filename, contentType, data, contentLength));
        }

        /**
         * Add a file object to MultipartBody.
         *
         * @param name  Name of the parameter or the key to be passed in multipart request
         * @param value Plain String value for the parameter
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        public Builder addPart(String name, String value) {
            parts.add(new StringPart(name, value));
            return this;
        }

        /**
         * This method is used for adding different parts extending {@link AbstractFilePart} class to
         * {@link MultipartBody}.
         *
         * @param filePart Any file part, such as {@link FilePart}, {@link InputStreamPart}, {@link BytePart} etc
         * @return A {@link MultipartBody.Builder} to build MultipartBody
         */
        private Builder addFilePart(AbstractFilePart filePart) {
            parts.add(filePart);
            return this;
        }

        /**
         * Creates {@link MultipartBody} from the provided parts.
         *
         * @return The {@link MultipartBody}
         * @throws MultipartException If there are no parts
         */
        public MultipartBody build() throws MultipartException {
            if (parts.isEmpty()) {
                throw new MultipartException("Cannot create a MultipartBody with no parts");
            }
            return new MultipartBody(parts);
        }
    }
}
