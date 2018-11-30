package io.micronaut.http.pagination

import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.PageableArgumentBinder
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.http.HttpMethod.GET

class PaginationSpec extends Specification {

    @Shared
    def context = new ArgumentConversionContext<Pageable>() {
        @Override
        Argument<Pageable> getArgument() {
            return Argument.of(Pageable)
        }
    };

    @Shared
    def sizeParamName = "size"
    @Shared
    def pageParamName = "page"
    @Shared
    def defaultSize = 10
    @Shared
    def maxSize = 100
    @Shared
    def minSize = 10


    def '"PageableArgumentBinder.bind" returns a pageable'() {
        setup:

        def binder = new PageableArgumentBinder(
                getPaginationConfig(sizeParamName, pageParamName, defaultSize, maxSize, minSize)
        )
        when:
        def request = HttpRequest.create(GET, "http://localhost")
        request.parameters
                .add(sizeName, size)
                .add(pageName, page)
        def bindedPageable = binder.bind(context, request)
        then:
        bindedPageable.isPresentAndSatisfied()
        def pageable = bindedPageable.get()
        pageable.size == expectedSize
        pageable.pageNumber == expectedPage
        where:
        sizeName | pageName | size  | page || expectedPage | expectedSize
        "size"   | "page"   | "50"  | "1"  || 1            | 50
        "size"   | "page"   | "120" | "1"   | 1            | maxSize
        "size"   | "page"   | "5"   | "1"   | 1            | minSize
        "s"      | "page"   | "5"   | "1"   | 1            | defaultSize
        "s"      | "p"      | "5"   | "1"   | 0            | defaultSize


    }

    def getPaginationConfig(String sizeName, String pageName,
                            int defaultSize, int max, int min) {
        def pageConf = new PaginationConfiguration.PaginationPageConfiguration().with {
            pageName = pageName
            it
        }
        def sizeConf = new PaginationConfiguration.PaginationSizeConfiguration().with {
            name = sizeName
            defaultSize = defaultSize
            max = max
            min = min
            it
        }
        return new PaginationConfiguration().with {
            size = sizeConf
            page = pageConf
            it
        }
    }

}