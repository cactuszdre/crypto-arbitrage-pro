package com.crypto.funding.controller;

import com.crypto.funding.dto.MessageDTO;
import com.crypto.funding.business.MistralService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * JAX-RS Resource for Chat.
 */
@Component
@Path("/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ChatResource {

    private final MistralService mistralService;

    @POST
    public Map<String, String> chat(List<MessageDTO> messages) {
        String response = mistralService.chat(messages);
        return Map.of("content", response);
    }
}
