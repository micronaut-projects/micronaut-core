package io.micronaut.jackson.modules

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.jackson.JacksonConfiguration
import spock.lang.Specification

class BeanIntrospectionModuleSpec extends Specification {

    void "test that introspected serialization works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run((JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true)
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        Book b = objectMapper.readValue('{"book_title":"The Stand", "book_pages":1000,"author":{"name":"Fred"}}', Book)

        then:
        ctx.getBean(JacksonConfiguration).beanIntrospectionModule
        ctx.containsBean(BeanIntrospectionModule)
        b.title == 'The Stand'
        b.pages == 1000
        b.author.name == "Fred"

        when:
        def sw = new StringWriter()
        objectMapper.writeValue(sw, b)
        def result = sw.toString()

        then:
        result.contains('"book_title":"The Stand"') // '{"book_title":"The Stand","book_pages":1000,author":{"name":"Fred"}}'
        result.contains('"book_pages":1000')
        result.contains('"author":{"name":"Fred"}')

        cleanup:
        ctx.close()
    }

    @Introspected
    static class Book {
        @JsonProperty("book_title")
        String title

        @JsonProperty("book_pages")
        int pages

        Author author

        @JsonCreator Book(@JsonProperty("book_title") String title) {
            this.title = title
        }
    }

    @Introspected
    static class Author {
        String name
    }
}
