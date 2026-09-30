package com.instaclone.messaging;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/** Live-delivery transport for messages — delegates straight to the same MessageService.sendMessage
 * the REST endpoint uses, so persistence and participant checks aren't duplicated. */
@Controller
public class MessageStompController {

    private final MessageService messageService;

    public MessageStompController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/conversations/{id}/messages")
    public void sendMessage(@DestinationVariable Long id, @Payload SendMessageRequest request, Principal principal) {
        messageService.sendMessage(id, Long.valueOf(principal.getName()), request);
    }
}
