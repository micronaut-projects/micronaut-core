package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.PythonStatic
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * Calls of compiled bodies on self: name-mangled private methods and attributes, overrides in
 * subclasses, property setters and siblings whose result breaks their hint.
 */
class StaticCompilationSiblingsSpec extends AbstractPythonTypeElementSpec {

    StaticCompilationMode mode = StaticCompilationMode.OFF
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode)
            .options(["-A${StaticCompilationMode.TRACE_OPTION}=true".toString()])
            .staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from jakarta.inject import Singleton

@Singleton
class Calc:
    def __init__(self):
        self.__count = 2
        self._factor = 1.0

    def __helper(self, n: int) -> int:
        return n * 10

    def mangled(self, n: int) -> int:
        return self.__helper(n) + 1

    def counted(self, n: int) -> int:
        return self.__count + n

    def helper(self, n: int) -> int:
        return n + 1

    def chained(self, n: int) -> int:
        return self.helper(n) * 2

    @property
    def factor(self) -> float:
        return self._factor

    @factor.setter
    def factor(self, value: float):
        self._factor = value * 2

    def scaled(self, value: float) -> float:
        self.factor = value
        return self.factor

    def _maybe(self, n: int) -> int:
        if n < 0:
            return None
        return n

    def maybe(self, n: int) -> int:
        if n < 0:
            return None
        return n

    def through_private(self, n: int) -> int:
        return self._maybe(n) + 1

    def through_public(self, n: int) -> int:
        return self.maybe(n) + 1

    def checks_none(self, n: int) -> bool:
        return self._maybe(n) is None

@Singleton
class Doubled(Calc):
    def helper(self, n: int) -> int:
        return n * 100
'''

    void "sibling calls agree with the Python bodies"() {
        given:
        Map results = [:]
        Map<String, StaticCompilationDecision> compiled = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            PythonStatic.resetEntries()
            def context = buildContext(SOURCE)
            try {
                def calc = getBean(context, 'python.Calc')
                def doubled = getBean(context, 'python.Doubled')
                results[m] = [
                    mangled         : invoke { calc.mangled(3) },
                    counted         : invoke { calc.counted(3) },
                    chained         : invoke { calc.chained(3) },
                    overridden      : invoke { doubled.chained(3) },
                    scaled          : invoke { calc.scaled(1.5d) },
                    private_ok      : invoke { calc.through_private(3) },
                    private_none    : invoke { calc.through_private(-1) },
                    public_ok       : invoke { calc.through_public(3) },
                    public_none     : invoke { calc.through_public(-1) },
                    checks_none     : invoke { calc.checks_none(-1) },
                ]
                if (m == StaticCompilationMode.ALL) {
                    decisions.each { compiled[it.qualifiedName()] = it }
                    PythonStatic.resetEntries()
                    doubled.chained(3)
                    results[m].overridden_ran = [PythonStatic.entries('python.Calc#chained'), PythonStatic.entries('python.Calc#helper'), PythonStatic.entries('python.Doubled#helper')]
                }
            } finally {
                context.close()
            }
        }

        expect:
        results[StaticCompilationMode.ALL].findAll { it.key != 'overridden_ran' } == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF].mangled == 31
        results[StaticCompilationMode.OFF].counted == 5
        results[StaticCompilationMode.OFF].chained == 8
        results[StaticCompilationMode.OFF].overridden == 600
        results[StaticCompilationMode.OFF].scaled == 3.0d
        results[StaticCompilationMode.OFF].private_ok == 4
        results[StaticCompilationMode.OFF].private_none == 'raised'  // None + 1 raises TypeError in Python
        results[StaticCompilationMode.OFF].public_none == 'raised'
        results[StaticCompilationMode.OFF].checks_none == true

        and: "a mangled private method is called by its mangled name, a mangled attribute stays in Python"
        compiled['Calc.mangled'].outcome() == StaticCompilationDecision.Outcome.COMPILED
        compiled['Calc.counted'].outcome() == StaticCompilationDecision.Outcome.SKIPPED

        and: "a sibling's int result is not taken for never None"
        compiled['Calc.checks_none'].reasons()*.rule() == ['unsupported-expression']

        and: "the compiled caller ran as Java and called the override of the subclass, never the compiled method it overrides"
        results[StaticCompilationMode.ALL].overridden_ran == [1, 0, 1]

        and: "the overridden sibling is dispatched through the Python object, the property through its setter"
        ['chained', 'scaled', 'through_private', 'through_public'].every { compiled["Calc.$it".toString()].outcome() == StaticCompilationDecision.Outcome.COMPILED }
    }

    void "a sibling hinted to return a primitive that returns None fails as a TypeError naming the method and its hint"() {
        given:
        mode = StaticCompilationMode.ALL
        def context = buildContext('''
from jakarta.inject import Singleton

@Singleton
class Calc:
    def _maybe(self, n: int) -> int:
        if n < 0:
            return None
        return n

    def _ratio(self, n: int) -> float:
        if n < 0:
            return None
        return n / 2

    def through_int(self, n: int) -> int:
        return self._maybe(n) + 1

    def through_float(self, n: int) -> float:
        return self._ratio(n) * 2.0
''')
        def calc = getBean(context, 'python.Calc')
        Map<String, StaticCompilationDecision> compiled = decisions.collectEntries { [it.qualifiedName(), it] }

        expect: "the callers are compiled and the hints are trusted for a result that is not None"
        compiled['Calc.through_int'].outcome() == StaticCompilationDecision.Outcome.COMPILED
        compiled['Calc.through_float'].outcome() == StaticCompilationDecision.Outcome.COMPILED
        calc.through_int(3) == 4
        calc.through_float(3) == 3.0d

        when:
        calc.through_int(-1)

        then:
        IllegalStateException e = thrown()
        e.message == 'TypeError: Calc._maybe() returned None but is hinted to return int'

        when:
        calc.through_float(-1)

        then:
        IllegalStateException f = thrown()
        f.message == 'TypeError: Calc._ratio() returned None but is hinted to return float'

        cleanup:
        context?.close()
    }

    void "a compiled sibling taking a list works on the caller's list as Java code, an overridden one through the Python object"() {
        given:
        Map results = [:]
        Map<String, StaticCompilationDecision> compiled = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            def context = buildContext('''
from jakarta.inject import Singleton

@Singleton
class Collector:
    def collected(self) -> str:
        xs: list[str] = []
        self.gather(xs)
        self.keep(xs)
        return ",".join(xs)

    def gather(self, xs: list[str]) -> None:
        xs.append("gathered")

    def keep(self, xs: list[str]) -> None:
        xs.append("kept")

@Singleton
class Special(Collector):
    def gather(self, xs: list[str]) -> None:
        xs.append("special")
''')
            try {
                def collector = getBean(context, 'python.Collector')
                def special = getBean(context, 'python.Special')
                PythonStatic.resetEntries()
                results[m] = [collector: collector.collected(), special: special.collected()]
                if (m == StaticCompilationMode.ALL) {
                    decisions.each { compiled[it.qualifiedName()] = it }
                    results[m].ran = ['Collector#collected', 'Collector#gather', 'Collector#keep', 'Special#gather'].collect { PythonStatic.entries("python.$it".toString()) }
                }
            } finally {
                context.close()
            }
        }

        expect:
        results[StaticCompilationMode.ALL].findAll { it.key != 'ran' } == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF] == [collector: 'gathered,kept', special: 'special,kept']

        and: "keep, which no subclass overrides, ran compiled in both calls; gather, which Special overrides, ran as Python would dispatch it"
        ['Collector.collected', 'Collector.gather', 'Collector.keep', 'Special.gather'].every { compiled[it].outcome() == StaticCompilationDecision.Outcome.COMPILED }
        compiled['Collector.collected'].stats().javaCalls() == 1
        compiled['Collector.collected'].stats().bridgeCalls() == 1
        results[StaticCompilationMode.ALL].ran == [2, 0, 2, 0]
    }

    private static Object invoke(Closure<?> call) {
        try {
            return call()
        } catch (Exception ignored) {
            return 'raised'
        }
    }
}
