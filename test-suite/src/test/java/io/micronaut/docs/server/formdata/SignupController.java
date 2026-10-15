package io.micronaut.docs.server.formdata;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.form.FileUpload;

import java.util.concurrent.CompletionStage;
// end::imports[]

@Requires(property = "spec.name", value = "ProfileControllerTest")
// tag::class[]
@Controller("/signup")
public class SignupController {

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public CompletionStage<String> signup(String name, FileUpload avatar) { // <1>
        return avatar.bytes(64 * 1024)
            .thenApply(bytes -> "Welcome " + name + ", your avatar has " + bytes.length + " bytes");
    }
}
// end::class[]
