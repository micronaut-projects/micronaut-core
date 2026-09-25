package io.micronaut.core;

import io.micronaut.core.util.ImmutableStringIntMap;
import io.micronaut.core.util.StringIntMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Compares the dense linear-scan layout with the open-addressed hash table layout under two
 * caller models:
 *
 * <ul>
 *     <li><b>identity</b> ({@code linearScanHit}/{@code Miss}, {@code hashTableHit}/{@code Miss},
 *     {@code immutableHit}/{@code Miss}) - the lookup key is the exact stored {@link String}
 *     instance, as when a caller passes back a name that is itself a compile-time literal
 *     (interned, hash already cached). {@link String#equals} takes its {@code ==} fast path
 *     here.</li>
 *     <li><b>fresh</b> ({@code linearScanFreshHit}/{@code Miss}, {@code hashTableFreshHit}/
 *     {@code Miss}) - the lookup key is a new {@link String} copy built from a {@code char[]} on
 *     every invocation, as when a caller parses or otherwise builds the name at runtime (not
 *     interned, hash not cached). Every fresh-model benchmark passes the new key to
 *     {@link Blackhole#consume} <em>before</em> the lookup, so it escapes and C2 cannot
 *     scalar-replace it away - without that, the key never truly leaves the compiled method and
 *     the JIT can eliminate its allocation entirely, understating the scan's cost (a real
 *     caller's key does escape: it is passed in from outside). A {@code freshCopyBaseline}
 *     benchmark does the same allocate-then-consume with no lookup at all, so
 *     {@code fresh* - freshCopyBaseline} isolates the lookup cost from the shared allocation
 *     profile; {@code -prof gc} confirmed the baseline and every fresh-model lookup variant
 *     allocate the same bytes per operation, so the subtraction leaves only the lookup's own
 *     cost. {@code freshCopyBaseline} itself is not part of either caller model above - it exists
 *     only to be subtracted out.</li>
 * </ul>
 *
 * Hits rotate over every stored key and misses rotate over several absent names, including ones
 * the same length as a stored name (a stored name with its last character changed), so no single
 * benchmark measures only a best or worst case.
 *
 * <p>Run with:</p>
 * <pre>{@code
 * ./gradlew :benchmarks:jmh -Pjmh.includes=io.micronaut.core.ImmutableStringIntMapBenchmark \
 *   -Pjmh.fork=3 -Pjmh.warmupIterations=2 -Pjmh.iterations=3 -Pjmh.warmupTime=1s -Pjmh.timeOnIteration=1s
 * }</pre>
 * <p>The {@code me.champeau.jmh} Gradle plugin runs each {@code @Benchmark} method with the mode,
 * units, fork and iteration counts above; it ignores {@link #main}'s {@link OptionsBuilder}
 * settings entirely. {@link #main} is only used when the benchmark jar is run standalone.</p>
 *
 * <p><b>Threshold decision rule:</b> for each size, average {@code Hit} and {@code Miss} within
 * each model, then average the identity and fresh models together, for the scan and for the
 * table. The threshold is the largest size where the scan's combined average is no worse than the
 * table's, for that size and every smaller one; a difference within the propagated error is a
 * tie, and a tie does not extend the threshold past it - only a size the scan clearly wins does.
 * Applying that walk from size 1 up: sizes 1-4 are clear scan wins, size 5 is a tie (so it does
 * not extend the threshold), and size 6 is the first clear table win. The paired difference
 * (scan minus table, ns/op, averaged over both caller models, with its propagated error) for
 * sizes 1-6:</p>
 * <table>
 *     <caption>Paired difference by size</caption>
 *     <tr><th>size</th><th>scan - table (ns)</th></tr>
 *     <tr><td>1</td><td>-0.711 &plusmn; 0.087 (scan faster)</td></tr>
 *     <tr><td>2</td><td>-0.685 &plusmn; 0.092 (scan faster)</td></tr>
 *     <tr><td>3</td><td>-1.422 &plusmn; 0.205 (scan faster)</td></tr>
 *     <tr><td>4</td><td>-0.497 &plusmn; 0.276 (scan faster)</td></tr>
 *     <tr><td>5</td><td>-0.171 &plusmn; 0.867 (tie)</td></tr>
 *     <tr><td>6</td><td>+1.442 &plusmn; 0.443 (table faster)</td></tr>
 * </table>
 * <p>Size 4 is the largest size the scan clearly wins (its difference exceeds its error), so
 * {@link io.micronaut.core.util.ImmutableStringIntMap}'s {@code LINEAR_SCAN_THRESHOLD} is 4.</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ImmutableStringIntMapBenchmark {
    private static final String[] NAMES = {
        "id", "name", "firstName", "lastName", "email", "age", "createdAt", "updatedAt",
        "street", "city", "zipCode", "country", "phoneNumber", "enabled", "roles", "version"
    };

    // This main() method (and its Mode/TimeUnit/fork/iteration settings) is only used when the
    // benchmark jar is run standalone; the me.champeau.jmh Gradle plugin used to run this
    // benchmark ignores it and reads mode/units from the @BenchmarkMode/@OutputTimeUnit
    // annotations above, and iterations/forks from -Pjmh.* Gradle properties instead.
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ImmutableStringIntMapBenchmark.class.getName() + ".*")
            .warmupIterations(3)
            .measurementIterations(5)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    // --- identity model: the stored key instance itself ---

    @Benchmark
    public int linearScanHit(S s) {
        return scan(s.keys, s.nextHitKey());
    }

    @Benchmark
    public int linearScanMiss(S s) {
        return scan(s.keys, s.nextMissKey());
    }

    @Benchmark
    public int hashTableHit(S s) {
        return s.table.get(s.nextHitKey(), -1);
    }

    @Benchmark
    public int hashTableMiss(S s) {
        return s.table.get(s.nextMissKey(), -1);
    }

    @Benchmark
    public int immutableHit(S s) {
        return s.immutable.get(s.nextHitKey(), -1);
    }

    @Benchmark
    public int immutableMiss(S s) {
        return s.immutable.get(s.nextMissKey(), -1);
    }

    // --- fresh model: a new String copy per invocation, hash not cached ---
    // (immutable* is not measured here; identity above already covers ImmutableStringIntMap
    // as shipped, and it uses the same get() as hashTable/linearScan, so its own fresh-model
    // numbers would not change the threshold decision.)
    //
    // Every variant below - including the baseline - allocates the key with `new String(chars)`
    // and immediately hands it to bh.consume() *before* using it. That makes the key escape the
    // compiled method, so C2 cannot scalar-replace the allocation away; skipping this step lets
    // the JIT eliminate the allocation in the lookup variants (which only use the key locally)
    // but not in a variant that returns it, silently comparing different allocation profiles.

    @Benchmark
    public void freshCopyBaseline(S s, Blackhole bh) {
        String k = new String(s.nextAnyChars());
        bh.consume(k);
    }

    @Benchmark
    public int linearScanFreshHit(S s, Blackhole bh) {
        String k = new String(s.nextHitChars());
        bh.consume(k);
        return scan(s.keys, k);
    }

    @Benchmark
    public int linearScanFreshMiss(S s, Blackhole bh) {
        String k = new String(s.nextMissChars());
        bh.consume(k);
        return scan(s.keys, k);
    }

    @Benchmark
    public int hashTableFreshHit(S s, Blackhole bh) {
        String k = new String(s.nextHitChars());
        bh.consume(k);
        return s.table.get(k, -1);
    }

    @Benchmark
    public int hashTableFreshMiss(S s, Blackhole bh) {
        String k = new String(s.nextMissChars());
        bh.consume(k);
        return s.table.get(k, -1);
    }

    private static int scan(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static char[] lastCharChanged(String s) {
        char[] chars = s.toCharArray();
        chars[chars.length - 1]++;
        return chars;
    }

    @State(Scope.Thread)
    public static class S {
        @Param({"1", "2", "3", "4", "5", "6", "7", "8", "10", "12", "16"})
        int size;

        String[] keys;
        char[][] hitChars;
        String[] missKeys;
        char[][] missChars;
        char[][] anyChars;
        ImmutableStringIntMap immutable;
        StringIntMap table;

        int hitCounter;
        int missCounter;
        int anyCounter;

        @Setup
        public void setUp() {
            keys = new String[size];
            System.arraycopy(NAMES, 0, keys, 0, size);
            immutable = ImmutableStringIntMap.of(keys, Function.identity());
            table = new StringIntMap(size);
            for (int i = 0; i < size; i++) {
                table.put(keys[i], i);
            }

            hitChars = new char[size][];
            for (int i = 0; i < size; i++) {
                hitChars[i] = keys[i].toCharArray();
            }

            // one same-length variant of every stored key (last character changed), plus one
            // different-length name, so misses aren't all the same shape
            missKeys = new String[size + 1];
            missChars = new char[size + 1][];
            for (int i = 0; i < size; i++) {
                missChars[i] = lastCharChanged(keys[i]);
                missKeys[i] = new String(missChars[i]);
            }
            missChars[size] = "nonexistent".toCharArray();
            missKeys[size] = new String(missChars[size]);

            anyChars = new char[hitChars.length + missChars.length][];
            System.arraycopy(hitChars, 0, anyChars, 0, hitChars.length);
            System.arraycopy(missChars, 0, anyChars, hitChars.length, missChars.length);
        }

        String nextHitKey() {
            int i = hitCounter;
            hitCounter = (i + 1) % keys.length;
            return keys[i];
        }

        char[] nextHitChars() {
            int i = hitCounter;
            hitCounter = (i + 1) % hitChars.length;
            return hitChars[i];
        }

        String nextMissKey() {
            int i = missCounter;
            missCounter = (i + 1) % missKeys.length;
            return missKeys[i];
        }

        char[] nextMissChars() {
            int i = missCounter;
            missCounter = (i + 1) % missChars.length;
            return missChars[i];
        }

        char[] nextAnyChars() {
            int i = anyCounter;
            anyCounter = (i + 1) % anyChars.length;
            return anyChars[i];
        }
    }
}
