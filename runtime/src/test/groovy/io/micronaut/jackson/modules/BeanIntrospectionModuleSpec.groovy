package io.micronaut.jackson.modules

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.hateoas.JsonError
import io.micronaut.jackson.JacksonConfiguration
import spock.lang.Specification

class BeanIntrospectionModuleSpec extends Specification {

    void "test that introspected serialization works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        Book b = objectMapper.readValue('{"opt":null,"book_title":"The Stand", "book_pages":1000,"author":{"name":"Fred"}}', Book)

        then:
        ctx.getBean(JacksonConfiguration).beanIntrospectionModule
        ctx.containsBean(BeanIntrospectionModule)
        b.title == 'The Stand'
        b.pages == 1000
        b.author.name == "Fred"
        !b.opt.isPresent()

        when:
        def sw = new StringWriter()
        objectMapper.writeValue(sw, b)
        def result = sw.toString()

        then:
        !result.contains('"opt":{"present":false}')
        result.contains('"book_title":"The Stand"') // '{"book_title":"The Stand","book_pages":1000,author":{"name":"Fred"}}'
        result.contains('"book_pages":1000')
        result.contains('"author":{"name":"Fred"}')

        when:
        result = objectMapper.writerWithView(PublicView).writeValueAsString(b)
        then:
        result.contains('"book_title":')
        !result.contains('"book_pages":')

        when:
        result = objectMapper.writerWithView(AllView).writeValueAsString(b)
        then:
        result.contains('"book_title":')
        result.contains('"book_pages":')

        cleanup:
        ctx.close()
    }

    void "test that introspected serialization of errors works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        JsonError error = objectMapper.readValue('{"message":"Page Not Found","_links":{"self":{"href":"/","templated":false}}}', JsonError)

        then:
        ctx.getBean(JacksonConfiguration).beanIntrospectionModule
        ctx.containsBean(BeanIntrospectionModule)
        error.message == 'Page Not Found'
        error.links.size() == 1
        error.links.get("self").isPresent()
        error.links.get("self").get().first().href == URI.create('/')

        when:
        def sw = new StringWriter()
        objectMapper.writeValue(sw, error)
        def result = sw.toString()

        then:
        result == '{"message":"Page Not Found","_links":{"self":{"href":"/","templated":false}}}'

        cleanup:
        ctx.close()
    }

    @Introspected
    static class Book {
        @JsonProperty("book_title")
        @JsonView(PublicView)
        String title

        @JsonProperty("book_pages")
        @JsonView(AllView)
        int pages

        Author author

        Optional<String> opt

        @JsonCreator Book(@JsonProperty("book_title") String title) {
            this.title = title
        }
    }

    @Introspected
    static class Author {
        String name
    }

    //Used for @JsonView
    static class PublicView {}
    static class AllView extends PublicView {}

}
