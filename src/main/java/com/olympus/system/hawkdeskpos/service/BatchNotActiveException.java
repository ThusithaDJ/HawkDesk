package com.olympus.system.hawkdeskpos.service;

public class BatchNotActiveException extends RuntimeException {
    public BatchNotActiveException(String batchNumber, String status) {
        super("Batch " + batchNumber + " is not active (status: " + status + ")");
    }
}
