package io.micronaut.core.convert

import org.jspecify.annotations.Nullable
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier

/**
 * {@link ConversionService#SHARED} is a {@link DefaultMutableConversionService}, which implements
 * {@link ConversionService}, so the class initialization of each one needs the other.
 * Every feature uses fresh class loaders, in which neither class is initialized yet.
 *
 * @see <a href="https://github.com/micronaut-projects/micronaut-core/issues/13392">#13392</a>
 */
class ConversionServiceInitializationSpec extends Specification {

    private static final int ITERATIONS = 100
    private static final long JOIN_TIMEOUT_MILLIS = 10_000

    void "MutableConversionService.create() and a concurrent read of ConversionService.SHARED do not deadlock"() {
        given:
        List<String> hung = []
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>()

        when:
        for (int i = 0; i < ITERATIONS && hung.empty; i++) {
            URLClassLoader loader = isolatedClassLoader()
            try {
                // Load both classes without initializing them, so that both threads start initializing at the barrier
                Method create = loader.loadClass(MutableConversionService.name).getMethod("create")
                Field shared = loader.loadClass(ConversionService.name).getField("SHARED")
                CyclicBarrier barrier = new CyclicBarrier(2)
                Thread createThread = startDaemon("create-$i", loader, barrier, errors) { create.invoke(null) }
                Thread sharedThread = startDaemon("shared-$i", loader, barrier, errors) { shared.get(null) }
                createThread.join(JOIN_TIMEOUT_MILLIS)
                sharedThread.join(JOIN_TIMEOUT_MILLIS)
                for (Thread thread : [createThread, sharedThread]) {
                    if (thread.alive) {
                        hung << "${thread.name} at ${thread.stackTrace.take(3).join(' <- ')}".toString()
                    }
                }
            } finally {
                loader.close()
            }
        }

        then:
        hung.empty
        errors.empty
    }

    void "a type converter registrar can look up an unregistered pair when MutableConversionService.create() initializes the conversion classes"() {
        given:
        URLClassLoader loader = isolatedClassLoader(
            location(UnregisteredPairLookupRegistrar),
            getClass().getResource("/conversion-service-initialization/")
        )
        ClassLoader contextClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader

        when:
        Object created = invokeStatic(loader.loadClass(MutableConversionService.name).getMethod("create"))
        Object shared = loader.loadClass(ConversionService.name).getField("SHARED").get(null)

        then:
        created.getClass().name == DefaultMutableConversionService.name
        shared.getClass().name == DefaultMutableConversionService.name
        !created.is(shared)

        and: 'the registrar ran for both conversion services'
        loader.loadClass(UnregisteredPairLookupRegistrar.name).getField("REGISTRATIONS").get(null).get() == 2

        cleanup:
        Thread.currentThread().contextClassLoader = contextClassLoader
        loader.close()
    }

    /**
     * A class loader that holds only micronaut-core and its runtime dependencies, and the given locations.
     * It must also be the context class loader of the code that uses it, because {@code StaticOptimizations}
     * loads its services from the context class loader.
     */
    private static URLClassLoader isolatedClassLoader(URL... additionalLocations) {
        List<URL> locations = [location(ConversionService), location(LoggerFactory), location(Nullable)]
        locations.addAll(additionalLocations)
        return new URLClassLoader(locations as URL[], ClassLoader.platformClassLoader)
    }

    private static URL location(Class<?> type) {
        return type.protectionDomain.codeSource.location
    }

    private static Object invokeStatic(Method method) {
        try {
            return method.invoke(null)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    private static Thread startDaemon(String name, ClassLoader contextClassLoader, CyclicBarrier barrier, Queue<Throwable> errors, Closure<?> action) {
        Thread thread = new Thread({
            try {
                barrier.await()
                action.call()
            } catch (Throwable t) {
                errors << t
            }
        }, name)
        thread.contextClassLoader = contextClassLoader
        thread.daemon = true
        thread.start()
        return thread
    }
}
