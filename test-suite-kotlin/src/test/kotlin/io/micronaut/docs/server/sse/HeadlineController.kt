package io.micronaut.docs.server.sse

// tag::imports[]

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.sse.Event
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ScheduleOn
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import org.reactivestreams.Publisher
import java.util.concurrent.Callable

// end::imports[]

// tag::class[]
@Controller("/headlines")
class HeadlineController {

    @Get
    @ScheduleOn(TaskExecutors.IO)
    fun index(): Publisher<Event<Headline>> { // <1>
        val versions = arrayOf("1.0", "2.0") // <2>

        return Flowable.generate<Event<Headline>, Int>(Callable<Int>{ 0 }, BiFunction { // <3>
            i: Int, emitter: Emitter<Event<Headline>> ->
            var nextInt: Int = i
            if (i < versions.size) {
                emitter.onNext( // <4>
                        Event.of<Headline>(Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
                )
            } else {
                emitter.onComplete() // <5>
            }
            ++nextInt
        })
    }
}
// end::class[]