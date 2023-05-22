package io.micronaut.inject.configproperties.itfce

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.inject.BeanDefinitionReference

class InterfaceNestingSpec extends AbstractTypeElementSpec {
    void "test nesting interfaces within each other"() {
        given:
        def context = buildContext('test.ItfceOuterConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import java.util.List;

@ConfigurationProperties("test")
interface ItfceOuterConfig {
    String getName();
    int getAge();
    ItfceInnerConfig getInner();
    List<ItfceInners> getInners();

    @ConfigurationProperties("inner")
    interface ItfceInnerConfig {
        String getFoo();
        ThirdLevel getThirdLevel();
        @ConfigurationProperties("nested")
        interface ThirdLevel {
            int getNum();
        }
    }

    @EachProperty("inners")
    interface ItfceInners {
        int getCount();
        ThirdLevel getThirdLevel();
        @ConfigurationProperties("nested")
        interface ThirdLevel {
            int getNum();
        }
    }
}
''', true)
        when:
        def config = getBean(context, 'test.ItfceOuterConfig')

        then:
        config.getName() == 'test1'
        config.getAge() == 10
        config.getInner().getFoo() == 'test2'
        config.getInner().getThirdLevel().getNum() == 20
        config.getInners().size() == 1
        config.getInners()[0].getCount() == 30
        config.getInners()[0].getThirdLevel().getNum() == 40
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
                'test.name':'test1',
                'test.age':'10',
                'test.inner.foo':'test2',
                'test.inner.nested.num':'20',
                'test.inners.one.count':'30',
                'test.inners.one.nested.num':'40'
        )
    }

}
