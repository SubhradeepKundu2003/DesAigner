package com.tcs.contentGenerator.llm;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Live vision probe. The "IT" suffix keeps it out of Surefire's default
 * includes, so the regular unit suite never touches Ollama — run it
 * explicitly with {@code ./mvnw test -Dtest=SpringAiLlmClientVisionIT}
 * (it additionally self-skips when local Ollama is down). This is the
 * definitive answer to the question TASKS.md flagged as unverified: does
 * Spring AI 2.0's Ollama backend actually plumb {@code Media} image bytes
 * through to the chat message's {@code images} field, or is it a silent
 * no-op? A solid red square makes the assertion model-proof: if the bytes
 * didn't arrive, the model cannot answer "red". Verified live 2026-07-08:
 * passes in ~18s with thinking disabled (with thinking left on, the same
 * call ran &gt;20 min and died — mirror the app's {@code think: false}).
 */
class SpringAiLlmClientVisionIT {

    private static final String BASE_URL = "http://localhost:11434";
    private static final String MODEL = "qwen3.5:4b";

    @Test
    void ollamaBackendPlumbsImageBytesThrough() throws Exception {
        assumeTrue(ollamaIsUp(), "local Ollama not reachable — skipping live vision probe");

        // disableThinking() mirrors the app's `spring.ai.ollama.chat.think: false` —
        // qwen3.5 is a reasoning model, and thinking + vision on CPU ran >20 min
        // before timing out when this was left on (observed live).
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(BASE_URL).build())
                .options((OllamaChatOptions) OllamaChatOptions.builder().model(MODEL).disableThinking().build())
                .build();
        LlmClient client = new SpringAiLlmClient(chatModel);

        String answer = client.generate(
                "You are a vision assistant. Answer in as few words as possible.",
                "What is the dominant color of this image? One word.",
                List.of(LlmImage.png(solidRedSquare())));

        assertTrue(answer != null && answer.toLowerCase(Locale.ROOT).contains("red"),
                "expected the model to see a red image, got: " + answer);
    }

    private static byte[] solidRedSquare() throws Exception {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 128, 128);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static boolean ollamaIsUp() {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) URI.create(BASE_URL + "/api/tags").toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
