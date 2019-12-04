/*
 *
 *  * Copyright 2017-2019 original authors
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package io.micronaut.xml.server;

import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractBufferingHttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.xml.server.convert.ByteArrayXmlStreamReader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;

/**
 * This class will handle subscribing to a Xml stream and binding once the events are complete in a non-blocking
 * manner.
 *
 * @author svishnyakov
 * @author jameskleeh
 * @since 1.2
 */
public class XmlContentProcessor extends AbstractBufferingHttpContentProcessor<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlContentProcessor.class);

    private final ByteArrayOutputStream byteArrayStream;


    /**
     * @param nettyHttpRequest The {@link NettyHttpRequest}
     * @param configuration    The {@link HttpServerConfiguration}
     */
    public XmlContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);

        byteArrayStream = new ByteArrayOutputStream((int) this.advertisedLength);
    }

    @Override
    public void subscribe(Subscriber<? super Object> downstreamSubscriber) {
        super.subscribe(downstreamSubscriber);
    }

    @Override
    protected void onUpstreamMessage(ByteBufHolder message) {
        ByteBuf content = message.content();
        byte[] bytes = ByteBufUtil.getBytes(content);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Buffer xml bytes of size {}", bytes.length);
        }

        byteArrayStream.write(bytes, 0, bytes.length);
        upstreamSubscription.request(1);
    }

    @Override
    protected void doOnComplete() {
        try {
            sendXmlStreamToSubscriber();
            super.doOnComplete();
        } catch (Exception e) {
            doOnError(e);
        }
    }

    private synchronized void sendXmlStreamToSubscriber() {
        currentDownstreamSubscriber().ifPresent(subscriber -> {
            try {
                byte[] bytes = byteArrayStream.toByteArray();
                ByteArrayXmlStreamReader byteArrayXmlStreamReader = new ByteArrayXmlStreamReader(bytes);
                subscriber.onNext(byteArrayXmlStreamReader);
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
        });
    }
}
