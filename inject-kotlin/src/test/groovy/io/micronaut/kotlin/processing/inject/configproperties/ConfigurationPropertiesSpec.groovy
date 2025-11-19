package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import spock.lang.Specification

class ConfigurationPropertiesSpec extends Specification {

    void "test submap with generics binding"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'foo.bar.map.key1.key2.property':10,
                'foo.bar.map.key1.key2.property2.property':10
        )

        expect:
        ctx.getBean(MyConfig).map.containsKey('key1')
        ctx.getBean(MyConfig).map.get("key1") instanceof Map
        ctx.getBean(MyConfig).map.get("key1").get("key2") instanceof MyConfig.Value
        ctx.getBean(MyConfig).map.get("key1").get("key2").property == 10
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2.property == 10

        cleanup:
        ctx.close()
    }

    void "test submap with generics binding and conversion"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'foo.bar.map.key1.key2.property':'10',
                'foo.bar.map.key1.key2.property2.property':'10'
        )

        expect:
        ctx.getBean(MyConfig).map.containsKey('key1')
        ctx.getBean(MyConfig).map.get("key1") instanceof Map
        ctx.getBean(MyConfig).map.get("key1").get("key2") instanceof MyConfig.Value
        ctx.getBean(MyConfig).map.get("key1").get("key2").property == 10
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2
        ctx.getBean(MyConfig).map.get("key1").get("key2").property2.property == 10

        cleanup:
        ctx.close()
    }

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").build()
        applicationContext.environment.addPropertySource(PropertySource.of(
            'test',
            ['foo.bar.innerVals': [
                    ['expire-unsigned-seconds': 123], ['expireUnsignedSeconds': 600]
            ],
             'foo.bar.port':'8080',
             'foo.bar.max-size':'1MB',
             'foo.bar.another-size':'1MB',
            'foo.bar.anotherPort':'9090',
            'foo.bar.intList':"1,2,3",
            'foo.bar.stringList':"1,2",
            'foo.bar.flags.one':'1',
            'foo.bar.flags.two':'2',
            'foo.bar.urlList':"http://test.com, http://test2.com",
            'foo.bar.urlList2':["http://test.com", "http://test2.com"],
            'foo.bar.url':'http://test.com']
        ))

        applicationContext.start()

        MyConfig config = applicationContext.getBean(MyConfig)

        expect:
        config.innerVals.size() == 2
        config.innerVals[0].expireUnsignedSeconds == 123
        config.innerVals[1].expireUnsignedSeconds == 600
        config.port == 8080
        config.maxSize == 1048576
        config.anotherPort == 9090
        config.intList == [1,2,3]
        config.flags == [one:1, two:2]
        config.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        config.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        config.stringList == ["1", "2"]
        config.emptyList == null
        config.url.get() == new URL('http://test.com')
        !config.anotherUrl.isPresent()
        config.defaultPort == 9999
        config.defaultValue == 9999
    }

    void "test configuration inner class properties binding"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").build()
        applicationContext.environment.addPropertySource(PropertySource.of(
            'foo.bar.inner.enabled':'true',
        ))

        applicationContext.start()

        MyConfig config = applicationContext.getBean(MyConfig)

        expect:
        config.inner.enabled
    }

    void "test binding to a map property"() {
        ApplicationContext context = ApplicationContext.run(CollectionUtils.mapOf("map.property.yyy.zzz", 3, "map.property.yyy.xxx", 2, "map.property.yyy.yyy", 3))
        MapProperties config = context.getBean(MapProperties.class)

        expect:
        config.property.containsKey('yyy')

        cleanup:
        context.close()
    }

    void "test camelCase vs kebab_case"() {
        ApplicationContext context1 = ApplicationContext.run("rec1")
        ApplicationContext context2 = ApplicationContext.run("rec2")

        RecConf config1 = context1.getBean(RecConf.class)
        RecConf config2 = context2.getBean(RecConf.class)

        expect:
            config1 == config2

        cleanup:
            context1.close()
            context2.close()
    }
}
