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

    public List<InterfaceHttpData> getData(HttpRequest request, HttpDataFactory factory) {
        List<InterfaceHttpData> data = new ArrayList<>(parts.size());
        for (Part part: parts) {
            data.add(part.getData(request, factory));
        }
        return data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Builder() { }

        private List<Part> parts = new ArrayList<>();

        public Builder addPart(String name, File file) {
            return addPart(name, file.getName(), file);
        }

        public Builder addPart(String name, String filename, File file) {
            return addFilePart(new FilePart(name, filename, file));
        }

        public Builder addPart(String name, String filename, MediaType contentType, File file) {
            return addFilePart(new FilePart(name, filename, contentType, file));
        }

        public Builder addPart(String name, String filename, byte[] data) {
            return addFilePart(new BytePart(name, filename, data));
        }

        public Builder addPart(String name, String filename, MediaType contentType, byte[] data) {
            return addFilePart(new BytePart(name, filename, contentType, data));
        }

        public Builder addPart(String name, String filename, InputStream data, long contentLength) {
            return addFilePart(new InputStreamPart(name, filename, data, contentLength));
        }

        public Builder addPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
            return addFilePart(new InputStreamPart(name, filename, contentType, data, contentLength));
        }

        public Builder addPart(String name, String value) {
            parts.add(new StringPart(name, value));
            return this;
        }

        private Builder addFilePart(AbstractFilePart filePart) {
            parts.add(filePart);
            return this;
        }

        public MultipartBody build() {
            if (parts.isEmpty()) {
                throw new MultipartException("Cannot create a MultipartBody with no parts");
            }
            return new MultipartBody(parts);
        }
    }
}
