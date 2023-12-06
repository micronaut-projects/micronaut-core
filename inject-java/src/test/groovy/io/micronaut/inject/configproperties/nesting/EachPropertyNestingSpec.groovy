package io.micronaut.inject.configproperties.nesting

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers

class EachPropertyNestingSpec extends AbstractTypeElementSpec {

    void "test nesting classes within each other"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import java.util.List;

@EachProperty(value = "test", primary = "one")
class ClassOuterConfig {

    private final String name;

    private int age;
    private ClassInnerConfig inner;

    public ClassOuterConfig(@Parameter String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void setInner(ClassInnerConfig inner) {
        this.inner = inner;
    }

    public ClassInnerConfig getInners() {
        return inner;
    }

    @ConfigurationProperties("inner")
    public static class ClassInnerConfig {

        private String foo;

        private List<ClassInnerEachConfig> inners;


        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String getFoo() {
            return foo;
        }

        public void setInners(List<ClassInnerEachConfig> inners) {
            this.inners = inners;
        }

        public List<ClassInnerEachConfig> getInners() {
            return inners;
        }

        @EachProperty(value = "inners")
        public static class ClassInnerEachConfig {

            private final String name;

            private int count;

            public ClassInnerEachConfig(@Parameter String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }

            public void setCount(int count) {
                this.count = count;
            }

            public int getCount() {
                return count;
            }
        }
    }
}
''')
        when:
        def config = getBean(context, 'test.ClassOuterConfig')

        then:
        config.getName() == 'one'
        config.getAge() == 10
        config.inner.getFoo() == 'test2'
        config.inner.inners.size() == 1
        config.inner.inners.get(0).getName() == "a"
        config.inner.inners.get(0).getCount() == 20

        when:
        def config2 = getBean(context, 'test.ClassOuterConfig', Qualifiers.byName("two"))

        then:
        config2.getName() == 'two'
        config2.getAge() == 30
        config2.inner.getFoo() == 'test3'
        !config2.inner.inners.isEmpty()
        config2.inner.inners.size() == 2
        config2.inner.inners.find { it.name == '1st' }.count == 30
        config2.inner.inners.find { it.name == '2nd' }.count == 40

        when:"A unresolvable bean is queried"
        getBean(context, 'test.ClassOuterConfig$ClassInnerConfig$ClassInnerEachConfig', Qualifiers.byName("foo-bar"))

        then:
        def e = thrown(NoSuchBeanException)
        def lines = e.message.lines().toList()
        lines[0] == 'No bean of type [test.ClassOuterConfig$ClassInnerConfig$ClassInnerEachConfig] exists for the given qualifier: @Named(\'foo-bar\'). '
        lines[1] == '* [ClassInnerEachConfig] is disabled because:'
        lines[2] == ' - Configuration requires entries under the prefix: [test.*.inner.inners.*]'
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
                'test.one.age': '10',
                'test.one.inner.foo': 'test2',
                'test.one.inner.inners.a.count': '20',
                'test.two.inner.foo': 'test3',
                'test.two.age': '30',
                'test.two.inner.inners.1st.count': '30',
                'test.two.inner.inners.2nd.count': '40'
        )
    }
}
