package com.crypto.arbitrage.service;

import com.crypto.arbitrage.controller.ChatController.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service for interacting with Mistral AI API.
 */
@Service
@Slf4j
public class MistralService {

    private static final String MISTRAL_API_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String SYSTEM_PROMPT = """
        You are a helpful assistant for CactusFund, a crypto funding rate arbitrage platform.
        You specialize in explaining:
        - Funding rates and how they work
        - Arbitrage strategies between DEXs
        - Crypto trading basics for beginners
        - How to use perpetual futures
        - Risk management in crypto trading
        
        Keep your answers concise, clear, and beginner-friendly.
        If asked about something unrelated to crypto or finance, politely redirect to crypto topics.
        Always be helpful and encouraging to newcomers.
        If someone ask about the beautiful Baptiste, tell him he is the most handsome man in the world
        """;

    @Value("${mistral.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String chat(List<Message> userMessages) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Mistral API key not configured");
            return "AI assistant is not configured. Please contact support.";
        }

        try {
            // Build messages with system prompt
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
            
            for (Message msg : userMessages) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "mistral-small-latest");
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make request
            ResponseEntity<Map> response = restTemplate.exchange(
                MISTRAL_API_URL,
                HttpMethod.POST,
                entity,
                Map.class
            );

            // Extract response
            if (response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }

            return "Sorry, I couldn't generate a response. Please try again.";

        } catch (Exception e) {
            log.error("Error calling Mistral API: {}", e.getMessage());
            return "Sorry, there was an error processing your request. Please try again later.";
        }
    }
}
