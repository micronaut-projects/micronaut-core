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
package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.beans.UnsafeBeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanWriteProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SuppressWarnings({"unchecked", "rawtypes"})
class PrimitiveBeanPropertyAccessTest {

    @Test
    void generatedIntrospectionsExposePrimitiveReadAndWriteAccess() {
        BeanIntrospection<PrimitiveBean> introspection = BeanIntrospection.getIntrospection(PrimitiveBean.class);
        PrimitiveBean bean = new PrimitiveBean();

        UnsafeBeanWriteProperty<PrimitiveBean, Object> intWriter = unsafeWriter(introspection, "intValue");
        intWriter.setIntUnsafe(bean, 1234);
        UnsafeBeanReadProperty<PrimitiveBean, Object> intReader = unsafeReader(introspection, "intValue");
        assertEquals(1234, intReader.getIntUnsafe(bean));

        unsafeWriter(introspection, "booleanValue").setBooleanUnsafe(bean, true);
        assertEquals(true, unsafeReader(introspection, "booleanValue").getBooleanUnsafe(bean));

        unsafeWriter(introspection, "byteValue").setByteUnsafe(bean, (byte) 12);
        assertEquals((byte) 12, unsafeReader(introspection, "byteValue").getByteUnsafe(bean));

        unsafeWriter(introspection, "shortValue").setShortUnsafe(bean, (short) 123);
        assertEquals((short) 123, unsafeReader(introspection, "shortValue").getShortUnsafe(bean));

        unsafeWriter(introspection, "charValue").setCharUnsafe(bean, 'x');
        assertEquals('x', unsafeReader(introspection, "charValue").getCharUnsafe(bean));

        unsafeWriter(introspection, "longValue").setLongUnsafe(bean, 1234567890123L);
        assertEquals(1234567890123L, unsafeReader(introspection, "longValue").getLongUnsafe(bean));

        unsafeWriter(introspection, "floatValue").setFloatUnsafe(bean, 12.5F);
        assertEquals(12.5F, unsafeReader(introspection, "floatValue").getFloatUnsafe(bean));

        unsafeWriter(introspection, "doubleValue").setDoubleUnsafe(bean, 123.456D);
        assertEquals(123.456D, unsafeReader(introspection, "doubleValue").getDoubleUnsafe(bean));

        BeanProperty<PrimitiveBean, Object> property = introspection.getProperty("intValue").orElseThrow();
        UnsafeBeanReadProperty<PrimitiveBean, Object> primitivePropertyReader = assertInstanceOf(UnsafeBeanReadProperty.class, property);
        UnsafeBeanWriteProperty<PrimitiveBean, Object> primitivePropertyWriter = assertInstanceOf(UnsafeBeanWriteProperty.class, property);
        primitivePropertyWriter.setIntUnsafe(bean, 4321);
        assertEquals(4321, primitivePropertyReader.getIntUnsafe(bean));
    }

    private static UnsafeBeanReadProperty<PrimitiveBean, Object> unsafeReader(BeanIntrospection<PrimitiveBean> introspection, String name) {
        BeanReadProperty<PrimitiveBean, Object> property = introspection.getBeanReadProperties()
            .stream()
            .filter(p -> p.getName().equals(name))
            .findFirst()
            .orElseThrow();
        return assertInstanceOf(UnsafeBeanReadProperty.class, property);
    }

    private static UnsafeBeanWriteProperty<PrimitiveBean, Object> unsafeWriter(BeanIntrospection<PrimitiveBean> introspection, String name) {
        BeanWriteProperty<PrimitiveBean, Object> property = introspection.getBeanWriteProperties()
            .stream()
            .filter(p -> p.getName().equals(name))
            .findFirst()
            .orElseThrow();
        return assertInstanceOf(UnsafeBeanWriteProperty.class, property);
    }

    @Introspected
    public static final class PrimitiveBean {
        private boolean booleanValue;
        private byte byteValue;
        private short shortValue;
        private char charValue;
        private int intValue;
        private long longValue;
        private float floatValue;
        private double doubleValue;
        private String stringValue;

        public boolean isBooleanValue() {
            return booleanValue;
        }

        public void setBooleanValue(boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        public byte getByteValue() {
            return byteValue;
        }

        public void setByteValue(byte byteValue) {
            this.byteValue = byteValue;
        }

        public short getShortValue() {
            return shortValue;
        }

        public void setShortValue(short shortValue) {
            this.shortValue = shortValue;
        }

        public char getCharValue() {
            return charValue;
        }

        public void setCharValue(char charValue) {
            this.charValue = charValue;
        }

        public int getIntValue() {
            return intValue;
        }

        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }

        public long getLongValue() {
            return longValue;
        }

        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }

        public float getFloatValue() {
            return floatValue;
        }

        public void setFloatValue(float floatValue) {
            this.floatValue = floatValue;
        }

        public double getDoubleValue() {
            return doubleValue;
        }

        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
    }
}
