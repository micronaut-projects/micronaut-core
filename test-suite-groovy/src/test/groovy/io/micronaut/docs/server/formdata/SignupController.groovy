package io.micronaut.docs.server.formdata

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.form.FileUpload

import java.util.concurrent.CompletionStage
// end::imports[]

@Requires(property = "spec.name", value = "ProfileControllerSpec")
// tag::class[]
@Controller("/signup")
class SignupController {

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    CompletionStage<String> signup(String name, FileUpload avatar) { // <1>
        avatar.bytes(64 * 1024)
            .thenApply { byte[] bytes -> "Welcome $name, your avatar has ${bytes.length} bytes".toString() }
    }
}
// end::class[]
