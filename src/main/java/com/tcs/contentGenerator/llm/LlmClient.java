package com.tcs.contentGenerator.llm;

import java.util.List;

/**
 * Provider-agnostic entry point for large-language-model calls. Every agent that
 * needs the model depends on this interface, never on a concrete provider SDK, so
 * the underlying model (local Ollama today; a cloud model such as Anthropic or
 * Gemini later) can be swapped via configuration without touching agent code.
 */
public interface LlmClient {

    /** Run a single system + user prompt and return the raw text completion. */
    String generate(String systemPrompt, String userPrompt);

    /**
     * Vision call: run a system + user prompt with one or more images attached
     * and return the raw text completion. Requires the configured model to be
     * vision-capable (the locally pulled {@code qwen3.5:4b} is — verified via
     * {@code /api/show}; re-check on other machines, capability is a property
     * of the pulled weights, not the tag name). Default throws so text-only
     * implementations (and test fakes) don't have to care about vision.
     */
    default String generate(String systemPrompt, String userPrompt, List<LlmImage> images) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support image input");
    }

    /**
     * Run a prompt and map the model's response onto {@code responseType}. The
     * implementation is responsible for instructing the model to emit a parseable
     * (typically JSON) payload matching the target type.
     */
    <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType);
}
