/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.async.publisher;

import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.reflect.ClassUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the static initializer of {@link Publishers} in a class loader of its own, which loads Micronaut core again from
 * the test class path and defines stand-ins for candidate reactive types that are not on it.
 */
class PublishersReactiveTypesTest {

    private static final String OBSERVABLE = "io.reactivex.Observable";
    private static final String SINGLE = "io.reactivex.Single";
    private static final String COMPLETABLE = "io.reactivex.Completable";
    private static final List<String> STAND_INS = List.of(OBSERVABLE, SINGLE, COMPLETABLE);
    private static final String FLUX = "reactor.core.publisher.Flux";
    private static final String MONO = "reactor.core.publisher.Mono";

    @AfterEach
    void clearInitializations() {
        STAND_INS.forEach(name -> System.clearProperty(initializedProperty(name)));
    }

    @Test
    void candidateReactiveTypesAreLoadedWithoutBeingInitialized() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            Class<?> publishers = classLoader.initialize(Publishers.class.getName());

            assertEquals(List.of(), STAND_INS.stream().filter(PublishersReactiveTypesTest::initialized).toList());
            assertTrue(typeNames(publishers, "getKnownReactiveTypes").containsAll(List.of(OBSERVABLE, SINGLE, COMPLETABLE, FLUX, MONO)));
            assertTrue(typeNames(publishers, "getKnownSingleTypes").containsAll(List.of(SINGLE, MONO)));
            assertTrue(typeNames(publishers, "getKnownCompletableTypes").contains(COMPLETABLE));

            Class.forName(OBSERVABLE, true, classLoader);
            assertTrue(initialized(OBSERVABLE), "the stand-in records its initialization");
        }
    }

    @Test
    void knownMissingTypesAreNotLoaded() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            classLoader.setOptimization(ClassUtils.Optimizations.class.getName(), new Class<?>[] {Set.class}, Set.copyOf(STAND_INS));
            Class<?> publishers = classLoader.initialize(Publishers.class.getName());

            List<String> reactiveTypes = typeNames(publishers, "getKnownReactiveTypes");
            assertTrue(reactiveTypes.contains(FLUX));
            assertEquals(List.of(), STAND_INS.stream().filter(reactiveTypes::contains).toList());
            assertEquals(List.of(), STAND_INS.stream().filter(classLoader.requested::contains).toList());
        }
    }

    @Test
    void typesCanBeRegisteredWithOptimizations() throws Exception {
        // Micronaut AOT generates fixed-size lists
        List<Class<?>> reactiveTypes = Arrays.asList(Flux.class, Mono.class, Completable.class);
        List<Class<?>> singleTypes = Arrays.asList(Mono.class);
        List<Class<?>> completableTypes = Arrays.asList(Completable.class);
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            classLoader.setOptimization(PublishersOptimizations.class.getName(), new Class<?>[] {List.class, List.class, List.class}, reactiveTypes, singleTypes, completableTypes);
            Class<?> publishers = classLoader.initialize(Publishers.class.getName());

            assertTypesCanBeRegistered(publishers);
            assertTrue(isType(publishers, "isSingle", Mono.class));
            assertEquals(List.of(Flux.class, Mono.class, Completable.class), reactiveTypes);
            assertEquals(List.of(Mono.class), singleTypes);
            assertEquals(List.of(Completable.class), completableTypes);
        }
    }

    @Test
    void typesCanBeRegisteredWithoutOptimizations() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            assertTypesCanBeRegistered(classLoader.initialize(Publishers.class.getName()));
        }
    }

    private static void assertTypesCanBeRegistered(Class<?> publishers) throws ReflectiveOperationException {
        publishers.getMethod("registerReactiveType", Class.class).invoke(null, CustomReactive.class);
        publishers.getMethod("registerReactiveSingle", Class.class).invoke(null, CustomSingle.class);
        publishers.getMethod("registerReactiveCompletable", Class.class).invoke(null, CustomCompletable.class);

        assertTrue(typeNames(publishers, "getKnownReactiveTypes").containsAll(List.of(CustomReactive.class.getName(), CustomSingle.class.getName(), CustomCompletable.class.getName())));
        assertTrue(isType(publishers, "isConvertibleToPublisher", CustomReactive.class));
        assertTrue(isType(publishers, "isSingle", CustomSingle.class));
        assertTrue(isType(publishers, "isCompletable", CustomCompletable.class));
    }

    private static boolean isType(Class<?> publishers, String method, Class<?> type) throws ReflectiveOperationException {
        return (boolean) publishers.getMethod(method, Class.class).invoke(null, type);
    }

    @SuppressWarnings("unchecked")
    private static List<String> typeNames(Class<?> publishers, String method) throws ReflectiveOperationException {
        List<Class<?>> types = (List<Class<?>>) publishers.getMethod(method).invoke(null);
        return types.stream().map(Class::getName).toList();
    }

    private static boolean initialized(String name) {
        return System.getProperty(initializedProperty(name)) != null;
    }

    private static String initializedProperty(String name) {
        return "micronaut.test.initialized." + name;
    }

    /**
     * A class whose static initializer records that it ran in a system property.
     */
    private static byte[] standIn(String name) {
        return ClassFile.of().build(ClassDesc.of(name), type -> type
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(ConstantDescs.CD_Object)
            .withMethodBody(ConstantDescs.CLASS_INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_STATIC, code -> code
                .ldc(initializedProperty(name))
                .ldc("true")
                .invokestatic(ClassDesc.of(System.class.getName()), "setProperty", MethodTypeDesc.of(ConstantDescs.CD_String, ConstantDescs.CD_String, ConstantDescs.CD_String))
                .pop()
                .return_()));
    }

    private interface CustomReactive {
    }

    private interface CustomSingle {
    }

    private interface CustomCompletable {
    }

    /**
     * Loads Micronaut core again from the test class path, apart from the class loader of this test, defines the
     * stand-ins and records the names it is asked for. The static optimizations are service loaded through the context
     * class loader, so it is the context class loader while the classes it loads are initialized.
     */
    private static final class IsolatedClassLoader extends URLClassLoader {

        private final Set<String> requested = ConcurrentHashMap.newKeySet();

        IsolatedClassLoader() throws MalformedURLException {
            super(classPath(), ClassLoader.getPlatformClassLoader());
        }

        Class<?> initialize(String name) throws Exception {
            return withContextClassLoader(() -> Class.forName(name, true, this));
        }

        void setOptimization(String type, Class<?>[] parameterTypes, Object... arguments) throws Exception {
            withContextClassLoader(() -> {
                Object optimization = loadClass(type).getConstructor(parameterTypes).newInstance(arguments);
                return loadClass(StaticOptimizations.class.getName()).getMethod("set", Object.class).invoke(null, optimization);
            });
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requested.add(name);
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (STAND_INS.contains(name)) {
                byte[] bytes = standIn(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }

        private <T> T withContextClassLoader(Callable<T> action) throws Exception {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(this);
            try {
                return action.call();
            } finally {
                thread.setContextClassLoader(previous);
            }
        }

        private static URL[] classPath() throws MalformedURLException {
            List<URL> urls = new ArrayList<>();
            for (String entry : Pattern.compile(File.pathSeparator, Pattern.LITERAL).splitAsStream(System.getProperty("java.class.path")).toList()) {
                urls.add(Path.of(entry).toUri().toURL());
            }
            return urls.toArray(URL[]::new);
        }
    }
}
