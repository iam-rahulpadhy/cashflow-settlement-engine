package com.cashflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

// @EnableAsync activates Spring's async method execution, wiring up the
// task executor configured in application.yml (spring.task.execution.*).
// Without this, @Async is a no-op and settlement runs on the I/O thread.
@SpringBootApplication
@EnableAsync
public class CashflowSettlementEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashflowSettlementEngineApplication.class, args);
    }
}
