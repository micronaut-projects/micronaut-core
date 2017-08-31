package org.particleframework.configuration.jackson.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.json.async.NonBlockingJsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class JacksonProcessor implements Publisher<JsonNode> {

    private final JsonFactory jsonFactory;
    private final NonBlockingJsonParser nonBlockingJsonParser;
    private final AtomicReference<JsonNode> jsonNode = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final ConcurrentLinkedQueue<Subscription> subscriptions = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Subscriber<? super JsonNode>> requested = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedDeque<JsonNode> nodeStack = new ConcurrentLinkedDeque<>();
    private String currentFieldName;

    public JacksonProcessor(JsonFactory jsonFactory) throws IOException {
        this.jsonFactory = jsonFactory;
        this.nonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
    }

    public JacksonProcessor() throws IOException {
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
                        subscriptions.remove(this);
                    }
                }

                @Override
                public void cancel() {
                    subscriptions.remove(this);
                }
            };
            subscriptions.add(subscription);
            subscriber.onSubscribe(subscription);
        }
        else if(jsonNodeObject != null) {
            Subscription subscription = new Subscription() {
                @Override
                public void request(long n) {
                    if (n > 0) {
                        subscriber.onNext(jsonNodeObject);
                        subscriber.onComplete();
                        subscriptions.remove(this);
                    }
                }

                @Override
                public void cancel() {
                    subscriptions.remove(this);
                }
            };
            subscriptions.add(subscription);
            subscriber.onSubscribe(subscription);
        }
        else {

            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    if(n > 0) {
                        JsonNode jsonNode = JacksonProcessor.this.jsonNode.get();
                        if(jsonNode == null) {
                            requested.add(subscriber);
                        }
                        else {
                            subscriptions.remove(this);
                            subscriber.onNext(jsonNode);
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                    requested.remove(subscriber);
                    subscriptions.remove(this);
                }
            });
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
                    JsonNode root = buildTree(event);
                    if (root != null) {
                        byteFeeder.endOfInput();
                        this.jsonNode.set(root);
                        for (Subscriber<? super JsonNode> subscriber : requested) {
                            subscriber.onNext(root);
                            subscriber.onComplete();
                        }
                        requested.clear();
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
    private JsonNode buildTree(JsonToken event) throws IOException {
        switch (event) {
            case START_OBJECT:
                nodeStack.push(createNode(nodeStack.peekLast()));
                break;

            case START_ARRAY:
                nodeStack.push(createArray(nodeStack.peekLast()));
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

//            case VALUE_STRING:
//                assert !nodeStack.isEmpty();
//                addString(nodeStack.peekLast(), nonBlockingJsonParser.getValueAsString());
//                break;
//
//            case VALUE_NUMBER_FLOAT:
//                assert !nodeStack.isEmpty();
//                addFloat(nodeStack.peekLast(), nonBlockingJsonParser.getFloatValue());
//                break;
//
//            case VALUE_NULL:
//                assert !nodeStack.isEmpty();
//                addNull(nodeStack.peekLast());
//                break;
//
//            case VALUE_TRUE:
//            case VALUE_FALSE:
//                assert !nodeStack.isEmpty();
//                addBoolean(nodeStack.peekLast(), nonBlockingJsonParser.getBooleanValue());
//                break;

            default:
                throw new IllegalStateException("Unsupported JSON event: " + event);
        }

        return null;
    }

    private JsonNode createArray(JsonNode jsonNode) {
        return null;
    }

    private JsonNode createNode(JsonNode jsonNode) {
        return null;
    }

}
