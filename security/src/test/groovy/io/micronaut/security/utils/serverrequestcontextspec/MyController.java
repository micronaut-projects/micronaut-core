package io.micronaut.security.utils.serverrequestcontextspec;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.reactivex.Flowable;
import io.reactivex.Single;

@Requires(property = "spec.name", value = "ServerRequestContextReactiveSpec")
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/mycontroller")
class MyController {

    @Get
    Flowable<Message> index() {
        return Flowable.just("foo")
                .flatMapSingle(name -> {
                    if (ServerRequestContext.currentRequest().isPresent()) {
                        return Single.just(new Message("Sergio"));
                    }
                    return Single.just(new Message("Anonymous"));
                });
    }

    @Get("/simple")
    Flowable<Message> simple() {
        if (ServerRequestContext.currentRequest().isPresent()) {
            return Flowable.just(new Message("Sergio"));
        }
        return Flowable.just(new Message("Anonymous"));
    }
}
