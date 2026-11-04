package adris.altoclef.eventbus.events;

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
