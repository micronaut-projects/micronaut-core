package io.micronaut.context;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanInitializationFailureTest {

    @Test
    void aDetachedFailureReadsAsTheOriginal() {
        IllegalStateException cause = new IllegalStateException("the cause");
        NoClassDefFoundError failure = new NoClassDefFoundError("a/Missing");
        failure.initCause(cause);
        failure.addSuppressed(new IllegalArgumentException("suppressed"));

        Throwable detached = BeanInitializationFailure.detach(failure);

        assertEquals("a/Missing", detached.getMessage());
        assertEquals("java.lang.NoClassDefFoundError: a/Missing", detached.toString());
        assertEquals(failure.getStackTrace().length, detached.getStackTrace().length);
        assertEquals(failure.getStackTrace()[0], detached.getStackTrace()[0]);
        assertEquals("java.lang.IllegalStateException: the cause", detached.getCause().toString());
        assertEquals(cause.getStackTrace()[0], detached.getCause().getStackTrace()[0]);
        assertEquals("java.lang.IllegalArgumentException: suppressed", detached.getSuppressed()[0].toString());
    }

    @Test
    void aFailureWithoutAMessageOrCauseIsDetached() {
        Throwable detached = BeanInitializationFailure.detach(new IllegalStateException());

        assertNull(detached.getMessage());
        assertNull(detached.getCause());
        assertEquals("java.lang.IllegalStateException", detached.toString());
    }

    @Test
    void aFailureThatRefersToItselfIsDetached() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second", first);
        first.initCause(second);
        first.addSuppressed(second);

        Throwable detached = BeanInitializationFailure.detach(first);

        assertEquals("second", detached.getCause().getMessage());
        assertNull(detached.getCause().getCause());
        assertEquals(0, detached.getSuppressed().length);
    }

    @Test
    void aFailureThatCannotBeReadLeavesItsType() {
        Throwable hostile = new IllegalStateException() {
            @Override
            public String getMessage() {
                throw new UnsupportedOperationException("no message");
            }
        };

        Throwable detached = BeanInitializationFailure.detach(hostile);

        assertEquals(hostile.getClass().getName(), detached.toString());
        assertNull(detached.getCause());
    }

    @Test
    void aChainOfCausesTooDeepToCopyLeavesItsType() {
        Throwable deep = new IllegalStateException("bottom");
        for (int i = 0; i < 200_000; i++) {
            deep = new Chained(deep);
        }

        Throwable detached = BeanInitializationFailure.detach(deep);

        assertEquals(Chained.class.getName(), detached.toString());
        assertNull(detached.getCause());
    }

    @Test
    void theOriginalFailureKeepsTheLoaderOfItsFramesAndTheDetachedOneDoesNot() throws Exception {
        // what the raw failure does, which is what makes the second half of this test mean something
        Failed raw = fail();
        collect(raw.loader);
        assertNotNull(raw.loader.get(), "the backtrace of a failure keeps the loader of its frames");
        assertTrue(raw.failure.getStackTrace().length > 0);

        Failed failed = fail();
        Throwable detached = BeanInitializationFailure.detach(failed.failure);
        failed.failure = null;
        collect(failed.loader);

        assertNull(failed.loader.get(), "the detached failure keeps nothing of the classes of its frames");
        assertEquals("from a retired loader", detached.getMessage());
        assertTrue(detached.getStackTrace()[0].getClassName().endsWith("Thrower"));
    }

    private static final class Chained extends Throwable {
        Chained(Throwable cause) {
            super(null, cause, false, false);
        }
    }

    private static void collect(WeakReference<ClassLoader> loader) throws InterruptedException {
        for (int i = 0; i < 50 && loader.get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
    }

    private static Failed fail() throws Exception {
        ClassLoader loader = new ThrowerLoader();
        Class<?> thrower = loader.loadClass(Thrower.class.getName());
        try {
            thrower.getMethod("fail").invoke(null);
            throw new AssertionError("expected a failure");
        } catch (InvocationTargetException e) {
            return new Failed(new WeakReference<>(loader), e.getCause());
        }
    }

    private static final class Failed {
        final WeakReference<ClassLoader> loader;
        Throwable failure;

        Failed(WeakReference<ClassLoader> loader, Throwable failure) {
            this.loader = loader;
            this.failure = failure;
        }
    }

    /**
     * Fails from a class of its own loader, as an application does from a generation's.
     */
    public static final class Thrower {
        public static void fail() {
            throw new IllegalStateException("from a retired loader");
        }
    }

    private static final class ThrowerLoader extends ClassLoader {
        ThrowerLoader() {
            super(BeanInitializationFailureTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(Thrower.class.getName())) {
                return super.loadClass(name, resolve);
            }
            try (InputStream bytes = getResourceAsStream(name.replace('.', '/') + ".class")) {
                byte[] code = bytes.readAllBytes();
                return defineClass(name, code, 0, code.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
