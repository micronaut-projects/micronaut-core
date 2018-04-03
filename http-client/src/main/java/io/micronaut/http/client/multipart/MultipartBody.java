package io.micronaut.http.client.multipart;

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

    private MultipartBody(List<Part> parts) {
        this.parts = parts;
    }

    /**
     * Create a list of {@link InterfaceHttpData} from list {@link Part} added to {@link MultipartBody.Builder} object
     *
     * @param request associated request
     * @param factory An object of class extending {@link HttpDataFactory}, to enable creation of InterfaceHttpData objects from {@link Part}
     * @return List of {@link InterfaceHttpData} objects created from {@link Part} list of {@link MultipartBody.Builder}
     */
    public List<InterfaceHttpData> getData(HttpRequest request, HttpDataFactory factory) {
        List<InterfaceHttpData> data = new ArrayList<>(parts.size());
        for (Part part: parts) {
            data.add(part.getData(request, factory));
        }
        return data;
    }

    /**
     * Creates a new {@link MultipartBody.Builder} object for adding parts to {@link MultipartBody}
     * @return {@link MultipartBody.Builder} object
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class to add different {@link Part} to {@link MultipartBody}
     */
    public static class Builder {

        private Builder() { }

        private List<Part> parts = new ArrayList<>();

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param file File object
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, File file) {
            return addPart(name, file.getName(), file);
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param file File object
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String filename, File file) {
            return addFilePart(new FilePart(name, filename, file));
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param contentType File content of type {@link MediaType}, possible values could be "text/plain", "application/json" etc
         * @param file File object
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String filename, MediaType contentType, File file) {
            return addFilePart(new FilePart(name, filename, contentType, file));
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param data A byte Array (byte[]) representing the contents of the file
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String filename, byte[] data) {
            return addFilePart(new BytePart(name, filename, data));
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param contentType File content of type {@link MediaType}, possible values could be "text/plain", "application/json" etc
         * @param data A byte Array (byte[]) representing the contents of the file
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String filename, MediaType contentType, byte[] data) {
            return addFilePart(new BytePart(name, filename, contentType, data));
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param data An {@link InputStream} data value representing the content of file object
         * @param contentLength A {@link long} number value representing the length of {@link InputStream} data
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String filename, InputStream data, long contentLength) {
            return addFilePart(new InputStreamPart(name, filename, data, contentLength));
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter for file object to be passed in multipart request
         * @param filename Name of the file
         * @param contentType File content of type {@link MediaType}, possible values could be "text/plain", "application/json" etc
         * @param data An {@link InputStream} data value representing the content of file object
         * @param contentLength A {@link long} number value representing the length of {@link InputStream} data
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
            return addFilePart(new InputStreamPart(name, filename, contentType, data, contentLength));
        }

        /**
         * Add a file object to MultipartBody
         *
         * @param name Name of the parameter or the key to be passed in multipart request
         * @param value Plain String value for the parameter
         * @return {@link MultipartBody.Builder} object
         */
        public Builder addPart(String name, String value) {
            parts.add(new StringPart(name, value));
            return this;
        }

        /**
         * This method is used for adding different parts extending {@link AbstractFilePart} class to {@link MultipartBody}
         *
         * @param filePart Any object to classes extending {@link AbstractFilePart}, such as {@link FilePart}, {@link InputStreamPart}, {@link BytePart} etc
         * @return {@link MultipartBody.Builder} object
         */
        private Builder addFilePart(AbstractFilePart filePart) {
            parts.add(filePart);
            return this;
        }

        /**
         * Assemble all parts from {@link MultipartBody.Builder} to {@link MultipartBody} object and should
         * be called after adding all the parts to the {@link MultipartBody.Builder}
         *
         * @return {@link MultipartBody} object
         */
        public MultipartBody build() {
            if (parts.isEmpty()) {
                throw new MultipartException("Cannot create a MultipartBody with no parts");
            }
            return new MultipartBody(parts);
        }
    }
}
