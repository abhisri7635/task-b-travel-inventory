package com.example.navan.util;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {}

    public static String transactionId() {
        return "txn_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String invoiceId() {
        return "inv_" + UUID.randomUUID().toString().replace("-", "");
    }
}
