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

    void "a method or attribute of another object hinted as a primitive that is None fails as a TypeError naming it and its hint"() {
        given:
        mode = StaticCompilationMode.ALL
        def context = buildContext('''
from jakarta.inject import Singleton

class Bag:
    def __init__(self, count: int):
        self.count = count

    def _size(self) -> int:
        if self.count < 0:
            return None
        return self.count

    @property
    def weight(self) -> float:
        if self.count < 0:
            return None
        return self.count * 1.5

@Singleton
class Packer:
    def size_of(self, n: int) -> int:
        return Bag(n)._size() + 1

    def weight_of(self, n: int) -> float:
        return Bag(n).weight * 2.0
''')
        def packer = getBean(context, 'python.Packer')
        Map<String, StaticCompilationDecision> compiled = decisions.collectEntries { [it.qualifiedName(), it] }

        expect: "the callers are compiled and the hints are trusted for a value that is not None"
        compiled['Packer.size_of'].outcome() == StaticCompilationDecision.Outcome.COMPILED
        compiled['Packer.weight_of'].outcome() == StaticCompilationDecision.Outcome.COMPILED
        packer.size_of(3) == 4
        packer.weight_of(2) == 6.0d

        when:
        packer.size_of(-1)

        then:
        IllegalStateException e = thrown()
        e.message == 'TypeError: Bag._size() returned None but is hinted to return int'

        when:
        packer.weight_of(-1)

        then:
        IllegalStateException f = thrown()
        f.message == 'TypeError: Bag.weight is None but is hinted as float'

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

    void "the order pricing of the runtime benchmark calls price as Java code"() {
        given:
        mode = StaticCompilationMode.ALL
        def context = buildContext(PRICING)
        def pricing = getBean(context, 'python.PricingService')
        ClassLoader loader = pricing.getClass().getClassLoader()
        def line = { String sku, int quantity, int price -> loader.loadClass('python.OrderLine').newInstance(sku, quantity, price) }
        def order = loader.loadClass('python.OrderRequest').newInstance('acme', 'EUR', 'DE', [line("BK-1", 10, 250), line("", 1, 1), line("EL-2", 1, 1000)])
        Map<String, StaticCompilationDecision> compiled = decisions.collectEntries { [it.qualifiedName(), it] }
        PythonStatic.resetEntries()

        when:
        def quote = pricing.quote(order)

        then: "quote and price are compiled, and quote reaches price through its shared entry, not Python"
        ['PricingService.quote', 'PricingService.price'].every { compiled[it].outcome() == StaticCompilationDecision.Outcome.COMPILED }
        PythonStatic.entries('python.PricingService#quote') == 1
        PythonStatic.entries('python.PricingService#price') == 1
        quote.summary == 'acme: 2 lines, 3748 EUR'
        quote.rejected == ['']

        cleanup:
        context?.close()
    }

    // the services of the runtime benchmark (perf python-static/python-app), introspected in place of serde, without the Java exception
    static final String PRICING = '''
from dataclasses import dataclass, field
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected


class InvalidLine(Exception):
    pass


@Introspected
@dataclass
class OrderLine:
    sku: str = ""
    quantity: int = 0
    unitPriceCents: int = 0


@Introspected
@dataclass
class OrderRequest:
    customer: str = ""
    currency: str = ""
    country: str = ""
    lines: list[OrderLine] = field(default_factory=list)


@Introspected
@dataclass
class LineQuote:
    sku: str
    quantity: int
    unitPriceCents: int
    discountPercent: int
    totalCents: int


@Introspected
@dataclass
class OrderQuote:
    customer: str
    currency: str
    lines: list[LineQuote]
    rejected: list[str]
    subtotalCents: int
    discountCents: int
    taxCents: int
    totalCents: int
    summary: str


@Singleton
class LineValidator:

    def check(self, line: OrderLine) -> None:
        if len(line.sku) == 0:
            raise InvalidLine("empty sku")
        if line.quantity <= 0:
            raise InvalidLine("quantity must be positive: " + line.sku)
        if line.unitPriceCents < 0:
            raise InvalidLine("negative price: " + line.sku)


@Singleton
class PricingService:

    def __init__(self, validator: LineValidator):
        self.validator = validator

    def discount_percent(self, sku: str, quantity: int) -> int:
        percent = 0
        if sku.startswith("BK"):
            percent = 5
        elif sku.startswith("EL"):
            percent = 10
        elif sku.startswith("TO"):
            percent = 15
        if quantity >= 10:
            percent += 5
        return percent

    def tax_per_mille(self, country: str) -> int:
        if country == "DE":
            return 190
        if country == "FR":
            return 200
        if country == "GB":
            return 200
        if country == "US":
            return 0
        return 210

    def quote(self, order: OrderRequest) -> OrderQuote:
        valid: list[OrderLine] = []
        rejected: list[str] = []
        for line in order.lines:
            try:
                self.validator.check(line)
            except InvalidLine:
                rejected.append(line.sku)
                continue
            valid.append(line)
        return self.price(order, valid, rejected)

    def price(self, order: OrderRequest, valid: list[OrderLine], rejected: list[str]) -> OrderQuote:
        quotes: list[LineQuote] = []
        subtotal = 0
        discount = 0
        for line in valid:
            gross = line.quantity * line.unitPriceCents
            percent = self.discount_percent(line.sku, line.quantity)
            off = gross * percent // 100
            subtotal += gross
            discount += off
            quotes.append(LineQuote(line.sku, line.quantity, line.unitPriceCents, percent, gross - off))
        net = subtotal - discount
        tax = net * self.tax_per_mille(order.country) // 1000
        total = net + tax
        summary = f"{order.customer}: {len(quotes)} lines, {total} {order.currency}"
        return OrderQuote(order.customer, order.currency, quotes, rejected, subtotal, discount, tax, total, summary)
'''

    private static Object invoke(Closure<?> call) {
        try {
            return call()
        } catch (Exception ignored) {
            return 'raised'
        }
    }
}
