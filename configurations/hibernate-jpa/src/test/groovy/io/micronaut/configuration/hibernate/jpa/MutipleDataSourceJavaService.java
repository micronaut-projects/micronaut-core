package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession;
import io.micronaut.spring.tx.annotation.Transactional;
import org.hibernate.Session;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MutipleDataSourceJavaService {

    private Session sessionField;
    private final Session session;

    public MutipleDataSourceJavaService(@CurrentSession Session session) {
        this.session = session;
    }

    @Inject
    public void setSessionField(@CurrentSession Session sessionField) {
        this.sessionField = sessionField;
    }

    @Transactional
    public boolean testCurrent() {
        session.clear();
        return true;
    }

    @Transactional
    public boolean testCurrentFromField() {
        sessionField.clear();
        return true;
    }

}
