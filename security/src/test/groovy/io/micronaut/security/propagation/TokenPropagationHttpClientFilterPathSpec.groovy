package io.micronaut.security.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.AnnotationMetadataResolver
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.util.PathMatcher
import io.micronaut.core.util.StringUtils
import io.micronaut.http.annotation.Filter
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import spock.lang.Specification

class TokenPropagationHttpClientFilterPathSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'


    void "default TokenPropagationHttpClientFilter path is /**"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.writer.header.enabled': true,
                'micronaut.security.token.propagation.enabled': true,
                (SPEC_NAME_PROPERTY):getClass().simpleName
        ], Environment.TEST)

        when:
        TokenPropagationHttpClientFilter filter = context.getBean(TokenPropagationHttpClientFilter)

        then:
        noExceptionThrown()

        when:
        AnnotationMetadataResolver annotationMetadataResolver = context.getBean(AnnotationMetadataResolver)

        then:
        noExceptionThrown()

        when:


        String[] patterns
        Optional<AnnotationValue<Filter>> filterOpt = annotationMetadataResolver.resolveMetadata(filter).findAnnotation(Filter.class)
        if (filterOpt.isPresent()) {
            AnnotationValue<Filter> filterAnn = filterOpt.get()
            patterns = filterAnn.getValue(String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY)
        }

        then:
        patterns

        when:
        String requestPath = "/invoice"
        boolean matches = PathMatcher.ANT.matches(patterns.first(), requestPath)

        then:
        matches

        cleanup:
        context.stop()
        context.close()
    }

    void "you can customize TokenPropagationHttpClientFilter pattern with micronaut.security.token.propagation.path"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.writer.header.enabled': true,
                'micronaut.security.token.propagation.enabled': true,
                'micronaut.security.token.propagation.path': '/books/**',
                (SPEC_NAME_PROPERTY):getClass().simpleName
        ], Environment.TEST)

        when:
        TokenPropagationHttpClientFilter filter = context.getBean(TokenPropagationHttpClientFilter)

        then:
        noExceptionThrown()

        when:
        AnnotationMetadataResolver annotationMetadataResolver = context.getBean(AnnotationMetadataResolver)

        then:
        noExceptionThrown()

        when:


        String[] patterns
        Optional<AnnotationValue<Filter>> filterOpt = annotationMetadataResolver.resolveMetadata(filter).findAnnotation(Filter.class)
        if (filterOpt.isPresent()) {
            AnnotationValue<Filter> filterAnn = filterOpt.get()
            patterns = filterAnn.getValue(String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY)
        }

        then:
        patterns

        when:
        String requestPath = "/books/1"
        boolean matches = PathMatcher.ANT.matches(patterns.first(), requestPath)

        then:
        matches

        when:
        requestPath = "/invoice"
        matches = PathMatcher.ANT.matches(patterns.first(), requestPath)

        then:
        !matches

        cleanup:
        context.stop()
        context.close()
    }
}
