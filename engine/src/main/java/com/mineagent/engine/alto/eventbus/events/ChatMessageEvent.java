// Ported from AltoClef (MIT, Copyright (c) 2020 Adris Jautakas; see THIRD_PARTY_NOTICES.md): adris/altoclef/eventbus/events/ChatMessageEvent.java at commit
// 1f8e038, in Mojang's names (third_party/altoclef). Changes for the companion's server-side body are marked SMH.
package com.mineagent.engine.alto.eventbus.events;

import net.minecraft.network.chat.ChatType;

/**
 * Whenever chat appears
 */
public class ChatMessageEvent {
    private final String message;
    private final String senderName;
    private final ChatType messageType;

    public ChatMessageEvent(String message, String senderName, ChatType messageType) {
        this.message = message;
        this.senderName = senderName;
        this.messageType = messageType;
    }
    public String messageContent() {
        return message;
    }

    public String senderName() {
        return senderName;
    }

    public ChatType messageType() {
        return messageType;
    }
}
