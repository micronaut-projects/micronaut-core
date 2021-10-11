package io.micronaut.inject.beans.inheritance;

import jakarta.inject.Singleton;

@Singleton
public class BankService extends AbstractService<String> {
}
