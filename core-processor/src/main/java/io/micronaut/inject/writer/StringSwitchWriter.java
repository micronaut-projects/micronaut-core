/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.TableSwitchGenerator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * String switch writer.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public abstract class StringSwitchWriter {

    /**
     * @return Get cases keys
     */
    protected abstract Set<String> getKeys();

    /**
     * Push the string value that is being evaluated.
     */
    protected abstract void pushStringValue();

    /**
     * Generate on case matches statement.
     *
     * @param value The string that matched
     * @param end   The end label
     */
    protected abstract void onMatch(String value, Label end);

    /**
     * Generate default statement.
     */
    protected void generateDefault() {
    }

    /**
     * Write the string switch implementation.
     *
     * @param writer The writer
     */
    public void write(GeneratorAdapter writer) {
        Set<String> keys = getKeys();
        if (keys.isEmpty()) {
            return;
        }
        if (keys.size() == 1) {
            Label end = new Label();
            String key = keys.iterator().next();
            generateValueCase(writer, key, end);
            writer.visitLabel(end);
            return;
        }
        Map<Integer, Set<String>> hashToValue = new HashMap<>();
        for (String string : keys) {
            hashToValue.computeIfAbsent(string.hashCode(), hashCode -> new TreeSet<>()).add(string);
        }
        int[] hashCodeArray = hashToValue.keySet().stream().mapToInt(i -> i).toArray();
        Arrays.sort(hashCodeArray);
        pushStringValue();
        writer.invokeVirtual(Type.getType(Object.class), new Method("hashCode", Type.INT_TYPE, new Type[]{}));
        writer.tableSwitch(hashCodeArray, new TableSwitchGenerator() {
            @Override
            public void generateCase(int hashCode, Label end) {
                for (String string : hashToValue.get(hashCode)) {
                    generateValueCase(writer, string, end);
                }
                writer.goTo(end);
            }

            @Override
            public void generateDefault() {
                StringSwitchWriter.this.generateDefault();
            }
        });
    }

    /**
     * Generate the switch case.
     *
     * @param writer The writer
     * @param string The string matched
     * @param end    The end label
     */
    protected void generateValueCase(GeneratorAdapter writer, String string, Label end) {
        pushStringValue();
        writer.push(string);
        writer.invokeVirtual(Type.getType(Object.class), new Method("equals", Type.BOOLEAN_TYPE, new Type[]{Type.getType(Object.class)}));
        writer.push(true);
        Label falseLabel = new Label();
        writer.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, falseLabel);
        onMatch(string, end);
        writer.visitLabel(falseLabel);
    }

}
