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

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

/**
 * {@link JsonParser} implementation that iterates over a {@link JsonNode}.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
final class JsonNodeTraversingParser extends ParserMinimalBase {
    private final Deque<Context> contextStack = new ArrayDeque<>();

    private boolean first = true;

    private ObjectCodec codec = null;

    JsonNodeTraversingParser(JsonNode node) {
        Context root;
        if (node.isArray()) {
            root = new ArrayContext(null, node.values().iterator());
        } else if (node.isObject()) {
            root = new ObjectContext(null, node.entries().iterator());
        } else {
            root = new SingleContext(node);
        }
        contextStack.add(root);
    }

    private JsonNode currentNodeOrNull() {
        for (Context context : contextStack) {
            JsonNode node = context.currentNode();
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    @Override
    public JsonToken nextToken() throws IOException {
        if (first) {
            // the context stack starts out positioned on the first token, so we need to intercept the first nextToken call.
            first = false;
            assert !contextStack.isEmpty();
            _currToken = contextStack.peekFirst().currentToken();
            return _currToken;
        }

        while (true) {
            if (contextStack.isEmpty()) {
                return null;
            }
            Context context = contextStack.peekFirst();
            if (context.lastToken) {
                contextStack.removeFirst();
            } else {
                Context childContext = context.next();
                if (childContext != null) {
                    contextStack.addFirst(childContext);
                    _currToken = childContext.currentToken();
                } else {
                    _currToken = context.currentToken();
                }
                return _currToken;
            }
        }
    }

    @Override
    protected void _handleEOF() throws JsonParseException {
        _throwInternal();
    }

    @Override
    public String getCurrentName() throws IOException {
        return contextStack.isEmpty() ? null : contextStack.peekFirst().getCurrentName();
    }

    @Override
    public ObjectCodec getCodec() {
        return codec;
    }

    @Override
    public void setCodec(ObjectCodec oc) {
        this.codec = oc;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void close() throws IOException {
        contextStack.clear();
    }

    @Override
    public boolean isClosed() {
        return contextStack.isEmpty();
    }

    @Override
    public JsonStreamContext getParsingContext() {
        // may be null
        return contextStack.peekFirst();
    }

    @Override
    public JsonLocation getTokenLocation() {
        return JsonLocation.NA;
    }

    @Override
    public JsonLocation getCurrentLocation() {
        return JsonLocation.NA;
    }

    @Override
    public void overrideCurrentName(String name) {
        if (!contextStack.isEmpty()) {
            contextStack.peekFirst().setCurrentName(name);
        }
    }

    @Override
    public String getText() throws IOException {
        if (contextStack.isEmpty()) {
            return null;
        } else {
            return contextStack.peekFirst().getText();
        }
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        return getText().toCharArray();
    }

    @Override
    public boolean hasTextCharacters() {
        return false;
    }

    private JsonNode currentNumberNode() throws JsonParseException {
        JsonNode node = currentNodeOrNull();
        if (node != null && node.isNumber()) {
            return node;
        } else {
            throw new JsonParseException(this, "Not a number");
        }
    }

    @Override
    public Number getNumberValue() throws IOException {
        return currentNumberNode().getNumberValue();
    }

    @Override
    public NumberType getNumberType() throws IOException {
        JsonNode currentNode = currentNodeOrNull();
        if (currentNode == null || !currentNode.isNumber()) {
            return null;
        }
        Number value = currentNode.getNumberValue();
        if (value instanceof BigDecimal) {
            return JsonParser.NumberType.BIG_DECIMAL;
        } else if (value instanceof Double) {
            return JsonParser.NumberType.DOUBLE;
        } else if (value instanceof Float) {
            return JsonParser.NumberType.FLOAT;
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return JsonParser.NumberType.INT;
        } else if (value instanceof Long) {
            return JsonParser.NumberType.LONG;
        } else if (value instanceof BigInteger) {
            return JsonParser.NumberType.BIG_INTEGER;
        } else {
            throw new IllegalStateException("Unknown number type " + value.getClass().getName());
        }
    }

    @Override
    public int getIntValue() throws IOException {
        return currentNumberNode().getIntValue();
    }

    @Override
    public long getLongValue() throws IOException {
        return currentNumberNode().getLongValue();
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        return currentNumberNode().getBigIntegerValue();
    }

    @Override
    public float getFloatValue() throws IOException {
        return currentNumberNode().getFloatValue();
    }

    @Override
    public double getDoubleValue() throws IOException {
        return currentNumberNode().getDoubleValue();
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        return currentNumberNode().getBigDecimalValue();
    }

    @Override
    public int getTextLength() throws IOException {
        return getText().length();
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException {
        return null;
    }

    private static String nodeToText(JsonNode node) {
        if (node.isString()) {
            return node.getStringValue();
        } else if (node.isBoolean()) {
            return Boolean.toString(node.getBooleanValue());
        } else if (node.isNumber()) {
            return node.getNumberValue().toString();
        } else if (node.isNull()) {
            return "null";
        } else if (node.isArray()) {
            return "[";
        } else {
            return "{";
        }
    }

    private static JsonToken asToken(JsonNode node) {
        if (node.isString()) {
            return JsonToken.VALUE_STRING;
        } else if (node.isBoolean()) {
            return node.getBooleanValue() ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
        } else if (node.isNumber()) {
            Number numberValue = node.getNumberValue();
            return numberValue instanceof Float || numberValue instanceof Double || numberValue instanceof BigDecimal ?
                    JsonToken.VALUE_NUMBER_FLOAT : JsonToken.VALUE_NUMBER_INT;
        } else if (node.isNull()) {
            return JsonToken.VALUE_NULL;
        } else if (node.isArray()) {
            return JsonToken.START_ARRAY;
        } else {
            return JsonToken.START_OBJECT;
        }
    }

    private abstract static class Context extends JsonStreamContext {
        boolean lastToken = false;

        /**
         * Only used to implement {@link JsonStreamContext#getParent()}.
         */
        private final Context parent;

        Context(Context parent) {
            this.parent = parent;
        }

        protected Context createSubContextIfContainer(JsonNode node) {
            if (node.isArray()) {
                return new ArrayContext(this, node.values().iterator());
            } else if (node.isObject()) {
                return new ObjectContext(this, node.entries().iterator());
            } else {
                return null;
            }
        }

        @Override
        public final Context getParent() {
            return parent;
        }

        @Nullable
        abstract Context next();

        @Nullable
        abstract JsonNode currentNode();

        @Override
        public abstract String getCurrentName();

        abstract void setCurrentName(String currentName);

        abstract JsonToken currentToken();

        abstract String getText();
    }

    private static class ArrayContext extends Context {
        final Iterator<JsonNode> iterator;
        /**
         * If {@code null}, we're either at the start or the end array token.
         */
        JsonNode currentNode = null;

        ArrayContext(Context parent, Iterator<JsonNode> iterator) {
            super(parent);
            this._type = TYPE_ARRAY;
            this.iterator = iterator;
        }

        @Override
        @Nullable
        Context next() {
            if (iterator.hasNext()) {
                currentNode = iterator.next();
                return createSubContextIfContainer(currentNode);
            } else {
                currentNode = null;
                lastToken = true;
                return null;
            }
        }

        @Override
        JsonNode currentNode() {
            return currentNode;
        }

        @Override
        public String getCurrentName() {
            return null;
        }

        @Override
        void setCurrentName(String currentName) {
        }

        @Override
        JsonToken currentToken() {
            if (currentNode == null) {
                return lastToken ? JsonToken.END_ARRAY : JsonToken.START_ARRAY;
            } else {
                return asToken(currentNode);
            }
        }

        @Override
        String getText() {
            if (currentNode != null) {
                return nodeToText(currentNode);
            } else {
                return currentToken().asString();
            }
        }
    }

    private static class ObjectContext extends Context {
        final Iterator<Map.Entry<String, JsonNode>> iterator;
        @Nullable String currentName = null;
        @Nullable JsonNode currentValue = null;

        boolean inFieldName = false;

        ObjectContext(Context parent, Iterator<Map.Entry<String, JsonNode>> iterator) {
            super(parent);
            this._type = TYPE_OBJECT;
            this.iterator = iterator;
        }

        @Nullable
        @Override
        Context next() {
            if (inFieldName) {
                inFieldName = false;
                assert currentValue != null;
                return createSubContextIfContainer(currentValue);
            } else {
                if (iterator.hasNext()) {
                    Map.Entry<String, JsonNode> entry = iterator.next();
                    currentName = entry.getKey();
                    currentValue = entry.getValue();
                    inFieldName = true;
                } else {
                    lastToken = true;
                    currentName = null;
                    currentValue = null;
                }
                return null;
            }
        }

        @Nullable
        @Override
        JsonNode currentNode() {
            return inFieldName ? null : currentValue;
        }

        @Override
        @Nullable
        public String getCurrentName() {
            return currentName;
        }

        @Override
        void setCurrentName(@Nullable String currentName) {
            this.currentName = currentName;
        }

        @Override
        JsonToken currentToken() {
            if (inFieldName) {
                return JsonToken.FIELD_NAME;
            } else if (currentValue != null) {
                return asToken(currentValue);
            } else if (lastToken) {
                return JsonToken.END_OBJECT;
            } else {
                return JsonToken.START_OBJECT;
            }
        }

        @Override
        String getText() {
            if (inFieldName) {
                return currentName;
            } else if (currentValue != null) {
                return nodeToText(currentValue);
            } else {
                return currentToken().asString();
            }
        }
    }

    /**
     * Used as the singular context when the root object we're traversing is a scalar.
     */
    private static class SingleContext extends Context {
        private final JsonNode value;

        SingleContext(JsonNode value) {
            super(null);
            this._type = TYPE_ROOT;
            this.value = value;
            this.lastToken = true;
        }

        @Nullable
        @Override
        Context next() {
            return null;
        }

        @Nullable
        @Override
        JsonNode currentNode() {
            return value;
        }

        @Override
        public String getCurrentName() {
            return null;
        }

        @Override
        void setCurrentName(String currentName) {
        }

        @Override
        JsonToken currentToken() {
            return asToken(value);
        }

        @Override
        String getText() {
            return nodeToText(value);
        }
    }
}
