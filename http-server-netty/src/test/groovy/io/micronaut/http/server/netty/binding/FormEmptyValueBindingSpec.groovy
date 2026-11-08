/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binding

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.server.netty.AbstractMicronautSpec
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import spock.lang.Unroll
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class FormEmptyValueBindingSpec extends AbstractMicronautSpec {

    @Unroll
    void "test an empty value keeps binding to a target that can hold it: #form"() {
        expect:
        post("/empty-form/typed?field=$field", form) == expected

        where:
        form                  | field          | expected
        'text='               | 'text'         | '[]'
        'chars='              | 'chars'        | '[]'
        'object='             | 'object'       | '[[]]'
        'optionalText='       | 'optionalText' | 'Optional[]'
        'textList='           | 'textList'     | '[[]]'
        'textSet='            | 'textSet'      | '[[]]'
        'textList=&textList=' | 'textList'     | '[[, ]]'
        'uri='                | 'uri'          | '[]'
        'locale='             | 'locale'       | '[]'
        'money='              | 'money'        | '[Money()]'
        'customDate='         | 'customDate'   | '[1970-01-01]'
        'renamedText='        | 'someText'     | '[]'
        'kebab-text='         | 'kebabText'    | '[]'
        'count2='             | 'count2'       | '[Count2()]'
        'creatorColor='       | 'creatorColor' | '[NONE]'
        'annotatedColor='     | 'annotatedColor' | '[NONE]'
    }

    @Unroll
    void "test an empty value is absent for a target that cannot hold it: #form"() {
        expect:
        post("/empty-form/typed?field=$field", form) == expected

        where:
        form                 | field            | expected
        'count='             | 'count'          | 'null'
        'primitiveCount='    | 'primitiveCount' | '[0]'
        'primitiveFlag='     | 'primitiveFlag'  | '[false]'
        'amount='            | 'amount'         | 'null'
        'date='              | 'date'           | 'null'
        'dateTime='          | 'dateTime'       | 'null'
        'instant='           | 'instant'        | 'null'
        'duration='          | 'duration'       | 'null'
        'legacyDate='        | 'legacyDate'     | 'null'
        'color='             | 'color'          | 'null'
        'defaulted='         | 'defaulted'      | '[5]'
        'renamed='           | 'renamedColor'   | 'null'
        'kebab-color='       | 'kebabColor'     | 'null'
        'Color='             | 'color'          | 'null'
        'oldColor='          | 'aliasedColor'   | 'null'
        'introspectedColor=' | 'introspectedColor' | 'null'
        'usinglessColor='    | 'usinglessColor' | 'null'
    }

    @Unroll
    void "test what a field holds beside an elided one: #form"() {
        expect:
        post("/empty-form/typed?field=$field", form) == expected

        where:
        form                  | field   | expected
        'text=&count=&color=' | 'text'  | '[]'
        'text=&count=&color=' | 'count' | 'null'
        'text=x&count=7'      | 'text'  | '[x]'
        'text=x&count=7'      | 'count' | '[7]'
    }

    @Unroll
    void "test a target outside the elided ones keeps today's binding: #form"() {
        expect:
        post("/empty-form/typed?field=$field", form) == expected

        where:
        form                  | field            | expected
        'flag='               | 'flag'           | 'null'
        'optionalCount='      | 'optionalCount'  | 'Optional.empty'
        'countList='          | 'countList'      | '[[null]]'
        'count=&count='       | 'count'          | 'HTTP 400'
        'count=+'             | 'count'          | 'null'
        'address='            | 'address'        | 'HTTP 400'
        'address.city='       | 'address'        | '[Address(, null)]'
        'address.zip='        | 'address'        | '[Address(null, null)]'
        'address.city=&address.zip=7' | 'address' | '[Address(, 7)]'
    }

    void "test a renamed property is found by the name the form uses"() {
        expect: 'the form name is that of the String property, not the bean name of the Integer one'
        post('/empty-form/renamed', 'age=') == 'ageText=[], age=null'
        post('/empty-form/renamed', 'years=') == 'ageText=null, age=null'
    }

    void "test the creator parameter decides over a getter of another type"() {
        expect:
        post('/empty-form/creator', 'when=') == 'raw=[]'
    }

    void "test an optional body is looked through"() {
        expect:
        post('/empty-form/optional', 'text=&color=') == 'text=[], color=null'
    }

    @Unroll
    void "test a body that is not a bean keeps its empty values: #path"() {
        expect:
        post("/empty-form/$path", 'empty=&value=present') == expected

        where:
        path                 | expected
        'map'                | '[empty:, value:present]'
        'properties'         | '[empty:, value:present]'
        'convertible-values' | '[empty:, value:present]'
        'object'             | '[empty:, value:present]'
    }

    void "test an empty value is elided when the content type carries parameters"() {
        when:
        String result = client.exchange(HttpRequest.POST('/empty-form/optional', 'text=&color=')
                .contentType(MediaType.of('application/x-www-form-urlencoded;charset=UTF-8')), String).body()

        then:
        result == 'text=[], color=null'
    }

    void "test a multipart text part keeps its empty value for every target"() {
        given:
        MultipartBody body = MultipartBody.builder().addPart('text', '').addPart('count', '7').build()

        when:
        String result = client.exchange(HttpRequest.POST('/empty-form-multipart/typed', body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String).body()

        then:
        result == 'text=[], count=[7]'

        when:
        client.exchange(HttpRequest.POST('/empty-form-multipart/typed', MultipartBody.builder().addPart('color', '').build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    void "test a bean that is not introspected keeps its empty values"() {
        expect:
        post('/empty-form/plain', 'text=') == '[]'
    }

    @Unroll
    void "test validation sees the empty string of a String property: #path"() {
        expect:
        post("/empty-form/$path", form) == expected

        where:
        path        | form      | expected
        'not-null'  | 'value='  | '[]'
        'size'      | 'value='  | 'HTTP 400'
        'not-blank' | 'value='  | 'HTTP 400'
        'not-null-date' | 'value=' | 'HTTP 400'
        'not-null'  | 'other=x' | 'HTTP 400'
        'size'      | 'other=x' | 'null'
    }

    private String post(String uri, String form) {
        try {
            return client.exchange(HttpRequest.POST(uri, form).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).body()
        } catch (HttpClientResponseException e) {
            return "HTTP ${e.status.code}"
        }
    }

    private static String show(Object value) {
        if (value == null) {
            return 'null'
        }
        if (value instanceof Optional) {
            return value.isPresent() ? "Optional[${value.get()}]" : 'Optional.empty'
        }
        return "[$value]"
    }

    @Controller(value = '/empty-form', consumes = MediaType.APPLICATION_FORM_URLENCODED)
    @Requires(property = 'spec.name', value = 'FormEmptyValueBindingSpec')
    static class EmptyFormController {

        @Post('/typed')
        String typed(@Body TypedForm form, @QueryValue String field) {
            show(form."$field")
        }

        @Post('/renamed')
        String renamed(@Body RenamedForm form) {
            "ageText=${show(form.ageText)}, age=${show(form.age)}"
        }

        @Post('/creator')
        String creator(@Body CreatorForm form) {
            "raw=${show(form.raw)}"
        }

        @Post('/optional')
        String optional(@Body Optional<TypedForm> form) {
            "text=${show(form.get().text)}, color=${show(form.get().color)}"
        }

        @Post('/map')
        String map(@Body Map<String, String> form) {
            form.toMapString()
        }

        @Post('/properties')
        String properties(@Body Properties form) {
            new TreeMap<>(form).toMapString()
        }

        @Post('/convertible-values')
        String convertibleValues(@Body ConvertibleValues<String> form) {
            new TreeMap<>(form.asMap()).toMapString()
        }

        @Post('/object')
        String object(@Body Object form) {
            ((Map<String, Object>) form).toMapString()
        }

        @Post('/plain')
        String plain(@Body PlainForm form) {
            show(form.text)
        }

        @Post('/not-null')
        String notNull(@Body @Valid NotNullForm form) {
            show(form.value)
        }

        @Post('/size')
        String size(@Body @Valid SizeForm form) {
            show(form.value)
        }

        @Post('/not-null-date')
        String notNullDate(@Body @Valid NotNullDateForm form) {
            show(form.value)
        }

        @Post('/not-blank')
        String notBlank(@Body @Valid NotBlankForm form) {
            show(form.value)
        }
    }

    @Controller(value = '/empty-form-multipart', consumes = MediaType.MULTIPART_FORM_DATA)
    @Requires(property = 'spec.name', value = 'FormEmptyValueBindingSpec')
    static class EmptyMultipartController {

        @Post('/typed')
        String typed(@Body TypedForm form) {
            "text=${show(form.text)}, count=${show(form.count)}"
        }
    }

    static enum Color {
        RED
    }

    @Introspected
    static enum IntrospectedColor {
        RED
    }

    @Introspected
    static class Address {
        String city
        Integer zip

        @Override
        String toString() {
            "Address($city, $zip)"
        }
    }

    static class Count2 extends Number {
        final String value

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        Count2(String value) {
            this.value = value
        }

        @Override int intValue() { 0 }
        @Override long longValue() { 0 }
        @Override float floatValue() { 0 }
        @Override double doubleValue() { 0 }

        @Override
        String toString() {
            "Count2($value)"
        }
    }

    @Introspected
    static enum CreatorColor {
        NONE

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static CreatorColor of(String value) {
            NONE
        }
    }

    @Introspected
    @JsonDeserialize(using = NoneDeserializer)
    static enum AnnotatedColor {
        NONE
    }

    static class NoneDeserializer extends ValueDeserializer<AnnotatedColor> {
        @Override
        AnnotatedColor deserialize(JsonParser p, DeserializationContext ctxt) {
            ctxt.readTree(p)
            AnnotatedColor.NONE
        }
    }

    static class Money {
        final String amount

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        Money(String amount) {
            this.amount = amount
        }

        @Override
        String toString() {
            "Money($amount)"
        }
    }

    static class EpochWhenEmptyDeserializer extends ValueDeserializer<LocalDate> {
        @Override
        LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
            // a top level form value arrives as an array of one
            String text = ctxt.readTree(p).get(0).asString()
            text ? LocalDate.parse(text) : LocalDate.EPOCH
        }
    }

    @Introspected
    static class TypedForm {
        String text
        CharSequence chars
        Object object
        Optional<String> optionalText
        List<String> textList
        Set<String> textSet
        URI uri
        Locale locale
        Money money
        Count2 count2
        CreatorColor creatorColor
        AnnotatedColor annotatedColor
        @JsonDeserialize(using = EpochWhenEmptyDeserializer)
        LocalDate customDate
        @JsonProperty('renamedText')
        String someText
        String kebabText

        Integer count
        int primitiveCount
        BigDecimal amount
        LocalDate date
        LocalDateTime dateTime
        Instant instant
        Duration duration
        Date legacyDate
        Color color
        Integer defaulted = 5
        @JsonProperty('renamed')
        Color renamedColor
        Color kebabColor
        @JsonAlias('oldColor')
        Color aliasedColor
        IntrospectedColor introspectedColor
        @JsonDeserialize
        Color usinglessColor

        Boolean flag
        boolean primitiveFlag
        Optional<Integer> optionalCount
        List<Integer> countList
        Address address
    }

    @Introspected
    static class RenamedForm {
        @JsonProperty('age')
        String ageText
        @JsonProperty('years')
        Integer age
    }

    @Introspected
    static class CreatorForm {
        final String raw

        @JsonCreator
        CreatorForm(@JsonProperty('when') String when) {
            this.raw = when
        }

        LocalDate getWhen() {
            raw ? LocalDate.parse(raw) : null
        }
    }

    static class PlainForm {
        String text
    }

    @Introspected
    static class NotNullForm {
        @NotNull
        String value
    }

    @Introspected
    static class SizeForm {
        @Size(min = 1)
        String value
    }

    @Introspected
    static class NotNullDateForm {
        @NotNull
        LocalDateTime value
    }

    @Introspected
    static class NotBlankForm {
        @NotBlank
        String value
    }
}
