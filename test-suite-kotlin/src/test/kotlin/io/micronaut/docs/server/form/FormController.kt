package io.micronaut.docs.server.form

import io.micronaut.context.annotation.Requires
//tag::imports[]
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.PartData
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Files
//end::imports[]

@Requires(property = "spec.name", value = "FormControllerTest")
//tag::class[]
@Controller("/form")
class FormController {
//end::class[]

    //tag::String[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/string")
    fun setUserName(userId: Int, userName: String): String {
        if (!userName.matches(Regex("[a-z]+"))) {
            throw HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid username")
        }
        return "New user name for user ID $userId: $userName"
    }
    //end::String[]

//tag::CompletedFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload")
    @ExecuteOn(TaskExecutors.BLOCKING) // <1>
    fun fileUpload(userId: Int, avatar: CompletedFileUpload): String {
        val tmp = Files.createTempFile("avatar$userId", null)
        try {
            avatar.transferTo(tmp) // <2>

            return "Uploaded avatar for user $userId: ${Files.size(tmp)} bytes"
        } finally {
            Files.delete(tmp)
        }
    }
//end::CompletedFileUpload[]

//tag::StreamingFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-streaming")
    @ExecuteOn(TaskExecutors.BLOCKING) // <1>
    fun streamingFileUpload(userId: Int, avatar: StreamingFileUpload): String {
        val count = avatar.asInputStream().use { stream -> // <2>
            stream.readAllBytes().size // <3>
        }
        return "Streamed avatar for user $userId: $count bytes"
    }
//end::StreamingFileUpload[]

//tag::PublisherCompletedFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-completed-publisher")
    fun fileUploadCompletedPublisher(userId: Int, avatar: Publisher<CompletedFileUpload>): Mono<String> {
        return Mono.from(avatar) // <1>
            .map { cfu -> "Uploaded avatar for user $userId: ${cfu.size} bytes" }
    }
//end::PublisherCompletedFileUpload[]

//tag::PublisherStreamingFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-streaming-publisher")
    fun fileUploadStreamingPublisher(userId: Int, avatar: Publisher<StreamingFileUpload>): Publisher<String> {
        val tmp = Files.createTempFile("upload", null) // <1>
        return Mono.from(avatar)
            .flatMap { sfu -> Mono.from(sfu.transferTo(tmp)) }
            .then(Mono.fromCallable { "Streamed avatar for user $userId: ${Files.size(tmp)} bytes" }) // <1>
            .doOnTerminate { Files.deleteIfExists(tmp) } // <1>
    }
//end::PublisherStreamingFileUpload[]

//tag::PublisherPublisherBytes[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/publisher-publisher")
    fun publisherPublisher(userId: Int, avatar: Publisher<Publisher<ByteArray>>): Publisher<String> {
        return Flux.from(avatar)
            .flatMap { p -> Flux.from(p).reduce(0) { acc, arr -> acc + arr.size } }
            .collectList()
            .map { lengths -> "Streamed avatars for user $userId: $lengths bytes" }
    }
//end::PublisherPublisherBytes[]

//tag::PublisherPartData[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/publisher-part-data")
    fun publisherPartData(userId: Int, avatar: Publisher<PartData>): Publisher<String> {
        return Flux.from(avatar)
            .reduce(0) { acc, part -> part.use { acc + it.bytes.size } }
            .map { lengths -> "Streamed avatars for user $userId: $lengths bytes" }
    }
//end::PublisherPartData[]

//tag::endclass[]
}
//end::endclass[]
