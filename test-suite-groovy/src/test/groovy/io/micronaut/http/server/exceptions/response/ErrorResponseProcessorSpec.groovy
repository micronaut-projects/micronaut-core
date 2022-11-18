package io.micronaut.http.server.exceptions.response

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import spock.lang.Specification
import jakarta.inject.Singleton

class ErrorResponseProcessorSpec extends Specification {

    def "default can simply be replaced by binding a different processor"() {
        given:
        def ctx = ApplicationContext.run(
                'spec.name': 'CustomErrorResponseProcessor'
        )

        when:
        def bean = ctx.getBean(ErrorResponseProcessor)

        then:
        bean instanceof CustomErrorResponseProcessor

        cleanup:
        ctx.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'CustomErrorResponseProcessor')
    static class CustomErrorResponseProcessor implements ErrorResponseProcessor<String> {

        @NonNull
        @Override
        MutableHttpResponse<String> processResponse(@NonNull ErrorContext errorContext, @NonNull MutableHttpResponse baseResponse) {
            return HttpResponse.ok("test")
        }
    }
}
