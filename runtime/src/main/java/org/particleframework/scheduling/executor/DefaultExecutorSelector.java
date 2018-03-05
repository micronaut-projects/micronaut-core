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
package org.particleframework.scheduling.executor;

import org.particleframework.core.annotation.NonBlocking;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.inject.MethodReference;
import org.particleframework.scheduling.Schedulers;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation of the {@link ExecutorSelector} interface that regards methods that return reactive types as non-blocking
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultExecutorSelector implements ExecutorSelector {


    private final ExecutorService ioExecutor;

    protected DefaultExecutorSelector(@Named(Schedulers.IO) ExecutorService ioExecutor) {
        this.ioExecutor = ioExecutor;
    }

    @Override
    public Optional<ExecutorService> select(MethodReference method) {
        if( method.hasStereotype(NonBlocking.class) ) {
            return Optional.empty();
        }
        else {
            Class returnType = method.getReturnType().getType();
            if(Publishers.isConvertibleToPublisher(returnType) || CompletableFuture.class.isAssignableFrom(returnType)) {
                return Optional.empty();
            }
        }
        return Optional.of(ioExecutor);
    }
}
