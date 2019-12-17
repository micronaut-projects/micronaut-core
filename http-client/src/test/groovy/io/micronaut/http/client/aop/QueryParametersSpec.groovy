/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
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
        flavour << [ "pojo", "singlePojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping multiple URL parameters appended through a Map (served through #flavour)"() {
        expect:
        client.searchExplodedMap(flavour, [term: ["Tool", 'Agnes Obel']]).albums.size() == 4
        where:
        flavour << [ "pojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping URL parameters appended through a List (served through #flavour)"() {
        expect:
        client.searchExplodedList(flavour, ["Tool", 'Riverside']).albums.size() == 3
        where:
        flavour << [ "pojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping URL parameters through singleton list (served through #flavour)"() {
        expect:
        client.searchExplodedList(flavour, ["Tool"]).albums.size() == 1
        where:
        flavour << [ "pojo", "singlePojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping URL parameters appended through a POJO (served through #flavour)"() {
        expect:
        client.searchExplodedSinglePojo(flavour, new SearchParams(term: "Agnes Obel")).albums.size() == 3
        where:
        flavour << [ "pojo", "singlePojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping URL parameters appended through a POJO with a list (served through #flavour)"() {
        expect:
        client.searchExplodedPojo(flavour, new SearchParamsAsList(term: ["Tool", "Agnes Obel"])).albums.size() == 4

        where:
        flavour << [ "pojo", "list", "map" ]
    }

    @Unroll
    void "test client mappping URL parameters appended through an introspected POJO with a list"() {
        expect:
        client.searchExplodedIntrospectedPojo(flavour, new IntrospectedSearchParamsAsList(term: ["Tool", "Agnes Obel"])).albums.size() == 4

        where:
        flavour << [ "pojo", "list", "map" ]
    }

    void "test client mapping URL parameters appended through multiple POJOs"() {
        expect: ""
            client.searchExplodedMultiplePojos(new SearchParams(term: "Agnes Obel"), new SearchParams2(song: 'Citizen of Glass')).albums.size() == 1
    }

    void "test client mapping URL parameters appended through multiple optional POJOs"() {
        expect: ""
            client.searchExplodedMultipleOptionalPojos(null, null).albums.size() == 6
        and:
            client.searchExplodedMultipleOptionalPojos(new SearchParams(term: "Agnes Obel"), null).albums.size() == 3
        and:
            client.searchExplodedMultipleOptionalPojos(new SearchParams(term: "Agnes Obel"), new SearchParams2(song: 'Citizen of Glass')).albums.size() == 1
        and:
            client.searchExplodedMultipleOptionalPojos(null, new SearchParams2(song: 'Citizen of Glass')).albums.size() == 1
    }

    void "test query value with default value"() {
        given:
        RxHttpClient lowLevelClient = embeddedServer.getApplicationContext().createBean(RxHttpClient.class, embeddedServer.getURL())

        expect:
        client.searchDefault("Riverside").albums.size() == 2
        client.searchDefault(null).albums.size() == 1
        lowLevelClient.retrieve(HttpRequest.GET('/itunes/search-default'), SearchResult).blockingFirst().albums.size() == 1

        when:
        lowLevelClient.retrieve(HttpRequest.GET('/itunes/search-exploded/list'), SearchResult).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND // because null is returned

        when:
        lowLevelClient.retrieve(HttpRequest.GET('/itunes/search-exploded/map'), SearchResult).blockingFirst()

        then:
        ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND // because null is returned

        when:
        lowLevelClient.retrieve(HttpRequest.GET('/itunes/search-exploded/pojo'), SearchResult).blockingFirst()

        then:
        ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND // because null is returned

        cleanup:
        lowLevelClient.close()
    }

    static class SearchParamsAsList {
        List<String> term // POJO parameters can bind request params to list fields
    }

    @Introspected
    static class IntrospectedSearchParamsAsList {
        List<String> term // POJO parameters can bind request params to list fields
    }

    static class SearchParams {
        String term // Now, POJO parameters can bind request params to simple fields, too.
    }

    static class SearchParams2 {
        String song // Now, POJO parameters can bind request params to multiple POJOs, too.
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
            def albums = (params.term ?: []).collectMany { artists.get(it) }
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/list{?term*}")
        SearchResult searchExploded2(@QueryValue List term) {
            // Yes, we get a list of terms, but we only use the first one
            def albums = (term ?: []).collectMany { artists.get(it) }
            if(albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/pojo{?params*}")
        SearchResult searchExploded3(@QueryValue SearchParamsAsList params) {
            // We get a POJO with a list of terms, but we only use the first one
            def albums = (params.term ?: []).collectMany { artists.getOrDefault(it, []) }
            if (albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/singlePojo{?params*}")
        SearchResult searchExploded4(@QueryValue SearchParams params) {
            // We get a POJO with a list of terms, but we only use the first one
            def albums = params.term?.with { artists.get(it) }
            if (albums) {
                return new SearchResult(albums: albums)
            }
        }

        @Get("/search-exploded/multipleExplodedPojos{?term*}{&song*}")
        SearchResult searchMultipleExplodedPojos(@QueryValue SearchParams term, @QueryValue SearchParams2 song) {
            // We get a POJO with terms and song
            def albums = term.term?.with { artists.get(it) }
            return new SearchResult(albums: albums.findAll { it.matches(song.song)})
        }

        @Get("/search-exploded/multipleOptionalExplodedPojos{?term*}{?song*}")
        SearchResult searchMultipleOptionalExplodedPojos(@QueryValue SearchParams term, @QueryValue SearchParams2 song) {
            // We get a POJO with terms and/or song
            def albums = term.term?.with { artists.get(it) } ?: artists.values().flatten()
            if (song?.song) {
                albums = albums.findAll { it.matches(song.song)}
            }
            return new SearchResult(albums: albums)
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
        SearchResult searchExplodedMap(String flavour, Map<String, ?> params)

        @Get("/search-exploded/{flavour}{?term*}")
        SearchResult searchExplodedList(String flavour, List<String> term)

        @Get("/search-exploded/{flavour}{?params*}")
        SearchResult searchExplodedSinglePojo(String flavour, SearchParams params)

        @Get("/search-exploded/multipleExplodedPojos{?term*}{&song*}")
        SearchResult searchExplodedMultiplePojos(SearchParams term, SearchParams2 song)

        @Get("/search-exploded/multipleOptionalExplodedPojos{?term*}{?song*}")
        SearchResult searchExplodedMultipleOptionalPojos(@Nullable SearchParams term, @Nullable SearchParams2 song)

        @Get("/search-exploded/{flavour}{?params*}")
        SearchResult searchExplodedPojo(String flavour, SearchParamsAsList params)

        @Get("/search-exploded/{flavour}{?params*}")
        SearchResult searchExplodedIntrospectedPojo(String flavour, IntrospectedSearchParamsAsList params)

        @Get("/search-default")
        SearchResult searchDefault(@QueryValue(defaultValue = "Tool") String term)
    }

    static class SearchResult {
        List<String> albums
    }
}
