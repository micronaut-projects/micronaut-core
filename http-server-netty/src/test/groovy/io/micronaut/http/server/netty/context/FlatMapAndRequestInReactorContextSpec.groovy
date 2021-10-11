package io.micronaut.http.server.netty.context

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import javax.validation.constraints.NotBlank
import java.util.stream.Stream

class FlatMapAndRequestInReactorContextSpec extends Specification {

    private static final Logger LOG = LoggerFactory.getLogger(FlatMapAndRequestInReactorContextSpec.class)

    @Shared
    @AutoCleanup
    EmbeddedServer bookInventoryServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'FlatMapAndRequestInReactorContextSpec.bookinventory'
    ])

    @Shared
    @AutoCleanup
    EmbeddedServer bookCatalogueServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'FlatMapAndRequestInReactorContextSpec.bookcatalogue'
    ])

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'FlatMapAndRequestInReactorContextSpec.bookrecommendation',
            'micronaut.http.services.bookcatalogue.url': "http://localhost:$bookCatalogueServer.port",
            'micronaut.http.services.bookinventory.url': "http://localhost:$bookInventoryServer.port",
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Shared
    BlockingHttpClient client = httpClient.toBlocking()

    @Unroll
    void "HTTP Client Filters can access original request even if they are executed from calls within a flatMap"(String path, Set<String> expected) {
        when:
        List<BookRecommendation> response = client.retrieve(HttpRequest.GET(path), Argument.listOf(BookRecommendation))

        then:
        noExceptionThrown()
        expected == response*.name as Set<String>

        where:
        path            || expected
        '/four'         || ["Building Microservices", "Release It!", "Continuous Delivery"]
        '/zero'         || []
    }

    @Unroll
    void "HTTP Client Filters can access original request in a blocking flow"(String path, Set<String> expected) {
        when:
        List<BookRecommendation> response = client.retrieve(HttpRequest.GET(path), Argument.listOf(BookRecommendation))

        then:
        noExceptionThrown()
        expected == response*.name as Set<String>

        where:
        path            || expected
        '/fourblocking' || ["Building Microservices", "Release It!", "Continuous Delivery"]
        '/zeroblocking' || []
    }

    @Requires(property = 'spec.name', value = 'FlatMapAndRequestInReactorContextSpec.bookrecommendation')
    @Filter("/books/stock/**")
    static class StockFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return Flux.deferContextual(contextView -> {
                HttpRequest<?> contexRequest = contextView.getOrDefault(ServerRequestContext.KEY, null)
                if (contexRequest == null) {
                    LOG.warn("parent request is not in the reactor context")
                }
                return chain.proceed(contexRequest == null ? request : request.header("FOOBAR", contexRequest.path))
            })
        }
    }

    @Requires(property = 'spec.name', value = 'FlatMapAndRequestInReactorContextSpec.bookcatalogue')
    @Controller("/books")
    static class BooksCatalogueController {
        @Get
        List<BookMeta> index() {
            [
                    new BookMeta(isbn: "1491950358", name: "Building Microservices"),
                    new BookMeta(isbn: "1680502395", name: "Release It!"),
                    new BookMeta(isbn: "0321601912", name: "Continuous Delivery")
            ]
        }
    }

    @CompileStatic
    @EqualsAndHashCode
    @Introspected
    static class BookMeta {
        String isbn
        String name
    }

    @Requires(property = 'spec.name', value = 'FlatMapAndRequestInReactorContextSpec.bookrecommendation')
    @Controller
    static class BookController {

        @Inject
        BookCatalogueClient bookCatalogueClient

        @Inject
        BookInventoryClient bookInventoryClient

        BookController(BookCatalogueClient bookCatalogueClient,
                       BookInventoryClient bookInventoryClient) {
            this.bookCatalogueClient = bookCatalogueClient
            this.bookInventoryClient = bookInventoryClient
        }

        @Get("/four")
        Flux<BookRecommendation> four() {
            recommendations()
        }

        @Get("/zero")
        Flux<BookRecommendation> zero() {
            recommendations()
        }

        @Get("/fourblocking")
        @ExecuteOn(TaskExecutors.IO)
        Stream<BookRecommendation> fourblocking() {
            blockingrecommendations()
        }

        @Get("/zeroblocking")
        @ExecuteOn(TaskExecutors.IO)
        Stream<BookRecommendation> zeroblocking() {
            blockingrecommendations()
        }

        private Stream<BookRecommendation> blockingrecommendations() {
            bookCatalogueClient.findAllBlocking()
                    .stream()
                    .filter(b -> bookInventoryClient.stockBlocking(b.isbn).stock > 0)
                    .map(b -> new BookRecommendation(name: b.name))
        }

        private Flux<BookRecommendation> recommendations() {
            Flux.from(bookCatalogueClient.findAll())
                .flatMap(b -> {
                    Flux.from(bookInventoryClient.stock(b.isbn))
                            .filter(bi -> bi.stock > 0)
                            .map(bi -> b)
                })
                    .map(b -> new BookRecommendation(name: b.name))
        }
    }

    static class BookRecommendation {
        String name
    }

    @Requires(property = 'spec.name', value = 'FlatMapAndRequestInReactorContextSpec.bookrecommendation')
    @Client(id = "bookcatalogue")
    static interface BookCatalogueClient {
        @Get("/books")
        Publisher<BookMeta> findAll()

        @Get("/books")
        List<BookMeta> findAllBlocking()
    }

    @Requires(property = 'spec.name', value = 'FlatMapAndRequestInReactorContextSpec.bookrecommendation')
    @Client(id = "bookinventory")
    static interface BookInventoryClient {
        @Get("/books/stock/{isbn}")
        @SingleResult
        Publisher<BookInventory> stock(@NonNull @NotBlank String isbn)

        @Get("/books/stock/{isbn}")
        BookInventory stockBlocking(@NonNull @NotBlank String isbn)
    }

    @Requires(property = 'spec.name', value = 'FlatMapAndRequestInReactorContextSpec.bookinventory')
    @CompileStatic
    @Controller("/books")
    static class BooksController {

        @Get("/stock/{isbn}")
        BookInventory stock(String isbn, @Nullable @Header("FOOBAR") String stocklevel) {
            new BookInventory(isbn: isbn, stock: (stocklevel == '/four' || stocklevel == '/fourblocking' ? 4 : 0))
        }
    }

    static class BookInventory {
        String isbn
        int stock
    }
}
