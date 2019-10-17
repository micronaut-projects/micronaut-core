package io.micronaut.docs.sse

import io.micronaut.docs.streaming.Headline
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import reactor.core.publisher.Flux

// tag::class[]
@Client("/streaming/sse")
interface HeadlineClient {

    @Get(value = "/headlines", processes = MediaType.TEXT_EVENT_STREAM)
    Flux<Event<Headline>> streamHeadlines()
}
// end::class[]