package com.tcs.contentGenerator.llm;

/**
 * One image attached to a vision LLM call — raw bytes plus their IANA mime
 * type (e.g. {@code image/png}). Kept provider-agnostic so agents never touch
 * Spring AI's {@code Media} type directly.
 */
public record LlmImage(String mimeType, byte[] bytes) {

    public static LlmImage png(byte[] bytes) {
        return new LlmImage("image/png", bytes);
    }

    public static LlmImage jpeg(byte[] bytes) {
        return new LlmImage("image/jpeg", bytes);
    }
}
