package com.cashflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CashflowSettlementEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashflowSettlementEngineApplication.class, args);
    }
}
