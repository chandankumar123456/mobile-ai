#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>
#include <unistd.h>
#include <vector>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;

constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   DEFAULT_BATCH_SIZE      = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;

static llama_model                      * g_model = nullptr;
static llama_context                    * g_context = nullptr;
static common_batch                       g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler = nullptr;

static int g_n_ctx     = DEFAULT_CONTEXT_SIZE;
static int g_n_threads = 0;
static int g_n_batch   = DEFAULT_BATCH_SIZE;

struct native_sampler_config {
    float    temp           = DEFAULT_SAMPLER_TEMP;
    int      top_k          = 40;
    float    top_p          = 0.9f;
    float    min_p          = 0.05f;
    float    repeat_penalty = 1.1f;
    float    freq_penalty   = 0.0f;
    float    pres_penalty   = 0.0f;
    uint32_t seed           = LLAMA_DEFAULT_SEED;
};

static native_sampler_config g_sampler_config;

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    // Set llama log handler to Android
    llama_log_set(aichat_android_log_callback, nullptr);

    // Loading all CPU backend variants
    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    LOGi("Registered backends count: %zu", ggml_backend_reg_count());
    if (ggml_backend_reg_count() == 0) {
        LOGw("No backends found in %s! Trying ggml_backend_load_all()...", path_to_backend);
        ggml_backend_load_all();
        LOGi("After fallback, registered backends count: %zu", ggml_backend_reg_count());
    }
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Initialize backends
    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

static jint load_model_internal(JNIEnv *env, jstring jmodel_path) {
    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    if (!model_path) {
        LOGe("%s: failed to read model path", __func__);
        return 2;
    }
    LOGd("%s: Loading model from: \n%s\n", __func__, model_path);

    // Drop any previous resources before replacing the model pointer.
    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    g_batch = common_batch();
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_model_params model_params = llama_model_default_params();
    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    g_n_ctx     = DEFAULT_CONTEXT_SIZE;
    g_n_threads = 0;
    g_n_batch   = DEFAULT_BATCH_SIZE;
    return load_model_internal(env, jmodel_path);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeLoadModel(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jmodel_path,
        jint n_ctx,
        jint n_threads,
        jint n_batch
) {
    g_n_ctx     = n_ctx > 0 ? n_ctx : DEFAULT_CONTEXT_SIZE;
    g_n_threads = n_threads > 0 ? n_threads : 0;
    g_n_batch   = n_batch > 0 ? n_batch : DEFAULT_BATCH_SIZE;
    LOGi("%s: runtime config n_ctx=%d, n_threads=%d, n_batch=%d",
         __func__, g_n_ctx, g_n_threads, g_n_batch);
    return load_model_internal(env, jmodel_path);
}

static int resolve_threads(const int requested) {
    if (requested > 0) {
        return requested;
    }
    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                            (int) sysconf(_SC_NPROCESSORS_ONLN) -
                                            N_THREADS_HEADROOM));
}

static llama_context *init_context(llama_model *model, const int n_ctx, const int n_threads, const int n_batch) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    const int threads = resolve_threads(n_threads);
    LOGi("%s: Using %d threads, context=%d, batch=%d", __func__, threads, n_ctx, n_batch);

    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d context size...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = n_batch;
    ctx_params.n_ubatch = n_batch;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    auto *context = llama_init_from_model(model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_new_context_with_model() returned null)", __func__);
    }
    return context;
}

static common_sampler *new_sampler(const native_sampler_config &cfg) {
    common_params_sampling sparams;
    sparams.temp = cfg.temp;
    sparams.top_k = cfg.top_k;
    sparams.top_p = cfg.top_p;
    sparams.min_p = cfg.min_p;
    sparams.penalty_last_n = 64;
    sparams.penalty_repeat = cfg.repeat_penalty;
    sparams.penalty_freq = cfg.freq_penalty;
    sparams.penalty_present = cfg.pres_penalty;
    sparams.seed = cfg.seed;
    return common_sampler_init(g_model, sparams);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    if (!g_model) {
        LOGe("%s: model cannot be null", __func__);
        return 1;
    }

    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    g_chat_templates.reset();

    auto *context = init_context(g_model, g_n_ctx, g_n_threads, g_n_batch);
    if (!context) { return 1; }
    g_context = context;
    g_batch = common_batch(context);
    g_chat_templates = common_chat_templates_init(g_model, "", "", "");
    if (!g_chat_templates) {
        LOGe("%s: failed to initialize chat templates", __func__);
        return 3;
    }
    g_sampler = new_sampler(g_sampler_config);
    if (!g_sampler) {
        LOGe("%s: failed to create sampler", __func__);
        return 2;
    }
    return 0;
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    const int bench_batch = std::max(g_n_batch, (int) pp);
    auto *context = init_context(g_model, pp, 0, bench_batch);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const uint32_t n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp = %d)", pp);

        common_batch batch(context);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            batch.add(0, i, 0, false);
        }

        batch.set_output(batch.size() - 1, true);
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg = %d)", tg);

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            batch.clear();
            for (j = 0; j < pl; j++) {
                batch.add(0, i, j, true);
            }

            if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 * - statistics
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
static llama_pos system_prompt_position = 0;
static llama_pos current_position = 0;

static int g_prompt_tokens = 0;
static int g_generated_tokens = 0;

static std::atomic<bool> g_cancel_requested{false};

static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    system_prompt_position = 0;
    current_position = 0;

    if (clear_kv_cache && g_context) {
        llama_memory_clear(llama_get_memory(g_context), false);
    }
}

/**
 * Completion loop's short-term states:
 * - stop generation position
 * - token chars caching
 * - current assistant message being generated
 * - stop sequences
 */
static llama_pos stop_generation_position = 0;
static std::string cached_token_chars;
static std::string g_assistant_text;
static std::string g_pending_text;
static std::vector<std::string> g_stop_sequences;
static size_t g_max_stop_len = 0;
static bool g_generation_finished = true;

static void reset_short_term_states() {
    stop_generation_position = 0;
    cached_token_chars.clear();
    g_assistant_text.clear();
    g_pending_text.clear();
    g_generation_finished = true;
}

/**
 * Context shifting by discarding the older half of the tokens appended after system prompt:
 * - take the [system_prompt_position] first tokens from the original prompt
 * - take half of the last (current_position - system_prompt_position) tokens
 * - recompute the logits in batches
 */
static void shift_context() {
    if (!g_context) { return; }
    const int n_discard = (current_position - system_prompt_position) / 2;
    LOGi("%s: Discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, system_prompt_position, system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, system_prompt_position + n_discard, current_position, -n_discard);
    current_position -= n_discard;
    LOGi("%s: Context shifting done! Current position: %d", __func__, current_position);
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    chat_msgs.push_back(new_msg);
    LOGi("%s: Formatted and added %s message: \n%s\n", __func__, role.c_str(), formatted.c_str());
    return formatted;
}

static int decode_tokens_in_batches(
        llama_context *context,
        common_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false) {
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(), start_pos);
    for (int i = 0; i < (int) tokens.size(); i += g_n_batch) {
        const int cur_batch_size = std::min((int) tokens.size() - i, g_n_batch);
        batch.clear();
        LOGv("%s: Preparing a batch size of %d starting at: %d", __func__, cur_batch_size, i);

        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
            batch.add(token_id, position, 0, want_logit);
        }

        const int decode_result = llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get());
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Obtain system prompt from JEnv
    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    LOGd("%s: System prompt received: \n%s", __func__, system_prompt);
    std::string formatted_system_prompt(system_prompt);

    // Format system prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // Tokenize system prompt
    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);
    for (auto id: system_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Handle context overflow
    const int max_batch_size = g_n_ctx - OVERFLOW_HEADROOM;
    if ((int) system_tokens.size() > max_batch_size) {
        LOGe("%s: System prompt too long for context! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_batch_size);
        return 1;
    }

    // Decode system tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, system_tokens, current_position)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    system_prompt_position = current_position = (int) system_tokens.size();
    g_prompt_tokens = (int) system_tokens.size();
    g_generated_tokens = 0;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();
    g_cancel_requested.store(false);
    g_stop_sequences.clear();
    g_max_stop_len = 0;

    // Obtain and tokenize user prompt
    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    LOGd("%s: User prompt received: \n%s", __func__, user_prompt);
    std::string formatted_user_prompt(user_prompt);

    // Format user prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);
    }
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Decode formatted user prompts
    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);
    for (auto id: user_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Ensure user prompt fits into the remaining context; shift or truncate if needed.
    const int max_batch_size = g_n_ctx - OVERFLOW_HEADROOM;
    int available = max_batch_size - (int) current_position;
    if (available <= 0) {
        shift_context();
        available = max_batch_size - (int) current_position;
    }
    if (available <= 0) {
        LOGe("%s: No context space left for user prompt!", __func__);
        return 1;
    }
    if ((int) user_tokens.size() > available) {
        const int skipped_tokens = (int) user_tokens.size() - available;
        user_tokens.resize(available);
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, skipped_tokens);
    }

    // Decode user tokens in batches
    if (decode_tokens_in_batches(g_context, g_batch, user_tokens, current_position, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    current_position += (int) user_tokens.size();
    stop_generation_position = current_position + std::max(1, (int) n_predict);
    g_prompt_tokens += (int) user_tokens.size();
    g_generated_tokens = 0;
    g_generation_finished = false;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

static size_t find_stop_position(const std::string &text) {
    size_t best = std::string::npos;
    for (const auto &stop : g_stop_sequences) {
        if (stop.empty()) { continue; }
        const size_t pos = text.find(stop);
        if (pos != std::string::npos && (best == std::string::npos || pos < best)) {
            best = pos;
        }
    }
    return best;
}

static void add_assistant_message() {
    if (!g_chat_templates || g_assistant_text.empty()) { return; }
    common_chat_msg msg;
    msg.role = ROLE_ASSISTANT;
    msg.content = g_assistant_text;
    chat_msgs.push_back(std::move(msg));
}

static jstring finalize_generation(JNIEnv *env) {
    if (g_generation_finished) { return nullptr; }
    g_generation_finished = true;

    const size_t stop_pos = find_stop_position(g_pending_text);
    if (stop_pos != std::string::npos) {
        g_pending_text.resize(stop_pos);
    }

    std::string remaining;
    remaining.swap(g_pending_text);
    g_assistant_text += remaining;
    add_assistant_message();

    return remaining.empty() ? nullptr : env->NewStringUTF(remaining.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    if (g_generation_finished) {
        return nullptr;
    }

    // Cooperative cancellation check
    if (g_cancel_requested.load()) {
        LOGw("%s: Generation cancelled.", __func__);
        g_generation_finished = true;
        g_pending_text.clear();
        return nullptr;
    }

    if (!g_context || !g_model || !g_sampler) {
        LOGe("%s: missing native resources", __func__);
        g_generation_finished = true;
        return nullptr;
    }

    // Infinite text generation via context shifting
    if (current_position >= g_n_ctx - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Stop if reaching the marked position
    if (current_position >= stop_generation_position) {
        LOGw("%s: STOP: hitting stop position: %d", __func__, stop_generation_position);
        return finalize_generation(env);
    }

    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    // Populate the batch with new token, then decode
    g_batch.clear();
    g_batch.add(new_token_id, current_position, 0, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        return finalize_generation(env);
    }

    // Update position
    current_position++;

    // Stop if next token is EOG
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("id: %d,\tIS EOG!\nSTOP.", new_token_id);
        return finalize_generation(env);
    }

    g_generated_tokens++;

    // If not EOG, convert to text
    const auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    // Wait for a complete UTF-8 sequence before emitting
    if (!is_valid_utf8(cached_token_chars.c_str())) {
        LOGv("id: %d,\tappend to cache", new_token_id);
        return env->NewStringUTF("");
    }

    g_pending_text += cached_token_chars;
    cached_token_chars.clear();

    // Stop sequence detection on the buffered output
    const size_t stop_pos = find_stop_position(g_pending_text);
    if (stop_pos != std::string::npos) {
        std::string out = g_pending_text.substr(0, stop_pos);
        g_pending_text.clear();
        g_assistant_text += out;
        g_generation_finished = true;
        add_assistant_message();
        LOGi("%s: STOP: generated stop sequence", __func__);
        return out.empty() ? nullptr : env->NewStringUTF(out.c_str());
    }

    if (g_max_stop_len == 0) {
        std::string out;
        out.swap(g_pending_text);
        g_assistant_text += out;
        LOGv("id: %d,\temit: `%s`", new_token_id, out.c_str());
        return out.empty() ? env->NewStringUTF("") : env->NewStringUTF(out.c_str());
    }

    // Keep the trailing window so a stop sequence spanning chunks is never emitted.
    if (g_pending_text.size() <= g_max_stop_len) {
        return env->NewStringUTF("");
    }

    size_t emit_len = g_pending_text.size() - g_max_stop_len;
    while (emit_len > 0 && ((unsigned char) g_pending_text[emit_len] & 0xC0) == 0x80) {
        emit_len--;
    }
    if (emit_len == 0) {
        return env->NewStringUTF("");
    }

    std::string out = g_pending_text.substr(0, emit_len);
    g_pending_text.erase(0, emit_len);
    g_assistant_text += out;
    LOGv("id: %d,\temit window: `%s`", new_token_id, out.c_str());
    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeGenerateStart(
        JNIEnv *env,
        jobject /*unused*/,
        jobjectArray roles,
        jobjectArray contents,
        jfloat temperature,
        jint top_k,
        jfloat top_p,
        jfloat min_p,
        jfloat repeat_penalty,
        jfloat frequency_penalty,
        jfloat presence_penalty,
        jlong seed,
        jint predict_length,
        jobjectArray stops,
        jboolean enable_thinking,
        jboolean continue_final
) {
    if (!g_model || !g_context || !g_chat_templates) {
        LOGe("%s: model resources are not ready", __func__);
        return 1;
    }

    reset_long_term_states();
    reset_short_term_states();
    g_cancel_requested.store(false);
    g_stop_sequences.clear();
    g_max_stop_len = 0;
    g_prompt_tokens = 0;
    g_generated_tokens = 0;

    // Build the message list from JNI arrays
    std::vector<common_chat_msg> messages;
    if (roles && contents) {
        const jsize role_count = env->GetArrayLength(roles);
        const jsize content_count = env->GetArrayLength(contents);
        const jsize count = std::min(role_count, content_count);
        messages.reserve((size_t) count);
        for (jsize i = 0; i < count; i++) {
            auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
            auto jcontent = (jstring) env->GetObjectArrayElement(contents, i);

            const char *role_chars = jrole ? env->GetStringUTFChars(jrole, nullptr) : nullptr;
            const char *content_chars = jcontent ? env->GetStringUTFChars(jcontent, nullptr) : nullptr;

            common_chat_msg msg;
            if (role_chars) { msg.role = role_chars; }
            if (content_chars) { msg.content = content_chars; }

            if (role_chars) { env->ReleaseStringUTFChars(jrole, role_chars); }
            if (content_chars) { env->ReleaseStringUTFChars(jcontent, content_chars); }
            if (jrole) { env->DeleteLocalRef(jrole); }
            if (jcontent) { env->DeleteLocalRef(jcontent); }

            messages.push_back(std::move(msg));
        }
    }

    if (messages.empty()) {
        LOGe("%s: empty message list", __func__);
        return 2;
    }

    common_chat_templates_inputs inputs;
    inputs.messages = messages;
    inputs.add_generation_prompt = true;
    inputs.continue_final_message = continue_final
            ? COMMON_CHAT_CONTINUATION_AUTO
            : COMMON_CHAT_CONTINUATION_NONE;
    inputs.use_jinja = true;
    inputs.enable_thinking = enable_thinking;

    common_chat_params params;
    try {
        params = common_chat_templates_apply(g_chat_templates.get(), inputs);
    } catch (const std::exception &e) {
        LOGw("%s: jinja chat template failed (%s); retrying without jinja", __func__, e.what());
        inputs.use_jinja = false;
        try {
            params = common_chat_templates_apply(g_chat_templates.get(), inputs);
        } catch (const std::exception &fallback_error) {
            LOGe("%s: chat template failed: %s", __func__, fallback_error.what());
            return 3;
        }
    }

    // Collect stop sequences: template-provided and caller-provided
    const auto add_stop = [&](const std::string &stop) {
        if (stop.empty()) { return; }
        g_stop_sequences.push_back(stop);
        g_max_stop_len = std::max(g_max_stop_len, stop.size());
    };
    for (const auto &stop : params.additional_stops) {
        add_stop(stop);
    }
    if (stops) {
        const jsize stop_count = env->GetArrayLength(stops);
        for (jsize i = 0; i < stop_count; i++) {
            auto jstop = (jstring) env->GetObjectArrayElement(stops, i);
            if (!jstop) { continue; }
            const char *stop_chars = env->GetStringUTFChars(jstop, nullptr);
            if (stop_chars) {
                add_stop(std::string(stop_chars));
                env->ReleaseStringUTFChars(jstop, stop_chars);
            }
            env->DeleteLocalRef(jstop);
        }
    }

    // Tokenize the rendered prompt
    const auto prompt_text = params.prompt.str();
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    std::vector<llama_token> prompt_tokens;
    try {
        prompt_tokens = common_tokenize(g_context, prompt_text, has_chat_template, has_chat_template);
    } catch (const std::exception &e) {
        LOGe("%s: failed to tokenize prompt: %s", __func__, e.what());
        return 4;
    }

    const int max_prompt_tokens = g_n_ctx - OVERFLOW_HEADROOM;
    if ((int) prompt_tokens.size() > max_prompt_tokens) {
        const int skipped = (int) prompt_tokens.size() - max_prompt_tokens;
        prompt_tokens.resize(max_prompt_tokens);
        LOGw("%s: prompt too long! Skipped %d tokens", __func__, skipped);
    }
    if (prompt_tokens.empty()) {
        LOGe("%s: empty prompt after template application", __func__);
        return 5;
    }

    if (decode_tokens_in_batches(g_context, g_batch, prompt_tokens, 0, true)) {
        LOGe("%s: failed to decode prompt", __func__);
        return 6;
    }

    current_position = (llama_pos) prompt_tokens.size();
    system_prompt_position = 0;
    g_prompt_tokens = (int) prompt_tokens.size();
    stop_generation_position = current_position + std::max(1, (int) predict_length);

    // Rebuild the sampler with request configuration
    native_sampler_config cfg;
    cfg.temp = temperature;
    cfg.top_k = (int) top_k;
    cfg.top_p = top_p;
    cfg.min_p = min_p;
    cfg.repeat_penalty = repeat_penalty;
    cfg.freq_penalty = frequency_penalty;
    cfg.pres_penalty = presence_penalty;
    cfg.seed = seed < 0 ? LLAMA_DEFAULT_SEED : (uint32_t) seed;
    g_sampler_config = cfg;

    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_sampler = new_sampler(cfg);
    if (!g_sampler) {
        LOGe("%s: failed to create sampler", __func__);
        return 7;
    }

    LOGi("%s: prompt tokens=%d, stop sequences=%zu, predict=%d",
         __func__, g_prompt_tokens, g_stop_sequences.size(), (int) predict_length);
    g_generation_finished = false;
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeRequestCancel(JNIEnv * /*unused*/, jobject /*unused*/) {
    LOGi("%s: cancellation requested", __func__);
    g_cancel_requested.store(true);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeResetConversation(JNIEnv * /*unused*/, jobject /*unused*/) {
    reset_long_term_states();
    reset_short_term_states();
    g_cancel_requested.store(false);
    g_stop_sequences.clear();
    g_max_stop_len = 0;
    g_prompt_tokens = 0;
    g_generated_tokens = 0;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeRuntimeStats(JNIEnv *env, jobject /*unused*/) {
    const jint values[4] = {
            (jint) g_prompt_tokens,
            (jint) g_generated_tokens,
            (jint) current_position,
            (jint) g_n_ctx,
    };
    jintArray result = env->NewIntArray(4);
    if (result) {
        env->SetIntArrayRegion(result, 0, 4, values);
    }
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();
    g_cancel_requested.store(false);

    // Free up resources (idempotent)
    if (g_sampler) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    g_batch = common_batch();
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    g_stop_sequences.clear();
    g_max_stop_len = 0;
    g_prompt_tokens = 0;
    g_generated_tokens = 0;
    g_n_ctx = DEFAULT_CONTEXT_SIZE;
    g_n_threads = 0;
    g_n_batch = DEFAULT_BATCH_SIZE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}
