/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.rxjava2;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.functions.Function;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.annotation.Requires;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.http.client.interceptor.HttpClientIntroductionAdvice;
import org.particleframework.runtime.server.EmbeddedServer;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Extended version of {@link HttpClientIntroductionAdvice} with RxJava 2.x specific extensions
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Replaces(HttpClientIntroductionAdvice.class)
@Requires(classes = Flowable.class)
public class RxHttpClientIntroductionAdvice extends HttpClientIntroductionAdvice {
    public RxHttpClientIntroductionAdvice(BeanContext beanContext, Optional<EmbeddedServer> embeddedServer) {
        super(beanContext, embeddedServer);
    }

    @Override
    protected Object finalizePublisher(Object finalPublisher) {
        if(finalPublisher instanceof Maybe) {
            Maybe<?> maybe = (Maybe) finalPublisher;
            // add 404 handling for maybe
            return maybe.onErrorResumeNext(throwable -> {
                if(throwable instanceof HttpClientResponseException) {
                    HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                    if(responseException.getStatus() == HttpStatus.NOT_FOUND) {
                        return Maybe.empty();
                    }
                }
                return Maybe.error(throwable);
            });
        }
        else {
            return super.finalizePublisher(finalPublisher);
        }
    }
}
