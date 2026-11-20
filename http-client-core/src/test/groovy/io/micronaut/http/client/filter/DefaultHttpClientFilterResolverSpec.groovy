package io.micronaut.http.client.filter

import io.micronaut.core.annotation.AnnotationMetadataResolver
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.filter.FilterPatternStyle
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.http.filter.HttpFilterResolver.FilterEntry
import spock.lang.Specification

class DefaultHttpClientFilterResolverSpec extends Specification {

    DefaultHttpClientFilterResolver resolver = new DefaultHttpClientFilterResolver(null, AnnotationMetadataResolver.DEFAULT, [])

    HttpClientFilter filter = { request, chain -> chain.proceed(request) } as HttpClientFilter

    void "a filter entry that the resolver did not create is matched by its #style patterns against #path"() {
        given: 'an entry from another resolver, which has no pattern matcher of its own'
        FilterEntry entry = FilterEntry.of(filter, null, null, style, pattern)

        expect:
        resolver.resolveFilters(HttpRequest.GET(path), [entry]).size() == (matches ? 1 : 0)

        where:
        style                    | pattern           | path         | matches
        FilterPatternStyle.ANT   | '/books/**'       | '/books/1'   | true
        FilterPatternStyle.ANT   | '/books/**'       | 'books/1'    | true
        FilterPatternStyle.ANT   | '/books/**'       | '/authors/1' | false
        FilterPatternStyle.ANT   | '/**'             | '/authors/1' | true
        FilterPatternStyle.REGEX | '^/authors/\\d+$' | '/authors/42' | true
        FilterPatternStyle.REGEX | '^/authors/\\d+$' | '/authors/x' | false
    }

    void "a filter entry that the resolver did not create is matched by its methods before its patterns"() {
        given:
        FilterEntry entry = FilterEntry.of(filter, null, [HttpMethod.POST] as Set, FilterPatternStyle.ANT, '/books/**')

        expect:
        resolver.resolveFilters(HttpRequest.GET('/books/1'), [entry]).empty
        resolver.resolveFilters(HttpRequest.POST('/books/1', ''), [entry]).size() == 1
    }
}
