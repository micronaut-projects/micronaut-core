package io.micronaut.docs.server.sse;

// tag::imports[]

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.sse.Event
import io.reactivex.Emitter;
import io.reactivex.Flowable
import io.reactivex.annotations.NonNull
import io.reactivex.functions.BiFunction;
import org.reactivestreams.Publisher;
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller("/headlines")
class HeadlineController {

    @Get
    Publisher<Event<Headline>> index() { // <1>
        String[] versions = ["1.0", "2.0"] // <2>

          return Flowable.generate({ ->0 }, new BiFunction<Integer, Emitter<Event<Headline>>, Integer>() {
              @Override
              Integer apply(@NonNull Integer i, @NonNull Emitter<Event<Headline>> tEmitter) throws Exception {
                if (i < versions.length) {
                    tEmitter.onNext( // <4>
                              Event.of(new Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
                      )
                  } else {
                    tEmitter.onComplete() // <5>
                  }
                  ++i
              }})
//        return Flowable.generate({ -> 0 }, { i, emitter ->  // <3>
//            if (i < versions.length) {
//                emitter.onNext( // <4>
//                        Event.of(new Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
//                )
//            } else {
//                emitter.onComplete() // <5>
//            }
//            ++i
//        });
    }
}
// end::class[]
