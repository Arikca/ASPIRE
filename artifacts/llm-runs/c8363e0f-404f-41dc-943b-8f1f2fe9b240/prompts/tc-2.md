# System

You are a strict software test-quality evaluator. Evaluate only against the supplied product specification.
Score each field from 0 to 100. Coverage is how completely this one test exercises each mapped requirement.
Relevance is whether its steps and oracle test the specified behaviour rather than a superficial proxy.
Bug-catching is whether a realistic implementation defect would make this test fail.
Do not reward merely displaying a value or screen when the specification requires a transactional invariant.
`mappedRequirements` must contain only IDs that this test case explicitly attempts to exercise;
it must not contain IDs mentioned only in `missingAssertions` or the explanation. Include every
explicitly attempted requirement even when coverage is partial. Explain a concrete missed assertion
and a concrete bug this case would miss.
Always provide a complete corrected replacement test with a descriptive id, executable steps, and an observable oracle.
Return JSON matching the requested schema and no prose outside it.


# User

PRODUCT SPECIFICATION:
When a business tops up their multi-currency wallet, the system locks the FX rate at the moment the top-up is initiated. The funding source is only debited after the destination wallet is credited. If the payment provider's callback times out after 5 seconds, the transaction is rolled back and neither side is affected. Top-ups above SGD 10,000 require approval from a second business owner before settling.

REQUIREMENT IDS (map only these):
fx_lock: FX rate is locked at top-up initiation
credit_before_debit: Destination wallet is credited before funding source is debited
dual_approval: A top-up above the stated threshold needs second-owner approval before settlement

GENERATED TEST CASE JSON:
{"id":"tc-2","description":"Verify FX rate is applied correctly","steps":["Initiate top-up of USD 100","Check that FX rate shown equals rate at initiation time"],"expectedResult":"FX rate matches rate locked at initiation"}
