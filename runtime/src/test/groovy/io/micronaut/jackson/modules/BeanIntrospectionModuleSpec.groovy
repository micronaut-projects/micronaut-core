package io.micronaut.jackson.modules

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.hateoas.JsonError
import io.micronaut.jackson.JacksonConfiguration
import org.checkerframework.checker.nullness.qual.NonNull
import spock.lang.Issue
import spock.lang.Specification

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank

class BeanIntrospectionModuleSpec extends Specification {

    void "Bean introspection works with a bean without JsonInclude annotations"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        GuideWithoutJsonIncludeAnnotations guide = new GuideWithoutJsonIncludeAnnotations()
        guide.name = 'Bean Introspection Guide'
        String json = objectMapper.writeValueAsString(guide)

        then:
        noExceptionThrown()
        json == '{"name":"Bean Introspection Guide"}'

        cleanup:
        ctx.close()
    }

    void "Bean introspection works with a bean without JsonInclude annotations - serializationInclusion ALWAYS"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true,
                'jackson.serializationInclusion':'ALWAYS'
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        GuideWithoutJsonIncludeAnnotations guide = new GuideWithoutJsonIncludeAnnotations()
        guide.name = 'Bean Introspection Guide'
        String json = objectMapper.writeValueAsString(guide)

        then:
        noExceptionThrown()
        json == '{"name":"Bean Introspection Guide","author":null}'

        cleanup:
        ctx.close()
    }

    void "Bean introspection works with JsonInclude.Include.NON_NULL"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true,
                'jackson.serializationInclusion':'ALWAYS'
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        Guide guide = new Guide()
        guide.name = 'Bean Introspection Guide'
        String json = objectMapper.writeValueAsString(guide)

        then:
        noExceptionThrown()
        json == '{"name":"Bean Introspection Guide"}'

        cleanup:
        ctx.close()
    }

    void "Bean introspection works with JsonInclude.Include.ALWAYS"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true,
                'jackson.serializationInclusion':'ALWAYS'
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        GuideWithNull guide = new GuideWithNull()
        guide.name = 'Bean Introspection Guide'
        String json = objectMapper.writeValueAsString(guide)

        then:
        noExceptionThrown()
        json == '{"name":"Bean Introspection Guide","author":null}'

        cleanup:
        ctx.close()
    }

    void "test that introspected serialization works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:"json unwrapped is used"
        def plant = new Plant(name: "Rose", attributes: new Attributes(hasFlowers: true, color: "green"))
        def str = objectMapper.writeValueAsString(plant)

        then:"The result is correct"
        str == '{"name":"Rose","color":"green","hasFlowers":true}'

        when:"deserializing"
        def read = objectMapper.readValue(str, Plant)

        then:
        read == plant
        read.attributes.color == 'green'
        read.attributes.hasFlowers

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
        error.links.get("self").get().first().href == '/'

        when:
        def sw = new StringWriter()
        objectMapper.writeValue(sw, error)
        def result = sw.toString()

        then:
        result == '{"message":"Page Not Found","_links":{"self":{"href":"/","templated":false}}}'

        cleanup:
        ctx.close()
    }

    void "test that introspected serialization works for JsonCreator.Mode.DELEGATING"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                (JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION):true
        )
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        Edition e = objectMapper.readValue('{"book_title":"The Stand"}', Edition)

        then:
        ctx.getBean(JacksonConfiguration).beanIntrospectionModule
        ctx.containsBean(BeanIntrospectionModule)
        e.title == 'The Stand'

        when:
        def sw = new StringWriter()
        objectMapper.writeValue(sw, e)
        def result = sw.toString()

        then:
        result.contains('"title":"The Stand"')

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
    static class GuideWithoutJsonIncludeAnnotations {
        String name
        String author
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Introspected
    static class Guide {
        String name
        String author
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Introspected
    static class GuideWithNull {
        String name
        String author
    }

    @Introspected
    static class Author {
        String name
    }

    @Introspected
    static class Edition {

        String title

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Edition fromBook(Book book) {
            return new Edition(title: book.title)
        }

    }

    @Introspected
    @EqualsAndHashCode
    static class Plant {
        String name
        @JsonUnwrapped
        Attributes attributes
    }

    @Introspected
    @EqualsAndHashCode
    static class Attributes {
        String color
        boolean hasFlowers

    }
        //Used for @JsonView
    static class PublicView {}
    static class AllView extends PublicView {}

}
