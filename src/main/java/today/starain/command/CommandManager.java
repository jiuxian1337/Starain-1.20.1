package today.starain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import lombok.Getter;
import net.minecraft.commands.SharedSuggestionProvider;
import org.lwjgl.glfw.GLFW;
import today.starain.Starain;
import today.starain.command.impl.BindCommand;
import today.starain.command.impl.ConfigCommand;
import today.starain.command.impl.ToggleCommand;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.ChatEvent;
import today.starain.utils.wrapper.Wrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Getter
public final class CommandManager {
    public static final String PREFIX = ".";

    private final List<Command> commands = new ArrayList<>();
    private final CommandDispatcher<SharedSuggestionProvider> dispatcher = new CommandDispatcher<>();

    private CommandManager() {
        load();
        loadDispatcher();
        Starain.getInstance().getEventManager().register(this);
    }

    public static CommandManager newInstance() {
        return new CommandManager();
    }

    private void load() {
        addCommand(new BindCommand(), new ToggleCommand(), new ConfigCommand());
    }

    private void loadDispatcher() {
        registerBindCommand("bind");
        registerBindCommand("b");
        registerToggleCommand("toggle");
        registerToggleCommand("t");
        registerConfigCommand("config");
        registerConfigCommand("cfg");
    }

    private void addCommand(Command... commands) {
        for (Command command : commands) {
            this.commands.add(command);
            Starain.getInstance().getEventManager().register(command);
        }
    }

    private void registerBindCommand(String name) {
        dispatcher.register(literal(name)
                .then(moduleArgument()
                        .executes(context -> 1)
                        .then(RequiredArgumentBuilder
                                .<SharedSuggestionProvider, String>argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(keySuggestions(), builder))
                                .executes(context -> 1)))
                .executes(context -> 1));
    }

    private void registerToggleCommand(String name) {
        dispatcher.register(literal(name)
                .then(moduleArgument().executes(context -> 1))
                .executes(context -> 1));
    }

    private void registerConfigCommand(String name) {
        dispatcher.register(literal(name)
                .then(RequiredArgumentBuilder
                        .<SharedSuggestionProvider, String>argument("action", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("load", "save"), builder))
                        .then(RequiredArgumentBuilder
                                .<SharedSuggestionProvider, String>argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(configSuggestions(), builder))
                                .executes(context -> 1))
                        .executes(context -> 1))
                .executes(context -> 1));
    }

    private List<String> configSuggestions() {
        List<String> suggestions = new ArrayList<>(List.of("latest"));
        File[] files = Wrapper.resolveConfigFolder().listFiles((dir, fileName) -> fileName.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String configName = fileName.substring(0, fileName.length() - ".json".length());
                if (!suggestions.contains(configName)) {
                    suggestions.add(configName);
                }
            }
        }
        return suggestions;
    }

    private RequiredArgumentBuilder<SharedSuggestionProvider, String> moduleArgument() {
        return RequiredArgumentBuilder
                .<SharedSuggestionProvider, String>argument("module", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Starain.getInstance().getModuleManager().getModules().stream()
                                .map(module -> module.getName()),
                        builder
                ));
    }

    private LiteralArgumentBuilder<SharedSuggestionProvider> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private List<String> keySuggestions() {
        List<String> suggestions = new ArrayList<>(List.of(
                "None", "MouseLeft", "MouseRight", "MouseMiddle",
                "LShift", "RShift", "LCtrl", "RCtrl", "LAlt", "RAlt",
                "Space", "Enter", "Esc", "Delete", "Backspace", "Tab",
                "Up", "Down", "Left", "Right"
        ));

        for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) {
            suggestions.add(String.valueOf((char) ('A' + key - GLFW.GLFW_KEY_A)));
        }
        for (int key = GLFW.GLFW_KEY_0; key <= GLFW.GLFW_KEY_9; key++) {
            suggestions.add(String.valueOf((char) ('0' + key - GLFW.GLFW_KEY_0)));
        }
        return suggestions;
    }

    @EventTarget
    public void onChat(ChatEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith(PREFIX)) {
            return;
        }

        String[] args = message.trim().split("\\s+");
        if (args.length == 0) {
            return;
        }

        String commandName = args[0].substring(PREFIX.length());
        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(commandName)) {
                command.onCommand(args);
                event.setCancelled(true);
                return;
            }

            for (String alias : command.getAliases()) {
                if (alias.equalsIgnoreCase(commandName)) {
                    command.onCommand(args);
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
