package io.micronaut.inject.context

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.context.Qualifier
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

class FindBeanDefinitionAmongCandidatesSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

interface Several {}
@Singleton @Primary @Order(2) class SeveralPrimaryLater implements Several {}
@Singleton @Primary @Order(1) class SeveralPrimaryFirst implements Several {}
@Singleton @Order(0) class SeveralPlain implements Several {}

interface Ordered {}
@Singleton @Order(10) class OrderedLater implements Ordered {}
@Singleton @Order(-10) class OrderedFirst implements Ordered {}

@DefaultImplementation(WithDefaultImpl.class)
interface WithDefault {}
@Singleton class WithDefaultImpl implements WithDefault {}
@Singleton class WithDefaultOther implements WithDefault {}

@Singleton class Exact {}
@Singleton class ExactSub extends Exact {}

interface Custom {}
@Singleton class CustomOne implements Custom {}
@Singleton class CustomTwo implements Custom {}

interface Ambiguous {}
@Singleton class AmbiguousOne implements Ambiguous {}
@Singleton class AmbiguousTwo implements Ambiguous {}

interface Named_ {}
@Singleton @Named("left") class NamedLeft implements Named_ {}
@Singleton @Named("right") class NamedRight implements Named_ {}

interface Lowered {}
@Singleton @Secondary class LoweredSecondary implements Lowered {}
@Singleton class LoweredPlain implements Lowered {}

@Singleton
class Consumer {
    final Several several;
    final Ordered ordered;
    final WithDefault withDefault;
    final Exact exact;
    final Custom custom;
    final Lowered lowered;
    Consumer(Several several, Ordered ordered, WithDefault withDefault, Exact exact, Custom custom, Lowered lowered) {
        this.several = several;
        this.ordered = ordered;
        this.withDefault = withDefault;
        this.exact = exact;
        this.custom = custom;
        this.lowered = lowered;
    }
}
'''

    private ApplicationContext context

    def setup() {
        context = buildContext('test.Consumer', SOURCE)
    }

    def cleanup() {
        context.close()
    }

    void "several primaries narrow the candidates before the order decides, as injection does"() {
        when:
        def picked = resolve('test.Several')

        then:
        picked.beanType.name == 'test.SeveralPrimaryFirst'
        injected('several').class.name == 'test.SeveralPrimaryFirst'
    }

    void "the order picks among plain candidates, as injection does"() {
        when:
        def picked = resolve('test.Ordered')

        then:
        picked.beanType.name == 'test.OrderedFirst'
        injected('ordered').class.name == 'test.OrderedFirst'
    }

    void "the default implementation picks among candidates of the same order, as injection does"() {
        when:
        def picked = resolve('test.WithDefault')

        then:
        picked.beanType.name == 'test.WithDefaultImpl'
        injected('withDefault').class.name == 'test.WithDefaultImpl'
    }

    void "the exact type picks between a bean and its subclass bean, as injection does"() {
        when:
        def picked = resolve('test.Exact')

        then:
        picked.beanType.name == 'test.Exact'
        injected('exact').class.name == 'test.Exact'
    }

    void "the resolution customizer is consulted, as injection consults it"() {
        when:
        def picked = resolve('test.Custom')

        then:
        picked.beanType.name == 'test.CustomTwo'
        injected('custom').class.name == 'test.CustomTwo'
    }

    void "a secondary candidate gives way to a plain one, as injection does"() {
        when:
        def picked = resolve('test.Lowered')

        then:
        picked.beanType.name == 'test.LoweredPlain'
        injected('lowered').class.name == 'test.LoweredPlain'
    }

    void "candidates nothing decides among are non-unique, as a lookup is"() {
        when:
        resolve('test.Ambiguous')

        then:
        thrown(NonUniqueBeanException)

        when:
        context.getBean(type('test.Ambiguous'))

        then:
        thrown(NonUniqueBeanException)
    }

    void "the any qualifier takes the first of candidates nothing decides among"() {
        when:
        def picked = resolve('test.Ambiguous', Qualifiers.any())

        then:
        picked.beanType.name in ['test.AmbiguousOne', 'test.AmbiguousTwo']
    }

    void "the qualifier narrows the candidates first"() {
        when:
        def picked = resolve('test.Named_', Qualifiers.byName('right'))

        then:
        picked.beanType.name == 'test.NamedRight'

        when:
        def none = context.findBeanDefinition(argument('test.Named_'), candidates('test.Named_'), Qualifiers.byName('middle'))

        then:
        !none.present
    }

    void "a single candidate is taken as it is, and none resolve to empty"() {
        given:
        def candidates = candidates('test.Ambiguous')

        expect:
        context.findBeanDefinition(argument('test.Ambiguous'), [candidates[0]], null).get().is(candidates[0])
        !context.findBeanDefinition(argument('test.Ambiguous'), [], null).present
    }

    private BeanDefinition<?> resolve(String name, Qualifier qualifier = null) {
        context.findBeanDefinition(argument(name), candidates(name), qualifier).get()
    }

    private List<BeanDefinition<?>> candidates(String name) {
        def all = new ArrayList<>(context.getBeanDefinitions(argument(name)))
        assert all.size() > 1
        all
    }

    private Argument<?> argument(String name) {
        Argument.of(type(name))
    }

    private Class<?> type(String name) {
        context.classLoader.loadClass(name)
    }

    private Object injected(String field) {
        getBean(context, 'test.Consumer')."$field"
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanResolutionCustomizer(new BeanResolutionCustomizer() {
            @Override
            <T> Optional<BeanDefinition<T>> resolveNonUniqueBean(Argument<T> beanType, Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
                if (beanType.type.name == 'test.Custom') {
                    return Optional.of(candidates.find { it.beanType.name == 'test.CustomTwo' })
                }
                return Optional.empty()
            }
        })
    }
}
