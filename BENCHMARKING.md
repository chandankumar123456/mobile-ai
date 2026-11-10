# On-Device Inference Benchmarking Harness

A key differentiator of this platform is the transition from anecdotal observation to reproducible, statistically sound on-device inference benchmarking.

---

## 1. Benchmarking Philosophy & Integrity

- **Zero Fabricated Metrics:** All recorded numbers stem from real execution against the native llama.cpp runtime or OpenAI-compatible backends.
- **Controlled Hardware Capture:** Every benchmark report includes device metadata:
  - Device Manufacturer & Model
  - Hardware SoC & Supported CPU Extensions (ARM NEON, FP16)
  - Total Physical RAM & Available System RAM
  - Android OS Version & API Level
  - Model Path, GGUF Quantization Type, Context Window Length, Thread Count
- **Warm-Up Runs:** Prior to statistical recording, a designated warm-up cycle is executed to ensure model weights and KV caches are mapped into active RAM.

---

## 2. Metrics Captured

1. **Model Load Time ($T_{\text{load}}$):** Latency from initiating file mapping to the return of valid native memory pointers.
2. **Time to First Token (TTFT):** Latency between prompt ingestion and the emission of the first token delta:
   $$\text{TTFT} = t_{\text{first\_token}} - t_{\text{prompt\_dispatched}}$$
3. **Prefill Throughput:** Rate of processing input prompt tokens prior to generation:
   $$\text{Throughput}_{\text{prefill}} = \frac{N_{\text{prompt\_tokens}}}{\text{TTFT}} \quad (\text{tok/s})$$
4. **Decode Throughput:** Rate of sequential autoregressive token generation:
   $$\text{Throughput}_{\text{decode}} = \frac{N_{\text{generated\_tokens}} - 1}{t_{\text{completion}} - t_{\text{first\_token}}} \quad (\text{tok/s})$$
5. **Peak RAM & Native Allocation:** Sampled through `Debug.getNativeHeapAllocatedSize()` and Android `ActivityManager`.
6. **Context Utilization:** Ratio of prompt + generated tokens to the configured context window limit.

---

## 3. Statistical Aggregations

To guard against mobile thermal throttling, background OS scheduling jitter, and frequency scaling variances, the harness executes repeated trials ($N \ge 3$) and computes:

- **Mean:**
  $$\mu = \frac{1}{N} \sum_{i=1}^{N} x_i$$
- **Standard Deviation:**
  $$\sigma = \sqrt{\frac{1}{N-1} \sum_{i=1}^{N} (x_i - \mu)^2}$$
- **Percentiles:**
  - **P50 (Median):** 50th percentile of distribution.
  - **P95:** 95th percentile, highlighting worst-case latency tail.
  - **P99:** Computed for high-sample benchmark suites.

---

## 4. Standardized Evaluation Suites

Standardized, version-controlled prompt datasets are maintained under `app/src/main/assets/benchmark/prompts/`:

1. `short_qa.json`: Short question-answering tasks measuring pure TTFT and minimal KV cache footprint.
2. `medium_reasoning.json`: Multi-step reasoning and logical deduction prompts (50–150 output tokens).
3. `long_generation.json`: Long-form summary and document synthesis (300+ output tokens) measuring decode stability and memory creep.
4. `tool_calling.json`: Prompts demanding structured JSON function calls conforming to supplied schemas.
5. `genui_generation.json`: Generation of custom JSON cards and parameter widgets.
6. `a2ui_generation.json`: Incremental JSONL generation conforming to the Google A2UI v0.8 specification.

---

## 5. Export Formats

Benchmark results can be viewed directly on-device in `BenchmarkScreen` or exported:
- **JSON:** Complete raw trial data, hardware signatures, and timing arrays for programmatic analysis.
- **CSV:** Tabular summary suitable for spreadsheet analysis, charting, and GitHub release artifacts.

