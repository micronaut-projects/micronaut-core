package io.micronaut.http.server.netty.xml;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncByteBufferFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractHttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.jackson.parser.JacksonProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.codehaus.stax2.XMLStreamReader2;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

public class XmlContentProcessor extends AbstractHttpContentProcessor<XMLStreamReader2> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlContentProcessor.class);

    private AsyncXMLStreamReader<AsyncByteArrayFeeder> parser;
    private AsyncByteArrayFeeder feeder;

    public XmlContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);
    }

    @Override
    protected void doOnSubscribe(Subscription subscription, Subscriber<? super XMLStreamReader2> subscriber) {
        if (parentSubscription == null) {
            return;
        }

        AsyncXMLInputFactory inputFactory = new InputFactoryImpl();
        parser = inputFactory.createAsyncForByteArray();
        feeder = parser.getInputFeeder();
        super.doOnSubscribe(subscription, subscriber);
    }

    @Override
    protected void onData(ByteBufHolder message) {
        ByteBuf content = message.content();
        try {
            byte[] bytes = ByteBufUtil.getBytes(content);
            if (bytes.length == 0) {
                if (feeder.needMoreInput()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("More input required to parse XML. Demanding more.");
                    }
                    parentSubscription.request(1);
                }
                return;
            }
            if (feeder.needMoreInput()) {
                parser.getInputFeeder().feedInput(bytes, 0, bytes.length);
            }
        } catch (XMLStreamException e) {
            onError(e);
        } finally {
            ReferenceCountUtil.release(content);
        }
    }

    @Override
    protected void doOnComplete() {
        feeder.endOfInput();
        try {
            if (parser.next() == XMLStreamConstants.END_DOCUMENT) {
                super.doOnComplete();
            } else {
                doOnError(new XMLStreamException("Unexpected end-of-input"));
            }
        } catch (XMLStreamException e) {
            doOnError(e);
        }
    }
}
