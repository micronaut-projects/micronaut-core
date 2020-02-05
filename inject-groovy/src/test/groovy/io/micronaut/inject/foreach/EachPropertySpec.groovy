/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.foreach

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class EachPropertySpec extends Specification {

    void "test configuration properties binding for similar names" () {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                [ 'foo.bar.two-public.port':'8989',
                  'foo.bar.two-public.anotherPort':'9990',
                  'foo.bar.two-public.intList':"7,8,9",
                  'foo.bar.two-public.stringList':"1,2",
                  'foo.bar.two-public.inner.enabled':'false',
                  'foo.bar.two-public.flags.two':'2',
                  'foo.bar.two-public.flags.three':'3',
                  'foo.bar.two-public.urlList':"http://test.com, http://test2.com",
                  'foo.bar.two-public.urlList2':["http://test.com", "http://test2.com"],
                  'foo.bar.two-public.url':'http://test.com',

                 'foo.bar.one.port':'8080',
                 'foo.bar.one.anotherPort':'9090',
                 'foo.bar.one.intList':"1,2,3",
                 'foo.bar.one.stringList':"1,2",
                 'foo.bar.one.inner.enabled':'true',
                 'foo.bar.one.flags.one':'1',
                 'foo.bar.one.flags.two':'2',
                 'foo.bar.one.urlList':"http://test.com, http://test2.com",
                 'foo.bar.one.urlList2':["http://test.com", "http://test2.com"],
                 'foo.bar.one.url':'http://test.com',

                 'foo.bar.two.port':'8888',
                 'foo.bar.two.anotherPort':'9999',
                 'foo.bar.two.intList':"4,5,6",
                 'foo.bar.two.stringList':"1,2",
                 'foo.bar.two.inner.enabled':'false',
                 'foo.bar.two.flags.two':'2',
                 'foo.bar.two.flags.three':'3',
                 'foo.bar.two.urlList':"http://test.com, http://test2.com",
                 'foo.bar.two.urlList2':["http://test.com", "http://test2.com"],
                 'foo.bar.two.url':'http://test.com']
        ))

        applicationContext.start()

        when:
        applicationContext.getBean(MyConfiguration)

        then:
        thrown(NonUniqueBeanException)

        when:
        MyConfiguration one = applicationContext.getBean(MyConfiguration, Qualifiers.byName("one"))
        MyConfiguration two = applicationContext.getBean(MyConfiguration, Qualifiers.byName("two"))
        MyConfiguration twoPublic = applicationContext.getBean(MyConfiguration, Qualifiers.byName("two-public"))

        then:
        one.port == 8080
        two.port == 8888
        twoPublic.port == 8989
    }

    void "test configuration properties binding"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['foo.bar.one.port':'8080',
                'foo.bar.one.anotherPort':'9090',
                'foo.bar.one.intList':"1,2,3",
                'foo.bar.one.stringList':"1,2",
                'foo.bar.one.inner.enabled':'true',
                'foo.bar.one.flags.one':'1',
                'foo.bar.one.flags.two':'2',
                'foo.bar.one.urlList':"http://test.com, http://test2.com",
                'foo.bar.one.urlList2':["http://test.com", "http://test2.com"],
                'foo.bar.one.url':'http://test.com',

                'foo.bar.two.port':'8888',
                'foo.bar.two.anotherPort':'9999',
                'foo.bar.two.intList':"4,5,6",
                'foo.bar.two.stringList':"1,2",
                'foo.bar.two.inner.enabled':'false',
                'foo.bar.two.flags.two':'2',
                'foo.bar.two.flags.three':'3',
                'foo.bar.two.urlList':"http://test.com, http://test2.com",
                'foo.bar.two.urlList2':["http://test.com", "http://test2.com"],
                'foo.bar.two.url':'http://test.com']
        ))

        applicationContext.start()

        when:
        applicationContext.getBean(MyConfiguration)

        then:
        thrown(NonUniqueBeanException)

        when:
        MyConfiguration bean = applicationContext.getBean(MyConfiguration, Qualifiers.byName("one"))
        MyConfiguration bean2 = applicationContext.getBean(MyConfiguration, Qualifiers.byName("two"))

        then:
        bean.port == 8080
        bean.anotherPort == 9090
        bean.intList == [1,2,3]
        bean.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        bean.flags == [one:1, two:2]
        bean.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        bean.stringList == ["1", "2"]
        bean.emptyList == null
        bean.url.get() == new URL('http://test.com')
        !bean.anotherUrl.isPresent()
        bean.defaultPort == 9999
        bean.defaultValue == 9999
        bean.primitiveDefaultValue == 9999
        bean.inner.enabled == 'true'

        bean2.port == 8888
        bean2.anotherPort == 9999
        bean2.intList == [4,5,6]
        bean2.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        bean2.flags == [two:2, three:3]
        bean2.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        bean2.stringList == ["1", "2"]
        bean2.emptyList == null
        bean2.url.get() == new URL('http://test.com')
        !bean2.anotherUrl.isPresent()
        bean2.defaultPort == 9999
        bean2.defaultValue == 9999
        bean2.primitiveDefaultValue == 9999
        bean2.inner.enabled == 'false'

    }

    void "test configuration properties binding by bean type"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.one.port':'8080',
                'foo.bar.one.anotherPort':'9090',
                'foo.bar.one.intList':"1,2,3",
                'foo.bar.one.stringList':"1,2",
                'foo.bar.one.inner.enabled':'true',
                'foo.bar.one.flags.one':'1',
                'foo.bar.one.flags.two':'2',
                'foo.bar.one.urlList':"http://test.com, http://test2.com",
                'foo.bar.one.urlList2':["http://test.com", "http://test2.com"],
                'foo.bar.one.url':'http://test.com',

                'foo.bar.two.port':'8888',
                'foo.bar.two.anotherPort':'9999',
                'foo.bar.two.intList':"4,5,6",
                'foo.bar.two.stringList':"1,2",
                'foo.bar.two.inner.enabled':'false',
                'foo.bar.two.flags.two':'2',
                'foo.bar.two.flags.three':'3',
                'foo.bar.two.urlList':"http://test.com, http://test2.com",
                'foo.bar.two.urlList2':["http://test.com", "http://test2.com"],
                'foo.bar.two.url':'http://test.com']


        ))

        applicationContext.start()

        when:
        applicationContext.getBean(MyBean)

        then:
        thrown(NonUniqueBeanException)

        when:
        MyBean bean = applicationContext.getBean(MyBean, Qualifiers.byName("one"))
        MyBean bean2 = applicationContext.getBean(MyBean, Qualifiers.byName("two"))

        then:
        bean != bean2
        bean.configuration.port == 8080
        bean.configuration.anotherPort == 9090
        bean.configuration.intList == [1,2,3]
        bean.configuration.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        bean.configuration.flags == [one:1, two:2]
        bean.configuration.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        bean.configuration.stringList == ["1", "2"]
        bean.configuration.emptyList == null
        bean.configuration.url.get() == new URL('http://test.com')
        !bean.configuration.anotherUrl.isPresent()
        bean.configuration.defaultPort == 9999
        bean.configuration.defaultValue == 9999
        bean.configuration.primitiveDefaultValue == 9999
        bean.configuration.inner.enabled == 'true'

        bean2.configuration.port == 8888
        bean2.configuration.anotherPort == 9999
        bean2.configuration.intList == [4,5,6]
        bean2.configuration.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        bean2.configuration.flags == [two:2, three:3]
        bean2.configuration.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        bean2.configuration.stringList == ["1", "2"]
        bean2.configuration.emptyList == null
        bean2.configuration.url.get() == new URL('http://test.com')
        !bean2.configuration.anotherUrl.isPresent()
        bean2.configuration.defaultPort == 9999
        bean2.configuration.defaultValue == 9999
        bean2.configuration.primitiveDefaultValue == 9999
        bean2.configuration.inner.enabled == 'false'

    }

    void "test configuration properties binding by a non bean type"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.one.port':'8080',
                'foo.bar.two.port':'8888']
        ))

        applicationContext.start()

        when:
        applicationContext.getBean(NonBeanClass)

        then:
        thrown(NonUniqueBeanException)

        when:
        NonBeanClass bean = applicationContext.getBean(NonBeanClass, Qualifiers.byName("one"))
        NonBeanClass bean2 = applicationContext.getBean(NonBeanClass, Qualifiers.byName("two"))

        then:
        bean != bean2
        bean.port == 8080
        bean2.port == 8888

        cleanup:
        applicationContext.close()
    }

    void "test configuration properties binding by bean type with primary"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.one.port':'8080',
                'foo.bar.one.anotherPort':'9090',
                'foo.bar.one.intList':"1,2,3",
                'foo.bar.one.stringList':"1,2",
                'foo.bar.one.inner.enabled':'true',
                'foo.bar.one.flags.one':'1',
                'foo.bar.one.flags.two':'2',
                'foo.bar.one.urlList':"http://test.com, http://test2.com",
                'foo.bar.one.urlList2':["http://test.com", "http://test2.com"],
                'foo.bar.one.url':'http://test.com',

                'foo.bar.two.port':'8888',
                'foo.bar.two.anotherPort':'9999',
                'foo.bar.two.intList':"4,5,6",
                'foo.bar.two.stringList':"1,2",
                'foo.bar.two.inner.enabled':'false',
                'foo.bar.two.flags.two':'2',
                'foo.bar.two.flags.three':'3',
                'foo.bar.two.urlList':"http://test.com, http://test2.com",
                'foo.bar.two.urlList2':["http://test.com", "http://test2.com"],
                'foo.bar.two.url':'http://test.com']

        ))

        applicationContext.start()

        MyBeanWithPrimary bean2 = applicationContext.getBean(MyBeanWithPrimary)
        MyBeanWithPrimary bean = applicationContext.getBean(MyBeanWithPrimary, Qualifiers.byName("one"))

        expect:
        bean.configuration.port == 8080
        bean.configuration.anotherPort == 9090
        bean.configuration.intList == [1,2,3]
        bean.configuration.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        bean.configuration.flags == [one:1, two:2]
        bean.configuration.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        bean.configuration.stringList == ["1", "2"]
        bean.configuration.emptyList == null
        bean.configuration.url.get() == new URL('http://test.com')
        !bean.configuration.anotherUrl.isPresent()
        bean.configuration.defaultPort == 9999
        bean.configuration.defaultValue == 9999
        bean.configuration.primitiveDefaultValue == 9999
        bean.configuration.inner.enabled == 'true'

        bean2.configuration.port == 8888
        bean2.configuration.anotherPort == 9999
        bean2.configuration.intList == [4,5,6]
        bean2.configuration.urlList == [new URL('http://test.com'),new URL('http://test2.com')]
        bean2.configuration.flags == [two:2, three:3]
        bean2.configuration.urlList2 == [new URL('http://test.com'),new URL('http://test2.com')]
        bean2.configuration.stringList == ["1", "2"]
        bean2.configuration.emptyList == null
        bean2.configuration.url.get() == new URL('http://test.com')
        !bean2.configuration.anotherUrl.isPresent()
        bean2.configuration.defaultPort == 9999
        bean2.configuration.defaultValue == 9999
        bean2.configuration.primitiveDefaultValue == 9999
        bean2.configuration.inner.enabled == 'false'

    }

    void "test configuration properties binding by non bean type with primary"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(PropertySource.of(
                'test',
                ['foo.bar.one.port':'8080',
                'foo.bar.two.port':'8888']
        ))

        applicationContext.start()

        NonBeanClassWithPrimary bean2 = applicationContext.getBean(NonBeanClassWithPrimary)
        NonBeanClassWithPrimary bean = applicationContext.getBean(NonBeanClassWithPrimary, Qualifiers.byName("one"))

        expect:
        bean.port == 8080
        bean2.port == 8888

        cleanup:
        applicationContext.close()
    }

    void "test resolve for empty configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")


        applicationContext.start()

        expect:
        !applicationContext.containsBean(MyBean)
        !applicationContext.containsBean(MyConfiguration)
        applicationContext.getBeansOfType(MyBean).size() == 0
        applicationContext.getBeansOfType(MyConfiguration).size() == 0
        applicationContext.streamOfType(MyBean).count() == 0
        applicationContext.streamOfType(MyConfiguration).count() == 0
    }

}

@EachBean(MyConfiguration)
class MyBean {
    final MyConfiguration configuration

    MyBean(MyConfiguration configuration) {
        this.configuration = configuration
    }
}

@EachProperty('foo.bar')
class MyConfiguration {
    int port
    Integer defaultValue = 9999
    int primitiveDefaultValue = 9999
    protected int defaultPort = 9999
    protected Integer anotherPort
    List<String> stringList
    List<Integer> intList
    List<URL> urlList
    List<URL> urlList2
    List<URL> emptyList
    Map<String,Integer> flags
    Optional<URL> url
    Optional<URL> anotherUrl = Optional.empty()
    Inner inner

    Integer getAnotherPort() {
        return anotherPort
    }

    int getDefaultPort() {
        return defaultPort
    }

    @ConfigurationProperties("inner")
    static class Inner {
        String enabled
    }
}

@EachBean(MyConfigurationWithPrimary)
class MyBeanWithPrimary {
    final MyConfigurationWithPrimary configuration

    MyBeanWithPrimary(MyConfigurationWithPrimary configuration) {
        this.configuration = configuration
    }
}

@EachProperty(value = 'foo.bar', primary = 'two')
class MyConfigurationWithPrimary {
    int port
    Integer defaultValue = 9999
    int primitiveDefaultValue = 9999
    protected int defaultPort = 9999
    protected Integer anotherPort
    List<String> stringList
    List<Integer> intList
    List<URL> urlList
    List<URL> urlList2
    List<URL> emptyList
    Map<String,Integer> flags
    Optional<URL> url
    Optional<URL> anotherUrl = Optional.empty()
    Inner inner

    Integer getAnotherPort() {
        return anotherPort
    }

    int getDefaultPort() {
        return defaultPort
    }

    @ConfigurationProperties("inner")
    static class Inner {
        String enabled
    }
}

@Factory
class MyNonBean {

    @EachBean(MyConfiguration.class)
    NonBeanClass nonBeanClass(MyConfiguration myConfiguration) {
        new NonBeanClass(myConfiguration.port)
    }

}

@Factory
class MyNonBeanWithPrimary {

    @EachBean(MyConfigurationWithPrimary.class)
    NonBeanClassWithPrimary nonBeanClassWithPrimary(MyConfigurationWithPrimary myConfiguration) {
        new NonBeanClassWithPrimary(myConfiguration.port)
    }
}


class NonBeanClass {

    int port

    NonBeanClass(int port) {
        this.port = port
    }
}

class NonBeanClassWithPrimary {

    int port

    NonBeanClassWithPrimary(int port) {
        this.port = port
    }
}
