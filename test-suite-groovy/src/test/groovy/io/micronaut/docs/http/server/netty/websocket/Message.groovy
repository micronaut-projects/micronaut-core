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
package io.micronaut.docs.http.server.netty.websocket

class Message {

    private String text

    Message(String text) {
        this.text = text
    }

    Message() {
    }

    String getText() {
        return text
    }

    void setText(String text) {
        this.text = text
    }

    @Override
    boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false
        Message message = (Message) o
        return text == message.text
    }

    @Override
    int hashCode() {
        return Objects.hash(text)
    }

    @Override
    String toString() {
        return "Message{" +
                "text='" + text + '\'' +
                '}'
    }
}
