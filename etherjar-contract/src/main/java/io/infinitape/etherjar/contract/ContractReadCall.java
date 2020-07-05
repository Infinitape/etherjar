package io.infinitape.etherjar.contract;

import io.infinitape.etherjar.domain.Address;
import io.infinitape.etherjar.hex.HexData;

import java.util.function.Function;

public class ContractReadCall<T> extends ContractCall {

    private final Function<HexData, T> processor;

    public ContractReadCall(Address contract, ContractData data, Function<HexData, T> processor) {
        super(contract, data);
        this.processor = processor;
    }

    public Function<HexData, T> getProcessor() {
        return processor;
    }

    public T processResult(HexData result) {
        return processor.apply(result);
    }
}
