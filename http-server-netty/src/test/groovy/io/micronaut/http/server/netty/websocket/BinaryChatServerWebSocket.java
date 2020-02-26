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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

import java.util.Set;

@ServerWebSocket("/binary/chat/{topic}/{username}")
public class BinaryChatServerWebSocket {
    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) {
        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        System.out.println("Server session opened for username = " + username);
        System.out.println("Server openSessions = " + openSessions);
        for (WebSocketSession openSession : openSessions) {
            if(isValid(topic, session, openSession)) {
                String msg = "[" + username + "] Joined!";
                System.out.println("Server sending msg = " + msg);
                openSession.sendSync(msg.getBytes());
            }
        }
    }

    @OnMessage
    public void onMessage(
            String topic,
            String username,
            byte[] message,
            WebSocketSession session) {

        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        System.out.println("Server received message = " + new String(message));
        System.out.println("Server openSessions = " + openSessions);
        for (WebSocketSession openSession : openSessions) {
            if(isValid(topic, session, openSession)) {
                String msg = "[" + username + "] " + new String(message);
                System.out.println("Server sending msg = " + msg);
                openSession.sendSync(msg.getBytes());
            }
        }
    }

    @OnClose
    public void onClose(
            String topic,
            String username,
            WebSocketSession session) {
        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        System.out.println("Server session closing for username = " + username);
        for (WebSocketSession openSession : openSessions) {
            if(isValid(topic, session, openSession)) {
                String msg = "[" + username + "] Disconnected!";
                System.out.println("Server sending msg = " + msg);
                openSession.sendSync(msg.getBytes());
            }
        }
    }

    private boolean isValid(String topic, WebSocketSession session, WebSocketSession openSession) {
        return openSession != session && topic.equalsIgnoreCase(openSession.getUriVariables().get("topic", String.class).orElse(null));
    }
}
