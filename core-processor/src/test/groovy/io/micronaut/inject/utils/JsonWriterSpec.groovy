package io.micronaut.inject.utils

import spock.lang.Specification

class JsonWriterSpec extends Specification {

    void "objects, arrays and values are separated and quoted"() {
        expect:
        new JsonWriter()
            .beginObject()
            .name("name").value("time\"out\\")
            .name("count").value(3L)
            .name("ratio").value(1.5d)
            .name("on").value(true)
            .name("none").value((String) null)
            .name("tags").beginArray().value("a").value("b").endArray()
            .name("empty").beginArray().endArray()
            .name("nested").beginObject().endObject()
            .endObject()
            .toString() == '{"name":"time\\"out\\\\","count":3,"ratio":1.5,"on":true,"none":null,"tags":["a","b"],"empty":[],"nested":{}}'
    }

    void "control characters are escaped"() {
        expect:
        JsonWriter.quote("a\nb\tc\u0001d/e") == '"a\\nb\\tc\\u0001d/e"'
        JsonWriter.quote(null) == 'null'
        new JsonWriter().value("x").toString() == '"x"'
        new JsonWriter().value(7L).toString() == '7'
    }

    void "any value renders as JSON"() {
        expect:
        new JsonWriter().value([name: "n", n: 2, list: ["a", 1, true, null], other: URI.create("http://x")]).toString() ==
            '{"name":"n","n":2,"list":["a",1,true,null],"other":"http://x"}'
    }

    void "an indented document breaks members onto lines"() {
        expect:
        JsonWriter.indented("  ").beginObject().name("a").value(1L).name("b").beginArray().value("x").endArray().name("c").beginObject().endObject().endObject().toString() == '''{
  "a": 1,
  "b": [
    "x"
  ],
  "c": {}
}'''
        JsonWriter.indented("  ").value([:]).toString() == '{}'
    }

    void "misuse fails instead of producing malformed JSON"() {
        when:
        new JsonWriter().beginObject().value("no name")

        then:
        thrown(IllegalStateException)

        when:
        new JsonWriter().beginObject().name("a").endObject()

        then:
        thrown(IllegalStateException)

        when:
        new JsonWriter().beginArray().endObject()

        then:
        thrown(IllegalStateException)

        when:
        new JsonWriter().beginObject().toString()

        then:
        thrown(IllegalStateException)

        when:
        new JsonWriter().value(1L).value(2L)

        then:
        thrown(IllegalStateException)

        when:
        new JsonWriter().toString()

        then:
        thrown(IllegalStateException)
    }
}
