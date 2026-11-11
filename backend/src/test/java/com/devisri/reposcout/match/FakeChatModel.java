package com.devisri.reposcout.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** A stand-in for Groq: returns the queued replies in order and remembers every prompt it was sent. */
final class FakeChatModel implements ChatModel {

    private final Deque<Object> replies = new ArrayDeque<>();
    final List<Prompt> prompts = new ArrayList<>();

    FakeChatModel reply(String text) {
        replies.add(text);
        return this;
    }

    FakeChatModel fail(RuntimeException e) {
        replies.add(e);
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Object next = replies.isEmpty() ? "{}" : replies.removeFirst();
        if (next instanceof RuntimeException e) {
            throw e;
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage((String) next))));
    }

    String lastUserText() {
        return prompts.get(prompts.size() - 1).getUserMessage().getText();
    }
}
