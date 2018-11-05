package io.micronaut.docs.rejection;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.handlers.HttpStatusCodeRejectionHandler;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

@Requires(property = "spec.name", value = "rejection-handler")
//tag::clazz[]
@Singleton
@Replaces(HttpStatusCodeRejectionHandler.class)
public class MyRejectionHandler extends HttpStatusCodeRejectionHandler {

    @Override
    public Publisher<MutableHttpResponse<?>> reject(HttpRequest<?> request, boolean forbidden) {
        //Let the HttpStatusCodeRejectionHandler create the initial request
        //then add a header
        return Flowable.fromPublisher(super.reject(request, forbidden))
                .map(response -> response.header("X-Reason", "Example Header"));
    }
}
//end::clazz[]