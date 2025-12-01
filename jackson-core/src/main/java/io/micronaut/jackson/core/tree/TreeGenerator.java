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
package io.micronaut.jackson.core.tree;

import tools.jackson.core.Base64Variant;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.SerializableString;
import tools.jackson.core.StreamWriteCapability;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TreeNode;
import tools.jackson.core.Version;
import tools.jackson.core.util.JacksonFeatureSet;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NonNull;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link JsonGenerator} that returns tokens as a {@link JsonNode}.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Experimental
public final class TreeGenerator extends JsonGenerator {

    private ObjectWriteContext codec;
    private int generatorFeatures;

    private final Deque<StructureBuilder> structureStack = new ArrayDeque<>();
    private JsonNode completed = null;
    private Object currentValue;

    TreeGenerator() {
    }

    @Override
    public ObjectWriteContext objectWriteContext() {
        return codec;
    }

    @Override
    public Object streamWriteOutputTarget() {
        return null;
    }

    @Override
    public int streamWriteOutputBuffered() {
        return -1;
    }

    @Override
    public Object currentValue() {
        return currentValue;
    }

    @Override
    public void assignCurrentValue(Object v) {
        currentValue = v;
    }

    @Override
    public JsonGenerator configure(StreamWriteFeature f, boolean state) {
        return this;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public TokenStreamContext streamWriteContext() {
        return null;
    }

    @Override
    public boolean isEnabled(StreamWriteFeature f) {
        return (generatorFeatures & f.getMask()) != 0;
    }

    @Override
    public int streamWriteFeatures() {
        return 0;
    }

    @Override
    public boolean has(StreamWriteCapability capability) {
        return false;
    }

    @Override
    public JacksonFeatureSet<StreamWriteCapability> streamWriteCapabilities() {
        return JacksonFeatureSet.fromDefaults(StreamWriteCapability.values());
    }

    private void checkEmptyNodeStack(JsonToken token) throws StreamWriteException {
        if (structureStack.isEmpty()) {
            throw new StreamWriteException(this, "Unexpected " + tokenType(token) + " literal");
        }
    }

    private static String tokenType(JsonToken token) {
        return switch (token) {
            case END_OBJECT, END_ARRAY -> "container end";
            case PROPERTY_NAME -> "field";
            case VALUE_NUMBER_INT -> "integer";
            case VALUE_STRING -> "string";
            case VALUE_NUMBER_FLOAT -> "float";
            case VALUE_NULL -> "null";
            case VALUE_TRUE, VALUE_FALSE -> "boolean";
            default -> "";
        };
    }

    private void complete(JsonNode value) throws StreamWriteException {
        if (completed != null) {
            throw new StreamWriteException(this, "Tree generator has already completed");
        }
        completed = value;
    }

    /**
     * @return Whether this generator has visited a complete node.
     */
    public boolean isComplete() {
        return completed != null;
    }

    /**
     * @return The completed node.
     * @throws IllegalStateException If there is still data missing. Check with {@link #isComplete()}.
     */
    @NonNull
    public JsonNode getCompletedValue() {
        if (!isComplete()) {
            throw new IllegalStateException("Not completed");
        }
        return completed;
    }

    @Override
    public JsonGenerator writeStartArray() {
        structureStack.push(new ArrayBuilder());
        return this;
    }

    @Override
    public JsonGenerator writeStartArray(Object currentValue) throws JacksonException {
        return writeStartArray();
    }

    @Override
    public JsonGenerator writeStartArray(Object currentValue, int size) throws JacksonException {
        return writeStartArray();
    }

    private JsonGenerator writeEndStructure(JsonToken token) throws StreamWriteException {
        checkEmptyNodeStack(token);
        final StructureBuilder current = structureStack.pop();
        if (structureStack.isEmpty()) {
            complete(current.build());
        } else {
            structureStack.peekFirst().addValue(current.build());
        }
        return null;
    }

    @Override
    public JsonGenerator writeEndArray() {
        return writeEndStructure(JsonToken.END_ARRAY);
    }

    @Override
    public JsonGenerator writeStartObject() {
        structureStack.push(new ObjectBuilder());
        return this;
    }

    @Override
    public JsonGenerator writeStartObject(Object currentValue) throws JacksonException {
        return writeStartObject();
    }

    @Override
    public JsonGenerator writeStartObject(Object forValue, int size) throws JacksonException {
        return writeStartObject();
    }

    @Override
    public JsonGenerator writeEndObject() {
        return writeEndStructure(JsonToken.END_OBJECT);
    }

    @Override
    public JsonGenerator writeName(String name) {
        checkEmptyNodeStack(JsonToken.PROPERTY_NAME);
        structureStack.peekFirst().setCurrentFieldName(name);
        return this;
    }

    @Override
    public JsonGenerator writeName(SerializableString name) {
        return writeName(name.getValue());
    }

    @Override
    public JsonGenerator writePropertyId(long id) throws JacksonException {
        return writeName(Long.toString(id));
    }

    private JsonGenerator writeScalar(JsonToken token, JsonNode value) throws StreamWriteException {
        if (structureStack.isEmpty()) {
            complete(value);
        } else {
            structureStack.peekFirst().addValue(value);
        }
        return this;
    }

    @Override
    public JsonGenerator writeString(String text) {
        return writeScalar(JsonToken.VALUE_STRING, JsonNode.createStringNode(text));
    }

    @Override
    public JsonGenerator writeString(Reader reader, int len) throws JacksonException {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeString(char[] buffer, int offset, int len) {
        return writeString(new String(buffer, offset, len));
    }

    @Override
    public JsonGenerator writeString(SerializableString text) {
        return writeString(text.getValue());
    }

    @Override
    public JsonGenerator writeRawUTF8String(byte[] buffer, int offset, int len) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeUTF8String(byte[] buffer, int offset, int len) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(String text) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(String text, int offset, int len) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(char[] text, int offset, int len) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRaw(char c) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeRawValue(String text) {
        return writePOJO(text);
    }

    @Override
    public JsonGenerator writeRawValue(String text, int offset, int len) {
        return writeRawValue(text.substring(offset, len));
    }

    @Override
    public JsonGenerator writeRawValue(char[] text, int offset, int len) {
        return writeRawValue(new String(text, offset, len));
    }

    @Override
    public JsonGenerator writeBinary(Base64Variant bv, byte[] data, int offset, int len) {
        return _reportUnsupportedOperation();
    }

    @Override
    public int writeBinary(Base64Variant bv, InputStream data, int dataLength) {
        _reportUnsupportedOperation();
        return 0;
    }

    @Override
    public JsonGenerator writeNumber(short v) throws JacksonException {
        return writeScalar(JsonToken.VALUE_NUMBER_INT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(int v) {
        return writeScalar(JsonToken.VALUE_NUMBER_INT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(long v) {
        return writeScalar(JsonToken.VALUE_NUMBER_INT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(BigInteger v) {
        // the tree codec could normalize
        return writeScalar(JsonToken.VALUE_NUMBER_INT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(double v) {
        return writeScalar(JsonToken.VALUE_NUMBER_FLOAT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(float v) {
        return writeScalar(JsonToken.VALUE_NUMBER_FLOAT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(BigDecimal v) {
        return writeScalar(JsonToken.VALUE_NUMBER_FLOAT, JsonNode.createNumberNode(v));
    }

    @Override
    public JsonGenerator writeNumber(String encodedValue) {
        return _reportUnsupportedOperation();
    }

    @Override
    public JsonGenerator writeBoolean(boolean state) {
        return writeScalar(state ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE, JsonNode.createBooleanNode(state));
    }

    @Override
    public JsonGenerator writeNull() {
        return writeScalar(JsonToken.VALUE_NULL, JsonNode.nullNode());
    }

    @Override
    public JsonGenerator writePOJO(Object pojo) {
        objectWriteContext().writeValue(this, pojo);
        return this;
    }

    @Override
    public JsonGenerator writeTree(TreeNode rootNode) {
        if (rootNode == null) {
            return writeNull();
        } else if (rootNode instanceof JsonNode node) {
            return writeScalar(JsonToken.VALUE_EMBEDDED_OBJECT, node);
        } else {
            try {
                JsonStreamTransfer.transferNext(rootNode.traverse(ObjectReadContext.empty()), this, JsonStreamConfig.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {
    }

    private interface StructureBuilder {
        void addValue(JsonNode value) throws StreamWriteException;

        void setCurrentFieldName(String currentFieldName) throws StreamWriteException;

        JsonNode build();
    }

    private final class ArrayBuilder implements StructureBuilder {

        final List<JsonNode> values = new ArrayList<>();

        @Override
        public void addValue(JsonNode value) {
            values.add(value);
        }

        @Override
        public void setCurrentFieldName(String currentFieldName) throws StreamWriteException {
            throw new StreamWriteException(TreeGenerator.this, "Expected array value, got field name");
        }

        @Override
        public JsonNode build() {
            return JsonNode.createArrayNode(values);
        }
    }

    private final class ObjectBuilder implements StructureBuilder {

        final Map<String, JsonNode> values = new LinkedHashMap<>();
        String currentFieldName = null;

        @Override
        public void addValue(JsonNode value) throws StreamWriteException {
            if (currentFieldName == null) {
                throw new StreamWriteException(TreeGenerator.this, "Expected field name, got value");
            }
            values.put(currentFieldName, value);
            currentFieldName = null;
        }

        @Override
        public void setCurrentFieldName(String currentFieldName) {
            this.currentFieldName = currentFieldName;
        }

        @Override
        public JsonNode build() {
            return JsonNode.createObjectNode(values);
        }
    }
}
