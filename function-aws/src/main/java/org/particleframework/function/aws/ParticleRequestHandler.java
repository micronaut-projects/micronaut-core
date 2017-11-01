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
package org.particleframework.function.aws;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

/**
 * <p>An Amazon Lambda {@link RequestHandler} implementation for Particle {@link org.particleframework.function.Function}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class ParticleRequestHandler<I,O> implements RequestHandler<I, O> {
    @Override
    public abstract O handleRequest(I input, Context context);
}
