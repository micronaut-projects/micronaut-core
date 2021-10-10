package io.micronaut.docs.server.filters;

// tag::imports[]

import io.micronaut.http.HttpRequest;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
// end::imports[]


// tag::class[]
@Singleton
public class TraceService {

    private static final Logger LOG = LoggerFactory.getLogger(TraceService.class);

    Flowable<Boolean> trace(HttpRequest<?> request) {
        return Flowable.fromCallable(() -> { // <1>
            if (LOG.isDebugEnabled()) {
                LOG.debug("Tracing request: " + request.getUri());
            }
            // trace logic here, potentially performing I/O <2>
            return true;
        }).subscribeOn(Schedulers.io()); // <3>
    }
}
// end::class[]