package io.micronaut.docs.server.body;

// tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.reactivex.Flowable;
import io.reactivex.Single;

import javax.validation.constraints.Size;
// end::imports[]

// tag::class[]
@Controller("/receive")
public class MessageController {
// end::class[]

    // tag::echo[]
    @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN) // <1>
    String echo(@Size(max = 1024) @Body String text) { // <2>
        return text; // <3>
    }
    // end::echo[]

    // tag::echoReactive[]
    @Post(value = "/echo-flow", consumes = MediaType.TEXT_PLAIN) // <1>
    Single<MutableHttpResponse<String>> echoFlow(@Body Flowable<String> text) { //<2>
        return text.collect(StringBuffer::new, StringBuffer::append) // <3>
                   .map(buffer ->
                        HttpResponse.ok(buffer.toString())
                   );
    }
    // end::echoReactive[]
// tag::class[]
}
// end::class[]
