package io.micronaut.runtime.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Mapper
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionService
import jakarta.inject.Singleton
import spock.lang.Specification

class MapperMethodWatcherSpec extends Specification {

    void "a mapper's converter follows the mapper through a reload and goes with it"() {
        given:
        def context = ApplicationContext.run(["spec.name": "MapperMethodWatcherSpec"])
        def conversionService = context.getBean(ConversionService)
        def processor = context.getBean(MapperMethodProcessor)

        expect: "the startup batch registered the converter, and the processor is not adapted: it watches"
        conversionService.convert(new WatchedSource("a"), WatchedTarget).get().name == "a"
        !((DefaultBeanContext) context).adaptedProcessors().contains(processor)

        when: "the mapper comes back in a new generation"
        def definition = context.getBeanDefinition(WatchedMapper)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [definition])

        then: "the converter still converts"
        conversionService.convert(new WatchedSource("b"), WatchedTarget).get().name == "b"

        when: "the mapper goes as another, mapping the source to another type, comes"
        def other = context.getBeanDefinition(OtherMapper)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [other])

        then: "the retired pair is registered again for the mapper that still maps it, and the new one converts"
        conversionService.convert(new WatchedSource("d"), WatchedTarget).get().name == "dup:d"
        conversionService.convert(new WatchedSource("e"), OtherTarget).get().name == "e"

        when: "the mapper is back"
        ((DefaultBeanContext) context).notifyDefinitionChange([other], [definition])

        then:
        conversionService.convert(new WatchedSource("f"), WatchedTarget).get().name == "f"
        conversionService.convert(new WatchedSource("g"), OtherTarget).isEmpty()

        when: "the mapper goes while another maps the same pair"
        def duplicate = context.getBeanDefinition(DuplicateMapper)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])

        then: "the pair is registered again for the other"
        conversionService.convert(new WatchedSource("c"), WatchedTarget).get().name == "dup:c"

        when: "both go"
        ((DefaultBeanContext) context).notifyDefinitionChange([definition, duplicate], [])

        then: "so does the converter"
        conversionService.convert(new WatchedSource("h"), WatchedTarget).isEmpty()

        when: "the application registers its own converter for the pair over a mapper that then goes"
        ((DefaultBeanContext) context).notifyDefinitionChange([], [definition])
        context.getBean(io.micronaut.core.convert.MutableConversionService).addConverter(WatchedSource, WatchedTarget, { WatchedSource s -> new WatchedTarget("app:" + s.name) } as java.util.function.Function)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition, duplicate], [])

        then: "the application's converter stays"
        conversionService.convert(new WatchedSource("i"), WatchedTarget).get().name == "app:i"

        when: "the same, with a mapper of the same pair surviving the one that goes"
        ((DefaultBeanContext) context).notifyDefinitionChange([], [definition, duplicate])
        context.getBean(io.micronaut.core.convert.MutableConversionService).addConverter(WatchedSource, WatchedTarget, { WatchedSource s -> new WatchedTarget("app2:" + s.name) } as java.util.function.Function)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])

        then: "the surviving mapper is not registered over the application's converter"
        conversionService.convert(new WatchedSource("j"), WatchedTarget).get().name == "app2:j"

        cleanup:
        context.close()
    }

    @Requires(property = "spec.name", value = "MapperMethodWatcherSpec")
    @Singleton
    static abstract class WatchedMapper {
        @Mapper
        abstract WatchedTarget toTarget(WatchedSource source)
    }

    @Requires(property = "spec.name", value = "MapperMethodWatcherSpec")
    @Singleton
    static abstract class DuplicateMapper {
        @Mapper
        @Mapper.Mapping(to = "name", from = "#{'dup:' + source.name}")
        abstract WatchedTarget toTarget(WatchedSource source)
    }

    @Requires(property = "spec.name", value = "MapperMethodWatcherSpec")
    @Singleton
    static abstract class OtherMapper {
        @Mapper
        abstract OtherTarget toTarget(WatchedSource source)
    }

    @Introspected
    static class OtherTarget {
        final String name
        OtherTarget(String name) { this.name = name }
    }

    @Introspected
    static class WatchedSource {
        final String name
        WatchedSource(String name) { this.name = name }
    }

    @Introspected
    static class WatchedTarget {
        final String name
        WatchedTarget(String name) { this.name = name }
    }
}
