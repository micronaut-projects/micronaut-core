package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode
import org.graalvm.polyglot.Value

/**
 * Attributes fixed at construction: a compiled body of a plain class reads an attribute only
 * {@code __init__} assigns from a Java field of the stub, reaching the Python object once per
 * object, and reads it again once a change of an attribute of the object is reported.
 */
class StaticCompilationFixedAttributesSpec extends AbstractPythonTypeElementSpec {

    StaticCompilationMode mode = StaticCompilationMode.OFF
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.context.python.annotation import CompileStatic

@Singleton
class Rates:
    def rate(self, n: int) -> float:
        return n * 1.5

@Singleton
class Pricing:
    def __init__(self, rates: Rates, name: Annotated[str, Value("${pricing.name:std}")], limit: Annotated[int, Value("${pricing.limit:10}")]):
        self.rates = rates
        self.name = name
        self.limit = limit
        self.hits = 0

    def total(self, n: int) -> float:
        return self.rates.rate(min(n, self.limit))

    def label(self, n: int) -> str:
        return self.name + ":" + str(min(n, self.limit))

    def graalpyInternalFixed0(self) -> str:
        return "a method of the class, named as the stub names its fixed readers"

@Singleton
class Counting:
    def __init__(self, name: Annotated[str, Value("${pricing.name:std}")]):
        self.name = name
        self.calls = 0

    def label(self, n: int) -> str:
        return self.name + ":" + str(n)

    def count(self) -> None:
        self.calls += 1

@Singleton
@CompileStatic(False)
class Front:
    def __init__(self, pricing: Pricing):
        self.pricing = pricing

    def label(self, n: int) -> str:
        return self.pricing.label(n)

    def bump(self) -> None:
        self.pricing.hits += 1
'''

    void "fixed attributes give the results of the python bodies, before and after a change"() {
        given:
        Map results = [:]
        Map<String, StaticCompilationDecision> compiled = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            PythonStatic.resetEntries()
            def context = buildContext(SOURCE)
            try {
                def pricing = getBean(context, 'python.Pricing')
                def front = getBean(context, 'python.Front')
                Value self = pricing.asPolyglotValue()
                def python = { String code ->
                    self.getContext().getBindings('python').putMember('pricing', self)
                    self.getContext().eval('python', code)
                }
                Map row = [:]
                row.total = invoke { pricing.total(3) }
                row.capped = invoke { pricing.total(30) }
                row.label = invoke { pricing.label(4) }
                row.again = invoke { pricing.label(40) }
                row.front = invoke { front.label(5) }
                row.own = invoke { pricing.graalpyInternalFixed0() }
                python('pricing.name = "vip"')
                row.renamed = invoke { pricing.label(4) }
                row.front_renamed = invoke { front.label(5) }
                self.putMember('limit', 2)
                row.limited = invoke { pricing.total(30) }
                row.limited_label = invoke { pricing.label(30) }
                python('setattr(pricing, "name", "gold")')
                row.set = invoke { pricing.label(1) }
                if (m == StaticCompilationMode.ALL) {
                    decisions.each { compiled[it.qualifiedName()] = it }
                    row.java_runs = PythonStatic.entries('python.Pricing#label')
                    row.hooked = python('type(pricing).__setattr__.__name__').asString()
                }
                results[m] = row
            } finally {
                context.close()
            }
        }

        expect: "the compiled bodies read what the python bodies read, changes included"
        results[StaticCompilationMode.ALL].findAll { !(it.key in ['java_runs', 'hooked']) } == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF].total == 4.5d
        results[StaticCompilationMode.OFF].capped == 15.0d
        results[StaticCompilationMode.OFF].label == 'std:4'
        results[StaticCompilationMode.OFF].again == 'std:10'
        results[StaticCompilationMode.OFF].renamed == 'vip:4'
        results[StaticCompilationMode.OFF].front_renamed == 'vip:5'
        results[StaticCompilationMode.OFF].limited == 3.0d
        results[StaticCompilationMode.OFF].limited_label == 'vip:2'
        results[StaticCompilationMode.OFF].set == 'gold:1'
        results[StaticCompilationMode.OFF].own == 'a method of the class, named as the stub names its fixed readers'

        and: "the bodies are compiled, and an attribute fixed at construction is no bridge call"
        ['Pricing.total', 'Pricing.label', 'Counting.label'].every { compiled[it].outcome() == StaticCompilationDecision.Outcome.COMPILED }
        compiled['Pricing.label'].stats().bridgeCalls() == 0
        compiled['Pricing.total'].stats().bridgeCalls() == 0

        and: "the same body of a class writing an attribute after construction reads it through the python object"
        compiled['Counting.label'].stats().bridgeCalls() == 1

        and: "the java bodies ran, and the class reports changes of the attributes of its objects"
        results[StaticCompilationMode.ALL].java_runs >= 6
        results[StaticCompilationMode.ALL].hooked == '_micronaut_fixed_setattr'
    }

    void "a write of an attribute no stub keeps reports no change, a write of a fixed attribute does"() {
        given:
        Map results = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            PythonStatic.resetEntries()
            def context = buildContext(SOURCE)
            try {
                def pricing = getBean(context, 'python.Pricing')
                def front = getBean(context, 'python.Front')
                Value self = pricing.asPolyglotValue()
                self.getContext().getBindings('python').putMember('pricing', self)
                Map row = [:]
                row.label = invoke { pricing.label(4) }
                Long before = epoch(pricing)
                10.times { front.bump() }
                self.getContext().eval('python', 'for _ in range(10):\n    pricing.hits += 1')
                row.hits = self.getMember('hits').asInt()
                row.counted = invoke { pricing.label(4) }
                Long counted = epoch(pricing)
                self.getContext().eval('python', 'pricing.name = "vip"')
                row.renamed = invoke { pricing.label(4) }
                if (m == StaticCompilationMode.ALL) {
                    row.counter_moved_epoch = counted != before
                    row.name_moved_epoch = epoch(pricing) != counted
                    row.hooked = self.getContext().eval('python', 'type(pricing).__setattr__ is object.__setattr__').asBoolean()
                }
                results[m] = row
            } finally {
                context.close()
            }
        }

        expect: "the same results either way"
        results[StaticCompilationMode.ALL].subMap(['label', 'hits', 'counted', 'renamed']) == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF] == [label: 'std:4', hits: 20, counted: 'std:4', renamed: 'vip:4']

        and: "the counter leaves the epoch of the fixed slots alone; the fixed attribute moves it"
        results[StaticCompilationMode.ALL].counter_moved_epoch == false
        results[StaticCompilationMode.ALL].name_moved_epoch == true
        results[StaticCompilationMode.ALL].hooked == false
    }

    void "the slots are primed when the stub takes its Python object, and read it again only after a change"() {
        given:
        mode = StaticCompilationMode.ALL
        decisions.clear()
        def context = buildContext(SOURCE)

        when: "the bean is created, before any compiled body runs"
        def pricing = getBean(context, 'python.Pricing')
        Value self = pricing.asPolyglotValue()
        Map<String, Object> primed = slots(pricing)

        then: "every slot holds the value of its attribute for the object"
        primed.size() == 3
        primed.values().every { it != null && component(it, 'owner').is(self) && component(it, 'held') }

        when: "the compiled bodies run"
        def total = pricing.total(3)
        def label = pricing.label(4)

        then: "they read the slots as primed"
        total == 4.5d
        label == 'std:4'
        slots(pricing).every { name, fixed -> fixed.is(primed[name]) }

        when: "code outside the compilation assigns a fixed attribute"
        self.getContext().getBindings('python').putMember('pricing', self)
        self.getContext().eval('python', 'pricing.name = "vip"')

        then: "the next body reads the attributes again"
        pricing.label(4) == 'vip:4'
        slots(pricing).any { name, fixed -> !fixed.is(primed[name]) }

        cleanup:
        context?.close()
    }

    /**
     * What the slots of the attributes fixed at construction of a stub keep, by slot.
     */
    private static Map<String, Object> slots(Object stub) {
        Map<String, Object> found = [:]
        for (Class<?> type = stub.getClass(); type != null; type = type.superclass) {
            type.declaredFields.findAll { it.type.name == 'io.micronaut.context.python.PythonFixedAttributes$Slot' }.each { slot ->
                slot.accessible = true
                def value = slot.get(stub)
                def current = value == null ? null : value.getClass().getDeclaredField('current')
                current?.accessible = true
                found[slot.name] = current?.get(value)
            }
        }
        return found
    }

    private static Object component(Object record, String name) {
        def field = record.getClass().getDeclaredField(name)
        field.accessible = true
        return field.get(record)
    }

    /**
     * The epoch of the fixed attributes of the stub class of a bean, or null when it has none.
     */
    private static Long epoch(Object bean) {
        for (Class<?> type = bean.getClass(); type != null; type = type.superclass) {
            def field = type.declaredFields.find { it.name == 'graalpyInternalFixedAttributes' }
            if (field != null) {
                field.accessible = true
                return field.get(null).epoch()
            }
        }
        return null
    }

    private static Object invoke(Closure<?> call) {
        try {
            return call()
        } catch (Exception ignored) {
            return 'raised'
        }
    }
}
