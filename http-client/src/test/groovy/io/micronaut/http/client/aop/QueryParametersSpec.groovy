package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification


class QueryParametersSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    ItunesClient client = embeddedServer.getApplicationContext().getBean(ItunesClient)

    void "test parameters retained when context path present"() {
        expect:
        client.search("Riverside").albums.size() == 2
    }

    void "test @QueryValue parameters are appended to the URL"() {
        expect:
        client.searchTwo("Riverside").albums.size() == 2
    }

    @Controller('/itunes')
    static class ItunesController {
        Map<String, List<String>> artists = [Riverside:["Out of Myself", "Second Life Syndrome"]]

        @Get("/search")
        SearchResult search(@QueryValue String term) {
            def albums = artists.get(term)
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }
    }


    @Client("/itunes")
    static interface ItunesClient {

        @Get("/search?limit=25&media=music&entity=album&term={term}")
        SearchResult search(String term)

        @Get("/search")
        SearchResult searchTwo(@QueryValue String term)
    }

    static class SearchResult {
        List<String> albums
    }
}
