package com.example.evaluator.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class FixtureService {
    private final ObjectMapper objectMapper;

    public FixtureService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> assignmentFixture() {
        String fixture = """
                {
                  "spec": "When a business tops up their multi-currency wallet, the system locks the FX rate at the moment the top-up is initiated. The funding source is only debited after the destination wallet is credited. If the payment provider's callback times out after 5 seconds, the transaction is rolled back and neither side is affected. Top-ups above SGD 10,000 require approval from a second business owner before settling.",
                  "testCases": [
                    {"id":"tc-1","description":"Verify that a wallet top-up completes successfully","steps":["Initiate top-up of SGD 500","Check wallet balance increases"],"expectedResult":"Wallet balance increases by SGD 500"},
                    {"id":"tc-2","description":"Verify FX rate is applied correctly","steps":["Initiate top-up of USD 100","Check that FX rate shown equals rate at initiation time"],"expectedResult":"FX rate matches rate locked at initiation"},
                    {"id":"tc-3","description":"Verify rollback on timeout","steps":["Initiate top-up","Simulate provider timeout","Check wallet balance"],"expectedResult":"Wallet balance unchanged and funding source not debited"},
                    {"id":"tc-4","description":"Verify large top-up requires approval","steps":["Initiate top-up of SGD 15,000", {"expectedResult":"Approval screen is shown"}, "Check approval screen appears"]}
                  ],
                  "correctedTestCases": [
                    {"id":"tc-1-corrected","description":"Credit the destination wallet before debiting the funding source","steps":["Initiate an SGD 500 top-up using a controllable payment provider","Observe the destination-wallet credit event","Observe the funding-source debit event","Compare event timestamps and ledger balances"],"expectedResult":"The destination wallet is credited by SGD 500 before the funding source is debited by SGD 500."},
                    {"id":"tc-2-corrected","description":"Use the FX rate locked at initiation even if the market moves before settlement","steps":["Set the USD/SGD rate to R1 and initiate a USD 100 top-up","Record the quoted locked rate R1","Change the live market rate to R2 before the provider callback","Complete the provider callback and inspect the destination-wallet credit"],"expectedResult":"The settled SGD credit is calculated with R1, not R2, and the stored transaction rate remains R1."},
                    {"id":"tc-3-corrected","description":"Roll back both ledgers when the provider callback exceeds five seconds","steps":["Record destination-wallet and funding-source balances","Initiate a top-up","Delay the payment-provider callback beyond 5 seconds","Wait for timeout handling to complete","Read transaction state and both balances"],"expectedResult":"The transaction is rolled back; the destination wallet is unchanged and the funding source was not debited."},
                    {"id":"tc-4-corrected","description":"Require a distinct second owner before an SGD 10,001 top-up can settle","steps":["As business owner A, initiate an SGD 10,001 top-up","Assert transaction status is pending second-owner approval and no settlement occurred","As a different business owner B, approve the top-up","Wait for settlement and inspect the transaction"],"expectedResult":"Owner A alone cannot settle the top-up. Settlement occurs only after owner B approves."}
                  ]
                }
                """;
        try {
            JsonNode root = objectMapper.readTree(fixture);
            return objectMapper.convertValue(root, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Fixture JSON is invalid", exception);
        }
    }
}
