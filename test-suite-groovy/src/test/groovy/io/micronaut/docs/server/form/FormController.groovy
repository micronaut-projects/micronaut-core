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
import java.nio.file.Path
import java.util.stream.Collectors
//end::imports[]

@Requires(property = "spec.name", value = "FormControllerTest")
//tag::class[]
@Controller("/form")
class FormController {
//end::class[]

    //tag::String[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/string")
    String setUserName(int userId, String userName) {
        if (!(userName ==~ /[a-z]+/)) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid username")
        }
        "New user name for user ID $userId: $userName"
    }
    //end::String[]

//tag::CompletedFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload")
    @ExecuteOn(TaskExecutors.BLOCKING) // <1>
    String fileUpload(int userId, CompletedFileUpload avatar) {
        Path tmp = Files.createTempFile("avatar" + userId, null)
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
    String streamingFileUpload(int userId, StreamingFileUpload avatar) {
        int count
        try (InputStream stream = avatar.asInputStream()) { // <2>
            count = stream.readAllBytes().length // <3>
        }
        "Streamed avatar for user $userId: $count bytes"
    }
//end::StreamingFileUpload[]

//tag::PublisherCompletedFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-completed-publisher")
    Mono<String> fileUploadCompletedPublisher(int userId, Publisher<CompletedFileUpload> avatar) {
        Mono.from(avatar) // <1>
                .map(cfu -> "Uploaded avatar for user $userId: ${cfu.size} bytes".toString())
    }
//end::PublisherCompletedFileUpload[]

//tag::PublisherStreamingFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-streaming-publisher")
    Publisher<String> fileUploadStreamingPublisher(int userId, Publisher<StreamingFileUpload> avatar) {
        Path tmp = Files.createTempFile("upload", null) // <1>
        Mono.from(avatar)
                .flatMap(sfu -> Mono.from(sfu.transferTo(tmp)))
                .then(Mono.fromCallable(() ->
                        "Streamed avatar for user $userId: ${Files.size(tmp)} bytes".toString())) // <1>
                .doOnTerminate(() -> Files.deleteIfExists(tmp)) // <1>
    }
//end::PublisherStreamingFileUpload[]

//tag::PublisherPublisherBytes[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/publisher-publisher")
    Publisher<String> publisherPublisher(int userId, Publisher<Publisher<byte[]>> avatar) {
        Flux.from(avatar)
                .flatMap(p -> Flux.from(p).collect(Collectors.summingInt(arr -> ((byte[]) arr).length)))
                .collectList()
                .map(lengths -> "Streamed avatars for user $userId: $lengths bytes".toString())
    }
//end::PublisherPublisherBytes[]

//tag::PublisherPartData[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/publisher-part-data")
    Publisher<String> publisherPartData(int userId, Publisher<PartData> avatar) {
        Flux.from(avatar)
                .collect(Collectors.summingInt(part -> {
                    try (PartData p = (PartData) part) {
                        return p.bytes.length
                    }
                }))
                .map(lengths -> "Streamed avatars for user $userId: $lengths bytes".toString())
    }
//end::PublisherPartData[]

//tag::endclass[]
}
//end::endclass[]
