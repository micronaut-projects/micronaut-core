package io.micronaut.docs.server.form;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.core.async.annotation.SingleResult;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Requires(property = "spec.name", value = "FormControllerTest")
//tag::class[]
@Controller("/form")
public class FormController {
//end::class[]

//tag::CompletedFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload")
    @ExecuteOn(TaskExecutors.BLOCKING) // <1>
    public String fileUpload(int userId, CompletedFileUpload avatar) throws IOException {
        Path tmp = Files.createTempFile("avatar" + userId, null);
        try {
            avatar.transferTo(tmp); // <2>

            return "Uploaded avatar for user " + userId + ": " + Files.size(tmp) + " bytes";
        } finally {
            Files.delete(tmp);
        }
    }
//end::CompletedFileUpload[]

//tag::StreamingFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-streaming")
    @ExecuteOn(TaskExecutors.BLOCKING) // <1>
    public String streamingFileUpload(int userId, StreamingFileUpload avatar) throws IOException {
        int count;
        try (InputStream stream = avatar.asInputStream()) { // <2>
            count = stream.readAllBytes().length; // <3>
        }
        return "Streamed avatar for user " + userId + ": " + count + " bytes";
    }
//end::StreamingFileUpload[]

//tag::PublisherCompletedFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-completed-publisher")
    public Mono<String> fileUploadCompletedPublisher(int userId, Publisher<CompletedFileUpload> avatar) {
        return Mono.from(avatar) // <1>
            .map(cfu -> "Uploaded avatar for user " + userId + ": " + cfu.getSize() + " bytes");
    }
//end::PublisherCompletedFileUpload[]

//tag::PublisherStreamingFileUpload[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/file-upload-streaming-publisher")
    public Publisher<String> fileUploadStreamingPublisher(int userId, Publisher<StreamingFileUpload> avatar) throws IOException {
        Path tmp = Files.createTempFile("upload", null); // <1>
        return Mono.from(avatar)
            .flatMap(sfu -> Mono.from(sfu.transferTo(tmp)))
            .then(Mono.fromCallable(() ->{
                try {
                    return "Streamed avatar for user " + userId + ": " + Files.size(tmp) + " bytes"; // <1>
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }))
            .doOnTerminate(() -> {
                try {
                    Files.deleteIfExists(tmp); // <1>
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
    }
//end::PublisherStreamingFileUpload[]

//tag::PublisherPublisherBytes[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/publisher-publisher")
    public Publisher<String> publisherPublisher(int userId, Publisher<Publisher<byte[]>> avatar) {
        return Flux.from(avatar)
            .flatMap(p -> Flux.from(p).collect(Collectors.summingInt(arr -> arr.length)))
            .collectList()
            .map(lengths -> "Streamed avatars for user " + userId + ": " + lengths + " bytes");
    }
//end::PublisherPublisherBytes[]

//tag::PublisherPartData[]
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Post("/publisher-part-data")
    public Publisher<String> publisherPartData(int userId, Publisher<PartData> avatar) {
        return Flux.from(avatar)
            .collect(Collectors.summingInt(part -> {
                try (part) {
                    return part.getBytes().length;
                }
            }))
            .map(lengths -> "Streamed avatars for user " + userId + ": " + lengths + " bytes");
    }
//end::PublisherPartData[]

//tag::endclass[]
}
//end::endclass[]
