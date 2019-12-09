package io.micronaut.docs.sse;

import io.micronaut.docs.streaming.Headline;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.sse.Event;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.ZonedDateTime;

@Controller("/streaming/sse")
public class HeadlineController {

    // tag::streaming[]
    @Get(value = "/headlines", processes = MediaType.TEXT_EVENT_STREAM) // <1>
    Flux<Event<Headline>> streamHeadlines() {
        return Flux.<Event<Headline>>create((emitter) -> {  // <2>
            Headline headline = new Headline();
            headline.setText("Latest Headline at " + ZonedDateTime.now());
            emitter.next(Event.of(headline));
            emitter.complete();
        }).repeat(100) // <3>
          .delayElements(Duration.ofSeconds(1)); // <4>
    }
    // end::streaming[]
}
