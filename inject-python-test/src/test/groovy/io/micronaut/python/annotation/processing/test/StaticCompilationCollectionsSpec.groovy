package io.micronaut.python.annotation.processing.test

import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision
import io.micronaut.python.processing.staticcompile.StaticCompilationMode

/**
 * Collections and conversions of compiled bodies against Java callers: the boxing of int type
 * arguments, None among tuples, Python's numeric equality in membership tests, and the literals
 * int() and float() parse.
 */
class StaticCompilationCollectionsSpec extends AbstractPythonTypeElementSpec {

    StaticCompilationMode mode = StaticCompilationMode.OFF
    List<StaticCompilationDecision> decisions = []

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        compilerBuilder.staticCompilation(mode).staticCompilationDecisionCallback { decisions << it }
    }

    static final String SOURCE = '''
from jakarta.inject import Singleton
from io.micronaut.python.annotation.processing.test import LongCollections
from java.lang import Long

@Singleton
class Bag:
    def looked_up(self, names: dict[int, str]) -> str:
        return names[1]

    def found(self, values: list[int], n: int) -> bool:
        return n in values

    def keyed(self, names: dict[int, str]) -> bool:
        return 2 in names

    def grown(self, values: list[int]) -> list[int]:
        values.append(3)
        values.append(5000000000)
        return values

    def indexed(self, n: int) -> dict[int, str]:
        built: dict[int, str] = {}
        for i in range(n):
            built[i] = str(i)
        return built

    def named(self, name: str | None) -> bool:
        return name in ("a", "b")

    def paired(self, first: str | None, second: str) -> int:
        pair = (first, second)
        return len(pair)

    def mixed(self, n: int) -> bool:
        return n in [1.0, "a"]

    def among(self, values: list, n: int) -> bool:
        return n in values

    def long_found(self, n: int) -> bool:
        return n in LongCollections.ids()

    def long_named(self, n: int) -> str:
        return LongCollections.names()[n]

    def long_summed(self) -> int:
        total = 0
        for i in LongCollections.ids():
            total += i
        return total

    def long_hinted(self, ids: list[Long], n: int) -> bool:
        return n in ids

    def long_hinted_sum(self, ids: list[Long]) -> int:
        total = 0
        for i in ids:
            total += i
        return total

    def parsed_int(self, text: str) -> int:
        return int(text)

    def parsed_float(self, text: str) -> float:
        return float(text)

    def flagged(self) -> str:
        flags: dict[int, str] = {1: "one"}
        return flags[True]

    def objects(self, n: int) -> str:
        names = {1.0: "a", "b": "c"}
        return names[n]

    def stored(self) -> int:
        counts = {"x": 0, 2.5: 0}
        counts[1] = 1
        counts[1.0] = 2
        return len(counts)

    def got(self, n: int) -> str:
        names: dict[float, str] = {1.0: "a"}
        return names.get(n, "none")

    def added(self) -> int:
        seen: set[int] = {1}
        seen.add(True)
        return len(seen)

    def merged(self) -> int:
        return len({1, 1.0, True})

    def literal_keys(self) -> int:
        return len({1: "a", True: "b"})

    def deduped(self, values: list[object]) -> int:
        return len(set(values))

    def bool_member(self) -> bool:
        flags: set[bool] = {True}
        return 1 in flags

    def str_keyed(self, names: dict[str, int], key: str) -> int:
        names[key] = 1
        return names[key] + names.get("b", 0)
'''

    static final List<List> CASES = [
        ["looked_up", [1: "a", 2: "b"]],
        ["found", [1, 2], 1], ["found", [1, 2], 3],
        ["keyed", [1: "a", 2: "b"]], ["keyed", [1: "a"]],
        ["indexed", 3],
        ["named", null], ["named", "a"], ["named", "c"],
        ["paired", null, "b"], ["paired", "a", "b"],
        ["mixed", 1], ["mixed", 2],
        ["among", [1.0d, "a"], 1],
        ["long_found", 2], ["long_named", 1], ["long_summed"],
        ["parsed_int", "1_000"], ["parsed_int", " -42 "], ["parsed_int", "1__0"], ["parsed_int", "_1"],
        ["parsed_int", "99999999999999999999"], ["parsed_int", "1.5"],
        ["parsed_float", "1_0.5"], ["parsed_float", "1e1_0"], ["parsed_float", ".5"], ["parsed_float", "1."],
        ["parsed_float", "+infinity"], ["parsed_float", "1d"], ["parsed_float", "2f"], ["parsed_float", "0x1p3"],
        ["parsed_float", "1_"],
        ["flagged"], ["objects", 1], ["stored"], ["got", 1], ["added"], ["merged"], ["literal_keys"],
        ["deduped", [1, 1.0d]], ["bool_member"], ["str_keyed", [b: 2], "a"],
    ]

    void "collections and conversions agree with the Python bodies"() {
        given:
        Map results = [:]
        Map<String, StaticCompilationDecision> compiled = [:]
        [StaticCompilationMode.OFF, StaticCompilationMode.ALL].each { StaticCompilationMode m ->
            mode = m
            decisions.clear()
            def context = buildContext(SOURCE)
            try {
                def bag = getBean(context, 'python.Bag')
                results[m] = CASES.collectEntries { List row -> [(row.toString()): invoke(bag, row)] }
                // a Java caller's List<Integer>, whose new elements must be Integers where they fit
                List<Integer> given = new ArrayList<>([1, 2])
                List<?> grown = bag.grown(given)
                results[m]['grown'] = grown
                results[m]['grown classes'] = grown*.getClass()*.simpleName
                results[m]['given'] = given
                Map<?, ?> indexed = bag.indexed(2)
                results[m]['indexed classes'] = indexed.keySet()*.getClass()*.simpleName
                results[m]['immutable lookup'] = bag.looked_up(Map.of(1, 'x'))
                results[m]['immutable found'] = bag.found(List.of(7, 8), 8)
                results[m]['long hinted'] = bag.long_hinted(new ArrayList<Long>([1L, 2L]), 2)
                results[m]['long hinted sum'] = bag.long_hinted_sum(new ArrayList<Long>([1L, 2L]))
                if (m == StaticCompilationMode.ALL) {
                    decisions.each { compiled[it.qualifiedName()] = it }
                }
            } finally {
                context.close()
            }
        }

        expect:
        results[StaticCompilationMode.ALL] == results[StaticCompilationMode.OFF]
        results[StaticCompilationMode.OFF][["looked_up", [1: "a", 2: "b"]].toString()] == 'a'
        results[StaticCompilationMode.OFF][["found", [1, 2], 1].toString()] == true
        results[StaticCompilationMode.OFF][["keyed", [1: "a", 2: "b"]].toString()] == true
        results[StaticCompilationMode.OFF][["named", null].toString()] == false
        results[StaticCompilationMode.OFF][["paired", null, "b"].toString()] == 2
        results[StaticCompilationMode.OFF][["mixed", 1].toString()] == true
        results[StaticCompilationMode.OFF][["among", [1.0d, "a"], 1].toString()] == true
        results[StaticCompilationMode.OFF][["long_found", 2].toString()] == true
        results[StaticCompilationMode.OFF][["long_summed"].toString()] == 6
        results[StaticCompilationMode.OFF][["parsed_int", "1_000"].toString()] == 1000
        results[StaticCompilationMode.OFF][["parsed_int", "1__0"].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["parsed_float", "1_0.5"].toString()] == 10.5d
        results[StaticCompilationMode.OFF][["parsed_float", "+infinity"].toString()] == Double.POSITIVE_INFINITY
        results[StaticCompilationMode.OFF][["parsed_float", "1d"].toString()] == 'raised'
        results[StaticCompilationMode.OFF][["parsed_float", "0x1p3"].toString()] == 'raised'
        results[StaticCompilationMode.OFF]['grown classes'] == ['Integer', 'Integer', 'Integer', 'Long']
        results[StaticCompilationMode.OFF]['given'] == [1, 2]
        results[StaticCompilationMode.OFF]['indexed classes'] == ['Integer', 'Integer']
        // Python holds 1.0 == 1 == True in its dicts and sets
        results[StaticCompilationMode.OFF][["flagged"].toString()] == 'one'
        results[StaticCompilationMode.OFF][["objects", 1].toString()] == 'a'
        results[StaticCompilationMode.OFF][["stored"].toString()] == 3
        results[StaticCompilationMode.OFF][["got", 1].toString()] == 'a'
        results[StaticCompilationMode.OFF][["added"].toString()] == 1
        results[StaticCompilationMode.OFF][["merged"].toString()] == 1
        results[StaticCompilationMode.OFF][["literal_keys"].toString()] == 1
        results[StaticCompilationMode.OFF][["deduped", [1, 1.0d]].toString()] == 1
        results[StaticCompilationMode.OFF][["bool_member"].toString()] == true
        results[StaticCompilationMode.OFF][["str_keyed", [b: 2], "a"].toString()] == 3

        and: "the bodies compile, except the membership tests Python's numeric equality decides"
        ['looked_up', 'found', 'keyed', 'grown', 'indexed', 'named', 'paired', 'parsed_int', 'parsed_float', 'mixed', 'among'].collectEntries {
            def decision = compiled["Bag.$it".toString()]
            [(it): decision.outcome() == StaticCompilationDecision.Outcome.COMPILED ? 'compiled' : decision.reasons()*.rule()]
        } == [looked_up: 'compiled', found: 'compiled', keyed: 'compiled', grown: 'compiled', indexed: 'compiled', named: 'compiled',
              paired: 'compiled', parsed_int: 'compiled', parsed_float: 'compiled', mixed: ['unsupported-expression'], among: ['unsupported-expression']]

        and: "a key or element Python's numeric equality decides is refused in lookups, stores, additions, literals and set()"
        def numeric = ['flagged', 'objects', 'stored', 'got', 'added', 'merged', 'literal_keys', 'deduped', 'bool_member']
        numeric.collectEntries { [(it): compiled["Bag.$it".toString()].reasons()*.rule()] } == numeric.collectEntries { [(it): ['unsupported-expression']] }
        numeric.findAll { !compiled["Bag.$it".toString()].reasons()[0].message().contains('Python compares numbers across their types') } == []
        compiled['Bag.str_keyed'].outcome() == StaticCompilationDecision.Outcome.COMPILED

        and: "a lookup in a Java collection of Longs, which a compiled body would box an Integer for, stays in Python"
        compiled['Bag.long_found'].outcome() == StaticCompilationDecision.Outcome.SKIPPED
        compiled['Bag.long_named'].outcome() == StaticCompilationDecision.Outcome.SKIPPED
        results[StaticCompilationMode.OFF]['long hinted'] == true
        results[StaticCompilationMode.OFF]['long hinted sum'] == 3
        compiled['Bag.long_hinted'].reasons()*.rule() == ['unsupported-expression']
        compiled['Bag.long_hinted_sum'].outcome() == StaticCompilationDecision.Outcome.COMPILED
    }

    private static Object invoke(Object bean, List row) {
        def arguments = row.tail().collect { it instanceof Map ? new LinkedHashMap<>(it) : it instanceof List ? new ArrayList<>(it) : it }
        try {
            return bean."${row[0]}"(*arguments)
        } catch (Exception ignored) {
            return 'raised'
        }
    }
}
