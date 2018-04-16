/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.instrument.rxjava;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.plugins.RxJavaHooks;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

/**
 * Instrumentation for RxJava 1
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Context
@Requires(classes = Single.class)
@Requires(beans = RxJavaRunnableInstrumenter.class)
public class RxJava1Instrumentation {

    @PostConstruct
    void init(RxJavaRunnableInstrumenter instrumenter) {
        if(instrumenter != null) {
            RxJavaHooks.setOnObservableStart(new Func2<Observable, Observable.OnSubscribe, Observable.OnSubscribe>() {
                @Override
                public Observable.OnSubscribe call(Observable observable, Observable.OnSubscribe onSubscribe) {
                    return onSubscribe;
                }
            });
            Func1<Action0, Action0> existing = RxJavaHooks.getOnScheduleAction();
            if(existing != null && !(existing instanceof InstrumentScheduleAction)) {
                RxJavaHooks.setOnScheduleAction(action0 ->
                        new InstrumentScheduleAction(instrumenter).call(existing.call(action0))
                );
            }
            else {
                RxJavaHooks.setOnScheduleAction(new InstrumentScheduleAction(instrumenter));
            }
        }
    }

    private static class InstrumentScheduleAction implements Func1<Action0, Action0> {
        private final RxJavaRunnableInstrumenter instrumenter;

        InstrumentScheduleAction(RxJavaRunnableInstrumenter instrumenter) {
            this.instrumenter = instrumenter;
        }

        @Override
        public Action0 call(Action0 action0) {
            return () -> instrumenter.apply(action0::call);
        }
    }
}
