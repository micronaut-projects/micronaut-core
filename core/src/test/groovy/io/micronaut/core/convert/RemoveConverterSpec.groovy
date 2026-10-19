package io.micronaut.core.convert

import spock.lang.Specification

class RemoveConverterSpec extends Specification {

    void "a removed converter converts no more, with the variations registered with it"() {
        given:
        def service = new DefaultMutableConversionService()
        service.addConverter(CharSequence, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target(s.toString())) } as TypeConverter)

        expect:
        service.convert("a", Target).get().name == "a"
        service.convert(new StringBuilder("b"), Target).get().name == "b"

        when:
        service.removeConverter(CharSequence, Target)

        then: "neither the pair nor the String variation registered with it converts, and the cache does not either"
        service.convert("c", Target).isEmpty()
        service.convert(new StringBuilder("d"), Target).isEmpty()

        when: "a String converter registered over the variation of a CharSequence one survives the removal of the latter"
        service = new DefaultMutableConversionService()
        service.addConverter(CharSequence, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target("cs:" + s)) } as TypeConverter)
        service.addConverter(String, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target("s:" + s)) } as TypeConverter)
        service.removeConverter(CharSequence, Target)

        then: "and so does the CharSequence variation the String converter registered over the removed pair"
        service.convert("e", Target).get().name == "s:e"
        service.convert(new StringBuilder("f"), Target).get().name == "s:f"

        when: "a converter is removed by instance while another is registered for the pair"
        service = new DefaultMutableConversionService()
        TypeConverter first = { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target("1:" + s)) } as TypeConverter
        TypeConverter second = { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target("2:" + s)) } as TypeConverter
        service.addConverter(CharSequence, Target, first)
        service.addConverter(CharSequence, Target, second)
        service.removeConverter(CharSequence, Target, first)

        then: "the one registered over it stays"
        service.convert("g", Target).get().name == "2:g"

        when:
        service.removeConverter(CharSequence, Target, second)

        then:
        service.convert("h", Target).isEmpty()

        when: "one instance registered for two pairs is removed for one"
        service = new DefaultMutableConversionService()
        TypeConverter shared = { Object s, Class<Target> t, ConversionContext c -> Optional.of(new Target("shared:" + s)) } as TypeConverter
        service.addConverter(Integer, Target, shared)
        service.addConverter(Long, Target, shared)
        service.removeConverter(Integer, Target)

        then: "the other pair keeps it"
        service.convert(1, Target).isEmpty()
        service.convert(2L, Target).get().name == "shared:2"

        when: "a converter that was never registered is removed"
        service.removeConverter(Integer, Target)

        then:
        noExceptionThrown()
    }

    static class Target {
        final String name
        Target(String name) { this.name = name }
    }
}
