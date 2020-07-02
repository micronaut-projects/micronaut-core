/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.session.websocket;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import io.micronaut.websocket.event.WebSocketEvent;
import io.micronaut.websocket.event.WebSocketMessageProcessedEvent;
import io.micronaut.websocket.event.WebSocketSessionClosedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

/**
 * Persists the session in the background on web socket events.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = WebSocketEvent.class)
@Requires(beans = SessionStore.class)
@Singleton
@Internal
public class SessionWebSocketEventListener implements ApplicationEventListener<WebSocketEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionWebSocketEventListener.class);

    private final SessionStore<Session> sessionStore;

    /**
     * Default constructor.
     * @param sessionStore The session store
     */
    SessionWebSocketEventListener(SessionStore<Session> sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void onApplicationEvent(WebSocketEvent event) {
        if (event instanceof WebSocketMessageProcessedEvent || event instanceof WebSocketSessionClosedEvent) {
            MutableConvertibleValues<Object> attributes = event.getSource().getAttributes();
            if (attributes instanceof Session) {
                Session session = (Session) attributes;
                if (session.isModified()) {
                    sessionStore.save(session).whenComplete((entries, throwable) -> {
                        if (throwable != null && LOG.isErrorEnabled()) {
                            LOG.error("Error persisting session following WebSocket event: " + throwable.getMessage(), throwable);
                        }
                    });
                }
            }
        }
    }
}
