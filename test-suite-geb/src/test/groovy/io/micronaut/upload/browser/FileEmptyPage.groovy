package io.micronaut.upload.browser

import geb.Page

class FileEmptyPage extends Page {

    static url = '/image/save'

    static at = { title == 'File is Empty' }
}
