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
package io.micronaut.inject.beans;

import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanWriteProperty;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"unchecked", "rawtypes"})
class AbstractInitializableBeanIntrospectionTest {

    @Test
    void readOnlyBeanPropertyUsesNullForNoArgumentDispatch() {
        TestBean bean = new TestBean();
        TestBeanIntrospection introspection = new TestBeanIntrospection();
        BeanReadProperty<TestBean, Object> property = introspection.getBeanReadProperties().iterator().next();

        assertSame(bean, property.get(bean));
        assertNull(introspection.lastDispatchArgument);

        assertSame(bean, ((UnsafeBeanReadProperty<TestBean, Object>) property).getUnsafe(bean));
        assertNull(introspection.lastDispatchArgument);
    }

    @Test
    void primitiveBeanWritePropertyUsesVoidDispatch() {
        PrimitiveTestBean bean = new PrimitiveTestBean();
        PrimitiveTestBeanIntrospection introspection = new PrimitiveTestBeanIntrospection();
        UnsafeBeanWriteProperty<PrimitiveTestBean, Object> writeProperty = (UnsafeBeanWriteProperty<PrimitiveTestBean, Object>) introspection.getWriteProperty("value").orElseThrow();
        UnsafeBeanReadProperty<PrimitiveTestBean, Object> readProperty = (UnsafeBeanReadProperty<PrimitiveTestBean, Object>) introspection.getReadProperty("value").orElseThrow();

        writeProperty.setIntUnsafe(bean, 42);

        assertEquals(42, bean.value);
        assertTrue(introspection.voidSetterUsed);
        assertFalse(introspection.objectSetterUsed);
        assertEquals(42, readProperty.getIntUnsafe(bean));
    }

    @Test
    void primitiveBeanWritePropertyFallsBackToBoxedDispatch() {
        PrimitiveTestBean bean = new PrimitiveTestBean();
        BoxedPrimitiveFallbackIntrospection introspection = new BoxedPrimitiveFallbackIntrospection();
        UnsafeBeanWriteProperty<PrimitiveTestBean, Object> writeProperty = (UnsafeBeanWriteProperty<PrimitiveTestBean, Object>) introspection.getWriteProperty("value").orElseThrow();

        writeProperty.setIntUnsafe(bean, 99);

        assertEquals(99, bean.value);
        assertTrue(introspection.voidSetterUsed);
        assertFalse(introspection.objectSetterUsed);
        assertEquals(Integer.valueOf(99), introspection.lastValue);

        assertNull(introspection.dispatchSetInt(1, bean, 100));
        assertEquals(100, bean.value);
        assertTrue(introspection.objectSetterUsed);
        assertEquals(Integer.valueOf(100), introspection.lastValue);
    }

    @Test
    void primitiveBeanReadPropertyFallsBackToBoxedDispatch() {
        PrimitiveTestBean bean = new PrimitiveTestBean();
        bean.value = 123;
        BoxedPrimitiveFallbackIntrospection introspection = new BoxedPrimitiveFallbackIntrospection();
        UnsafeBeanReadProperty<PrimitiveTestBean, Object> readProperty = (UnsafeBeanReadProperty<PrimitiveTestBean, Object>) introspection.getReadProperty("value").orElseThrow();

        assertEquals(123, readProperty.getIntUnsafe(bean));
        assertTrue(introspection.objectGetterUsed);
        assertFalse(introspection.objectSetterUsed);
    }

    private static final class TestBean {
    }

    private static final class PrimitiveTestBean {
        private int value;
    }

    private static final class TestBeanIntrospection extends AbstractInitializableBeanIntrospection<TestBean> {
        private Object lastDispatchArgument = new Object();

        private TestBeanIntrospection() {
            super(
                TestBean.class,
                null,
                null,
                null,
                new BeanPropertyRef[] {
                    new BeanPropertyRef<>(
                        Argument.OBJECT_ARGUMENT,
                        Argument.OBJECT_ARGUMENT,
                        null,
                        0,
                        -1,
                        -1,
                        true,
                        false
                    )
                },
                null
            );
        }

        @Override
        @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
        protected <V> V dispatchOne(int index, Object target, Object arg) {
            lastDispatchArgument = arg;
            return (V) target;
        }

        @Override
        protected Method getTargetMethodByIndex(int index) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class PrimitiveTestBeanIntrospection extends AbstractInitializableBeanIntrospection<PrimitiveTestBean> {
        private boolean voidSetterUsed;
        private boolean objectSetterUsed;

        private PrimitiveTestBeanIntrospection() {
            super(
                PrimitiveTestBean.class,
                null,
                null,
                null,
                primitiveProperties(),
                null
            );
        }

        @Override
        protected int dispatchGetInt(int index, Object target) {
            if (index != 0) {
                throw unknownDispatchAtIndexException(index);
            }
            return ((PrimitiveTestBean) target).value;
        }

        @Override
        protected Object dispatchSetInt(int index, Object target, int value) {
            objectSetterUsed = true;
            if (index != 1) {
                throw unknownDispatchAtIndexException(index);
            }
            ((PrimitiveTestBean) target).value = value;
            return null;
        }

        @Override
        protected void dispatchSetIntVoid(int index, Object target, int value) {
            voidSetterUsed = true;
            if (index != 1) {
                throw unknownDispatchAtIndexException(index);
            }
            ((PrimitiveTestBean) target).value = value;
        }

        @Override
        protected Method getTargetMethodByIndex(int index) {
            throw new UnsupportedOperationException();
        }

        private static BeanPropertyRef[] primitiveProperties() {
            Argument<Integer> argument = Argument.of(int.class, "value");
            return new BeanPropertyRef[] {
                new BeanPropertyRef<>(
                    argument,
                    argument,
                    argument,
                    0,
                    1,
                    -1,
                    false,
                    true
                )
            };
        }
    }

    private static final class BoxedPrimitiveFallbackIntrospection extends AbstractInitializableBeanIntrospection<PrimitiveTestBean> {
        private boolean objectGetterUsed;
        private boolean voidSetterUsed;
        private boolean objectSetterUsed;
        private Object lastValue;

        private BoxedPrimitiveFallbackIntrospection() {
            super(
                PrimitiveTestBean.class,
                null,
                null,
                null,
                PrimitiveTestBeanIntrospection.primitiveProperties(),
                null
            );
        }

        @Override
        @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
        protected <V> V dispatchOne(int index, Object target, Object arg) {
            if (index == 0) {
                objectGetterUsed = true;
                return (V) Integer.valueOf(((PrimitiveTestBean) target).value);
            }
            objectSetterUsed = true;
            setValue(index, target, arg);
            return null;
        }

        @Override
        protected void dispatchOneVoid(int index, Object target, Object arg) {
            voidSetterUsed = true;
            setValue(index, target, arg);
        }

        @Override
        protected Method getTargetMethodByIndex(int index) {
            throw new UnsupportedOperationException();
        }

        private void setValue(int index, Object target, Object arg) {
            if (index != 1) {
                throw unknownDispatchAtIndexException(index);
            }
            lastValue = arg;
            ((PrimitiveTestBean) target).value = (Integer) arg;
        }
    }
}
