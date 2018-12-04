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
    def offsetParamName = "offset"
    @Shared
    def defaultSize = 10
    @Shared
    def maxSize = 100
    @Shared
    def minSize = 10


    def '"PageableArgumentBinder.bind" returns a pageable'() {
        setup:

        def binder = new PageableArgumentBinder(
                getPaginationConfig(sizeParamName, offsetParamName, defaultSize, maxSize, minSize)
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
        pageable.offset == expectedOffset
        where:
        sizeName | pageName | size  | page || expectedOffset | expectedSize
        "size"   | "offset" | "50"  | "10" || 10             | 50
        "size"   | "offset" | "120" | "0"   | 0              | maxSize
        "size"   | "offset" | "5"   | "10"  | 10             | minSize
        "s"      | "offset" | "5"   | "11"  | 11             | defaultSize
        "s"      | "o"      | "5"   | "12"  | 0              | defaultSize

    }

    def 'PageImpl validates all input params'() {
        when:
        new PageImpl(offset, size)
        then:
        thrown IllegalArgumentException
        where:
        offset | size
        0      | 0
        -1     | 0
        -1     | 1
    }

    def getPaginationConfig(String sizeName, String offsetName,
                            int defaultSize, int max, int min) {
        def offsetConf = new PaginationConfiguration.PaginationOffsetConfiguration().with {
            offsetName = offsetName
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
            offset = offsetConf
            it
        }
    }

}
