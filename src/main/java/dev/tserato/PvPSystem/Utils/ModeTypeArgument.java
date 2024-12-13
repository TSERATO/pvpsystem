package dev.tserato.PvPSystem.Utils;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ModeTypeArgument implements CustomArgumentType.Converted<ModeType, String> {

    @Override
    public ModeType convert(String nativeType) throws CommandSyntaxException {
        try {
            return ModeType.valueOf(nativeType.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ignored) {
            Message message = MessageComponentSerializer.message().serialize(Component.text("Invalid mode %s!".formatted(nativeType), NamedTextColor.RED));

            throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
        }
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (ModeType flavor : ModeType.values()) {
            builder.suggest(flavor.name(), MessageComponentSerializer.message().serialize(Component.text("Choose your gamemode.", NamedTextColor.GREEN)));
        }

        return builder.buildFuture();
    }
}