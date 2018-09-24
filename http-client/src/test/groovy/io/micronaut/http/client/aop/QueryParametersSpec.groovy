package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nullable


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

    @Unroll
    void "test client mappping URL parameters appended through a Map (served through #flavour)"() {
        expect:
            client.searchExplodedMap(flavour, [term: "Riverside"]).albums.size() == 2
        where:
            flavour << [ "pojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping URL parameters appended through a List (served through #flavour)"() {
        expect:
        client.searchExplodedList(flavour, ["Tool"]).albums.size() == 1
        where:
        flavour << [ "pojo", "list", "map" ]
    }

    void "test query value with default value"() {
        given:
        RxHttpClient lowLevelClient = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL())

        expect:
        client.searchDefault("Riverside").albums.size() == 2
        client.searchDefault(null).albums.size() == 1
        lowLevelClient.retrieve(HttpRequest.GET('/itunes/search-default'), SearchResult).blockingFirst().albums.size() == 1

        cleanup:
        lowLevelClient.close()
    }

    static class SearchParamsAsList {
        List<String> term // Yes, this is silly - but POJOs can't bind request params to simple fields
    }

    @Controller('/itunes')
    static class ItunesController {
        Map<String, List<String>> artists = [
                Riverside:["Out of Myself", "Second Life Syndrome"],
                Tool:["Undertow"],
                'Agnes Obel': ['Late Night Tales', 'Citizen of Glass', 'Philharmonics']]

        @Get("/search")
        SearchResult search(@QueryValue String term) {
            def albums = artists.get(term)
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/map{?params*}")
        SearchResult searchExploded1(@QueryValue Map<String, List<?>> params) {
            // Exploded parameters from query params come in as a map of lists (to accomodate "url?a=1&a=2")
            // We only use the first one
            def albums = artists.get(params.term?.getAt(0) ?: 'Unknown')
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/list{?term*}")
        SearchResult searchExploded2(@QueryValue @Nullable List term) {
            // Yes, we get a list of terms, but we only use the first one
            def albums = artists.get(term?.getAt(0) ?: 'Unknown')
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/pojo{?params*}")
        SearchResult searchExploded3(@QueryValue @Nullable SearchParamsAsList params) {
            // We get a POJO with a list of terms, but we only use the first one
            def albums = artists.get(params.term?.get(0) ?: 'Unknown')
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-default")
        SearchResult searchDefault(@QueryValue(defaultValue = "Tool") String term) {
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

        @Get("/search-exploded/{flavour}{?params*}")
        SearchResult searchExplodedMap(String flavour, Map params)

        @Get("/search-exploded/{flavour}{?term*}")
        SearchResult searchExplodedList(String flavour, @Nullable List term)

        @Get("/search-default")
        SearchResult searchDefault(@QueryValue(defaultValue = "Tool") String term)
    }

    static class SearchResult {
        List<String> albums
    }
}
