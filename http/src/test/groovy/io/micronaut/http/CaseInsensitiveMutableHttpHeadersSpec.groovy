package io.micronaut.http

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import spock.lang.Specification

class CaseInsensitiveMutableHttpHeadersSpec extends Specification {

    void "starts empty"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        expect:
        headers.isEmpty()
    }

    void "can be set up with a map"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "Content-Type": ["application/json"], "Content-Length": ["123"])

        expect:
        headers.size() == 2
        headers.get("content-type") == "application/json"
        headers.get("content-length") == "123"
    }

    void "values can be converted"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "Content-Type": ["application/json"], "Content-Length": ["123"])

        expect:
        headers.size() == 2
        headers.get("content-type", Argument.of(MediaType)).get() == MediaType.APPLICATION_JSON_TYPE
        headers.get("content-length", Argument.of(Integer)).get() == 123
        headers.get("content-type", Argument.of(String)).get() == MediaType.APPLICATION_JSON
    }

    void "values can be removed"() {
        when:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "Content-Type": ["application/json"])

        then:
        headers.size() == 1
        headers.get("content-type") == "application/json"

        when:
        headers.remove("content-TYPE")

        then:
        headers.empty
    }

    void "case insensitivity"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "foo": ["123"])

        expect:
        ["foo", "FOO", "Foo", "fOo"].each {
            assert headers.get(it) == "123"
        }
    }

    void "getAll returns an unmodifiable collection"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "foo": ["123"])

        when:
        headers.getAll("foo").add("456")

        then:
        thrown(UnsupportedOperationException)

        when:
        headers.getAll("missing").add("456")

        then:
        thrown(UnsupportedOperationException)

        when:
        headers.getAll(null).add("456")

        then:
        thrown(UnsupportedOperationException)
    }

    void "calling get with a null name returns null"() {
        expect:
        new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED).get(null) == null
    }

    void "calling getAll with a null name returns an empty list"() {
        expect:
        new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED).getAll(null) == []
    }

    void "calling remove with a null name doesn't throw an exception"() {
        when:
        new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED).remove(null)

        then:
        noExceptionThrown()
    }

    void "getAll on a missing key results in an empty collection"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        expect:
        headers.getAll("foo").empty
    }

    void "get on a missing key results in null"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        expect:
        headers.get("foo") == null
    }

    void "empty header value"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when:
        headers.add("foo", "")

        then:
        headers.get("foo") == ""
    }

    void "cannot add invalid or insecure header names"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when:
        headers.add("foo ", "bar")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == '''A header name can only contain "token" characters, but found invalid character 0x20 at index 3 of header 'foo '.'''

        when:
        new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "foo\nha": ["bar"])

        then:
        IllegalArgumentException cex = thrown()
        cex.message == '''A header name can only contain "token" characters, but found invalid character 0xa at index 3 of header 'foo\nha'.'''

        when: "a character outside US-ASCII whose low byte is a token character"
        headers.add("foo\u0141", "bar")

        then:
        IllegalArgumentException wex = thrown()
        wex.message == '''A header name can only contain "token" characters, but found invalid character 0x141 at index 3 of header 'foo\u0141'.'''

        when:
        headers.add(null, "null isn't allowed")

        then:
        IllegalArgumentException nex = thrown()
        nex.message == "Header name cannot be null"
    }

    void "cannot add invalid or insecure header values"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when:
        headers.add("foo", "bar\nOrigin: localhost")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "The header value for 'foo' contains prohibited character 0xa at index 3."

        when:
        new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "foo": ["bar\nOrigin: localhost"])

        then:
        IllegalArgumentException cex = thrown()
        cex.message == "The header value for 'foo' contains prohibited character 0xa at index 3."
    }

    void "cannot add a header value with a character that is not an octet"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when: "a character above 0xFF whose low byte is a line feed"
        headers.add("foo", "bar" + ((char) 0x010A) + "Origin: localhost")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "The header value for 'foo' contains prohibited character 0x10a at index 3."

        when: "the value starts with one"
        headers.add("foo", "" + ((char) 0x010A) + "bar")

        then:
        IllegalArgumentException fex = thrown()
        fex.message == "The header value for 'foo' contains prohibited character 0x10a at index 0."

        when: "it arrives through the defaults constructor"
        new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED, "foo": ["bar" + ((char) 0x010A)])

        then:
        IllegalArgumentException cex = thrown()
        cex.message == "The header value for 'foo' contains prohibited character 0x10a at index 3."
    }

    void "obs-text remains a valid header value"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)
        String disposition = 'attachment; filename="r' + ((char) 0xE9) + 'sum' + ((char) 0xE9) + '.pdf"'

        when: "the value only uses characters that are representable as a single octet"
        headers.add("Content-Disposition", disposition)

        then: "it is accepted, as RFC 7230 obs-text (%x80-FF)"
        noExceptionThrown()
        headers.get("Content-Disposition") == disposition
    }

    void "the octet boundary is U+00FF"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)
        String highest = "" + ((char) 0x00FF)

        when: "the highest character that is still a single octet is used"
        headers.add("foo", highest + "bar" + highest)

        then: "it is accepted, as the last obs-text character"
        noExceptionThrown()
        headers.get("foo") == highest + "bar" + highest

        when: "the very next character is used at the start of the value"
        headers.add("foo", "" + ((char) 0x0100) + "bar")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "The header value for 'foo' contains prohibited character 0x100 at index 0."

        when: "the very next character is used inside the value"
        headers.add("foo", "bar" + ((char) 0x0100))

        then:
        IllegalArgumentException iex = thrown()
        iex.message == "The header value for 'foo' contains prohibited character 0x100 at index 3."
    }

    void "cannot add a header value that starts with a character that is not field-vchar"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when: "the value starts with a space"
        headers.add("foo", " bar")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "The header value for 'foo' contains prohibited character 0x20 at index 0."

        when: "the value starts with a delete character"
        headers.add("foo", "" + ((char) 0x7F) + "bar")

        then:
        IllegalArgumentException dex = thrown()
        dex.message == "The header value for 'foo' contains prohibited character 0x7f at index 0."
    }

    void "cannot add a header value holding a delete character"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when:
        headers.add("foo", "bar" + ((char) 0x7F) + "baz")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "The header value for 'foo' contains prohibited character 0x7f at index 3."
    }

    void "a horizontal tab is allowed inside a header value"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(ConversionService.SHARED)

        when: "the only character below 0x20 that field-content allows is used"
        headers.add("foo", "bar\tbaz")

        then:
        noExceptionThrown()
        headers.get("foo") == "bar\tbaz"
    }

    void "can switch off validation"() {
        given:
        CaseInsensitiveMutableHttpHeaders headers = new CaseInsensitiveMutableHttpHeaders(false, ConversionService.SHARED)

        when:
        headers.add("foo ", "bar")
        headers.add("foo", "bar\nOrigin: localhost")

        then:
        noExceptionThrown()

        when:
        headers.add(null, "null isn't allowed")

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "Header name cannot be null"
    }
}
