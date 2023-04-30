package io.micronaut.inject.configproperties.records

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder

class RecordNestingSpec extends AbstractTypeElementSpec {

    void "test nesting records within each other"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import java.util.List;

@ConfigurationProperties("test")
record RecordOuterConfig(
    String name,
    int age,
    RecordInnerConfig inner,
    List<RecordInners> inners
) {

    @ConfigurationProperties("inner")
    record RecordInnerConfig(String foo, ThirdLevel thirdLevel) {

        @ConfigurationProperties("nested")
        record ThirdLevel(int num) {}
    }

    @EachProperty("inners")
    record RecordInners(@Parameter String name, int count, ThirdLevel thirdLevel) {

        @ConfigurationProperties("nested")
        record ThirdLevel(int num) {}
    }
}
''')
        when:
        def config = getBean(context, 'test.RecordOuterConfig')

        then:
        config.name() == 'test1'
        config.age() == 10
        config.inner().foo() == 'test2'
        config.inner().thirdLevel().num() == 20
        config.inners().size() == 1
        config.inners()[0].count() == 30
        config.inners()[0].thirdLevel().num() == 40
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
