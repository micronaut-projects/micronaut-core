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

// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller("/headlines")
class HeadlineController {

    @Get
    @ScheduleOn(TaskExecutors.IO)
    Publisher<Event<Headline>> index() { // <1>
        String[] versions = ["1.0", "2.0"] // <2>

        def initialState = { -> 0 }
        def emitterFunction = { Integer i, Emitter emitter ->  // <3>
            if (i < versions.length) {
                emitter.onNext( // <4>
                        Event.of(new Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
                )
            } else {
                emitter.onComplete() // <5>
            }
            return ++i
        }

        return Flowable.generate(initialState, emitterFunction as BiFunction<Integer,Emitter<Event<Headline>>,Integer>)
    }
}
// end::class[]
