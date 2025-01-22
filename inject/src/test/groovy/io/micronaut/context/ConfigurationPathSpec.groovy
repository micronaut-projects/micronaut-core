package io.micronaut.context

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.env.ConfigurationPath
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.qualifiers.PrimaryQualifier
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class ConfigurationPathSpec extends Specification {

    void "test configuration path computation"() {
        given:
        def bc = BeanContext.build()


        def mock = RuntimeBeanDefinition.of(ConfigurationPathSpec.class, {-> this})

        when:
        def context = new DefaultBeanResolutionContext(bc, mock)
        ConfigurationPath configurationPath = context.configurationPath

        then:
        configurationPath.prefix() == ''
        configurationPath.primary() == null


        when:
        def primaryName = 'default'
        def eachPropertyPrefix = 'test.*'

        RuntimeBeanDefinition<Class<ConfigurationPathSpec>> beanDefinition =
                newEachPropertyBean(primaryName, eachPropertyPrefix)

        configurationPath.pushEachPropertyRoot(
                beanDefinition
        )

        then:
        configurationPath.prefix() == 'test'
        configurationPath.path() == eachPropertyPrefix
        configurationPath.primary() == primaryName

        when:
        configurationPath.pushConfigurationSegment("one")

        then:
        configurationPath.prefix() == 'test.one'
        configurationPath.path() == eachPropertyPrefix
        !configurationPath.isPrimary()

        when:
        configurationPath.removeLast()
        configurationPath.pushConfigurationSegment("default")

        then:
        configurationPath.prefix() == 'test.default'
        configurationPath.path() == eachPropertyPrefix
        configurationPath.isPrimary()
        configurationPath.name() == 'default'
        configurationPath.beanQualifier() == Qualifiers.byName('default')

        when:
        configurationPath.pushConfigurationReader(
            newConfigurationReader("test.*.inner")
        )

        then:
        configurationPath.prefix() == 'test.default.inner'
        configurationPath.path() == 'test.*.inner'
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.ROOT
        configurationPath.name() == 'default' // named inherited from parent

        when:
        configurationPath.pushEachPropertyRoot(newEachPropertyBean(null, "test.*.inner.inners.*"))

        then:
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.MAP
        configurationPath.prefix() == 'test.default.inner.inners'
        configurationPath.path() == 'test.*.inner.inners.*'

        when:
        configurationPath.pushConfigurationSegment("other")

        then:
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.NAME
        configurationPath.path() == 'test.*.inner.inners.*'
        configurationPath.prefix() == 'test.default.inner.inners.other'
        configurationPath.name() == 'default-other'
        configurationPath.simpleName() == 'other'
        configurationPath.beanQualifier() == Qualifiers.byName('default-other')

        when:
        configurationPath.removeLast()

        then:
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.MAP
        configurationPath.prefix() == 'test.default.inner.inners'
        configurationPath.path() == 'test.*.inner.inners.*'

        when:
        configurationPath.removeLast()

        then:
        configurationPath.prefix() == 'test.default.inner'
        configurationPath.path() == 'test.*.inner'
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.ROOT

        when:
        configurationPath.pushEachPropertyRoot(newEachPropertyBean(1, "test.*.inner.indexed[*]"))

        then:
        configurationPath.path() == "test.*.inner.indexed[*]"
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.LIST
        configurationPath.prefix() == 'test.default.inner.indexed'

        when:
        configurationPath.pushConfigurationSegment(1)

        then:
        configurationPath.path() == "test.*.inner.indexed[*]"
        configurationPath.kind() == ConfigurationPath.ConfigurationSegment.ConfigurationKind.INDEX
        configurationPath.prefix() == 'test.default.inner.indexed[1]'

    }

    private RuntimeBeanDefinition<Class<ConfigurationPathSpec>> newConfigurationReader(String prefix) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(ConfigurationReader.name, [(ConfigurationReader.PREFIX): prefix])
        return newBeanDef(metadata)
    }

    private RuntimeBeanDefinition<Class<ConfigurationPathSpec>> newEachPropertyBean(String primaryName, String eachPropertyPrefix) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(EachProperty.name, [primary: primaryName])
        metadata.addAnnotation(ConfigurationReader.name, [(ConfigurationReader.PREFIX): eachPropertyPrefix])
        return newBeanDef(metadata)
    }

    private RuntimeBeanDefinition<Class<ConfigurationPathSpec>> newEachPropertyBean(int primaryIndex, String eachPropertyPrefix) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(EachProperty.name, [primary: primaryIndex, list:true])
        metadata.addAnnotation(ConfigurationReader.name, [(ConfigurationReader.PREFIX): eachPropertyPrefix])

        return newBeanDef(metadata)
    }

    private RuntimeBeanDefinition<Object> newBeanDef(MutableAnnotationMetadata metadata) {
        def t = new GroovyClassLoader().parseClass("class Dynamic${System.currentTimeMillis()} {}")
        def beanDefinition = RuntimeBeanDefinition.builder(t, () -> t.newInstance())
                .annotationMetadata(metadata)
                .build()
        return beanDefinition
    }
}
