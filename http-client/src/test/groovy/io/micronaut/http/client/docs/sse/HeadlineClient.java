package io.micronaut.http.client.docs.sse;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.Client;
import io.micronaut.http.client.docs.streaming.Headline;
import io.micronaut.http.sse.Event;
import reactor.core.publisher.Flux;

// tag::class[]
@Client("/streaming/sse")
public interface HeadlineClient {

    @Get(uri = "/headlines", processes = MediaType.TEXT_EVENT_STREAM)
    Flux<Event<Headline>> streamHeadlines();
}
// end::class[]