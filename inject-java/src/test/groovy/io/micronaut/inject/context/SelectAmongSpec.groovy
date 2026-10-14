package io.micronaut.inject.context

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.context.Qualifier
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

class SelectAmongSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
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

interface AllLowered {}
@Singleton @Secondary class AllLoweredOne implements AllLowered {}
@Singleton @Secondary class AllLoweredTwo implements AllLowered {}

interface AllLoweredOrdered {}
@Singleton @Secondary @Order(5) class AllLoweredOrderedLater implements AllLoweredOrdered {}
@Singleton @Secondary @Order(-5) class AllLoweredOrderedFirst implements AllLoweredOrdered {}

interface Lowered {}
@Singleton @Secondary class LoweredSecondary implements Lowered {}
@Singleton class LoweredPlain implements Lowered {}

@EachProperty("each")
class Each {
    final String name;
    Each(@Parameter String name) {
        this.name = name;
    }
}

@Singleton
class Consumer {
    final Several several;
    final Ordered ordered;
    final WithDefault withDefault;
    final Exact exact;
    final Custom custom;
    final Lowered lowered;
    final AllLoweredOrdered allLoweredOrdered;
    Consumer(Several several, Ordered ordered, WithDefault withDefault, Exact exact, Custom custom, Lowered lowered, AllLoweredOrdered allLoweredOrdered) {
        this.several = several;
        this.ordered = ordered;
        this.withDefault = withDefault;
        this.exact = exact;
        this.custom = custom;
        this.lowered = lowered;
        this.allLoweredOrdered = allLoweredOrdered;
    }
}
'''

    private ApplicationContext context
    private RecordingCustomizer configured
    private RecordingCustomizer bound

    def setup() {
        configured = new RecordingCustomizer()
        context = buildContext('test.Consumer', SOURCE, false, ['each.one.a': '1', 'each.two.a': '2'])
    }

    def cleanup() {
        context.close()
    }

    void "several primaries narrow the candidates before the order decides, as injection does"() {
        when:
        def picked = select('test.Several')

        then:
        picked.beanType.name == 'test.SeveralPrimaryFirst'
        injected('several').class.name == 'test.SeveralPrimaryFirst'
    }

    void "the order picks among plain candidates, as injection does"() {
        when:
        def picked = select('test.Ordered')

        then:
        picked.beanType.name == 'test.OrderedFirst'
        injected('ordered').class.name == 'test.OrderedFirst'
    }

    void "the default implementation picks among candidates of the same order, as injection does"() {
        when:
        def picked = select('test.WithDefault')

        then:
        picked.beanType.name == 'test.WithDefaultImpl'
        injected('withDefault').class.name == 'test.WithDefaultImpl'
    }

    void "the exact type picks between a bean and its subclass bean, as injection does"() {
        when:
        def picked = select('test.Exact')

        then:
        picked.beanType.name == 'test.Exact'
        injected('exact').class.name == 'test.Exact'
    }

    void "the resolution customizer bound to the context is consulted, as injection consults it"() {
        when:
        def picked = select('test.Custom')

        then:
        picked.beanType.name == 'test.CustomTwo'
        injected('custom').class.name == 'test.CustomTwo'
        bound.consulted.contains('test.Custom')
        configured.consulted.isEmpty()
    }

    void "a secondary candidate gives way to a plain one, as injection does"() {
        when:
        def picked = select('test.Lowered')

        then:
        picked.beanType.name == 'test.LoweredPlain'
        injected('lowered').class.name == 'test.LoweredPlain'
    }

    void "candidates that are all secondary are still decided by the order, as injection does"() {
        when:
        def picked = select('test.AllLoweredOrdered')

        then:
        picked.beanType.name == 'test.AllLoweredOrderedFirst'
        injected('allLoweredOrdered').class.name == 'test.AllLoweredOrderedFirst'
    }

    void "candidates nothing decides among are all returned, where a lookup is non-unique"() {
        given:
        def ambiguous = candidates('test.Ambiguous')
        def allLowered = candidates('test.AllLowered')

        expect:
        context.selectAmong(argument('test.Ambiguous'), ambiguous) as Set == ambiguous as Set
        context.selectAmong(argument('test.AllLowered'), allLowered) as Set == allLowered as Set

        when:
        context.getBean(type('test.Ambiguous'))

        then:
        thrown(NonUniqueBeanException)

        when:
        context.getBean(type('test.AllLowered'), Qualifiers.any())

        then:
        thrown(NonUniqueBeanException)
    }

    void "no requested type skips the customizer and the exact type"() {
        given:
        def custom = candidates('test.Custom')
        def exact = candidates('test.Exact')

        expect:
        context.selectAmong(null, custom) as Set == custom as Set
        !bound.consulted.contains('test.Custom')
        context.selectAmong(null, exact) as Set == exact as Set

        and: 'the rules that need no type still decide'
        context.selectAmong(null, candidates('test.Several')).beanType.name == ['test.SeveralPrimaryFirst']
        context.selectAmong(null, candidates('test.Ordered')).beanType.name == ['test.OrderedFirst']
        context.selectAmong(null, candidates('test.WithDefault')).beanType.name == ['test.WithDefaultImpl']
    }

    void "one candidate or none is returned as it is"() {
        given:
        def candidates = candidates('test.Ambiguous')

        expect:
        context.selectAmong(argument('test.Ambiguous'), [candidates[0]]) == [candidates[0]]
        context.selectAmong(argument('test.Ambiguous'), [candidates[1]]) == [candidates[1]]
        context.selectAmong(argument('test.Ambiguous'), []) == []
        context.selectAmong(null, [candidates[0]]) == [candidates[0]]
    }

    void "the candidates come back as the caller's own instances"() {
        given: 'each-property definitions are delegates of one definition'
        def each = candidates('test.Each')

        expect:
        each.size() == 2
        context.selectAmong(argument('test.Each'), each).size() == 2
        context.selectAmong(argument('test.Each'), each).every { candidate -> each.any { it.is(candidate) } }
        context.selectAmong(argument('test.Each'), [each[1]])[0].is(each[1])
    }

    void "the candidates are not looked up: a definition the type lookup would not find is still considered"() {
        given:
        def ordered = candidates('test.Ordered')

        expect: 'selecting under an unrelated type does not drop them'
        context.selectAmong(argument('test.Several'), ordered).beanType.name == ['test.OrderedFirst']
    }

    private BeanDefinition<?> select(String name) {
        def selected = context.selectAmong(argument(name), candidates(name))
        assert selected.size() == 1
        selected[0]
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
        contextBuilder.beanResolutionCustomizer(configured)
    }

    /**
     * Picks CustomTwo for test.Custom and records what it was asked about; the context is served by a bound
     * copy, which is the one the selection has to consult.
     */
    class RecordingCustomizer implements BeanResolutionCustomizer {
        final List<String> consulted = []

        @Override
        BeanResolutionCustomizer forContext(BeanContext beanContext) {
            bound = new RecordingCustomizer()
            return bound
        }

        @Override
        <T> Optional<BeanDefinition<T>> resolveNonUniqueBean(Argument<T> beanType, Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
            consulted.add(beanType.type.name)
            if (beanType.type.name == 'test.Custom') {
                return Optional.of(candidates.find { it.beanType.name == 'test.CustomTwo' })
            }
            return Optional.empty()
        }
    }
}
