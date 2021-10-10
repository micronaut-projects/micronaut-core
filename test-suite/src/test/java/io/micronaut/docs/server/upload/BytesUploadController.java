package io.micronaut.docs.server.upload;

// tag::class[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller("/upload")
public class BytesUploadController {

    @Post(value = "/bytes", consumes = MediaType.MULTIPART_FORM_DATA) // <1>
    public HttpResponse<String> uploadBytes(byte[] file, String fileName) { // <2>
        try {
            File tempFile = File.createTempFile(fileName, "temp");
            Path path = Paths.get(tempFile.getAbsolutePath());
            Files.write(path, file); // <3>
            return HttpResponse.ok("Uploaded");
        } catch (IOException exception) {
            return HttpResponse.badRequest("Upload Failed");
        }
    }
}
// end::class[]
