/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.netty;

import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.particleframework.http.MediaType;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;

import java.nio.charset.Charset;

/**
 * Decodes {@link org.particleframework.http.MediaType#MULTIPART_FORM_DATA} in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FormDataHttpContentProcessor extends DefaultHttpContentProcessor {

    private final HttpPostRequestDecoder decoder;
    private final boolean enabled;

    public FormDataHttpContentProcessor(NettyHttpRequest<?> nettyHttpRequest, NettyHttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);
        Charset characterEncoding = nettyHttpRequest.getCharacterEncoding();
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(configuration.getMultipart().isDisk(), characterEncoding);
        factory.setMaxLimit(configuration.getMultipart().getMaxFileSize());
        this.decoder = new HttpPostRequestDecoder(factory, nettyHttpRequest.getNativeRequest(), characterEncoding);
        this.enabled = nettyHttpRequest.getContentType() == MediaType.APPLICATION_FORM_URLENCODED_TYPE ||
                                configuration.getMultipart().isEnabled();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }


    @Override
    protected void publishVerifiedContent(ByteBufHolder message) {
        super.publishVerifiedContent(message);
        if(message instanceof HttpContent) {
            try {
                HttpContent httpContent = (HttpContent) message;
                decoder.offer(httpContent);
                nettyHttpRequest.offer(decoder);
            } catch (Exception e) {
                onError(e);
            } finally {
                message.release();
            }
        }
        else {
            message.release();
        }
    }

    @Override
    public void onComplete() {
        decoder.destroy();
        super.onComplete();
    }
}
