package io.micronaut.inject.foreach

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.qualifiers.Qualifiers

class EachPropertySubclassPrefixSpec extends AbstractTypeElementSpec {

    void "a subclass that redeclares the EachProperty of its superclass binds under the same prefix"() {
        when:
        def context = buildContext('test.ServiceConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;

@EachProperty("services")
class ServiceConfig {
    private final String name;
    private String url;

    ServiceConfig(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

@EachProperty("services")
@Replaces(ServiceConfig.class)
class ExtendedServiceConfig extends ServiceConfig {
    private final Extra extra;
    private String flavour;

    @Inject
    ExtendedServiceConfig(@Parameter String name, Extra extra) {
        super(name);
        this.extra = extra;
    }

    public String getFlavour() {
        return flavour;
    }

    public void setFlavour(String flavour) {
        this.flavour = flavour;
    }

    public Extra getExtra() {
        return extra;
    }

    @ConfigurationProperties("extra")
    static class Extra {
        private int level;

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }
    }
}
''', false, [
            'services.one.url': 'http://one',
            'services.one.flavour': 'vanilla',
            'services.one.extra.level': 3,
        ])
        def type = context.classLoader.loadClass('test.ServiceConfig')
        def bean = context.getBean(type, Qualifiers.byName('one'))

        then:
        bean.class.name == 'test.ExtendedServiceConfig'
        bean.url == 'http://one'
        bean.flavour == 'vanilla'
        bean.extra.level == 3
        context.getBeanDefinition(context.classLoader.loadClass('test.ExtendedServiceConfig'))
            .stringValue(ConfigurationReader, ConfigurationReader.PREFIX).get() == 'services.*'

        cleanup:
        context.close()
    }
}
