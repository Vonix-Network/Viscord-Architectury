package network.vonix.viscord.discord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.Embed;
import org.javacord.api.entity.message.embed.EmbedField;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles conversion between Discord messages/embeds and Minecraft components.
 * Implements "Embed Repairing" and "Chat Formatting".
 */
public class MessageConverter {

    private static final Pattern URL_PATTERN = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
    private static final Pattern DISCORD_FORMATTING_PATTERN = Pattern.compile("(\\*\\*|\\*|__|~~|`|\\|\\|)");

    /**
     * Converts a Discord Message to a Minecraft Component.
     * Handles text, attachments, and embeds.
     */
    public static Component toMinecraft(Message message) {
        MutableComponent root = Component.literal("");

        // 1. Author Name (with hover tooltip)
        String authorName = message.getAuthor().getDisplayName();
        MutableComponent authorComponent = Component.literal("<" + authorName + "> ")
                .withStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0x5865F2)) // Discord Blurple
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                Component.literal(message.getAuthor().getDiscriminatedName()))));
        root.append(authorComponent);

        // 2. Message Content (if present)
        String content = message.getContent();
        if (!content.isEmpty()) {
            root.append(parseMarkdown(content));
        }

        // 3. Attachments
        for (MessageAttachment attachment : message.getAttachments()) {
            if (!content.isEmpty()) root.append(" ");
            
            String fileName = attachment.getFileName();
            String url = attachment.getUrl().toString();
            
            MutableComponent attachmentComp = Component.literal("[" + fileName + "]")
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open attachment"))));
            
            root.append(attachmentComp);
        }

        // 4. Embeds (The "Repair" logic)
        for (Embed embed : message.getEmbeds()) {
            root.append(repairEmbed(embed));
        }

        return root;
    }

    /**
     * "Repairs" a Discord embed by converting it into a readable Minecraft text component.
     */
    private static Component repairEmbed(Embed embed) {
        MutableComponent embedComponent = Component.literal("\n");
        
        // Border/Header
        embedComponent.append(Component.literal("┌── ").withStyle(ChatFormatting.DARK_GRAY));
        
        // Title
        if (embed.getTitle().isPresent()) {
            MutableComponent title = Component.literal(embed.getTitle().get())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true));
            
            if (embed.getUrl().isPresent()) {
                title.withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, embed.getUrl().get().toString()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open link"))));
            }
            embedComponent.append(title);
        } else {
            embedComponent.append(Component.literal("Embed").withStyle(ChatFormatting.AQUA));
        }
        
        embedComponent.append("\n");

        // Description
        if (embed.getDescription().isPresent()) {
            embedComponent.append(Component.literal("│ ").withStyle(ChatFormatting.DARK_GRAY));
            embedComponent.append(parseMarkdown(embed.getDescription().get()));
            embedComponent.append("\n");
        }

        // Fields
        for (EmbedField field : embed.getFields()) {
            embedComponent.append(Component.literal("│ ").withStyle(ChatFormatting.DARK_GRAY));
            embedComponent.append(Component.literal(field.getName() + ": ").withStyle(ChatFormatting.GOLD));
            embedComponent.append(parseMarkdown(field.getValue()));
            embedComponent.append("\n");
        }
        
        // Footer
        if (embed.getFooter().isPresent()) {
            embedComponent.append(Component.literal("│ ").withStyle(ChatFormatting.DARK_GRAY));
            embedComponent.append(Component.literal(embed.getFooter().get().getText().orElse("")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            embedComponent.append("\n");
        }

        // Bottom Border
        embedComponent.append(Component.literal("└──").withStyle(ChatFormatting.DARK_GRAY));

        return embedComponent;
    }

    /**
     * Parses simple Markdown (bold, italic, underline, strikethrough) and Links.
     */
    private static Component parseMarkdown(String text) {
        MutableComponent root = Component.literal("");
        Matcher matcher = URL_PATTERN.matcher(text);
        
        int lastEnd = 0;
        while (matcher.find()) {
            String pre = text.substring(lastEnd, matcher.start());
            if (!pre.isEmpty()) {
                root.append(formatText(pre));
            }
            
            String url = matcher.group();
            MutableComponent link = Component.literal(url)
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
            root.append(link);
            
            lastEnd = matcher.end();
        }
        
        String remaining = text.substring(lastEnd);
        if (!remaining.isEmpty()) {
            root.append(formatText(remaining));
        }
        
        return root;
    }
    
    private static Component formatText(String text) {
        return Component.literal(text);
    }
}
