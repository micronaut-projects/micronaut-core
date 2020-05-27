/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.handler.accesslog.element;

/**
 * Builder for ElapseTimeElement.
 *
 * @author croudet
 * @since 2.0
 */
public final class ElapseTimeElementBuilder implements LogElementBuilder {

    @Override
    public LogElement build(String token, String param) {
        if (ElapseTimeElement.ELAPSE_TIME_MILLIS.equals(token)) {
            return new ElapseTimeElement(false);
        } else if (ElapseTimeElement.ELAPSE_TIME_SECONDS.equals(token)) {
            return new ElapseTimeElement(true);
        }
        return null;
    }

}
