package com.enterprise.licensing.engine.ports;

import java.util.function.Supplier;

public interface TransactionRunner {
    <T> T read(Supplier<T> work);

    <T> T write(Supplier<T> work);
}
