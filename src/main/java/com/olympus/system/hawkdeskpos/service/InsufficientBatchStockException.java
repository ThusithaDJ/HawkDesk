package com.olympus.system.hawkdeskpos.service;

public class InsufficientBatchStockException extends RuntimeException {
    public InsufficientBatchStockException(int itemId, int requested, int available) {
        super("Item " + itemId + ": requested " + requested + " but only " + available + " available across all active batches");
    }
}
