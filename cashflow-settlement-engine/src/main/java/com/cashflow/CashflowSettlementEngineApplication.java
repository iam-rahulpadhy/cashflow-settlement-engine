package com.cashflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// @EnableAsync wires up the task executor in application.yml so that
// @Async methods in LedgerService actually run off the I/O thread.
@SpringBootApplication
@EnableAsync
public class CashflowSettlementEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashflowSettlementEngineApplication.class, args);
    }
}
