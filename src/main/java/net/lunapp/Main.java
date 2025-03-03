package net.lunapp;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.lunapp.commands.Gemini;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class Main {
    public static JDA jda;
    private static final FileUtils fileUtils = new FileUtils();
    private static String token;
    private static boolean listenerEnabled = true;
    private static Gemini gemini;

    public static void main(String[] args) {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            token = properties.getProperty("token");
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der config.properties: " + e.getMessage());
        }

        JDABuilder builder = JDABuilder.createDefault(token);

        builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setActivity(Activity.playing("ur mom"));
        builder.setStatus(OnlineStatus.DO_NOT_DISTURB);
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.MESSAGE_CONTENT);
        builder.setAutoReconnect(true);
        jda = builder.build();

        gemini = new Gemini();

        addEvents();
        addCommands();
    }

    private static void addCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("ping", "Ping Pong!").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL),
                Commands.slash("ask", "Ask Gemini").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL)
                        .addOption(OptionType.STRING, "prompt", "Prompt Gemini with a question", true)
                        .addOption(OptionType.STRING, "role", "Give gemini a role", false)
                        .addOption(OptionType.BOOLEAN, "ephemeral", "Sends the reply as a message only you can see", false)
                        .addOption(OptionType.ATTACHMENT, "file", "Upload a file", false),
                Commands.slash("watchlist", "Have a look at the watchlist").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL)
                        .addOption(OptionType.STRING, "add", "Add a show to your watchlist", false)
                        .addOption(OptionType.STRING, "remove", "Remove a show from your watchlist", false)
                        .addOption(OptionType.STRING, "source", "Specify the source of the show", false)
                        .addOption(OptionType.STRING, "edit", "Edit a show in your watchlist", false)
                        .addOption(OptionType.STRING, "newname", "New name for the show", false)
                        .addOption(OptionType.STRING, "newsource", "New source for the show", false)
                        .addOption(OptionType.BOOLEAN, "clear", "Clear the entire watchlist", false),
                Commands.slash("newchat", "Reset the chats log history").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL),
                Commands.slash("togglelistener", "Toggle the listener for 'Mitsuki' or 'Koga'").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL)
        ).queue();
    }

    public static void addEvents() {
        Reflections reflections = new Reflections("net.lunapp.commands", new SubTypesScanner(false), new TypeAnnotationsScanner());

        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Command.class);

        for (Class<?> clazz : annotatedClasses) {
            try {
                Object object = clazz.newInstance();
                jda.addEventListener(object);
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onMessageReceived(@NotNull MessageReceivedEvent event) {
                if (listenerEnabled) {
                    if (event.getAuthor().isBot()) {
                        return; // Ignoriere Nachrichten vom Bot selbst
                    }
                    String message = event.getMessage().getContentRaw().toLowerCase();
                    if (message.contains("mitsuki") || message.contains("koga")) {
                        gemini.handleAskCommand(event.getMessage().getContentRaw() + " Person who just talked to you: " + event.getMessage().getAuthor().getName(), null, false, null, event);
                    }
                }
            }
        });
    }

    public static void toggleListener() {
        listenerEnabled = !listenerEnabled;
    }

    public static boolean isListenerEnabled() {
        return listenerEnabled;
    }

    @NotNull
    public static List<Class<?>> getClassesInPackage(@NotNull String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        String packagePath = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File packageDir = new File(Objects.requireNonNull(classLoader.getResource(packagePath)).getFile());

        findClasses(packageDir, packageName, classes);

        System.out.println("Found " + classes.size() + " classes in package " + packageName);

        return classes;
    }

    private static void findClasses(@NotNull File directory, String packageName, List<Class<?>> classes) {
        if (!directory.exists()) {
            return;
        }

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                findClasses(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class")) {
                try {
                    classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static FileUtils getFileUtils() {
        return fileUtils;
    }
}