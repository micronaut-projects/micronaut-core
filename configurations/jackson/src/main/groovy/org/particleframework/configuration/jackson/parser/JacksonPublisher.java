package org.particleframework.configuration.jackson.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.json.async.NonBlockingJsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Reactive streams publisher that publishes a {@link JsonNode} once the JSON has been fully consumed.
 * Uses {@link com.fasterxml.jackson.core.json.async.NonBlockingJsonParser} internally allowing the parsing of JSON from an
 * incoming stream of bytes in a non-blocking manner
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JacksonPublisher implements Publisher<JsonNode> {

    private final NonBlockingJsonParser nonBlockingJsonParser;
    private final AtomicReference<JsonNode> jsonNode = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final ConcurrentLinkedQueue<Subscriber<? super JsonNode>> requested = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedDeque<JsonNode> nodeStack = new ConcurrentLinkedDeque<>();
    private String currentFieldName;

    public JacksonPublisher(JsonFactory jsonFactory) throws IOException {
        this.nonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
    }

    public JacksonPublisher() throws IOException {
        this(new JsonFactory());
    }

    @Override
    public void subscribe(Subscriber<? super JsonNode> subscriber) {
        JsonNode jsonNodeObject = this.jsonNode.get();
        Throwable thrownError = this.error.get();
        if(thrownError != null) {
            Subscription subscription = new Subscription() {
                @Override
                public void request(long n) {
                    if (n > 0) {
                        subscriber.onError(thrownError);
                    }
                }

                @Override
                public void cancel() {
                    // no-op
                }
            };
            subscriber.onSubscribe(subscription);
        }
        else if(jsonNodeObject != null) {
            Subscription subscription = new Subscription() {
                @Override
                public void request(long n) {
                    if (n > 0) {
                        subscriber.onNext(jsonNodeObject);
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    // no-op
                }
            };
            subscriber.onSubscribe(subscription);
        }
        else {

            Subscription subscription = new Subscription() {
                @Override
                public void request(long n) {
                    if (n > 0) {
                        JsonNode jsonNode = JacksonPublisher.this.jsonNode.get();
                        if (jsonNode == null) {
                            requested.add(subscriber);
                        } else {
                            subscriber.onNext(jsonNode);
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                    requested.remove(subscriber);
                }
            };
            subscriber.onSubscribe(subscription);
        }
    }

    /**
     * This method should be invoked by the thread which provides the bytes that produces the fully formed JSON. This could be in chunks, using
     * a ByteBuf or whatever form.
     *
     * Once fully formed JSON is produced future calls to this method will simply be ignored
     *
     * @param bytes The bytes
     */
    public void onNext(byte[] bytes) {
        try {
            ByteArrayFeeder byteFeeder = nonBlockingJsonParser.getNonBlockingInputFeeder();
            boolean consumed = false;
            while (!consumed && byteFeeder.needMoreInput()) {
                if (byteFeeder.needMoreInput()) {
                    byteFeeder.feedInput(bytes, 0, bytes.length);
                    consumed = true;
                }

                JsonToken event;
                while ((event = nonBlockingJsonParser.nextToken()) != JsonToken.NOT_AVAILABLE) {
                    JsonNode root = asJsonNode(event);
                    if (root != null) {
                        byteFeeder.endOfInput();
                        this.jsonNode.set(root);
                        for (Subscriber<? super JsonNode> subscriber : requested) {
                            subscriber.onNext(root);
                            subscriber.onComplete();
                        }
                        requested.clear();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            this.error.set(e);
            for (Subscriber<? super JsonNode> subscriber : requested) {
                subscriber.onError(e);
            }
            requested.clear();
        }
    }

    /**
     * @return The root node when the whole tree is built.
     **/
    private JsonNode asJsonNode(JsonToken event) throws IOException {
        switch (event) {
            case START_OBJECT:
                nodeStack.push(node(nodeStack.peekLast()));
                break;

            case START_ARRAY:
                nodeStack.push(array(nodeStack.peekLast()));
                break;

            case END_OBJECT:
            case END_ARRAY:
                assert !nodeStack.isEmpty();
                JsonNode current = nodeStack.pop();
                if (nodeStack.isEmpty())
                    return current;
                else
                    return null;

            case FIELD_NAME:
                assert !nodeStack.isEmpty();
                currentFieldName = nonBlockingJsonParser.getCurrentName();
                break;

            case VALUE_NUMBER_INT:
                assert !nodeStack.isEmpty();
                JsonNode intNode = nodeStack.peekLast();
                if (intNode instanceof ObjectNode) {
                    ((ObjectNode)intNode).put(currentFieldName, nonBlockingJsonParser.getLongValue());
                }
                else {
                    ((ArrayNode)intNode).add(nonBlockingJsonParser.getLongValue());
                }
                break;

            case VALUE_STRING:
                assert !nodeStack.isEmpty();
                JsonNode stringNode = nodeStack.peekLast();
                if (stringNode instanceof ObjectNode) {
                    ((ObjectNode)stringNode).put(currentFieldName, nonBlockingJsonParser.getValueAsString());
                }
                else {
                    ((ArrayNode)stringNode).add(nonBlockingJsonParser.getValueAsString());
                }
                break;

            case VALUE_NUMBER_FLOAT:
                assert !nodeStack.isEmpty();
                JsonNode floatNode = nodeStack.peekLast();
                if (floatNode instanceof ObjectNode) {
                    ((ObjectNode)floatNode).put(currentFieldName, nonBlockingJsonParser.getFloatValue());
                }
                else {
                    ((ArrayNode)floatNode).add(nonBlockingJsonParser.getFloatValue());
                }
                break;
            case VALUE_NULL:
                assert !nodeStack.isEmpty();
                JsonNode nullNode = nodeStack.peekLast();
                if (nullNode instanceof ObjectNode) {
                    ((ObjectNode)nullNode).putNull(currentFieldName);
                }
                else {
                    ((ArrayNode)nullNode).addNull();
                }
                break;

            case VALUE_TRUE:
            case VALUE_FALSE:
                assert !nodeStack.isEmpty();
                JsonNode booleanNode = nodeStack.peekLast();
                if (booleanNode instanceof ObjectNode) {
                    ((ObjectNode)booleanNode).put(currentFieldName, nonBlockingJsonParser.getBooleanValue());
                }
                else {
                    ((ArrayNode)booleanNode).add(nonBlockingJsonParser.getBooleanValue());
                }
                break;

            default:
                throw new IllegalStateException("Unsupported JSON event: " + event);
        }

        return null;
    }

    private JsonNode array(JsonNode node) {
        if (node instanceof ObjectNode)
            return ((ObjectNode)node).putArray(currentFieldName);
        else if (node instanceof ArrayNode)
            return ((ArrayNode)node).addArray();
        else
            return JsonNodeFactory.instance.arrayNode();
    }

    private JsonNode node(JsonNode node) {
        if (node instanceof ObjectNode)
            return ((ObjectNode)node).putObject(currentFieldName);
        else if (node instanceof ArrayNode)
            return ((ArrayNode)node).addObject();
        else
            return JsonNodeFactory.instance.objectNode();
    }

}
