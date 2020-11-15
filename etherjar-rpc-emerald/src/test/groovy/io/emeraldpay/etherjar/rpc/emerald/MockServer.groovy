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
package io.emeraldpay.etherjar.rpc.emerald

import io.emeraldpay.api.proto.BlockchainGrpc
import io.emeraldpay.api.proto.ReactorBlockchainGrpc
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.testing.GrpcCleanupRule

class MockServer {

    static GrpcCleanupRule grpcCleanup = new GrpcCleanupRule()

    static ReactorBlockchainGrpc.ReactorBlockchainStub createFor(BlockchainGrpc.BlockchainImplBase impl){
        String serverName = InProcessServerBuilder.generateName()
        grpcCleanup.register(InProcessServerBuilder
            .forName(serverName).directExecutor().addService(impl).build().start());
        def channel =  grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
        return ReactorBlockchainGrpc.newReactorStub(channel)
    }
}
