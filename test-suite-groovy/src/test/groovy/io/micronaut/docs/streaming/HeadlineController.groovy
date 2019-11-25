package io.micronaut.docs.streaming

// tag::imports[]

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.reactivex.Flowable

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
// end::imports[]

@Controller("/streaming")
class HeadlineController {

    // tag::streaming[]
    @Get(value = "/headlines", processes = MediaType.APPLICATION_JSON_STREAM) // <1>
    Flowable<Headline> streamHeadlines() {
        Flowable.fromCallable({ // <2>
            Headline headline = new Headline()
            headline.setText("Latest Headline at " + ZonedDateTime.now())
            return headline
        }).repeat(100) // <3>
          .delay(1, TimeUnit.SECONDS) // <4>
    }
    // end::streaming[]
}
