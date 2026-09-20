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
package io.micronaut.context.python.runtime;

import io.micronaut.context.python.runtime.codec.PythonMetadataCodec;
import io.micronaut.context.python.runtime.codec.PythonMetadataFormatException;
import io.micronaut.context.python.runtime.model.AnnotationMetadataModel;
import io.micronaut.context.python.runtime.model.AnnotationValueModel;
import io.micronaut.context.python.runtime.model.ArrayValueModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.ClassValueModel;
import io.micronaut.context.python.runtime.model.PythonMetadataModel;
import io.micronaut.context.python.runtime.model.ValueKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonMetadataCodecTest {

    static PythonMetadataModel model() {
        Map<String, Object> singleton = Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("string", "text");
        values.put("flag", true);
        values.put("count", 42);
        values.put("big", 42L);
        values.put("ratio", 1.5d);
        values.put("small", 0.5f);
        values.put("letter", 'x');
        values.put("tiny", (byte) 1);
        values.put("brief", (short) 2);
        values.put("type", new ClassValueModel("java.lang.String"));
        values.put("names", new ArrayValueModel(ValueKind.STRING, false, List.of("a", "b")));
        values.put("ints", new ArrayValueModel(ValueKind.INT, true, List.of(1, 2, 3)));
        values.put("nested", new AnnotationValueModel("test.Nested", Map.of("v", "w")));
        values.put("nestedArray", new ArrayValueModel(ValueKind.ANNOTATION, false, List.of(new AnnotationValueModel("test.Nested", Map.of()))));
        Map<String, Map<String, Object>> declared = new LinkedHashMap<>();
        declared.put("jakarta.inject.Singleton", singleton);
        declared.put("test.Custom", values);
        AnnotationMetadataModel metadata = new AnnotationMetadataModel(declared, Map.of("jakarta.inject.Scope", Map.of()),
            Map.of("jakarta.inject.Scope", Map.of()), declared, Map.of("jakarta.inject.Scope", List.of("jakarta.inject.Singleton")),
            Map.of("test.Custom", Map.of("string", "default"), "test.Marker", Map.of()), Map.of("test.Repeated", "test.Container"), true);
        ClassModel classModel = new ClassModel(SampleBean.class.getName(), metadata, PythonMetadataClassGeneratorTest.sample().beanDefinitions(),
            PythonMetadataClassGeneratorTest.sample().introspection());
        return new PythonMetadataModel(PythonMetadataModel.FORMAT_VERSION, "5.3.0-test", "app/models.py", classModel);
    }

    @Test
    void roundTripsAndIsDeterministic() {
        PythonMetadataModel model = model();
        byte[] bytes = PythonMetadataCodec.encode(model);
        assertArrayEquals(bytes, PythonMetadataCodec.encode(model));
        PythonMetadataModel decoded = PythonMetadataCodec.decode(bytes, "test");
        assertEquals(model, decoded);
        assertEquals(PythonMetadataCodec.identity(bytes), PythonMetadataCodec.identity(PythonMetadataCodec.encode(decoded)));
    }

    @Test
    void reportsCorruptionVersionAndMismatch() {
        byte[] bytes = PythonMetadataCodec.encode(model());
        byte[] corrupt = bytes.clone();
        corrupt[corrupt.length / 2] ^= 0x55;
        assertTrue(assertThrows(PythonMetadataFormatException.class, () -> PythonMetadataCodec.decode(corrupt, "corrupt.mpym")).getMessage().contains("checksum"));
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 10);
        assertThrows(PythonMetadataFormatException.class, () -> PythonMetadataCodec.decode(truncated, "truncated.mpym"));
        byte[] wrongMagic = bytes.clone();
        wrongMagic[0] = 'X';
        assertTrue(assertThrows(PythonMetadataFormatException.class, () -> PythonMetadataCodec.decode(wrongMagic, "magic.mpym")).getMessage().contains("magic"));
        byte[] newer = PythonMetadataCodec.encode(new PythonMetadataModel(PythonMetadataModel.FORMAT_VERSION + 1, "x", "y", model().classModel()));
        String message = assertThrows(PythonMetadataFormatException.class, () -> PythonMetadataCodec.decode(newer, "newer.mpym")).getMessage();
        assertTrue(message.contains("format version " + (PythonMetadataModel.FORMAT_VERSION + 1)), message);
        assertTrue(message.contains("newer.mpym"), message);
    }
}
