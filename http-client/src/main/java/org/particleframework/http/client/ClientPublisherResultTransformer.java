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
package org.particleframework.http.client;

/**
 * Allows hooking modifying the resulting publisher prior to returning it from the client. Useful for customization per reactive framework
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ClientPublisherResultTransformer {

    /**
     * Transform the publisher result
     *
     * @param publisherResult The publisher result that is an object that conforms to {@link org.particleframework.core.async.publisher.Publishers#isPublisher(Class)}
     * @return The transformed result
     */
    Object transform(Object publisherResult);
}
