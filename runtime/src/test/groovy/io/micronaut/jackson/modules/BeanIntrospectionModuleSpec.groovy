package io.micronaut.jackson.modules

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.hateoas.JsonError
import io.micronaut.jackson.JacksonConfiguration
import io.micronaut.jackson.modules.testcase.EmailTemplate
import io.micronaut.jackson.modules.testcase.Notification
import io.micronaut.jackson.modules.wrappers.BooleanWrapper
import io.micronaut.jackson.modules.wrappers.DoubleWrapper
import io.micronaut.jackson.modules.wrappers.IntWrapper
import io.micronaut.jackson.modules.wrappers.IntegerWrapper
import io.micronaut.jackson.modules.wrappers.LongWrapper
import io.micronaut.jackson.modules.wrappers.StringWrapper
import spock.lang.Ignore
import spock.lang.Specification

import java.beans.ConstructorProperties

class BeanIntrospectionModuleSpec extends Specification {

    void "Bean introspection works with a bean without JsonInclude annotations"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
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
                'jackson.serializationInclusion': 'ALWAYS'
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
                'jackson.serializationInclusion': 'ALWAYS'
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
                'jackson.serializationInclusion': 'ALWAYS'
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
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when: "json unwrapped is used"
        def plant = new Plant(name: "Rose", attributes: new Attributes(hasFlowers: true, color: "green"))
        def str = objectMapper.writeValueAsString(plant)

        then: "The result is correct"
        str == '{"name":"Rose","color":"green","hasFlowers":true}'

        when: "deserializing"
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
        ApplicationContext ctx = ApplicationContext.run()
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
        ApplicationContext ctx = ApplicationContext.run()
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

    def "should deserialize field with hierarchy"() {
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        def notif = """
{"id":586387198220282880, "template":{"templateType":"email","textTemplate":"Ahoj"}}
"""
        def value = objectMapper.readValue(notif, Notification.class)

        then:
        value.getTemplate() instanceof EmailTemplate

        cleanup:
        ctx.close()
    }

    void "test deserializing from basic types"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        def wrapper = objectMapper.readValue("\"string\"", StringWrapper)

        then:
        noExceptionThrown()
        wrapper.value == "string"

        when:
        wrapper = objectMapper.readValue("32", IntWrapper)

        then:
        noExceptionThrown()
        wrapper.value == 32I

        when:
        wrapper = objectMapper.readValue("32", IntegerWrapper)

        then:
        noExceptionThrown()
        wrapper.value == 32I

        when:
        wrapper = objectMapper.readValue("32", LongWrapper)

        then:
        noExceptionThrown()
        wrapper.value == 32L

        when:
        wrapper = objectMapper.readValue("false", BooleanWrapper)

        then:
        noExceptionThrown()
        !wrapper.value

        when:
        wrapper = objectMapper.readValue("23.23", DoubleWrapper)

        then:
        noExceptionThrown()
        wrapper.value == 23.23D

        cleanup:
        ctx.close()
    }

    void "test deserializing with a json naming strategy"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        NamingStrategy instance = objectMapper.readValue("{ \"FooBar\": \"bad\" }", NamingStrategy)

        then:
        instance.fooBar == "bad"

        cleanup:
        ctx.close()
    }

    void "test deserializing from a list"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        ListWrapper instance = objectMapper.readValue("[\"bad\"]", ListWrapper)

        then:
        instance.value == ["bad"]

        cleanup:
        ctx.close()
    }

    void "test serialize with AnyGetter/Setter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        def json = objectMapper.writeValueAsString(new Wrapper(new Body(123, ["foo": "bar"])))

        then:
        json == '{"body":{"foo_id":123,"foo":"bar"}}'

        cleanup:
        ctx.close()
    }

    @Ignore
    void "test deserialize with AnyGetter/Setter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        Wrapper wrapper = objectMapper.readValue('{"body":{"foo_id":123,"foo":"bar"}}', Wrapper)

        then:
        wrapper.body.id == 123
        wrapper.body.properties == ["foo": "bar"]

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

        @JsonCreator
        Book(@JsonProperty("book_title") String title) {
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

    @Introspected
    @JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
    static class NamingStrategy {

        @PackageScope
        final String fooBar

        @ConstructorProperties(["fooBar"])
        NamingStrategy(String fooBar) {
            this.fooBar = fooBar
        }
    }

    @Introspected
    static class ListWrapper {
        final List<String> value;

        @JsonCreator
        ListWrapper(List<String> value) {
            this.value = value;
        }
    }

    @Introspected
    static class Body {

        @JsonProperty(value = "foo_id")
        final long id

        @JsonAnySetter
        final Map<String, Object> properties

        Body(long id, Map<String, Object> properties) {
            this.id = id
            this.properties = properties
        }

        @JsonAnyGetter
        Map<String, Object> getProperties() {
            properties
        }
    }

    @Introspected
    static class Wrapper {

        final Body body

        Wrapper(@JsonProperty("body") Body body) {
            this.body = body
        }
    }
}
