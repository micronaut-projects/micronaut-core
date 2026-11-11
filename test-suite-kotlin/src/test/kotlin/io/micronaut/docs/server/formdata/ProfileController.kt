package io.micronaut.docs.server.formdata

import io.micronaut.context.annotation.Requires
// tag::imports[]
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
import java.util.UUID
import java.util.concurrent.CompletionStage
// end::imports[]

@Requires(property = "spec.name", value = "ProfileControllerTest")
// tag::class[]
@Controller(value = "/profile", consumes = [MediaType.MULTIPART_FORM_DATA])
class ProfileController {

    private val uploads: Path = Files.createTempDirectory("uploads")
// end::class[]

    // tag::formData[]
    @Post("/form")
    fun form(form: FormData): CompletionStage<String> { // <1>
        val name = form.getString("name") // <2>
        val age = form.getInt("age", 18) // <3>
        val avatar = form.getFile("avatar") // <4>
        return avatar.bytes(64 * 1024) // <5>
            .thenApply { bytes -> "$name ($age) sent ${avatar.fileName()} of ${bytes.size} bytes" }
    }
    // end::formData[]

    // tag::arguments[]
    @Post("/arguments")
    fun arguments(name: String, // <1>
                  @Part("avatar") picture: FileUpload, // <2>
                  cover: FileUpload?, // <3>
                  documents: List<FileUpload>): CompletionStage<String> { // <4>
        val destination = uploads.resolve(UUID.randomUUID().toString())
        return picture.transferTo(destination) // <5>
            .thenApply {
                "$name sent ${picture.fileName()}" +
                    (if (cover == null) " without a cover" else " with ${cover.fileName()}") +
                    " and ${documents.size} documents"
            }
    }
    // end::arguments[]

    // tag::formPart[]
    @Post("/video")
    fun video(title: String, // <1>
              video: FormPart): CompletionStage<String> { // <2>
        val destination = uploads.resolve(UUID.randomUUID().toString())
        return video.transferTo(destination) // <3>
            .thenApply { "$title stored ${video.fileName()}" }
    }
    // end::formPart[]

    // tag::formParts[]
    @Post("/parts")
    fun parts(parts: FormParts): CompletionStage<String> { // <1>
        val summary = StringBuilder()
        return parts.forEach { part -> // <2>
            if (part.isFile) {
                val destination = uploads.resolve(UUID.randomUUID().toString())
                part.transferTo(destination) // <3>
                    .thenRun { summary.append(part.name()).append(" stored, ") }
            } else {
                part.text(1024) // <4>
                    .thenAccept { value -> summary.append(part.name()).append('=').append(value).append(", ") }
            }
        }.thenApply { summary.toString() } // <5>
    }
    // end::formParts[]

// tag::endclass[]
}
// end::endclass[]
