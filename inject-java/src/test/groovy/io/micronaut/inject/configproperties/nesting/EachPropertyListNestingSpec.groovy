package io.micronaut.inject.configproperties.nesting

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.inject.qualifiers.Qualifiers

// https://github.com/micronaut-projects/micronaut-core/issues/10030
class EachPropertyListNestingSpec extends AbstractTypeElementSpec {

    void "test nested @EachProperty(list = true) within @EachProperty(list = true)"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import java.util.List;

@EachProperty(value = "test", primary = "one")
class OuterConfig {

    private final String name;

    private List<Source> sources;

    public OuterConfig(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    @EachProperty(value = "sources", list = true)
    public static class Source {

        private String name;

        private List<Transform> transforms;

        private Inner inner;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Transform> getTransforms() {
            return transforms;
        }

        public void setTransforms(List<Transform> transforms) {
            this.transforms = transforms;
        }

        public Inner getInner() {
            return inner;
        }

        public void setInner(Inner inner) {
            this.inner = inner;
        }

        @EachProperty(value = "transforms", list = true)
        public static class Transform {

            private final int index;

            private String source;

            public Transform(@Parameter int index) {
                this.index = index;
            }

            public int getIndex() {
                return index;
            }

            public String getSource() {
                return source;
            }

            public void setSource(String source) {
                this.source = source;
            }
        }

        @ConfigurationProperties("inner")
        public static class Inner {

            private final List<Transform> transforms;

            public Inner(List<Transform> transforms) {
                this.transforms = transforms;
            }

            public List<Transform> getTransforms() {
                return transforms;
            }

            @EachProperty(value = "transforms", list = true)
            public static class Transform {

                private String source;

                public String getSource() {
                    return source;
                }

                public void setSource(String source) {
                    this.source = source;
                }
            }
        }
    }
}
''')
        when:
        def one = getBean(context, 'test.OuterConfig')

        then:
        one.name == 'one'
        one.sources.size() == 2
        one.sources[0].name == 's1'
        one.sources[0].transforms*.source == ['a', 'b']
        one.sources[0].transforms*.index == [0, 1]
        one.sources[0].inner.transforms*.source == ['x']
        one.sources[1].name == 's2'
        one.sources[1].transforms*.source == ['c']
        one.sources[1].inner == null

        when:
        def two = getBean(context, 'test.OuterConfig', Qualifiers.byName("two"))

        then:
        two.name == 'two'
        two.sources.size() == 1
        two.sources[0].name == 's3'
        two.sources[0].transforms*.source == ['d', 'e', 'f']
        two.sources[0].inner.transforms*.source == ['y', 'z']
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
                'test.one.sources[0].name'                     : 's1',
                'test.one.sources[0].transforms[0].source'     : 'a',
                'test.one.sources[0].transforms[1].source'     : 'b',
                'test.one.sources[0].inner.transforms[0].source': 'x',
                'test.one.sources[1].name'                     : 's2',
                'test.one.sources[1].transforms[0].source'     : 'c',
                'test.two.sources[0].name'                     : 's3',
                'test.two.sources[0].transforms[0].source'     : 'd',
                'test.two.sources[0].transforms[1].source'     : 'e',
                'test.two.sources[0].transforms[2].source'     : 'f',
                'test.two.sources[0].inner.transforms[0].source': 'y',
                'test.two.sources[0].inner.transforms[1].source': 'z',
        )
    }
}
