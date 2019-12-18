package io.micronaut.docs.streaming

// tag::imports[]

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZonedDateTime

// end::imports[]

@Controller("/streaming")
class HeadlineFlowController {

    // tag::streamingWithFlow[]
    @Get(value = "/headlinesWithFlow", processes = [MediaType.APPLICATION_JSON_STREAM])
    internal fun streamHeadlinesWithFlow(): Flow<Headline> = // <1>
        flow { // <2>
            repeat(100) { // <3>
                with (Headline()) {
                    text = "Latest Headline at " + ZonedDateTime.now()
                    emit(this) // <4>
                    delay(1_000) // <5>
                }
            }
        }
    // end::streamingWithFlow[]
}
