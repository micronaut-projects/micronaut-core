/*
 * Copyright 2017-2023 original authors
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
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Measures name lookup through bean introspection, property access, and builder APIs.
 * Whole-bean benchmarks report one complete construction or copy per operation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StringIntMapUsageBenchmark {
    @Param({"2", "8"})
    public int size;

    @Param({"record", "mutable"})
    public String model;

    private BeanIntrospection<Object> introspection;
    private BeanIntrospection.Builder<Object> builder;
    private BeanWrapper<Object> wrapper;
    private Object bean;
    private String[] canonical;
    private String[] dynamic;
    private char[][] characters;
    private Object[] values;
    private int[] order;
    private int cursor;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Setup
    public void setup() {
        Class<?> type = switch (size) {
            case 2 -> model.equals("mutable") ? Mutable2.class : Person2.class;
            case 8 -> model.equals("mutable") ? Mutable8.class : Person8.class;
            default -> throw new IllegalArgumentException("Unsupported size: " + size);
        };
        introspection = (BeanIntrospection) BeanIntrospection.getIntrospection(type);
        canonical = introspection.getBeanProperties().stream().map(BeanProperty::getName).toArray(String[]::new);
        dynamic = new String[size];
        characters = new char[size][];
        values = new Object[size];
        var initial = introspection.builder();
        for (int i = 0; i < size; i++) {
            characters[i] = canonical[i].toCharArray();
            dynamic[i] = new String(characters[i]);
            // Separate cached-hash lookups from propertyReadFreshName.
            dynamic[i].hashCode();
            values[i] = "value-" + i;
            initial.with(canonical[i], values[i]);
        }
        bean = initial.build();
        builder = introspection.builder();
        wrapper = BeanWrapper.getWrapper(bean);
        order = new int[1024];
        Random random = new Random(23419);
        for (int i = 0; i < order.length; i++) {
            order[i] = random.nextInt(size);
        }
        for (int i = 0; i < size; i++) {
            if (introspection.propertyIndexOf(dynamic[i]) != i || builder.indexOf(dynamic[i]) != i) {
                throw new AssertionError("Unexpected property index");
            }
            if (!values[i].equals(wrapper.getRequiredProperty(dynamic[i], String.class))) {
                throw new AssertionError("Unexpected wrapper value");
            }
        }
        verify(buildByName());
        verify(copyAndBuild());
        if (introspection.getProperty("unknownField").isPresent()) {
            throw new AssertionError("Unexpected property");
        }
    }

    private void verify(Object built) {
        for (int i = 0; i < size; i++) {
            if (!values[i].equals(introspection.getProperty(dynamic[i]).orElseThrow().get(built))) {
                throw new AssertionError("Unexpected value for " + dynamic[i]);
            }
        }
    }

    private int next() {
        return order[cursor++ & (order.length - 1)];
    }

    @Benchmark
    public int indexCanonical() {
        return introspection.propertyIndexOf(canonical[next()]);
    }

    @Benchmark
    public int indexDynamic() {
        return introspection.propertyIndexOf(dynamic[next()]);
    }

    @Benchmark
    public Object propertyRead() {
        return introspection.getProperty(dynamic[next()]).orElseThrow().get(bean);
    }

    @Benchmark
    public Object propertyReadFreshName() {
        return introspection.getProperty(new String(characters[next()])).orElseThrow().get(bean);
    }

    @Benchmark
    public Object wrapperRead() {
        return wrapper.getRequiredProperty(dynamic[next()], String.class);
    }

    @Benchmark
    public int builderIndex() {
        return builder.indexOf(dynamic[next()]);
    }

    @Benchmark
    public Object unknownProperty() {
        return introspection.getProperty("unknownField");
    }

    @Benchmark
    public Object copyAndBuild() {
        return introspection.builder().with(bean).build();
    }

    @Benchmark
    public Object buildByName() {
        BeanIntrospection.Builder<Object> currentBuilder = introspection.builder();
        for (int i = 0; i < size; i++) {
            currentBuilder.with(dynamic[i], values[i]);
        }
        return currentBuilder.build();
    }

    @Introspected
    public record Person2(String name, String age) {
    }

    @Introspected
    public record Person8(String name, String age, String country, String email,
                          String city, String postalCode, String phone, String status) {
    }

    @Introspected
    public static class Mutable2 {
        private String name;
        private String age;

        public String getName() {
            return name;
        }

        public void setName(String value) {
            name = value;
        }

        public String getAge() {
            return age;
        }

        public void setAge(String value) {
            age = value;
        }
    }

    @Introspected
    public static class Mutable8 {
        private String name;
        private String age;
        private String country;
        private String email;
        private String city;
        private String postalCode;
        private String phone;
        private String status;

        public String getName() {
            return name;
        }

        public void setName(String value) {
            name = value;
        }

        public String getAge() {
            return age;
        }

        public void setAge(String value) {
            age = value;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String value) {
            country = value;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String value) {
            email = value;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String value) {
            city = value;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String value) {
            postalCode = value;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String value) {
            phone = value;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String value) {
            status = value;
        }
    }
}
