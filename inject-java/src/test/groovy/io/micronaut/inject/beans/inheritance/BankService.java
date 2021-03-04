package io.micronaut.inject.beans.inheritance;

import javax.inject.Singleton;

@Singleton
public class BankService extends AbstractService<String> {
}
