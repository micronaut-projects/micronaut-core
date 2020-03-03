package io.micronaut.docs.server.sse;

// tag::imports[]
import io.micronaut.http.annotation.*;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ScheduleOn;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
// end::imports[]

// tag::class[]
@Controller("/headlines")
public class HeadlineController {

    @Get
    @ScheduleOn(TaskExecutors.IO)
    public Publisher<Event<Headline>> index() { // <1>
        String[] versions = new String[]{"1.0", "2.0"}; // <2>

        return Flowable.generate(() -> 0, (i, emitter) -> { // <3>
            if (i < versions.length) {
                emitter.onNext( // <4>
                    Event.of(new Headline("Micronaut " + versions[i] + " Released", "Come and get it"))
                );
            } else {
                emitter.onComplete(); // <5>
            }
            return ++i;
        });
    }
}
// end::class[]
