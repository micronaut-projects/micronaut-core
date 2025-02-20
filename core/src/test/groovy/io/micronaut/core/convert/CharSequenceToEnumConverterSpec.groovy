package io.micronaut.core.convert

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import spock.lang.Specification

class CharSequenceToEnumConverterSpec extends Specification {

    void "test conversion enum with creator method"() {
        given:
        def converter = new CharSequenceToEnumConverter()

        when:
        def converted = converter.convert("1", EnumWithCreator.class, null).orElse(null)

        then:
        converted
        converted == EnumWithCreator.FIRST
    }

    void "test conversion enum without creator method"() {
        given:
        def converter = new CharSequenceToEnumConverter()

        when:
        def converted = converter.convert("FIRST", EnumWithoutCreator.class, null).orElse(null)

        then:
        converted
        converted == EnumWithoutCreator.FIRST
    }

    enum EnumWithoutCreator {

        FIRST(1),
        SECOND(2),
        ;

        @JsonValue
        private final int value;

        EnumWithoutCreator(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    enum EnumWithCreator {

        @JsonProperty("1")
        FIRST(1),
        @JsonProperty("2")
        SECOND(2),
        ;

        @JsonValue
        private final int value;

        EnumWithCreator(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }

        @JsonCreator
        static EnumWithCreator fromValue(int value) {
            return Arrays.stream(values()).filter(e -> e.value == value).findFirst().orElse(null);
        }
    }
}
