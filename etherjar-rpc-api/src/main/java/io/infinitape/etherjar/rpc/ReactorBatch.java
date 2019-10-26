/*
 * Copyright (c) 2016-2019 Igor Artamonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infinitape.etherjar.rpc;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ReactorBatch implements Batch<ReactorBatch.ReactorBatchItem>, Consumer<RpcCall> {

    private final List<ReactorBatch.ReactorBatchItem<?, ?>> items = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger(1);
    private final OnceExecuted onceExecuted = new OnceExecuted();

    public static Mono<ReactorBatch> from(Flux<RpcCall<?, ?>> calls) {
        ReactorBatch batch = new ReactorBatch();
        return calls
            .map(batch::add)
            .then(Mono.just(batch));
    }

    public <JS, RES> ReactorBatch.ReactorBatchItem<JS, RES> add(RpcCall<JS, RES> call) {
        ReactorBatch.ReactorBatchItem<JS, RES> b = new ReactorBatch.ReactorBatchItem<>(ids.getAndIncrement(), call, onceExecuted);
        items.add(b);
        return b;
    }

    public Flux<ReactorBatchItem<?, ?>> getItems() {
        return Flux.fromIterable(items);
    }

    @Override
    public void accept(RpcCall rpcCall) {
        add(rpcCall);
    }

    /**
     * Tracks execution result of the current batch and makes sure individual calls follow
     * the main execution flow.
     *
     * @param execution execution of the current batch
     */
    public void withExecution(Flux<RpcCallResponse> execution) {
        execution
            .last()
            .doOnNext((val) -> onceExecuted.onExecuted())
            .doOnError(onceExecuted::onFailed)
            .doOnCancel(() -> items.forEach(ReactorBatchItem::cancel))
            .subscribe();
    }

    public static class ReactorBatchItem<JS, RES> extends BatchItem<Mono<RES>, JS, RES> {
        //TODO change processor?!
        private MonoProcessor<RES> proc = MonoProcessor.create();
        private final Publisher onceExecuted;

        ReactorBatchItem(int id, RpcCall<JS, RES> call, Publisher onceExecuted) {
            super(id, call);
            this.onceExecuted = onceExecuted;
        }

        @Override
        public void onResult(RES value) {
            if (isClosed()) {
                return;
            }
            proc.onNext(value);
        }

        @Override
        public void onError(RpcException err) {
            if (isClosed()) {
                return;
            }
            proc.onError(err);
        }

        void cancel() {
            if (isClosed()) {
                return;
            }
            proc.cancel();
        }

        private boolean isClosed() {
            return proc.isDisposed() || proc.isSuccess() || proc.isError() || proc.isCancelled();
        }

        public Mono<RES> getResult() {
            return Mono.when(onceExecuted).then(Mono.from(proc));
        }
    }

    /**
     * Internal class to track execution result of a batch. It publishes result once it completed
     * (published true) or failed (publishes false)
     */
    public static class OnceExecuted implements Publisher<Boolean> {

        private Subscriber<? super Boolean> subscriber;
        private AtomicBoolean requested = new AtomicBoolean(false);
        private AtomicReference<State> executed = new AtomicReference<>(State.WAITING);

        @Override
        public void subscribe(Subscriber<? super Boolean> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested.set(true);
                    tryPublish();
                }

                @Override
                public void cancel() {
                    requested.set(false);
                }
            });
        }

        void onExecuted() {
            executed.set(State.EXECUTED);
            tryPublish();
        }

        void onFailed(Throwable t) {
            executed.set(State.FAILED);
            tryPublish();
        }

        private void tryPublish() {
            if (requested.get()) {
                State state = executed.get();
                if (state == State.EXECUTED) {
                    subscriber.onNext(true);
                    subscriber.onComplete();
                } else if (state == State.FAILED) {
                    subscriber.onNext(false);
                    subscriber.onComplete();
                }
            }
        }

        enum State {
            WAITING,
            EXECUTED,
            FAILED
        }

    }
}