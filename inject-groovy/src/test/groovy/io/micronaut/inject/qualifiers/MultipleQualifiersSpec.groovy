package io.micronaut.inject.qualifiers

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import org.junit.jupiter.api.Test

import jakarta.inject.Inject
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertTrue

class MultipleQualifiersSpec extends AbstractBeanDefinitionSpec {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test parse qualifiers"() {
        given:
        def definition = buildBeanDefinition('testmq.Test', '''
package testmq

import jakarta.inject.Singleton
import io.micronaut.inject.qualifiers.*

@Singleton
class Test {
    @PayBy(PaymentMethod.CREDIT_CARD)
    @Asynchronous
    Processor asyncCreditCartProcessor
    
    @PayBy(PaymentMethod.CREDIT_CARD)
    @Asynchronous
    protected Processor asyncCreditCartProcessorField
}
''')
        expect:
        definition.injectedFields.size() == 1
        definition.injectedMethods.size() == 1
    }

    void "test qualifiers"() {
        given:
            final MyBean bean = context.getBean(MyBean.class)
        expect:
            bean.asyncCreditCartProcessor instanceof AsyncCreditCardProcessor
            bean.asyncCreditCartProcessor.is(bean.fromCtorAsyncCreditCartProcessor)
            bean.syncCreditCartProcessor instanceof CreditCardProcessor
            bean.syncCreditCartProcessor.is(bean.fromCtorSyncCreditCartProcessor)
            bean.asyncBankTransferProcessor instanceof AsyncBankTransferProcessor
            bean.asyncBankTransferProcessor.is(bean.fromCtorAsyncBankTransferProcessor)
            bean.syncBankTransferProcessor instanceof BankTransferProcessor
            bean.syncBankTransferProcessor.is(bean.fromCtorSyncBankTransferProcessor)
    }
}

@Singleton
class MyBean {
    @PayBy(PaymentMethod.CREDIT_CARD)
    @Asynchronous
    Processor asyncCreditCartProcessor

    @Inject
    @PayBy(PaymentMethod.CREDIT_CARD)
    @Synchronous
    Processor syncCreditCartProcessor

    @Inject
    @PayBy(PaymentMethod.TRANSFER)
    @Asynchronous
    Processor asyncBankTransferProcessor

    @Inject
    @PayBy(PaymentMethod.TRANSFER)
    @Synchronous
    Processor syncBankTransferProcessor


    final Processor fromCtorAsyncCreditCartProcessor
    final Processor fromCtorSyncCreditCartProcessor
    final Processor fromCtorAsyncBankTransferProcessor
    final Processor fromCtorSyncBankTransferProcessor

    MyBean(
            @PayBy(PaymentMethod.CREDIT_CARD)
            @Asynchronous
                    Processor fromCtorAsyncCreditCartProcessor,
            @PayBy(PaymentMethod.CREDIT_CARD)
            @Synchronous
                    Processor fromCtorSyncCreditCartProcessor,
            @PayBy(PaymentMethod.TRANSFER)
            @Asynchronous
                    Processor fromCtorAsyncBankTransferProcessor,
            @PayBy(PaymentMethod.TRANSFER)
            @Synchronous
                    Processor fromCtorSyncBankTransferProcessor) {
        this.fromCtorAsyncCreditCartProcessor = fromCtorAsyncCreditCartProcessor
        this.fromCtorSyncCreditCartProcessor = fromCtorSyncCreditCartProcessor
        this.fromCtorAsyncBankTransferProcessor = fromCtorAsyncBankTransferProcessor
        this.fromCtorSyncBankTransferProcessor = fromCtorSyncBankTransferProcessor
    }
}

interface Processor {
}

@PayBy(PaymentMethod.CREDIT_CARD)
@Synchronous
@Singleton
class CreditCardProcessor implements Processor {
}

@PayBy(PaymentMethod.TRANSFER)
@Synchronous
@Singleton
class BankTransferProcessor implements Processor {
}

@PayBy(PaymentMethod.CREDIT_CARD)
@Asynchronous
@Singleton
class AsyncCreditCardProcessor implements Processor {
}

@PayBy(PaymentMethod.TRANSFER)
@Asynchronous
@Singleton
class AsyncBankTransferProcessor implements Processor {
}

@Qualifier
@Retention(RUNTIME)
@interface Synchronous {}

@Qualifier
@Retention(RUNTIME)
@interface Asynchronous {}

@Qualifier
@Retention(RUNTIME)
@interface PayBy {
    PaymentMethod value();
}

enum PaymentMethod {
    TRANSFER,
    CREDIT_CARD
}
