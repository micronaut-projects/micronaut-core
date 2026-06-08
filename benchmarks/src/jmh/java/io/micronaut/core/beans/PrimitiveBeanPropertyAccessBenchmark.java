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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Introspected;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class PrimitiveBeanPropertyAccessBenchmark {

    private static final int INT_VALUE_1 = 0;
    private static final int INT_VALUE_2 = 1;
    private static final int INT_VALUE_3 = 2;
    private static final int LONG_VALUE_1 = 3;
    private static final int LONG_VALUE_2 = 4;
    private static final int BOOLEAN_VALUE_1 = 5;
    private static final int BOOLEAN_VALUE_2 = 6;
    private static final int DOUBLE_VALUE_1 = 7;
    private static final int DOUBLE_VALUE_2 = 8;
    private static final int FLOAT_VALUE = 9;

    @Param({"DIRECT", "SAFE_INTROSPECTION", "UNSAFE_INTROSPECTION", "UNSAFE_PROPERTY", "PRIMITIVE_INTROSPECTION", "PRIMITIVE_PROPERTY"})
    Access access;

    private PrimitiveAccessBean bean;
    private int next;
    private BeanReadProperty<PrimitiveAccessBean, Object>[] safeReaders;
    private BeanWriteProperty<PrimitiveAccessBean, Object>[] safeWriters;
    private UnsafeBeanReadProperty<PrimitiveAccessBean, Object>[] unsafeReaders;
    private UnsafeBeanWriteProperty<PrimitiveAccessBean, Object>[] unsafeWriters;
    private UnsafeBeanReadProperty<PrimitiveAccessBean, Object>[] unsafePropertyReaders;
    private UnsafeBeanWriteProperty<PrimitiveAccessBean, Object>[] unsafePropertyWriters;
    private UnsafeBeanReadProperty<PrimitiveAccessBean, Object>[] primitiveReaders;
    private UnsafeBeanWriteProperty<PrimitiveAccessBean, Object>[] primitiveWriters;
    private UnsafeBeanReadProperty<PrimitiveAccessBean, Object>[] primitivePropertyReaders;
    private UnsafeBeanWriteProperty<PrimitiveAccessBean, Object>[] primitivePropertyWriters;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(PrimitiveBeanPropertyAccessBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Setup
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void setUp() {
        bean = new PrimitiveAccessBean();
        next = 1;
        BeanIntrospection<PrimitiveAccessBean> introspection = BeanIntrospection.getIntrospection(PrimitiveAccessBean.class);
        safeReaders = new BeanReadProperty[] {
            safeReader(introspection, "intValue1"),
            safeReader(introspection, "intValue2"),
            safeReader(introspection, "intValue3"),
            safeReader(introspection, "longValue1"),
            safeReader(introspection, "longValue2"),
            safeReader(introspection, "booleanValue1"),
            safeReader(introspection, "booleanValue2"),
            safeReader(introspection, "doubleValue1"),
            safeReader(introspection, "doubleValue2"),
            safeReader(introspection, "floatValue")
        };
        safeWriters = new BeanWriteProperty[] {
            safeWriter(introspection, "intValue1"),
            safeWriter(introspection, "intValue2"),
            safeWriter(introspection, "intValue3"),
            safeWriter(introspection, "longValue1"),
            safeWriter(introspection, "longValue2"),
            safeWriter(introspection, "booleanValue1"),
            safeWriter(introspection, "booleanValue2"),
            safeWriter(introspection, "doubleValue1"),
            safeWriter(introspection, "doubleValue2"),
            safeWriter(introspection, "floatValue")
        };
        unsafeReaders = new UnsafeBeanReadProperty[] {
            unsafeReader(introspection, "intValue1"),
            unsafeReader(introspection, "intValue2"),
            unsafeReader(introspection, "intValue3"),
            unsafeReader(introspection, "longValue1"),
            unsafeReader(introspection, "longValue2"),
            unsafeReader(introspection, "booleanValue1"),
            unsafeReader(introspection, "booleanValue2"),
            unsafeReader(introspection, "doubleValue1"),
            unsafeReader(introspection, "doubleValue2"),
            unsafeReader(introspection, "floatValue")
        };
        unsafeWriters = new UnsafeBeanWriteProperty[] {
            unsafeWriter(introspection, "intValue1"),
            unsafeWriter(introspection, "intValue2"),
            unsafeWriter(introspection, "intValue3"),
            unsafeWriter(introspection, "longValue1"),
            unsafeWriter(introspection, "longValue2"),
            unsafeWriter(introspection, "booleanValue1"),
            unsafeWriter(introspection, "booleanValue2"),
            unsafeWriter(introspection, "doubleValue1"),
            unsafeWriter(introspection, "doubleValue2"),
            unsafeWriter(introspection, "floatValue")
        };
        unsafePropertyReaders = new UnsafeBeanReadProperty[] {
            unsafePropertyReader(introspection, "intValue1"),
            unsafePropertyReader(introspection, "intValue2"),
            unsafePropertyReader(introspection, "intValue3"),
            unsafePropertyReader(introspection, "longValue1"),
            unsafePropertyReader(introspection, "longValue2"),
            unsafePropertyReader(introspection, "booleanValue1"),
            unsafePropertyReader(introspection, "booleanValue2"),
            unsafePropertyReader(introspection, "doubleValue1"),
            unsafePropertyReader(introspection, "doubleValue2"),
            unsafePropertyReader(introspection, "floatValue")
        };
        unsafePropertyWriters = new UnsafeBeanWriteProperty[] {
            unsafePropertyWriter(introspection, "intValue1"),
            unsafePropertyWriter(introspection, "intValue2"),
            unsafePropertyWriter(introspection, "intValue3"),
            unsafePropertyWriter(introspection, "longValue1"),
            unsafePropertyWriter(introspection, "longValue2"),
            unsafePropertyWriter(introspection, "booleanValue1"),
            unsafePropertyWriter(introspection, "booleanValue2"),
            unsafePropertyWriter(introspection, "doubleValue1"),
            unsafePropertyWriter(introspection, "doubleValue2"),
            unsafePropertyWriter(introspection, "floatValue")
        };
        primitiveReaders = new UnsafeBeanReadProperty[] {
            unsafeReader(introspection, "intValue1"),
            unsafeReader(introspection, "intValue2"),
            unsafeReader(introspection, "intValue3"),
            unsafeReader(introspection, "longValue1"),
            unsafeReader(introspection, "longValue2"),
            unsafeReader(introspection, "booleanValue1"),
            unsafeReader(introspection, "booleanValue2"),
            unsafeReader(introspection, "doubleValue1"),
            unsafeReader(introspection, "doubleValue2"),
            unsafeReader(introspection, "floatValue")
        };
        primitiveWriters = new UnsafeBeanWriteProperty[] {
            unsafeWriter(introspection, "intValue1"),
            unsafeWriter(introspection, "intValue2"),
            unsafeWriter(introspection, "intValue3"),
            unsafeWriter(introspection, "longValue1"),
            unsafeWriter(introspection, "longValue2"),
            unsafeWriter(introspection, "booleanValue1"),
            unsafeWriter(introspection, "booleanValue2"),
            unsafeWriter(introspection, "doubleValue1"),
            unsafeWriter(introspection, "doubleValue2"),
            unsafeWriter(introspection, "floatValue")
        };
        primitivePropertyReaders = new UnsafeBeanReadProperty[] {
            unsafePropertyReader(introspection, "intValue1"),
            unsafePropertyReader(introspection, "intValue2"),
            unsafePropertyReader(introspection, "intValue3"),
            unsafePropertyReader(introspection, "longValue1"),
            unsafePropertyReader(introspection, "longValue2"),
            unsafePropertyReader(introspection, "booleanValue1"),
            unsafePropertyReader(introspection, "booleanValue2"),
            unsafePropertyReader(introspection, "doubleValue1"),
            unsafePropertyReader(introspection, "doubleValue2"),
            unsafePropertyReader(introspection, "floatValue")
        };
        primitivePropertyWriters = new UnsafeBeanWriteProperty[] {
            unsafePropertyWriter(introspection, "intValue1"),
            unsafePropertyWriter(introspection, "intValue2"),
            unsafePropertyWriter(introspection, "intValue3"),
            unsafePropertyWriter(introspection, "longValue1"),
            unsafePropertyWriter(introspection, "longValue2"),
            unsafePropertyWriter(introspection, "booleanValue1"),
            unsafePropertyWriter(introspection, "booleanValue2"),
            unsafePropertyWriter(introspection, "doubleValue1"),
            unsafePropertyWriter(introspection, "doubleValue2"),
            unsafePropertyWriter(introspection, "floatValue")
        };
        directWrite(next);
    }

    @Benchmark
    public long read() {
        return switch (access) {
            case DIRECT -> directRead();
            case SAFE_INTROSPECTION -> safeRead();
            case UNSAFE_INTROSPECTION -> unsafeRead();
            case UNSAFE_PROPERTY -> unsafePropertyRead();
            case PRIMITIVE_INTROSPECTION -> primitiveRead();
            case PRIMITIVE_PROPERTY -> primitivePropertyRead();
        };
    }

    @Benchmark
    public PrimitiveAccessBean write() {
        int value = ++next;
        switch (access) {
            case DIRECT -> directWrite(value);
            case SAFE_INTROSPECTION -> safeWrite(value);
            case UNSAFE_INTROSPECTION -> unsafeWrite(value);
            case UNSAFE_PROPERTY -> unsafePropertyWrite(value);
            case PRIMITIVE_INTROSPECTION -> primitiveWrite(value);
            case PRIMITIVE_PROPERTY -> primitivePropertyWrite(value);
        }
        return bean;
    }

    private long directRead() {
        long result = bean.getIntValue1();
        result += bean.getIntValue2();
        result += bean.getIntValue3();
        result += bean.getLongValue1();
        result += bean.getLongValue2();
        result += bean.isBooleanValue1() ? 1 : 0;
        result += bean.isBooleanValue2() ? 1 : 0;
        result += Double.doubleToRawLongBits(bean.getDoubleValue1());
        result += Double.doubleToRawLongBits(bean.getDoubleValue2());
        result += Float.floatToRawIntBits(bean.getFloatValue());
        return result;
    }

    private long safeRead() {
        long result = (Integer) safeReaders[INT_VALUE_1].get(bean);
        result += (Integer) safeReaders[INT_VALUE_2].get(bean);
        result += (Integer) safeReaders[INT_VALUE_3].get(bean);
        result += (Long) safeReaders[LONG_VALUE_1].get(bean);
        result += (Long) safeReaders[LONG_VALUE_2].get(bean);
        result += (Boolean) safeReaders[BOOLEAN_VALUE_1].get(bean) ? 1 : 0;
        result += (Boolean) safeReaders[BOOLEAN_VALUE_2].get(bean) ? 1 : 0;
        result += Double.doubleToRawLongBits((Double) safeReaders[DOUBLE_VALUE_1].get(bean));
        result += Double.doubleToRawLongBits((Double) safeReaders[DOUBLE_VALUE_2].get(bean));
        result += Float.floatToRawIntBits((Float) safeReaders[FLOAT_VALUE].get(bean));
        return result;
    }

    private long unsafeRead() {
        long result = (Integer) unsafeReaders[INT_VALUE_1].getUnsafe(bean);
        result += (Integer) unsafeReaders[INT_VALUE_2].getUnsafe(bean);
        result += (Integer) unsafeReaders[INT_VALUE_3].getUnsafe(bean);
        result += (Long) unsafeReaders[LONG_VALUE_1].getUnsafe(bean);
        result += (Long) unsafeReaders[LONG_VALUE_2].getUnsafe(bean);
        result += (Boolean) unsafeReaders[BOOLEAN_VALUE_1].getUnsafe(bean) ? 1 : 0;
        result += (Boolean) unsafeReaders[BOOLEAN_VALUE_2].getUnsafe(bean) ? 1 : 0;
        result += Double.doubleToRawLongBits((Double) unsafeReaders[DOUBLE_VALUE_1].getUnsafe(bean));
        result += Double.doubleToRawLongBits((Double) unsafeReaders[DOUBLE_VALUE_2].getUnsafe(bean));
        result += Float.floatToRawIntBits((Float) unsafeReaders[FLOAT_VALUE].getUnsafe(bean));
        return result;
    }

    private long unsafePropertyRead() {
        long result = (Integer) unsafePropertyReaders[INT_VALUE_1].getUnsafe(bean);
        result += (Integer) unsafePropertyReaders[INT_VALUE_2].getUnsafe(bean);
        result += (Integer) unsafePropertyReaders[INT_VALUE_3].getUnsafe(bean);
        result += (Long) unsafePropertyReaders[LONG_VALUE_1].getUnsafe(bean);
        result += (Long) unsafePropertyReaders[LONG_VALUE_2].getUnsafe(bean);
        result += (Boolean) unsafePropertyReaders[BOOLEAN_VALUE_1].getUnsafe(bean) ? 1 : 0;
        result += (Boolean) unsafePropertyReaders[BOOLEAN_VALUE_2].getUnsafe(bean) ? 1 : 0;
        result += Double.doubleToRawLongBits((Double) unsafePropertyReaders[DOUBLE_VALUE_1].getUnsafe(bean));
        result += Double.doubleToRawLongBits((Double) unsafePropertyReaders[DOUBLE_VALUE_2].getUnsafe(bean));
        result += Float.floatToRawIntBits((Float) unsafePropertyReaders[FLOAT_VALUE].getUnsafe(bean));
        return result;
    }

    private long primitiveRead() {
        long result = primitiveReaders[INT_VALUE_1].getIntUnsafe(bean);
        result += primitiveReaders[INT_VALUE_2].getIntUnsafe(bean);
        result += primitiveReaders[INT_VALUE_3].getIntUnsafe(bean);
        result += primitiveReaders[LONG_VALUE_1].getLongUnsafe(bean);
        result += primitiveReaders[LONG_VALUE_2].getLongUnsafe(bean);
        result += primitiveReaders[BOOLEAN_VALUE_1].getBooleanUnsafe(bean) ? 1 : 0;
        result += primitiveReaders[BOOLEAN_VALUE_2].getBooleanUnsafe(bean) ? 1 : 0;
        result += Double.doubleToRawLongBits(primitiveReaders[DOUBLE_VALUE_1].getDoubleUnsafe(bean));
        result += Double.doubleToRawLongBits(primitiveReaders[DOUBLE_VALUE_2].getDoubleUnsafe(bean));
        result += Float.floatToRawIntBits(primitiveReaders[FLOAT_VALUE].getFloatUnsafe(bean));
        return result;
    }

    private long primitivePropertyRead() {
        long result = primitivePropertyReaders[INT_VALUE_1].getIntUnsafe(bean);
        result += primitivePropertyReaders[INT_VALUE_2].getIntUnsafe(bean);
        result += primitivePropertyReaders[INT_VALUE_3].getIntUnsafe(bean);
        result += primitivePropertyReaders[LONG_VALUE_1].getLongUnsafe(bean);
        result += primitivePropertyReaders[LONG_VALUE_2].getLongUnsafe(bean);
        result += primitivePropertyReaders[BOOLEAN_VALUE_1].getBooleanUnsafe(bean) ? 1 : 0;
        result += primitivePropertyReaders[BOOLEAN_VALUE_2].getBooleanUnsafe(bean) ? 1 : 0;
        result += Double.doubleToRawLongBits(primitivePropertyReaders[DOUBLE_VALUE_1].getDoubleUnsafe(bean));
        result += Double.doubleToRawLongBits(primitivePropertyReaders[DOUBLE_VALUE_2].getDoubleUnsafe(bean));
        result += Float.floatToRawIntBits(primitivePropertyReaders[FLOAT_VALUE].getFloatUnsafe(bean));
        return result;
    }

    private void directWrite(int value) {
        bean.setIntValue1(value);
        bean.setIntValue2(value + 1);
        bean.setIntValue3(value + 2);
        bean.setLongValue1(value + 3L);
        bean.setLongValue2(value + 4L);
        bean.setBooleanValue1((value & 1) == 0);
        bean.setBooleanValue2((value & 2) == 0);
        bean.setDoubleValue1(value + 5.5D);
        bean.setDoubleValue2(value + 6.5D);
        bean.setFloatValue(value + 7.5F);
    }

    private void safeWrite(int value) {
        safeWriters[INT_VALUE_1].set(bean, value);
        safeWriters[INT_VALUE_2].set(bean, value + 1);
        safeWriters[INT_VALUE_3].set(bean, value + 2);
        safeWriters[LONG_VALUE_1].set(bean, value + 3L);
        safeWriters[LONG_VALUE_2].set(bean, value + 4L);
        safeWriters[BOOLEAN_VALUE_1].set(bean, (value & 1) == 0);
        safeWriters[BOOLEAN_VALUE_2].set(bean, (value & 2) == 0);
        safeWriters[DOUBLE_VALUE_1].set(bean, value + 5.5D);
        safeWriters[DOUBLE_VALUE_2].set(bean, value + 6.5D);
        safeWriters[FLOAT_VALUE].set(bean, value + 7.5F);
    }

    private void unsafeWrite(int value) {
        unsafeWriters[INT_VALUE_1].setUnsafe(bean, value);
        unsafeWriters[INT_VALUE_2].setUnsafe(bean, value + 1);
        unsafeWriters[INT_VALUE_3].setUnsafe(bean, value + 2);
        unsafeWriters[LONG_VALUE_1].setUnsafe(bean, value + 3L);
        unsafeWriters[LONG_VALUE_2].setUnsafe(bean, value + 4L);
        unsafeWriters[BOOLEAN_VALUE_1].setUnsafe(bean, (value & 1) == 0);
        unsafeWriters[BOOLEAN_VALUE_2].setUnsafe(bean, (value & 2) == 0);
        unsafeWriters[DOUBLE_VALUE_1].setUnsafe(bean, value + 5.5D);
        unsafeWriters[DOUBLE_VALUE_2].setUnsafe(bean, value + 6.5D);
        unsafeWriters[FLOAT_VALUE].setUnsafe(bean, value + 7.5F);
    }

    private void unsafePropertyWrite(int value) {
        unsafePropertyWriters[INT_VALUE_1].setUnsafe(bean, value);
        unsafePropertyWriters[INT_VALUE_2].setUnsafe(bean, value + 1);
        unsafePropertyWriters[INT_VALUE_3].setUnsafe(bean, value + 2);
        unsafePropertyWriters[LONG_VALUE_1].setUnsafe(bean, value + 3L);
        unsafePropertyWriters[LONG_VALUE_2].setUnsafe(bean, value + 4L);
        unsafePropertyWriters[BOOLEAN_VALUE_1].setUnsafe(bean, (value & 1) == 0);
        unsafePropertyWriters[BOOLEAN_VALUE_2].setUnsafe(bean, (value & 2) == 0);
        unsafePropertyWriters[DOUBLE_VALUE_1].setUnsafe(bean, value + 5.5D);
        unsafePropertyWriters[DOUBLE_VALUE_2].setUnsafe(bean, value + 6.5D);
        unsafePropertyWriters[FLOAT_VALUE].setUnsafe(bean, value + 7.5F);
    }

    private void primitiveWrite(int value) {
        primitiveWriters[INT_VALUE_1].setIntUnsafe(bean, value);
        primitiveWriters[INT_VALUE_2].setIntUnsafe(bean, value + 1);
        primitiveWriters[INT_VALUE_3].setIntUnsafe(bean, value + 2);
        primitiveWriters[LONG_VALUE_1].setLongUnsafe(bean, value + 3L);
        primitiveWriters[LONG_VALUE_2].setLongUnsafe(bean, value + 4L);
        primitiveWriters[BOOLEAN_VALUE_1].setBooleanUnsafe(bean, (value & 1) == 0);
        primitiveWriters[BOOLEAN_VALUE_2].setBooleanUnsafe(bean, (value & 2) == 0);
        primitiveWriters[DOUBLE_VALUE_1].setDoubleUnsafe(bean, value + 5.5D);
        primitiveWriters[DOUBLE_VALUE_2].setDoubleUnsafe(bean, value + 6.5D);
        primitiveWriters[FLOAT_VALUE].setFloatUnsafe(bean, value + 7.5F);
    }

    private void primitivePropertyWrite(int value) {
        primitivePropertyWriters[INT_VALUE_1].setIntUnsafe(bean, value);
        primitivePropertyWriters[INT_VALUE_2].setIntUnsafe(bean, value + 1);
        primitivePropertyWriters[INT_VALUE_3].setIntUnsafe(bean, value + 2);
        primitivePropertyWriters[LONG_VALUE_1].setLongUnsafe(bean, value + 3L);
        primitivePropertyWriters[LONG_VALUE_2].setLongUnsafe(bean, value + 4L);
        primitivePropertyWriters[BOOLEAN_VALUE_1].setBooleanUnsafe(bean, (value & 1) == 0);
        primitivePropertyWriters[BOOLEAN_VALUE_2].setBooleanUnsafe(bean, (value & 2) == 0);
        primitivePropertyWriters[DOUBLE_VALUE_1].setDoubleUnsafe(bean, value + 5.5D);
        primitivePropertyWriters[DOUBLE_VALUE_2].setDoubleUnsafe(bean, value + 6.5D);
        primitivePropertyWriters[FLOAT_VALUE].setFloatUnsafe(bean, value + 7.5F);
    }

    @SuppressWarnings("unchecked")
    private static BeanReadProperty<PrimitiveAccessBean, Object> safeReader(BeanIntrospection<PrimitiveAccessBean> introspection, String name) {
        return (BeanReadProperty<PrimitiveAccessBean, Object>) introspection.getReadProperty(name).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static BeanWriteProperty<PrimitiveAccessBean, Object> safeWriter(BeanIntrospection<PrimitiveAccessBean> introspection, String name) {
        return (BeanWriteProperty<PrimitiveAccessBean, Object>) introspection.getWriteProperty(name).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static UnsafeBeanReadProperty<PrimitiveAccessBean, Object> unsafeReader(BeanIntrospection<PrimitiveAccessBean> introspection, String name) {
        return (UnsafeBeanReadProperty<PrimitiveAccessBean, Object>) introspection.getReadProperty(name).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static UnsafeBeanWriteProperty<PrimitiveAccessBean, Object> unsafeWriter(BeanIntrospection<PrimitiveAccessBean> introspection, String name) {
        return (UnsafeBeanWriteProperty<PrimitiveAccessBean, Object>) introspection.getWriteProperty(name).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static UnsafeBeanReadProperty<PrimitiveAccessBean, Object> unsafePropertyReader(BeanIntrospection<PrimitiveAccessBean> introspection, String name) {
        return (UnsafeBeanReadProperty<PrimitiveAccessBean, Object>) introspection.getProperty(name).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static UnsafeBeanWriteProperty<PrimitiveAccessBean, Object> unsafePropertyWriter(BeanIntrospection<PrimitiveAccessBean> introspection, String name) {
        return (UnsafeBeanWriteProperty<PrimitiveAccessBean, Object>) introspection.getProperty(name).orElseThrow();
    }

    public enum Access {
        DIRECT,
        SAFE_INTROSPECTION,
        UNSAFE_INTROSPECTION,
        UNSAFE_PROPERTY,
        PRIMITIVE_INTROSPECTION,
        PRIMITIVE_PROPERTY
    }

    @Introspected
    public static final class PrimitiveAccessBean {
        private int intValue1;
        private int intValue2;
        private int intValue3;
        private long longValue1;
        private long longValue2;
        private boolean booleanValue1;
        private boolean booleanValue2;
        private double doubleValue1;
        private double doubleValue2;
        private float floatValue;

        public int getIntValue1() {
            return intValue1;
        }

        public void setIntValue1(int intValue1) {
            this.intValue1 = intValue1;
        }

        public int getIntValue2() {
            return intValue2;
        }

        public void setIntValue2(int intValue2) {
            this.intValue2 = intValue2;
        }

        public int getIntValue3() {
            return intValue3;
        }

        public void setIntValue3(int intValue3) {
            this.intValue3 = intValue3;
        }

        public long getLongValue1() {
            return longValue1;
        }

        public void setLongValue1(long longValue1) {
            this.longValue1 = longValue1;
        }

        public long getLongValue2() {
            return longValue2;
        }

        public void setLongValue2(long longValue2) {
            this.longValue2 = longValue2;
        }

        public boolean isBooleanValue1() {
            return booleanValue1;
        }

        public void setBooleanValue1(boolean booleanValue1) {
            this.booleanValue1 = booleanValue1;
        }

        public boolean isBooleanValue2() {
            return booleanValue2;
        }

        public void setBooleanValue2(boolean booleanValue2) {
            this.booleanValue2 = booleanValue2;
        }

        public double getDoubleValue1() {
            return doubleValue1;
        }

        public void setDoubleValue1(double doubleValue1) {
            this.doubleValue1 = doubleValue1;
        }

        public double getDoubleValue2() {
            return doubleValue2;
        }

        public void setDoubleValue2(double doubleValue2) {
            this.doubleValue2 = doubleValue2;
        }

        public float getFloatValue() {
            return floatValue;
        }

        public void setFloatValue(float floatValue) {
            this.floatValue = floatValue;
        }
    }
}
