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
