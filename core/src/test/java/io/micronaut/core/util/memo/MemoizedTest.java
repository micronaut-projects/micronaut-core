package io.micronaut.core.util.memo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoizedTest {
    @Test
    public void referenceStored() {
        MemoizedReference<MyClass, String> ref = MyClass.NS.newReference(m -> {
            m.getCount++;
            return m.foo;
        });

        MyClass cl = new MyClass("bar");
        assertEquals(0, cl.getCount);
        assertEquals("bar", ref.get(cl));
        assertEquals(1, cl.getCount);
        assertEquals("bar", ref.get(cl));
        assertEquals(1, cl.getCount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void flagStored(boolean value) {
        MemoizedFlag<MyClass> flag = MyClass.NS.newFlag(m -> {
            m.getCount++;
            return value;
        });

        MyClass cl = new MyClass("bar");
        assertEquals(0, cl.getCount);
        assertEquals(value, cl.getMemoized(flag));
        assertEquals(1, cl.getCount);
        assertEquals(value, cl.getMemoized(flag));
        assertEquals(1, cl.getCount);
    }

    @Test
    public void manyFlagsStored() {
        class Cl extends AbstractMemoizer<Cl> {
            // isolated namespace for each instance
            final MemoizerNamespace<Cl> ns = MemoizerNamespace.create();

            @Override
            protected MemoizerNamespace<Cl> getMemoizerNamespace() {
                return ns;
            }
        }

        Cl cl = new Cl();
        List<MemoizedFlag<Cl>> flags = new ArrayList<>();
        BitSet set = new BitSet();
        List<Integer> getCounts = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            set.set(i, ThreadLocalRandom.current().nextBoolean());
            getCounts.add(0);
            int index = i;
            flags.add(cl.ns.newFlag(c -> {
                getCounts.set(index, getCounts.get(index) + 1);
                return set.get(index);
            }));
        }

        assertTrue(getCounts.stream().allMatch(i -> i == 0));
        for (int i = 0; i < flags.size(); i++) {
            assertEquals(set.get(i), cl.getMemoized(flags.get(i)));
        }
        assertTrue(getCounts.stream().allMatch(i -> i == 1));
        for (int i = 0; i < flags.size(); i++) {
            assertEquals(set.get(i), cl.getMemoized(flags.get(i)));
        }
        assertTrue(getCounts.stream().allMatch(i -> i == 1));
    }

    static class MyClass extends AbstractMemoizer<MyClass> {
        static final MemoizerNamespace<MyClass> NS = MemoizerNamespace.create();

        final String foo;
        int getCount;

        MyClass(String foo) {
            this.foo = foo;
        }

        @Override
        protected MemoizerNamespace<MyClass> getMemoizerNamespace() {
            return NS;
        }
    }
}
