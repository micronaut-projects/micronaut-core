package io.micronaut.docs.server.filters

// tag::imports[]

import io.micronaut.http.HttpRequest
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory

import javax.inject.Singleton

// end::imports[]

// tag::class[]
@Singleton
class TraceService {

    private val LOG = LoggerFactory.getLogger(TraceService::class.java)

    internal fun trace(request: HttpRequest<*>): Flowable<Boolean> {
        return Flowable.fromCallable {
            // <1>
            if (LOG.isDebugEnabled) {
                LOG.debug("Tracing request: " + request.uri)
            }
            // trace logic here, potentially performing I/O <2>
            true
        }.subscribeOn(Schedulers.io()) // <3>
    }
}
// end::class[]