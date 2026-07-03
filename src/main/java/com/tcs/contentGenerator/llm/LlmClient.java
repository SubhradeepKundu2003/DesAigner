package com.tcs.contentGenerator.llm;

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
     * Run a prompt and map the model's response onto {@code responseType}. The
     * implementation is responsible for instructing the model to emit a parseable
     * (typically JSON) payload matching the target type.
     */
    <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType);
}
