package io.micronaut.docs.security.session


import geb.Page

class HomePage extends Page {

    static url = '/'

    static at = { title == 'Home' }

    static content = {
        loginLink { $('a', text: 'Login') }
        logoutButton { $('input', type: 'submit', value: 'Logout') }
        usernameElement(required: false) { $('h1 span', 0) }
    }

    String username() {
        if ( usernameElement.empty ) {
            return null
        }
        usernameElement.text()
    }

    void login() {
        loginLink.click()
    }

    void logout() {
        logoutButton.click()
    }
}

