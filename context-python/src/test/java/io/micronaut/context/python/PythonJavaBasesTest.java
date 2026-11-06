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
package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PythonJavaBasesTest {

    @Test
    void nestedConstructionsAreAnsweredByClass() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value outer = context.eval(PYTHON, "object()");
            Value inner = context.eval(PYTHON, "object()");

            PythonJavaBases.Construction outerConstruction = PythonJavaBases.constructing(outer, Outer.class);
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class));
            assertNull(PythonJavaBases.underConstruction(Inner.class));

            // the super constructor of the outer instance creates the Java instance of another Python object
            PythonJavaBases.Construction innerConstruction = PythonJavaBases.constructing(inner, Inner.class);
            assertSame(inner, PythonJavaBases.underConstruction(Inner.class));
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class), "a bridge on the outer instance still reaches the outer object");

            innerConstruction.finished();
            assertNull(PythonJavaBases.underConstruction(Inner.class));
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class));

            outerConstruction.finished();
            assertNull(PythonJavaBases.underConstruction(Outer.class));
        }
    }

    @Test
    void aNestedConstructionAbandonedByAThrowingSuperConstructorIsPoppedWithTheEnclosingOne() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value outer = context.eval(PYTHON, "object()");
            Value abandoned = context.eval(PYTHON, "object()");

            PythonJavaBases.Construction outerConstruction = PythonJavaBases.constructing(outer, Outer.class);
            // the super constructor of the outer instance starts an inner construction whose super constructor throws
            PythonJavaBases.constructing(abandoned, Inner.class);
            assertSame(outer, PythonJavaBases.underConstruction(Outer.class));

            outerConstruction.finished();
            assertNull(PythonJavaBases.underConstruction(Inner.class));
            assertNull(PythonJavaBases.underConstruction(Outer.class));
        }
    }

    @Test
    void theMethodNamesLeaveOutWhatTheDispatcherLeavesOut() {
        assertEquals(
            Set.of("ofTheClass", "ofTheClassArray", "scalar", "ofAClass", "ofAList", "ofAListArray", "ofAnIntersectionInAClass", "selfBounded", "declaresAnException"),
            Set.of(PythonJavaBases.methodNames(Shapes.class)));
    }

    @Test
    void aClassOnlyTheGenericSignatureNamesMayBeAbsent() throws Exception {
        Class<?> loaded = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(Absent.class.getName())) {
                    throw new ClassNotFoundException(name);
                }
                if (name.equals(NamesAbsent.class.getName())) {
                    try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = stream.readAllBytes();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return super.loadClass(name, resolve);
            }
        }.loadClass(NamesAbsent.class.getName());

        assertEquals(Set.of("plain"), Set.of(PythonJavaBases.methodNames(loaded)));
    }

    public static class Shapes<E> {
        public <T> void arrayOfOwn(T[] values) {
        }

        public <T> void gridOfOwn(T[][] values) {
        }

        @SafeVarargs
        public final <T> void varargsOfOwn(T... values) {
        }

        public <T> void gridOfConcrete(String[][] values, T tag) {
        }

        public <T> void gridOfClassVariable(E[][] values, T tag) {
        }

        public <T extends Runnable & Comparable<T>> void intersection(T value) {
        }

        public <U extends Runnable & Comparable<U>, T extends U> void intersectionThroughABound(T value) {
        }

        public void throwsAThrowable() throws Throwable {
        }

        public <T> void ofTheClass(E value, T tag) {
        }

        public <T> void ofTheClassArray(E[] values, T tag) {
        }

        public <T> void scalar(T value) {
        }

        public <T> void ofAClass(Class<T> type) {
        }

        public <T> void ofAList(List<T> values) {
        }

        public <T> void ofAListArray(List<T>[] values) {
        }

        public <T extends Runnable & Comparable<T>> void ofAnIntersectionInAClass(Class<T> type) {
        }

        public <T extends Comparable<T>> void selfBounded(T value) {
        }

        public <X extends Exception> void declaresAnException(Class<X> type) throws X {
        }
    }

    public static final class Absent {
    }

    public static class NamesAbsent {
        public <T> void ofAnAbsentType(List<Absent> values, T tag) {
        }

        public <T extends Runnable & Comparable<Absent>> void ofAnAbsentBound(T value) {
        }

        public void plain() {
        }
    }

    private static final class Outer {
    }

    private static final class Inner {
    }
}
