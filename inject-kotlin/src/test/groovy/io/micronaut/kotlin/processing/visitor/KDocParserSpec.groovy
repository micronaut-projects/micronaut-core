package io.micronaut.kotlin.processing.visitor

import spock.lang.Specification

class KDocParserSpec extends Specification {

    void "description is the prose before the first block tag"() {
        expect:
        KDocParserKt.parseKDoc('VAT configuration.\n\n @property percentage pct\n @param region reg\n').description == 'VAT configuration.'
    }

    void "description is empty when the doc is only block tags"() {
        expect:
        KDocParserKt.parseKDoc(' @param name only a tag\n').description == ''
    }

    void "parameterDoc returns the named property tag content"() {
        expect:
        KDocParserKt.parseKDoc('Doc.\n @property percentage the pct text\n').parameterDoc('percentage') == 'the pct text'
    }

    void "parameterDoc prefers property over param"() {
        expect:
        KDocParserKt.parseKDoc('Doc.\n @property region prop text\n @param region param text\n').parameterDoc('region') == 'prop text'
    }

    void "parameterDoc falls back to param and returns null when absent"() {
        given:
        def doc = 'Doc.\n @param region the region text\n'

        expect:
        KDocParserKt.parseKDoc(doc).parameterDoc('region') == 'the region text'
        KDocParserKt.parseKDoc(doc).parameterDoc('missing') == null
    }

    void "block tag content spanning multiple lines is joined"() {
        when:
        def parsed = KDocParserKt.parseKDoc('Doc.\n @param name first line\n second line\n')

        then:
        parsed.description == 'Doc.'
        parsed.blockTags.size() == 1
        parsed.blockTags[0].tag == 'param'
        parsed.blockTags[0].name == 'name'
        parsed.blockTags[0].content == 'first line second line'
    }

    void "unknown tags are captured without a name"() {
        when:
        def parsed = KDocParserKt.parseKDoc('Doc.\n @return the result\n')

        then:
        parsed.blockTags[0].tag == 'return'
        parsed.blockTags[0].name == null
        parsed.blockTags[0].content == 'the result'
    }

    void "blank doc yields empty description and no tags"() {
        when:
        def parsed = KDocParserKt.parseKDoc('')

        then:
        parsed.description == ''
        parsed.blockTags.isEmpty()
    }

    void "multi-line description keeps paragraph breaks and trims each line"() {
        expect:
        KDocParserKt.parseKDoc('First line.\n Second line.\n\n @param x y\n').description == 'First line.\nSecond line.'
    }

    void "a doc with no tags is entirely description"() {
        when:
        def parsed = KDocParserKt.parseKDoc('Just a description.\n with more.\n')

        then:
        parsed.description == 'Just a description.\nwith more.'
        parsed.blockTags.isEmpty()
    }

    void "parameterDoc returns null when the tag has blank content"() {
        expect:
        KDocParserKt.parseKDoc('Doc.\n @property percentage\n').parameterDoc('percentage') == null
    }

    void "parameterDoc disambiguates tags of the same type by name"() {
        given:
        def doc = 'Doc.\n @param first the first\n @param second the second\n'

        expect:
        KDocParserKt.parseKDoc(doc).parameterDoc('first') == 'the first'
        KDocParserKt.parseKDoc(doc).parameterDoc('second') == 'the second'
    }

    void "blank continuation lines within tag content are ignored"() {
        expect:
        KDocParserKt.parseKDoc('Doc.\n @param name line one\n\n line two\n').parameterDoc('name') == 'line one line two'
    }
}
