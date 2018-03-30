package io.micronaut.http.client.multipart;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.MultipartException;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
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

    public List<InterfaceHttpData> getDatas(HttpRequest request, HttpDataFactory factory) {
        List<InterfaceHttpData> datas = new ArrayList<>(parts.size());
        for (Part part: parts) {
            datas.add(part.getData(request, factory));
        }
        return datas;
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

    private static abstract class Part {
        protected final String name;

        private Part(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Adding parts with a null name is not allowed");
            }
            this.name = name;
        }

        abstract InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory);
    }

    private static class StringPart extends Part {
        protected final String value;

        private StringPart(String name, String value) {
            super(name);
            if (value == null) {
                this.value = "";
            } else {
                this.value = value;
            }
        }

        @Override
        InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
            return factory.createAttribute(request, name, value);
        }
    }

    private static abstract class AbstractFilePart extends Part {
        protected final String filename;
        protected final MediaType contentType;

        private AbstractFilePart(String name, String filename, @Nullable MediaType contentType) {
            super(name);
            if (filename == null) {
                throw new IllegalArgumentException("Adding file parts with a null filename is not allowed");
            }
            this.filename = filename;
            if (contentType == null) {
                this.contentType = MediaType.forExtension(NameUtils.extension(filename)).orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);
            } else {
                this.contentType = contentType;
            }
        }

        abstract void setContent(FileUpload fileUpload) throws IOException;

        abstract long getLength();

        @Override
        InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
            MediaType mediaType = contentType;
            String contentType = mediaType.toString();
            String encoding = mediaType.isTextBased() ? null : "binary";

            FileUpload fileUpload = factory.createFileUpload(request, name, filename, contentType,
                    encoding, null, getLength());
            try {
                setContent(fileUpload);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            return fileUpload;
        }
    }

    private static class FilePart extends AbstractFilePart {
        private final File data;

        private FilePart(String name, String filename, File data) {
            this(name, filename, null, data);
        }

        private FilePart(String name, String filename, MediaType contentType, File data) {
            super(name, filename, contentType);
            this.data = data;
        }

        @Override
        void setContent(FileUpload fileUpload) throws IOException {
            fileUpload.setContent(data);
        }

        @Override
        long getLength() {
            return data.length();
        }
    }

    private static class InputStreamPart extends AbstractFilePart {
        private final InputStream data;
        private final long contentLength;

        private InputStreamPart(String name, String filename, InputStream data, long contentLength) {
            this(name, filename, null, data, contentLength);
        }

        private InputStreamPart(String name, String filename, MediaType contentType, InputStream data, long contentLength) {
            super(name, filename, contentType);
            this.data = data;
            this.contentLength = contentLength;
        }

        @Override
        void setContent(FileUpload fileUpload) throws IOException {
            fileUpload.setContent(data);
        }

        @Override
        long getLength() {
            return contentLength;
        }
    }

    private static class BytePart extends AbstractFilePart {
        private final byte[] data;

        private BytePart(String name, String filename, byte[] data) {
            this(name, filename, null, data);
        }

        private BytePart(String name, String filename, MediaType contentType, byte[] data) {
            super(name, filename, contentType);
            this.data = data;
        }

        @Override
        void setContent(FileUpload fileUpload) throws IOException {
            fileUpload.setContent(new ByteArrayInputStream(data));
        }

        @Override
        long getLength() {
            return data.length;
        }
    }
}
