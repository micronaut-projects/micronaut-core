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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The attributes of the objects of a Python class that the statically compiled bodies of its stub
 * read from Java fields: attributes fixed at construction, which {@code __init__} assigns and
 * nothing in the compilation writes afterwards (see {@code StaticPlanner.fixed_attribute}). One
 * instance per stub class holds the epoch of those fields; each stub holds one {@link Slot} per
 * attribute.
 * <p>
 * A slot reads the attribute through the Python object the first time and keeps the converted
 * value, for that Python object, until the epoch moves on: once a slot has read an attribute of an
 * object ({@code micronaut_runtime.__micronaut_fixed_member}), the class's {@code __setattr__} and
 * {@code __delattr__} report every change of that attribute, by name, of an object of the class, so a
 * write the compilation does not see (a test's {@code monkeypatch.setattr}, code outside the
 * compilation) makes the slots read the attribute again. A write of an attribute no slot holds (a
 * counter the compilation mutates) reports nothing and keeps the slots' values. The epoch is one per
 * stub class rather than one per attribute: the compilation never writes a fixed attribute, so a
 * reported change is rare, and a single epoch keeps the read a field comparison. An object whose class serves its attributes itself (a {@code __getattribute__},
 * a {@code __setattr__} of its own, a class attribute of the name) is read every time, as the stub
 * reads any other attribute.
 * <p>
 * A stub primes its slots when it takes its Python object ({@link #prime}): when it is created, wraps
 * an object, or creates its object again, so a compiled body does not read an attribute through the
 * Python object the first time it runs (a route kept on the event loop, see {@code EventLoopSafety},
 * then never waits for the context). Only an attribute the object has is primed; a slot of one it
 * lacks (yet) reads it as before. A slot reads its attribute again after a reported change, on
 * whichever thread runs the next body: by the planner's rules only code outside the compilation
 * writes a fixed attribute. A cycle of objects holding each other in fixed attributes leaves the slots
 * of the wrapper that closes the cycle to their first read.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
@Internal
@UsedByGeneratedCode
public final class PythonFixedAttributes {

    private static final String FIXED_MEMBER = "__micronaut_fixed_member";
    private static final String FIXED_MEMBERS = "__micronaut_fixed_members";
    private static final AtomicLong IDS = new AtomicLong();
    /** The Python objects whose stubs this thread primes, innermost last: a wrapper of one of them closes a cycle. */
    private static final ThreadLocal<ArrayDeque<Value>> PRIMING = ThreadLocal.withInitial(ArrayDeque::new);

    private final AtomicLong epoch = new AtomicLong();
    private final long id = IDS.incrementAndGet();

    /**
     * @return The identity of these attributes among those of every stub class, as the Python side
     * keeps them
     */
    public long id() {
        return id;
    }

    /**
     * @return The epoch: the number of changes of attributes of objects of the class reported so far
     */
    public long epoch() {
        return epoch.get();
    }

    /**
     * Reports a change of an attribute a slot holds of an object of the class: every slot reads its
     * attribute again.
     * Called by the {@code __setattr__} and {@code __delattr__} the Python side gives the class.
     */
    public void changed() {
        epoch.incrementAndGet();
    }

    /**
     * Fills the slots of a stub for its Python object, in one call into Python, when the stub takes
     * the object: each slot keeps the value of its attribute as its first read would, so the compiled
     * bodies read none of them through the object. A slot of an attribute the object does not have
     * (yet) is left empty, and a value that does not convert is left to the read, which fails as
     * before. A wrapper the conversion creates primes its own slots; one of an object being primed on
     * this thread does not, which ends a cycle of objects holding each other.
     *
     * @param self       The Python object of the stub
     * @param names      The attributes, one per slot
     * @param slots      The slots of the stub
     * @param converters The conversion of each attribute to the Java type the bodies read
     * @param wrappers   Whether each type is the generated class of a plain class of the compilation
     */
    public void prime(@Nullable Value self, String[] names, Slot[] slots, PolyglotValueConverter<?>[] converters, boolean[] wrappers) {
        if (self == null || names.length == 0) {
            return;
        }
        ArrayDeque<Value> priming = PRIMING.get();
        for (Value outer : priming) {
            if (outer == self || outer.equals(self)) {
                return;
            }
        }
        // the epoch is read before the attributes: a change after it moves the epoch past the kept values
        long current = epoch();
        Value found;
        try {
            found = PythonContextRuntime.helper(self.getContext(), FIXED_MEMBERS).execute(self, names, this, id);
        } catch (PolyglotException e) {
            if (fatal(e)) {
                throw e;
            }
            return;
        }
        priming.addLast(self);
        try {
            for (int i = 0; i < slots.length; i++) {
                slots[i].prime(self, current, found.getArrayElement(i), converters[i], wrappers[i]);
            }
        } finally {
            priming.removeLast();
        }
    }

    private static boolean fatal(PolyglotException e) {
        return e.isCancelled() || e.isExit() || e.isInterrupted() || e.isResourceExhausted() || e.isInternalError();
    }

    /**
     * The value of one attribute of {@code self} a stub keeps, as its compiled bodies read it.
     */
    @UsedByGeneratedCode
    public static final class Slot {
        // the value for one Python object at one epoch; replaced, never changed, so it is published whole
        private @Nullable Fixed current;

        /**
         * The value of the attribute of the Python object, converted as a read of it through the
         * object converts it: the value kept for the object at the current epoch, else the value read
         * now, kept when the object's class leaves its attributes to the object.
         *
         * @param self       The Python object of the stub
         * @param name       The attribute
         * @param attributes The attributes of the stub class
         * @param converter  The conversion of the Python value to the Java type the bodies read
         * @param wrapper    Whether the type is the generated class of a plain class of the compilation,
         *                   which wraps a Python object of it without copying its state
         * @return The value
         */
        public @Nullable Object read(Value self, String name, PythonFixedAttributes attributes, PolyglotValueConverter<?> converter, boolean wrapper) {
            // the epoch is read before the attribute: a change after it moves the epoch past the kept value
            long epoch = attributes.epoch();
            Fixed fixed = current;
            if (fixed != null && fixed.owner() == self && fixed.epoch() == epoch) {
                return fixed.held() ? fixed.value() : converter.convert(self.getMember(name));
            }
            Value found;
            try {
                found = PythonContextRuntime.helper(self.getContext(), FIXED_MEMBER).execute(self, name, attributes, attributes.id());
            } catch (PolyglotException e) {
                if (fatal(e)) {
                    throw e;
                }
                return converter.convert(self.getMember(name));
            }
            if (found.isNull()) {
                // no such attribute (yet): read as any other attribute, nothing kept
                return converter.convert(self.getMember(name));
            }
            if (found.isBoolean()) {
                // the class serves the attribute itself: read every time
                current = new Fixed(self, epoch, null, false);
                return converter.convert(self.getMember(name));
            }
            Value member = found.getArrayElement(0);
            Object value = converter.convert(member);
            // a value converted from a Python object other than an immutable one may be a copy of its
            // state (a target type mapping), which a change in place does not reach: read every time
            current = new Fixed(self, epoch, value, wrapper || unchanging(member));
            return value;
        }

        /**
         * Keeps the value of the attribute for the Python object at the epoch, as {@link #read} keeps it,
         * from what {@code __micronaut_fixed_member} found: nothing when the object has no such attribute
         * or the value does not convert.
         */
        void prime(Value self, long epoch, Value found, PolyglotValueConverter<?> converter, boolean wrapper) {
            if (found.isNull()) {
                return;
            }
            if (found.isBoolean()) {
                current = new Fixed(self, epoch, null, false);
                return;
            }
            Value member = found.getArrayElement(0);
            Object value;
            try {
                value = converter.convert(member);
            } catch (PolyglotException e) {
                if (fatal(e)) {
                    throw e;
                }
                return;
            } catch (RuntimeException e) {
                // the read converts it again and fails as it does without priming
                return;
            }
            current = new Fixed(self, epoch, value, wrapper || unchanging(member));
        }

        /**
         * Whether a Python value converts to an equal Java value as long as it is the same object: a
         * Java object, handed over as itself, or an immutable Python value.
         */
        private static boolean unchanging(Value member) {
            return member.isHostObject() || member.isNull() || member.isString() || member.isBoolean() || member.isNumber()
                || member.isDate() || member.isTime() || member.isTimeZone() || member.isDuration();
        }
    }

    /**
     * What a slot keeps for one Python object at one epoch.
     *
     * @param owner The Python object
     * @param epoch The epoch the value was read at
     * @param value The converted value, when held
     * @param held  Whether the value is held; else the object's attribute is read every time
     */
    private record Fixed(Value owner, long epoch, @Nullable Object value, boolean held) {
    }
}
