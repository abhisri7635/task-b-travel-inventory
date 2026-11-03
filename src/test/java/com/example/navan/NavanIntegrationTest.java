package com.example.navan;

import com.example.navan.repository.InvoiceLineRepository;
import com.example.navan.repository.InvoiceRepository;
import com.example.navan.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class NavanIntegrationTest {

    @Autowired
    protected TestRestTemplate rest;

    /** Apache HTTP Client supports PATCH; Java's HttpURLConnection does not. */
    @PostConstruct
    void configureHttpClient() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected InvoiceRepository invoiceRepository;

    @Autowired
    protected InvoiceLineRepository invoiceLineRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Delete in FK-safe order (children before parents)
        jdbcTemplate.execute("DELETE FROM invoice_fee_rollup");
        jdbcTemplate.execute("DELETE FROM invoice_tax_rollup");
        jdbcTemplate.execute("DELETE FROM invoice_lines");
        jdbcTemplate.execute("DELETE FROM invoices");
        jdbcTemplate.execute("DELETE FROM fee_lines");
        jdbcTemplate.execute("DELETE FROM tax_lines");
        jdbcTemplate.execute("DELETE FROM transactions");
    }
}
