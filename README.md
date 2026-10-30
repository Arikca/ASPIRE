# Hybrid LLM test-case evaluator

An assignment-ready Spring Boot endpoint that evaluates generated tests against a caller-supplied specification. It uses deterministic checks for malformed or shallow test shapes and an OpenAI-backed Spring AI judge for semantic scoring.

## What was designed first

The endpoint keeps four responsibilities separate:

1. Preserve input as JSON so a malformed generated case can be scored as invalid rather than rejected by HTTP deserialization.
2. Extract a small requirement inventory from the supplied spec; the wallet rules are not compiled into the LLM judge.
3. Use deterministic floors for structural validity, timeout boundary, both-side rollback, approval/settlement, and credit/debit order.
4. Use one temperature-zero, provider-native structured-output OpenAI call for semantic coverage, relevance, bug-catching, explanations, and a corrected replacement test.

`TestCaseQualityEvaluator` implements Spring AI's `Evaluator`; the endpoint calls its richer `judge` method so it can return and persist the raw completion too. Spring AI's stock `RelevancyEvaluator` and `FactCheckingEvaluator` are useful binary gates, but not sufficient for the assignment's numeric scores, requirement mapping, test repair, or raw-output artifact requirements.

## Run

```bash
export OPENAI_API_KEY='...'
export OPENAI_MODEL='gpt-4.1-mini' # optional
mvn spring-boot:run
```

The service deliberately returns HTTP 503 for `POST /api/eval` and `/api/eval/repeat` when no API key is present. It never fabricates model scores.

```bash
curl http://localhost:8080/api/eval/fixtures
curl -X POST http://localhost:8080/api/eval \
  -H 'Content-Type: application/json' \
  --data @request.json
curl -X POST http://localhost:8080/api/eval/repeat \
  -H 'Content-Type: application/json' \
  --data @request.json
```

`GET /api/eval/fixtures` returns the wallet exercise, including an intentionally malformed `tc-4` where an object is inserted into `steps` and `expectedResult` is absent. Fixtures are only for reproducibility.

It also returns `correctedTestCases`: runnable replacements for all four shallow or malformed assignment cases. `GET /api/eval/methodology` returns the exact deterministic, LLM, and Spring AI responsibilities used by the endpoint.

## Evaluation output and artifacts

`POST /api/eval` returns individual scores, a suggested corrected test per valid input, and an overall suite score: `40%` requirement-union coverage plus `60%` mean per-case quality. Each run is written under `artifacts/llm-runs/<runId>/` with request, exact prompts, raw completion, schema-validated parsed result, and final response.

`POST /api/eval/repeat` runs the identical payload five times and saves `artifacts/llm-runs/repeat-5.json`. The real assignment fixture produced suite scores **49.5, 48.5, 49.5, 49.0, and 50.5** (mean **49.4**, population standard deviation **0.66**, range **2.0**) with `gpt-4.1-mini` at temperature zero. The configured initial CI block threshold is **55/100**: it is 4.5 points above the worst observed poor-suite score and well beyond the measured noise. A candidate must not be allowed to calculate its own gate; recalibrate the configured threshold from labelled known-good and known-bad suites, choosing a value above the bad suites' noisy upper bound and below the good suites' noisy lower bound.

## Submission evidence: prompts, raw outputs, and model settings

This repository includes the exact evidence from the final five-run evaluation, not synthetic example responses:

- `artifacts/llm-runs/repeat-5.json` is the five-run aggregate response, including each run ID, all per-case scores, and the variance calculation.
- Each of these five run folders contains the exact system/user prompt sent for every valid test case in `prompts/`, the unmodified model response in `raw/`, and the schema-validated parsed JSON in `parsed/`:
  - `884c2564-2c24-46be-9656-9925cfca1f75`
  - `e1711cf1-4bd3-4bc5-a825-c6e388147d48`
  - `a9ddc9f6-aed1-4e6a-867a-9d65566e5667`
  - `00bb6442-e4a4-43c3-88f8-a6f089d31163`
  - `323134fd-cbde-4d92-b0e9-79cddc61304c`
- `tc-4` has no prompt or raw output because its malformed JSON is deterministically rejected before an LLM is called; the validation failure is recorded in each `result.json`.

The model is `gpt-4.1-mini` (override with `OPENAI_MODEL`) at temperature `0`; see `src/main/resources/application.yml`. Calls use Spring AI's provider-native structured-output mode with schema validation, and raw output is then validated independently against `src/main/resources/schema/llm-test-case-score.json`. The full scoring instruction is the `SYSTEM` prompt in `TestCaseQualityEvaluator.java`.

Use temperature 0, provider-native structured output, a fixed rubric, deterministic caps, and a pinned model version to reduce variance. Calibration should compare five-run distributions for accepted and rejected suites, then choose the highest threshold whose false-reject rate is acceptable.

## Known blind spot

This bad test may score too highly because it repeats the language of the spec and contains all the right keywords:

```json
{
  "id": "fooled-by-keywords",
  "description": "Verify timeout rollback preserves both sides after five seconds",
  "steps": ["Initiate top-up", "Wait five seconds", "Simulate timeout", "Read audit log"],
  "expectedResult": "Audit log says wallet unchanged and funding source not debited"
}
```

It is weak because it only checks an audit-log claim, not ledger balances or a provider debit. Semantic judging and keyword floors may over-credit this oracle. A production version should add domain adapters that inspect real ledger/funding-source assertions and mutation testing against deliberately faulty implementations.
