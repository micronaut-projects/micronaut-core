package io.micronaut.upload.browser

import geb.Page

class CreatePage extends Page {

    static url = '/image/create'

    static at = { title == 'Create Image' }

    static content = {
        uploadButton { $('input', type: 'submit', value: 'Upload') }
    }

    void upload() {
        uploadButton.click()
    }
}
