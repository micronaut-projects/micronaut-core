package io.micronaut.docs.websockets

import geb.Page
import geb.module.Textarea

class WebSocketsHomePage extends Page {

    static final String RECEIVED = 'RECEIVED:'
    static final String SENT = 'SENT:'

    static url = '/'

    static at = { title == 'WebSockets Demo' }

    @Override
    String convertToPath(Object[] args) {
        if(args) {
          return "?jwt="+args[0].toString()
        }
        ""
    }
    static content = {
        messagesList(required: false, wait: true) { $('ul#messages li') }
        messageInput { $('textarea', id: 'message').module(Textarea) }
        submitButton { $('button', type: 'submit', text: 'Send Message')}
        closeButton { $('button', id: 'close')}
        statusDiv { $('div#status') }
    }

    String status() {
        statusDiv.text()
    }

    List<String> receivedMessages() {
        filterMessagesByType(RECEIVED)
    }

    List<String> filterMessagesByType(String type) {
        messagesList.findAll {
            it.text().contains(type)
        }.collect {
            it.text().replaceAll(type, '')
        }
    }

    List<String> sentMessages() {
        filterMessagesByType(SENT)
    }


    void message(String msg) {
        messageInput.text = msg
    }

    void send(String msg) {
        message(msg)
        submit()
    }

    void close() {
        closeButton.click()
    }

    void submit() {
        submitButton.click()
    }
}

