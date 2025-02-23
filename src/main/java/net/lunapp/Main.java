package net.lunapp;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class Main {
    /*
     * @param jda The JDA instance
     */
    public static JDA jda;
    private static final FileUtils fileUtils = new FileUtils();
    private static String token;

    public static void main(String[] args) {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            token = properties.getProperty("token");
            //System.out.println("Discord Token: " + token);
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der config.properties: " + e.getMessage());
        }

        JDABuilder builder = JDABuilder.createDefault(token);

        builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE);
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setActivity(Activity.listening("ur mom"));
        builder.setStatus(OnlineStatus.DO_NOT_DISTURB);
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.MESSAGE_CONTENT);
        builder.setAutoReconnect(true);
        jda = builder.build();

        addEvents();
        addCommands();
    }

    /*
     * Adds all commands to the bot
     */
    private static void addCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("ping", "Ping Pong!").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL),
                Commands.slash("ask", "Ask Gemini").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL)
                        .addOption(OptionType.STRING, "prompt", "Prompt Gemini with a question", true)
                        .addOption(OptionType.STRING, "role", "Give gemini a role", false)
                        .addOption(OptionType.BOOLEAN, "ephemeral", "Sends the reply as a message only you can see", false),
                Commands.slash("watchlist", "Have a look at the watchlist").setContexts(InteractionContextType.ALL).setIntegrationTypes(IntegrationType.ALL)
                        .addOption(OptionType.STRING, "add", "Add a show to your watchlist", false)
                        .addOption(OptionType.STRING, "remove", "Remove a show from your watchlist", false)
                        .addOption(OptionType.STRING, "source", "Specify the source of the show", false)
                        .addOption(OptionType.STRING, "edit", "Edit a show in your watchlist", false)
                        .addOption(OptionType.STRING, "newname", "New name for the show", false)
                        .addOption(OptionType.STRING, "newsource", "New source for the show", false)
                        .addOption(OptionType.BOOLEAN, "clear", "Clear the entire watchlist", false)
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
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    classes.add(clazz);
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