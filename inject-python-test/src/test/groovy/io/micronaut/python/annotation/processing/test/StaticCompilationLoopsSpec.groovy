package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * Loops and exceptions of compiled bodies: a range loop whose body assigns the loop variable, the
 * checked exceptions javac would reject, the exceptions the runtime helpers throw where Python
 * raises its own, and the elements a loop unboxes.
 */
class StaticCompilationLoopsSpec extends AbstractPythonTypeElementSpec {

    StaticCompilationMode mode = StaticCompilationMode.OFF
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode).staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from jakarta.inject import Singleton
from java.io import IOException
from java.lang import ArithmeticException, IllegalArgumentException, RuntimeException
from java.net import URL, MalformedURLException
from java.time import DateTimeException

@Singleton
class Loops:
    def __init__(self):
        self.items: list[int] = [1, 2]

    def reassigned(self, n: int) -> int:
        total = 0
        for i in range(n):
            i += 10
            total += i
        return total

    def skipped(self, n: int) -> str:
        parts = ""
        for i in range(n):
            if i == 1:
                i = 7
                continue
            parts = parts + str(i) + ","
        return parts

    def raises_checked(self, n: int) -> int:
        if n == 0:
            raise IOException("zero")
        return n

    def catches_unthrown(self, n: int) -> int:
        try:
            return n + 1
        except IOException:
            return 0

    def catches_declared(self, spec: str) -> str:
        try:
            return URL(spec).getHost()
        except IOException as error:
            return "io"

    def catches_caught(self, spec: str) -> str:
        try:
            try:
                return URL(spec).getHost()
            except MalformedURLException:
                return "inner"
        except MalformedURLException:
            return "outer"

    def catches_step(self, n: int, step: int) -> int:
        total = 0
        try:
            for i in range(0, n, step):
                total += i
        except IllegalArgumentException:
            return -1
        return total

    def catches_division(self, a: int, b: int) -> int:
        try:
            return a // b
        except ArithmeticException:
            return -1

    def catches_runtime(self, n: int) -> int:
        try:
            return n + 1
        except RuntimeException:
            return -1

    def catches_raised(self, n: int) -> int:
        try:
            if n == 0:
                raise DateTimeException("zero")
            return n
        except DateTimeException:
            return -1

    def ints(self, xs: list[int]) -> str:
        parts = ""
        for x in xs:
            parts = parts + str(x) + ","
        return parts

    def floats(self, xs: list[float]) -> float:
        total = 0.0
        for x in xs:
            total += x
        return total

    def appends(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            if x < 3:
                xs.append(x + 10)
            count += 1
        return count

    def run_appends(self) -> int:
        return self.appends([1, 2])

    def clears(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            xs.clear()
            count += 1
        return count

    def stores(self, xs: list[int]) -> int:
        total = 0
        for x in xs:
            xs[0] = x
            total += x
        return total

    def deletes(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            if x == 1:
                del xs[0]
            count += 1
        return count

    def augments(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            if x < 3:
                xs += [x + 10]
            count += 1
        return count

    def aliases(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            ys = xs
            count += x
        return count

    def passes(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            count += self.grow(xs, x)
        return count

    def grow(self, xs: list[int], x: int) -> int:
        if x < 3:
            xs.append(x + 10)
        return 1

    def grows_items(self) -> int:
        count = 0
        for x in self.items:
            if x < 3:
                self.items.append(x + 10)
            count += 1
        return count

    def pairs(self, xs: list[int]) -> int:
        count = 0
        for x in xs:
            for y in xs:
                if x < y:
                    count += 1
        return count
'''

    void "compiled loops and handlers agree with the Python bodies, and the constructs that cannot are refused"() {
        given:
        Map results = [:]
        Map<String, StaticCompilationDecision> compiled = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            def context = buildContext(SOURCE)
            try {
                def loops = getBean(context, 'python.Loops')
                results[m] = [
                    reassigned      : invoke { loops.reassigned(5) },
                    skipped         : invoke { loops.skipped(4) },
                    raises_checked  : invoke { loops.raises_checked(3) },
                    catches_unthrown: invoke { loops.catches_unthrown(3) },
                    declared_good   : invoke { loops.catches_declared('http://example.com/x') },
                    declared_bad    : invoke { loops.catches_declared('not a url') },
                    caught          : invoke { loops.catches_caught('not a url') },
                    step_ok         : invoke { loops.catches_step(5, 2) },
                    step_zero       : invoke { loops.catches_step(5, 0) },
                    division        : invoke { loops.catches_division(7, 0) },
                    runtime         : invoke { loops.catches_runtime(1) },
                    raised          : invoke { loops.catches_raised(2) },
                    raised_zero     : invoke { loops.catches_raised(0) },
                    floats          : invoke { loops.floats(new ArrayList<>([true, 0.5d, 2])) },
                    appends         : invoke { loops.appends(new ArrayList<>([1, 2])) },
                    run_appends     : invoke { loops.run_appends() },
                    clears          : invoke { loops.clears(new ArrayList<>([1, 2])) },
                    stores          : invoke { loops.stores(new ArrayList<>([1, 2])) },
                    deletes         : invoke { loops.deletes(new ArrayList<>([1, 2, 3])) },
                    augments        : invoke { loops.augments(new ArrayList<>([1, 2])) },
                    aliases         : invoke { loops.aliases(new ArrayList<>([1, 2])) },
                    passes          : invoke { loops.passes(new ArrayList<>([1, 2])) },
                    grows_items     : invoke { loops.grows_items() },
                    pairs           : invoke { loops.pairs(new ArrayList<>([1, 2, 3])) },
                ]
                if (m == StaticCompilationMode.ALL) {
                    decisions.each { compiled[it.qualifiedName()] = it }
                }
            } finally {
                context.close()
            }
        }

        expect: "the same results compiled and interpreted"
        results[StaticCompilationMode.ALL] == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF].reassigned == 60
        results[StaticCompilationMode.OFF].skipped == '0,2,3,'
        results[StaticCompilationMode.OFF].declared_good == 'example.com'
        results[StaticCompilationMode.OFF].declared_bad == 'io'
        results[StaticCompilationMode.OFF].caught == 'inner'
        results[StaticCompilationMode.OFF].step_ok == 6
        results[StaticCompilationMode.OFF].step_zero == 'raised'  // Python raises ValueError, which no handler catches
        results[StaticCompilationMode.OFF].division == 'raised'   // Python raises ZeroDivisionError
        results[StaticCompilationMode.OFF].raised_zero == -1
        results[StaticCompilationMode.OFF].floats == 3.5d
        results[StaticCompilationMode.OFF].run_appends == 4      // Python goes on over the elements the body appends
        results[StaticCompilationMode.OFF].grows_items == 4
        results[StaticCompilationMode.OFF].pairs == 3

        and: "a range loop assigning its variable, a handler of a checked exception a Java call declares, and a loop unboxing elements are compiled"
        ['reassigned', 'skipped', 'catches_declared', 'ints', 'floats'].every { outcome(compiled, it) == StaticCompilationDecision.Outcome.COMPILED }

        and: "javac's checked exception rules refuse the others"
        rule(compiled, 'raises_checked') == 'unsupported-statement'
        rule(compiled, 'catches_unthrown') == 'unsupported-statement'
        rule(compiled, 'catches_caught') == 'unsupported-statement'

        and: "a handler that would catch what a compiled body throws where Python raises its own exception is refused"
        rule(compiled, 'catches_step') == 'python-exception'
        rule(compiled, 'catches_division') == 'python-exception'
        rule(compiled, 'catches_runtime') == 'python-exception'
        outcome(compiled, 'catches_raised') == StaticCompilationDecision.Outcome.COMPILED

        and: "a loop whose body may change the collection it iterates is refused: the Java iterator would throw ConcurrentModificationException"
        def changing = ['appends', 'clears', 'stores', 'deletes', 'augments', 'aliases', 'passes', 'grows_items']
        changing.collectEntries { [it, rule(compiled, it)] } == changing.collectEntries { [it, 'unsupported-statement'] }
        // the planner refuses a del statement before the loop is lowered
        (changing - 'deletes').findAll { !compiled["Loops.$it".toString()].reasons()[0].message().contains('ConcurrentModificationException') } == []

        and: "a loop that only reads it, in a nested loop and a comparison, is compiled"
        outcome(compiled, 'pairs') == StaticCompilationDecision.Outcome.COMPILED
    }

    void "a float element of a list hinted list[int] fails the compiled body rather than being truncated"() {
        given:
        mode = StaticCompilationMode.ALL
        def context = buildContext(SOURCE)
        def loops = getBean(context, 'python.Loops')

        expect:
        decisions.find { it.qualifiedName() == 'Loops.ints' }.outcome() == StaticCompilationDecision.Outcome.COMPILED
        loops.ints(new ArrayList<>([1, 2L])) == '1,2,'

        when:
        loops.ints(new ArrayList<>([1, 1.5d]))

        then:
        thrown(ClassCastException)

        when: "an int that does not fit a long"
        loops.ints(new ArrayList<>([new BigInteger('99999999999999999999')]))

        then:
        thrown(ArithmeticException)

        cleanup:
        context?.close()
    }

    private static Object invoke(Closure<?> call) {
        try {
            return call()
        } catch (Exception ignored) {
            return 'raised'
        }
    }

    private static StaticCompilationDecision.Outcome outcome(Map<String, StaticCompilationDecision> decisions, String method) {
        return decisions["Loops.$method".toString()].outcome()
    }

    private static String rule(Map<String, StaticCompilationDecision> decisions, String method) {
        def decision = decisions["Loops.$method".toString()]
        assert decision.outcome() == StaticCompilationDecision.Outcome.SKIPPED, decision.toString()
        return decision.reasons()[0].rule()
    }
}
