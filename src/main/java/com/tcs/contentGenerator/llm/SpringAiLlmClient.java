package com.tcs.contentGenerator.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * {@link LlmClient} backed by Spring AI's {@link ChatClient}. Spring AI already
 * abstracts over providers, so this wrapper's only job is to keep the rest of the
 * app off Spring AI types: agents talk to {@link LlmClient}, and whichever
 * {@link ChatModel} bean is auto-configured (Ollama here) is injected in.
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }

    /**
     * Structured variant. Rather than lean on {@code .entity()}'s built-in cleanup
     * (which small local models defeat by wrapping output in ```json fences or
     * surrounding prose), we append the converter's format instructions to the
     * prompt, take the raw completion, isolate the JSON payload ourselves, then let
     * the converter parse it. This keeps structured output robust across models.
     */
    @Override
    public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(responseType);
        String userWithFormat = userPrompt + "\n\n" + converter.getFormat();
        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userWithFormat)
                .call()
                .content();
        String json = extractJson(raw);
        try {
            return converter.convert(json);
        } catch (RuntimeException e) {
            // Observed live: the local model sometimes omits the commas between
            // array elements ("[{...} {...}]"). "} {" never occurs in valid JSON
            // outside strings, so inserting the commas is safe; retry once.
            String repaired = insertMissingCommas(json);
            if (repaired.equals(json)) {
                throw e;
            }
            return converter.convert(repaired);
        }
    }

    /**
     * Repairs a missing-comma quirk of small local models: a {@code }} or {@code ]}
     * followed (over whitespace) by {@code {} or {@code [} outside any string
     * literal can only be two adjacent array elements missing their separator.
     */
    private static String insertMissingCommas(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            sb.append(c);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '}' || c == ']') {
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < json.length() && (json.charAt(j) == '{' || json.charAt(j) == '[')) {
                    sb.append(',');
                }
            }
        }
        return sb.toString();
    }

    /**
     * Pull the first complete JSON value out of a model completion. Local models
     * routinely pad their answer with leading prose, markdown fences, or even a
     * second (e.g. fenced) copy of the same payload, so we can't just span from the
     * first bracket to the last — that would swallow whatever sits in between.
     * Instead we start at the first {@code [}/{@code &#123;} and track bracket
     * depth (ignoring brackets inside string literals) until it returns to zero,
     * which is exactly the end of that first value. Falls back to the trimmed text
     * if no brackets are found, letting the parser surface a clear error.
     */
    private static String extractJson(String raw) {
        if (raw == null) {
            return "";
        }
        int start = firstBracket(raw);
        if (start < 0) {
            return raw.strip();
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '[', '{' -> depth++;
                case ']', '}' -> {
                    depth--;
                    if (depth == 0) {
                        return raw.substring(start, i + 1);
                    }
                }
                default -> { }
            }
        }
        return raw.substring(start).strip();
    }

    private static int firstBracket(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '{') {
                return i;
            }
        }
        return -1;
    }
}
