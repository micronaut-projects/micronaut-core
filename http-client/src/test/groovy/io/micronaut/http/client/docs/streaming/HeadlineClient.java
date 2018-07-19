package io.micronaut.http.client.docs.streaming;

// tag::imports[]
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.Client;
import io.reactivex.Flowable;
// end::imports[]
import reactor.core.publisher.Flux;

// tag::class[]
@Client("/streaming")
public interface HeadlineClient {

    @Get(uri = "/headlines", processes = MediaType.APPLICATION_JSON_STREAM) // <1>
    Flowable<Headline> streamHeadlines(); // <2>
// end::class[]

    @Get(uri = "/headlines", processes = MediaType.APPLICATION_JSON_STREAM) // <1>
    Flux<Headline> streamFlux();
}
