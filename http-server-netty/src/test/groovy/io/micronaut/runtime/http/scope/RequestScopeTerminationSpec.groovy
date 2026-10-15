package io.micronaut.runtime.http.scope

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The request scope destroys its beans when the {@link HttpRequestTerminatedEvent} is published.
 * The server skips the event for requests that hold no request scoped beans, unless another
 * listener observes it.
 */
class RequestScopeTerminationSpec extends Specification {

    PollingConditions conditions = new PollingConditions(timeout: 5)

    @AutoCleanup
    ApplicationContext ctx

    @AutoCleanup
    HttpClient client

    ListAppender appender = new ListAppender()
    Logger publisherLogger = (Logger) LoggerFactory.getLogger(ApplicationEventPublisher)
    Level previousLevel = publisherLogger.level

    def setup() {
        TrackedBean.CREATED.clear()
        TrackedBean.DESTROYED.clear()
        appender.start()
        publisherLogger.addAppender(appender)
        publisherLogger.level = Level.DEBUG
    }

    def cleanup() {
        publisherLogger.detachAppender(appender)
        publisherLogger.level = previousLevel
    }

    private void start(boolean withListener) {
        ctx = ApplicationContext.run([
                'spec.name'         : 'RequestScopeTerminationSpec',
                'spec.with-listener': withListener,
        ])
        def server = ctx.getBean(EmbeddedServer).start()
        client = ctx.createBean(HttpClient, server.URI)
    }

    private int terminatedEventsPublished() {
        return appender.messages.count { it.startsWith('Publishing event: ') && it.contains(HttpRequestTerminatedEvent.name) }
    }

    def "request scoped beans are destroyed after the request"(boolean withListener, String path, String expected) {
        given:
        start(withListener)

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET(path), String)

        then:
        response.body() == expected
        TrackedBean.CREATED.size() == 1
        conditions.eventually {
            TrackedBean.DESTROYED.size() == 1
        }
        def destroyed = TrackedBean.DESTROYED.first()
        destroyed.is(TrackedBean.CREATED.first())
        destroyed.destroyedWithRequest
        destroyed.destroyThread.contains('eventLoopGroup')
        terminatedEventsPublished() == 1

        where:
        withListener | path                      | expected
        false        | '/request-scope/in-route' | 'route 1'
        true         | '/request-scope/in-route' | 'route 1'
        false        | '/request-scope/filter'   | 'no-bean'
        true         | '/request-scope/filter'   | 'no-bean'
        false        | '/request-scope/error'    | 'error 1'
        true         | '/request-scope/error'    | 'error 1'
    }

    def "the event is only published for requests without request scoped beans when a listener observes it"(boolean withListener) {
        given:
        start(withListener)

        when:
        3.times {
            assert client.toBlocking().retrieve('/request-scope/no-bean') == 'no-bean'
        }

        then:
        if (withListener) {
            conditions.eventually {
                ctx.getBean(TerminatedListener).count.get() == 3
            }
        }
        Thread.sleep(200)
        terminatedEventsPublished() == (withListener ? 3 : 0)
        TrackedBean.CREATED.isEmpty()

        where:
        withListener << [false, true]
    }

    @Requires(property = 'spec.name', value = 'RequestScopeTerminationSpec')
    @RequestScope
    static class TrackedBean {
        static final List<TrackedBean> CREATED = new CopyOnWriteArrayList<>()
        static final List<TrackedBean> DESTROYED = new CopyOnWriteArrayList<>()

        int calls
        boolean destroyedWithRequest
        String destroyThread

        TrackedBean() {
            // don't count the proxy
            if (getClass() == TrackedBean) {
                CREATED.add(this)
            }
        }

        int call() {
            return ++calls
        }

        @PreDestroy
        void destroy() {
            destroyedWithRequest = ServerRequestContext.currentRequest().isPresent()
            destroyThread = Thread.currentThread().name
            DESTROYED.add(this)
        }
    }

    @Requires(property = 'spec.name', value = 'RequestScopeTerminationSpec')
    @Controller('/request-scope')
    static class TestController {
        @Inject
        TrackedBean bean

        @Get('/in-route')
        String inRoute() {
            return 'route ' + bean.call()
        }

        @Get('/no-bean')
        String noBean() {
            return 'no-bean'
        }

        @Get('/filter')
        String filter() {
            return 'no-bean'
        }

        @Get('/error')
        String error() {
            throw new IllegalStateException('failed')
        }

        @Error(IllegalStateException)
        HttpResponse<String> onError(IllegalStateException e) {
            return HttpResponse.ok('error ' + bean.call())
        }
    }

    @Requires(property = 'spec.name', value = 'RequestScopeTerminationSpec')
    @ServerFilter('/request-scope/filter')
    static class LateBeanFilter {
        @Inject
        TrackedBean bean

        @ResponseFilter
        void afterRoute(MutableHttpResponse<?> response) {
            bean.call()
        }
    }

    @Requires(property = 'spec.name', value = 'RequestScopeTerminationSpec')
    @Requires(property = 'spec.with-listener', value = 'true')
    @Singleton
    static class TerminatedListener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        final AtomicInteger count = new AtomicInteger()

        @Override
        void onApplicationEvent(HttpRequestTerminatedEvent event) {
            count.incrementAndGet()
        }
    }

    static class ListAppender extends AppenderBase<ILoggingEvent> {
        final List<String> messages = new CopyOnWriteArrayList<>()

        @Override
        protected void append(ILoggingEvent event) {
            messages.add(event.formattedMessage)
        }
    }
}
