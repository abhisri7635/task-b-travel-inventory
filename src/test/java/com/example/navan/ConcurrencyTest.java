package com.example.navan;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required case 6: two overlapping POSTs with the same external_id → exactly one row.
 */
class ConcurrencyTest extends NavanIntegrationTest {

    private static final String BODY = """
            {"type":"navan_fee","external_id":"concurrent-dupe-001","currency":"EUR",
             "occurred_at":"2026-03-11T06:40:00Z","total":25.00,
             "tax_lines":[],"fee_lines":[],
             "metadata":{"fee_kind":"trip_fee","description":"Concurrent dupe test"}}""";

    @Test
    void concurrentDuplicatePosts_resultInExactlyOneRow() throws InterruptedException {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created   = new AtomicInteger();
        AtomicInteger conflicts  = new AtomicInteger();
        AtomicInteger errors     = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    HttpHeaders h = new HttpHeaders();
                    h.setContentType(MediaType.APPLICATION_JSON);
                    ResponseEntity<JsonNode> resp = rest.exchange(
                            "/transactions", HttpMethod.POST,
                            new HttpEntity<>(BODY, h), JsonNode.class);
                    if (resp.getStatusCode() == HttpStatus.CREATED)    created.incrementAndGet();
                    else if (resp.getStatusCode() == HttpStatus.CONFLICT) conflicts.incrementAndGet();
                    else errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertThat(errors.get()).isEqualTo(0);
        assertThat(created.get() + conflicts.get()).isEqualTo(threads);
        assertThat(created.get()).isEqualTo(1);

        // DB must have exactly one row with this external_id
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.findByExternalId("concurrent-dupe-001")).isPresent();
    }
}
