package io.micronaut.security.token.jwt.cookie

import geb.Page

class LoginPage extends Page {

    static url = '/login/auth'

    static at = { title.contains 'Login' }

    static content = {
        usernameInput { $('#username') }
        passwordInput { $('#password') }
        submitInput { $('input', type: 'submit') }
        errorsLi(required: false) { $('li#errors') }
    }

    boolean hasErrors() {
        !errorsLi.empty
    }

    void login(String username, String password) {
        usernameInput = username
        passwordInput = password
        submitInput.click()
    }
}
