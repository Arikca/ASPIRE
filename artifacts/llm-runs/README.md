# Runtime LLM artifacts

`POST /api/eval` creates `artifacts/llm-runs/<runId>/` and writes:

- `request.json` — the caller's spec and generated test cases
- `prompts/<testId>.md` — exact system and user prompts
- `raw/<testId>.txt` — unmodified OpenAI completion
- `parsed/<testId>.json` — model result only after JSON-schema validation
- `result.json` — API result

`POST /api/eval/repeat` also writes `repeat-5.json` with all five suite scores and variance statistics.

No raw model output is committed in this starter because no API key was available during implementation. The evaluator fails with HTTP 503 rather than inventing a completion; run it with `OPENAI_API_KEY` to generate submission artifacts.
