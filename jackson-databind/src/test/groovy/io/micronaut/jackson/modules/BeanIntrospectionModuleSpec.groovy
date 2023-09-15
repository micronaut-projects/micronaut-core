package io.micronaut.jackson.modules

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.hateoas.JsonError
import io.micronaut.jackson.JacksonConfiguration
import io.micronaut.jackson.modules.testcase.EmailTemplate
import io.micronaut.jackson.modules.testcase.Notification
import io.micronaut.jackson.modules.testclasses.HTTPCheck
import io.micronaut.jackson.modules.testclasses.InstanceInfo
import io.micronaut.jackson.modules.wrappers.BooleanWrapper
import io.micronaut.jackson.modules.wrappers.DoubleWrapper
import io.micronaut.jackson.modules.wrappers.IntWrapper
import io.micronaut.jackson.modules.wrappers.IntegerWrapper
import io.micronaut.jackson.modules.wrappers.LongWrapper
import io.micronaut.jackson.modules.wrappers.StringWrapper
import io.micronaut.json.JsonMapper
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.beans.ConstructorProperties
import java.time.LocalDateTime

class BeanIntrospectionModuleSpec extends Specification {

    void "test serialize/deserialize wrap/unwrap - simple"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                'jackson.serialization.WRAP_ROOT_VALUE': true
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        Author author = new Author(name:"Bob")

        String result = objectMapper.writeValueAsString(author)

        then:
        result == '{"Author":{"name":"Bob"}}'

        when:
        def read = objectMapper.readValue(result, Author)

        then:
        author == read

    }

    void "test serialize/deserialize wrap/unwrap -* complex"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                'jackson.serialization.WRAP_ROOT_VALUE': true
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        HTTPCheck check = new HTTPCheck(headers:[
                Accept:['application/json', 'application/xml']
        ] )

        String result = objectMapper.writeValueAsString(check)

        then:
        result == '{"HTTPCheck":{"Header":{"Accept":["application/json","application/xml"]}}}'

        when:
        def read = objectMapper.readValue(result, HTTPCheck)

        then:
        check == read

    }

    void "test serialize/deserialize wrap/unwrap -* constructors"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                'jackson.serialization.WRAP_ROOT_VALUE': true
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        IntrospectionCreator check = new IntrospectionCreator("test")

        String result = objectMapper.writeValueAsString(check)

        then:
        result == '{"IntrospectionCreator":{"label":"TEST"}}'

        when:
        def read = objectMapper.readValue(result, IntrospectionCreator)

        then:
        check == read

    }

    void "test serialize/deserialize wrap/unwrap -* constructors & JsonRootName"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                'jackson.serialization.WRAP_ROOT_VALUE': true
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        InstanceInfo check = new InstanceInfo("test")

        String result = objectMapper.writeValueAsString(check)

        then:
        result == '{"instance":{"hostName":"test"}}'

        when:
        def read = objectMapper.readValue(result, InstanceInfo)

        then:
        check == read

    }


    void "test serialize/deserialize convertible values"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        HTTPCheck check = new HTTPCheck(headers:[
                Accept:['application/json', 'application/xml']
        ] )

        String result = objectMapper.writeValueAsString(check)

        then:
        result == '{"Header":{"Accept":["application/json","application/xml"]}}'

        when:
        def read = objectMapper.readValue(result, HTTPCheck)

        then:
        check.header.getAll("Accept") == read.header.getAll("Accept")

    }

    void "Bean introspection works with a bean without JsonIgnore annotations"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        IgnoreTest guide = new IgnoreTest(name:"Test", code: 9999)
        String json = objectMapper.writeValueAsString(guide)

        then:
        noExceptionThrown()
        json == '{"name":"Test"}'

        cleanup:
        ctx.close()
    }

    void "Bean introspection works with a bean without JsonInclude annotations"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        GuideWithoutJsonIncludeAnnotations guide = new GuideWithoutJsonIncludeAnnotations()
        guide.name = 'Bean Introspection Guide'
        String json = objectMapper.writeValueAsString(guide)

        then:
        noExceptionThrown()
        json == '{"name":"Bean Introspection Guide"}'

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
    }

    void "Bean introspection works with a bean without JsonInclude annotations - serializationInclusion ALWAYS"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'jackson.serializationInclusion':'ALWAYS'
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

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
                'jackson.serializationInclusion':'ALWAYS'
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

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
                'jackson.serializationInclusion':'ALWAYS'
        )
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

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

    void "Bean introspection with empty optional"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        def value = new OptionalAuthor(name: Optional.<String>empty())
        String json = objectMapper.writeValueAsString(value)

        then:
        noExceptionThrown()
        json == '{}'

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
    }

    void "test that introspected serialization works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
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

    void "test that introspected serialization works with @JsonAnyGetter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        when:
        def plant = new PlantWithAnyGetter(name: "Rose", attributes: [color: "green", hasFlowers: true])
        String str = objectMapper.writeValueAsString(plant)

        then:
        str == '{"name":"Rose","color":"green","hasFlowers":true}'

        when:"deserializing"
        def read = objectMapper.readValue(str, PlantWithAnyGetter)

        then:
        read == plant
        read.attributes.color == 'green'
        read.attributes.hasFlowers

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
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

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5160")
    void "test that snake_case with non-introspected beans works"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'jackson.property-naming-strategy': 'SNAKE_CASE',
                'jackson.json-view.enabled': true,
                'jackson.bean-introspection-module': true
        ])
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        Publisher p = objectMapper.readValue('{"publisher_name":"RandomHouse"}', Publisher)

        then:
        ctx.getBean(JacksonConfiguration).beanIntrospectionModule
        ctx.containsBean(BeanIntrospectionModule)
        p.publisherName == 'RandomHouse'

        when:
        def result = objectMapper.writerWithView(PublicView).writeValueAsString(p)

        then:
        !result.contains('"publisher_name":')


        when:
        result = objectMapper.writerWithView(AllView).writeValueAsString(p)
        then:
        result.contains('"publisher_name":')

        cleanup:
        ctx.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5088")
    void "test deserializing from a list of pojos"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        MyReqBody reqBody = objectMapper.readValue('[{"name":"Joe"},{"name":"Sally"}]', MyReqBody)

        then:
        reqBody.getItems().size() == 2
        reqBody.getItems()[0].name == "Joe"
        reqBody.getItems()[1].name == "Sally"

        when:
        MyItemBody itemBody = objectMapper.readValue('[{"name":"Joe"},{"name":"Sally"}]', MyItemBody)

        then:
        itemBody.getItems().size() == 2
        itemBody.getItems()[0].name == "Joe"
        itemBody.getItems()[1].name == "Sally"

        cleanup:
        ctx.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5078")
    void "test more list wrapping scenarios"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ObjectMapper objectMapper = ctx.getBean(ObjectMapper)

        when:
        OuterList outerList = objectMapper.readValue("{\"wrapper\":{\"inner\":[]}}", OuterList.class)

        then:
        noExceptionThrown()
        outerList.wrapper.inner.isEmpty()

        when:
        OuterArray outerArray = objectMapper.readValue("{\"wrapper\":{\"inner\":[]}}", OuterArray.class)

        then:
        noExceptionThrown()
        outerArray.wrapper.inner.length == 0
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6472")
    void "@JsonProperty annotation on setter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.readValue('{"bar":"baz"}', JsonPropertyOnSetter.class).foo == 'baz'
        objectMapper.writeValueAsString(new JsonPropertyOnSetter(foo: 'baz')) == '{"bar":"baz"}'

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6309")
    void "@JsonSerialize annotation"() {
      given:
      ApplicationContext ctx = ApplicationContext.run()
      ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
      JsonMapper objectMapper = ctx.getBean(JsonMapper)

      expect:
      objectMapper.readValue('{"foo":"Bar"}', JsonSerializeAnnotated.class).foo == 'bar'
      objectMapper.writeValueAsString(new JsonSerializeAnnotated(foo: 'Bar')) == '{"foo":"BAR"}'

      cleanup:
      ctx.close()

      where:
      ignoreReflectiveProperties << [true, false]

    }

    void "creator property that doesn't have a getter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new DifferentCreator('baz')) == '{"fooBar":"baz"}'
        objectMapper.readValue('{"foo_bar":"baz"}', DifferentCreator).fooBar == 'baz'
        // this is currently broken with ignoreReflectiveProperties
        ignoreReflectiveProperties || objectMapper.readValue('{"fooBar":"baz"}', DifferentCreator).fooBar == 'baz'

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
    }

    void "introspection creator property that doesn't have a getter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new IntrospectionCreator('baz')) == '{"label":"BAZ"}'
        objectMapper.readValue('{"name":"baz","label":"foo"}', IntrospectionCreator).name == 'baz'

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
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
    static class Publisher {

        @JsonView(AllView)
        String publisherName

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
    @EqualsAndHashCode
    static class Author {
        String name
    }

    @Introspected
    static class OptionalAuthor {
        Optional<String> name
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

    @Introspected
    @EqualsAndHashCode
    static class PlantWithAnyGetter {
        String name

        private Map<String, Object> attributes = [:]

        @JsonAnyGetter
        Map<String, Object> getAttributes() {
            return attributes
        }

        @JsonAnySetter
        void addAttribute(String key, Object value) {
            attributes[key] = value
        }

    }
        //Used for @JsonView
    static class PublicView {}
    static class AllView extends PublicView {}

    @Introspected
    @JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy.class)
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
        final List<String> value

        @JsonCreator
        ListWrapper(List<String> value) {
            this.value = value
        }
    }

    @Introspected
    static class IgnoreTest {
        String name
        @JsonIgnore
        int code
    }

    @Introspected
    static class MyReqBody {

        private final List<MyItem> items

        @JsonCreator
        MyReqBody(final List<MyItem> items) {
            this.items = items
        }

        List<MyItem> getItems() {
            items
        }
    }

    @Introspected
    static class MyItem {
        String name
    }

    @Introspected
    static class MyGenericBody<T> {

        private final List<T> items

        MyGenericBody(final List<T> items) {
            this.items = items
        }

        List<T> getItems() {
            items
        }
    }

    @Introspected
    static class MyItemBody extends MyGenericBody<MyItem> {

        @JsonCreator
        MyItemBody(final List<MyItem> items) {
            super(items)
        }
    }

    @Introspected
    static class WrapperList {
        public final List<String> inner

        @ConstructorProperties(["inner"])
        @JsonCreator
        WrapperList(List<String> inner) {
            this.inner = inner
        }
    }

    @Introspected
    static class OuterList {
        public final WrapperList wrapper

        @ConstructorProperties(["wrapper"])
        @JsonCreator
        OuterList(WrapperList wrapper) {
            this.wrapper = wrapper
        }
    }

    @Introspected
    static class WrapperArray {
        public final String[] inner

        @ConstructorProperties(["inner"])
        @JsonCreator
        WrapperArray(String[] inner) {
            this.inner = inner
        }
    }

    @Introspected
    static class OuterArray {
        public final WrapperArray wrapper

        @ConstructorProperties(["wrapper"])
        @JsonCreator
        OuterArray(WrapperArray wrapper) {
            this.wrapper = wrapper
        }
    }

    @Introspected
    static class JsonPropertyOnSetter {
        private String foo

        public String getFoo() {
            return foo
        }

        @JsonProperty("bar")
        public void setFoo(String foo) {
            this.foo = foo
        }
    }

    @Introspected
    static class JsonSerializeAnnotated {
        @JsonSerialize(using = UpperCaseSerializer)
        @JsonDeserialize(using = LowerCaseDeserializer)
        String foo

        @Introspected
        static class UpperCaseSerializer extends JsonSerializer<String> {
            @Override
            void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toUpperCase(Locale.ENGLISH))
            }
        }

        @Introspected
        static class LowerCaseDeserializer extends JsonDeserializer<String> {
            @Override
            String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
                return p.valueAsString.toLowerCase(Locale.ENGLISH)
            }
        }
    }

    @Introspected
    static class DifferentCreator {
        private final String fooBar;

        @JsonCreator
        public DifferentCreator(@JsonProperty('foo_bar') String fooBar) {
            this.fooBar = fooBar
        }

        public String getFooBar() {
            return fooBar;
        }
    }

    @Introspected
    @EqualsAndHashCode
    static class IntrospectionCreator {
        private final String name

        @Creator
        public IntrospectionCreator(String name) {
            this.name = name
        }

        public String getLabel() {
            name.toUpperCase()
        }
    }

    void "JsonFormat"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)
        LocalDateTime ldt = LocalDateTime.of(2021, 11, 22, 10, 37)

        expect:
        objectMapper.writeValueAsString(new FormatBean(ldt: ldt)) == '{"ldt":"2021-11-22 10:37:00"}'
        objectMapper.readValue('{"ldt":"2021-11-22 10:37:00"}', FormatBean).ldt == ldt

        cleanup:
        ctx.close()

        where:
        ignoreReflectiveProperties << [true, false]
    }

    @Introspected
    static class FormatBean {
        private LocalDateTime ldt

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        public LocalDateTime getLdt() {
            return ldt
        }

        public void setLdt(LocalDateTime ldt) {
            this.ldt = ldt
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6625')
    void "JsonIgnore on main accessor, with secondary override"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new IgnoreBean()) == '{"foo":"bar"}'
        objectMapper.readValue('{"foo":"bar"}', IgnoreBean).foo == 'foo'
        objectMapper.readValue('{"foo":"bar"}', IgnoreBean).secondary == 'bar'

        cleanup:
        ctx.close()

        where:
        // need reflective access here, because with only introspections, the secondary methods arent available.
        ignoreReflectiveProperties << [false]//[true, false]
    }

    @Introspected
    static class IgnoreBean {

        private String foo = 'foo'
        private String secondary = 'bar'

        @JsonProperty('foo')
        public String secondary() {
            return secondary
        }

        @JsonProperty('foo')
        public void secondary(String s) {
            this.secondary = s
        }

        @JsonIgnore
        public String getFoo() {
            return foo
        }

        @JsonIgnore
        public void setFoo(String s) {
            this.foo = s
        }
    }

    @Unroll("JsonIgnore is supported with ignoreReflectiveProperties: #ignoreReflectiveProperties")
    void "JsonIgnore on one accessor"(boolean ignoreReflectiveProperties) {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new IgnoreBean2(foo: 'x', bar: 'y')) == '{"bar":"y"}'
        objectMapper.readValue('{"foo":"x","bar":"y"}', IgnoreBean2).foo == 'x'
        objectMapper.readValue('{"foo":"x","bar":"y"}', IgnoreBean2).bar == null

        cleanup:
        ctx.close()

        where:
        // without reflection we only see JsonIgnore on the whole property
        ignoreReflectiveProperties << [false]
    }

    @Introspected
    static class IgnoreBean2 {

        @JsonIgnore
        private String foo
        @JsonIgnore
        private String bar

        @JsonIgnore
        public String getFoo() {
            return foo
        }

        @JsonProperty('foo')
        public void setFoo(String s) {
            this.foo = s
        }

        @JsonProperty('bar')
        public String getBar() {
            return bar
        }

        @JsonIgnore
        public void setBar(String s) {
            this.bar = s
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5907')
    void "xml modifier"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        XmlMapper objectMapper = new XmlMapper()
        objectMapper.registerModule(ctx.getBean(BeanIntrospectionModule))

        expect:
        objectMapper.writeValueAsString(new UsesXmlElementWrapper()) ==
                '<UsesXmlElementWrapper><nestedItems><strings>foo</strings><strings>bar</strings></nestedItems></UsesXmlElementWrapper>'

        cleanup:
        ctx.close()
    }

    @Introspected
    static class UsesXmlElementWrapper {
        private List<String> strings = ["foo", "bar"];

        @JacksonXmlElementWrapper(/*useWrapping = false, */localName = "nestedItems")
        public List<String> getStrings() {
            return strings
        }
    }

    @Unroll("JsonProperty annotation is supported with ignoreReflectiveProperties: #ignoreReflectiveProperties")
    void "JsonProperty support"(boolean ignoreReflectiveProperties) {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new JsonPropertyBean(foo: 'x')) == '{"bar":"x"}'
        objectMapper.readValue('{"bar":"x"}', JsonPropertyBean).foo == 'x'

        cleanup:
        ctx.close()

        where:
        // without reflection we only see JsonIgnore on the whole property
        ignoreReflectiveProperties << [true, false]
    }

    @Introspected
    static class JsonPropertyBean {

        private String foo

        @JsonProperty('bar')
        public String getFoo() {
            return foo
        }

        public void setFoo(String s) {
            this.foo = s
        }
    }

    void "JsonNaming support"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new JsonNamingBean(fooBar: 'x')) == '{"foo_bar":"x"}'
        objectMapper.readValue('{"foo_bar":"x"}', JsonNamingBean).fooBar == 'x'

        cleanup:
        ctx.close()

        where:
        // without reflection we only see JsonIgnore on the whole property
        ignoreReflectiveProperties << [true, false]
    }

    @Introspected
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
    static class JsonNamingBean {

        private String fooBar

        public String getFooBar() {
            return fooBar
        }

        public void setFooBar(String s) {
            this.fooBar = s
        }
    }

    void "JsonNaming support in config"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["jackson.property-naming-strategy": "SNAKE_CASE"])
        ctx.getBean(BeanIntrospectionModule).ignoreReflectiveProperties = ignoreReflectiveProperties
        JsonMapper objectMapper = ctx.getBean(JsonMapper)

        expect:
        objectMapper.writeValueAsString(new JsonNamingBeanConfig(fooBar: 'x')) == '{"foo_bar":"x"}'
        objectMapper.readValue('{"foo_bar":"x"}', JsonNamingBeanConfig).fooBar == 'x'

        cleanup:
        ctx.close()

        where:
        // without reflection we only see JsonIgnore on the whole property
        ignoreReflectiveProperties << [true, false]
    }

    @Introspected
    static class JsonNamingBeanConfig {

        private String fooBar

        public String getFooBar() {
            return fooBar
        }

        public void setFooBar(String s) {
            this.fooBar = s
        }
    }
}
