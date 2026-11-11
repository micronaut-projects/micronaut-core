package io.micronaut.docs.server.formdata

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.form.FileUpload
import io.micronaut.http.form.FormData
import io.micronaut.http.form.FormPart
import io.micronaut.http.form.FormParts

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletionStage
// end::imports[]

@Requires(property = "spec.name", value = "ProfileControllerSpec")
// tag::class[]
@Controller(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA)
class ProfileController {

    private final Path uploads = Files.createTempDirectory("uploads")
// end::class[]

    // tag::formData[]
    @Post("/form")
    CompletionStage<String> form(FormData form) { // <1>
        String name = form.getString("name") // <2>
        int age = form.getInt("age", 18) // <3>
        FileUpload avatar = form.getFile("avatar") // <4>
        avatar.bytes(64 * 1024) // <5>
            .thenApply { byte[] bytes -> "$name ($age) sent ${avatar.fileName()} of ${bytes.length} bytes".toString() }
    }
    // end::formData[]

    // tag::arguments[]
    @Post("/arguments")
    CompletionStage<String> arguments(String name, // <1>
                                      @Part("avatar") FileUpload picture, // <2>
                                      @Nullable FileUpload cover, // <3>
                                      List<FileUpload> documents) { // <4>
        Path destination = uploads.resolve(UUID.randomUUID().toString())
        picture.transferTo(destination) // <5>
            .thenApply {
                "$name sent ${picture.fileName()}" +
                    (cover == null ? " without a cover" : " with ${cover.fileName()}") +
                    " and ${documents.size()} documents"
            }
    }
    // end::arguments[]

    // tag::formPart[]
    @Post("/video")
    CompletionStage<String> video(String title, // <1>
                                  FormPart video) { // <2>
        Path destination = uploads.resolve(UUID.randomUUID().toString())
        video.transferTo(destination) // <3>
            .thenApply { "$title stored ${video.fileName()}".toString() }
    }
    // end::formPart[]

    // tag::formParts[]
    @Post("/parts")
    CompletionStage<String> parts(FormParts parts) { // <1>
        StringBuilder summary = new StringBuilder()
        parts.forEach { FormPart part -> // <2>
            if (part.file) {
                Path destination = uploads.resolve(UUID.randomUUID().toString())
                return part.transferTo(destination) // <3>
                    .thenRun { summary.append(part.name()).append(" stored, ") }
            }
            return part.text(1024) // <4>
                .thenAccept { String value -> summary.append(part.name()).append('=').append(value).append(", ") }
        }.thenApply { summary.toString() } // <5>
    }
    // end::formParts[]

// tag::endclass[]
}
// end::endclass[]
