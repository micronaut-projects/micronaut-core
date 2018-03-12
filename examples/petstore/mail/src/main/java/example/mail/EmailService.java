package example.mail;

import example.api.v1.Email;

import java.io.IOException;

public interface EmailService {
    void send(Email email);
}
