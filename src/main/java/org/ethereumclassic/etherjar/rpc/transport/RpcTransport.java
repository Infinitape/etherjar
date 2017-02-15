package org.ethereumclassic.etherjar.rpc.transport;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Igor Artamonov
 */
public interface RpcTransport extends Closeable {

    <T> Future<T> execute(String method, List params, Class<T> resultType) throws IOException;
}
