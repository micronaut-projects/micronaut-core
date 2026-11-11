package io.micronaut.context.resolution;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runtime bean lookups against a started context with the whole benchmark classpath's definitions.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BeanResolutionBenchmark {
    private static final Argument<Fixtures.Repo<String>> STRING_REPO = (Argument) Argument.of(Fixtures.Repo.class, String.class);

    ApplicationContext ctx;
    Fixtures.ProviderHolder providers;
    int next;

    @Setup
    public void setup() {
        ctx = ApplicationContext.run(Map.of("spec.name", Fixtures.SPEC));
        providers = ctx.getBean(Fixtures.ProviderHolder.class);
        // populate the singletons
        ctx.getBean(Fixtures.SingletonA.class);
        ctx.getBean(Fixtures.Service.class);
        ctx.getBean(Fixtures.Greeter.class, Qualifiers.byName("english"));
        ctx.getBean(STRING_REPO);
    }

    @TearDown
    public void tearDown() {
        ctx.close();
    }

    @Benchmark
    public Object singletonByClass() {
        return ctx.getBean(Fixtures.SingletonA.class);
    }

    @Benchmark
    public Object singletonByInterface() {
        return ctx.getBean(Fixtures.Service.class);
    }

    @Benchmark
    public Object singletonByName() {
        return ctx.getBean(Fixtures.Greeter.class, Qualifiers.byName("english"));
    }

    @Benchmark
    public Object singletonGeneric() {
        return ctx.getBean(STRING_REPO);
    }

    @Benchmark
    public Object prototypeNoDeps() {
        return ctx.getBean(Fixtures.PrototypeNoDeps.class);
    }

    @Benchmark
    public Object prototypeWithDeps() {
        return ctx.getBean(Fixtures.PrototypeWithDeps.class);
    }

    @Benchmark
    public Object prototypeChain() {
        return ctx.getBean(Fixtures.Chain1.class);
    }

    @Benchmark
    public Object providerPrototype() {
        return providers.prototypes.get();
    }

    @Benchmark
    public Object providerSingleton() {
        return providers.services.get();
    }

    @Benchmark
    public Object jakartaProviderSingleton() {
        return providers.jakartaServices.get();
    }

    @Benchmark
    public Collection<Fixtures.Greeter> beansOfType() {
        return ctx.getBeansOfType(Fixtures.Greeter.class);
    }

    @Benchmark
    public Optional<Fixtures.Missing> findMissing() {
        return ctx.findBean(Fixtures.Missing.class);
    }

    @Benchmark
    public boolean containsBean() {
        return ctx.containsBean(Fixtures.SingletonB.class);
    }

    @Benchmark
    public Object manyPrototypeTypes() {
        Class<?>[] types = ManyPrototypes.TYPES;
        int i = next;
        next = i + 1 == types.length ? 0 : i + 1;
        return ctx.getBean(types[i]);
    }
}
