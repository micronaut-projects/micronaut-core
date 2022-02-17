package io.micronaut.inject.annotation


import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/4308')
class GroovyAnnotatedFieldWithSetterSpec extends Specification {

    void 'test that when MapFormat not present configuration keys are changed to dashed lowercase'() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': 'GroovyAnnotatedFieldWithSetterSpec',
                'conf.animals.VERY_FAST': 'rabbit',
        ])

        def config = context.getBean(GroovyFieldNotAnnotatedConfiguration)

        expect:
        config.animals.containsKey('very-fast')
        config.animals.'very-fast' == 'rabbit'

        cleanup:
        context.close()
    }

    void 'test that when MapFormat present on the field then configuration keys are according to keyFormat'() {
        ApplicationContext context = ApplicationContext.run([
                'spec.name': 'GroovyAnnotatedFieldWithSetterSpec',
                'conf.animals.VERY_FAST': 'rabbit',
        ])

        def config = context.getBean(GroovyFieldAnnotatedConfiguration)

        expect:
        config.animals.containsKey('VERY_FAST')
        config.animals.VERY_FAST == 'rabbit'

        cleanup:
        context.close()
    }
}

@Requires(property = "spec.name", value = "GroovyAnnotatedFieldWithSetterSpec")
@ConfigurationProperties("conf")
class GroovyFieldNotAnnotatedConfiguration {

    private Map<String, String> animals

    Map<String, String> getAnimals() {
        return animals
    }

    void setAnimals(Map<String, String> animals) {
        this.animals = animals
    }

}

@Requires(property = "spec.name", value = "GroovyAnnotatedFieldWithSetterSpec")
@ConfigurationProperties("conf")
class GroovyFieldAnnotatedConfiguration {

    @MapFormat(keyFormat = StringConvention.RAW)
    private Map<String, String> animals

    Map<String, String> getAnimals() {
        return animals
    }

    void setAnimals(Map<String, String> animals) {
        this.animals = animals
    }

}
