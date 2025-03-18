package net.lunapp.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import net.lunapp.Main;
import net.lunapp.commands.Gemini;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class TwitchBot {

    private static final Logger log = LoggerFactory.getLogger(TwitchBot.class);
    private Properties properties = loadConfigProperties();
    private TwitchClient twitchClient;

    public TwitchBot(){
        String twitchAccessToken = properties.getProperty("twitchAccessToken");
        OAuth2Credential credential = new OAuth2Credential("twitch", twitchAccessToken);
        twitchClient = TwitchClientBuilder.builder()
                .withEnableHelix(true)
                .withEnableChat(true)
                .withChatAccount(credential)
                .build();

        twitchClient.getChat().joinChannel("frecklesmp4");

        EventManager eventManager = twitchClient.getEventManager();
        eventManager.onEvent(ChannelMessageEvent.class, this::handleMessageEvent);

        twitchClient.getChat().sendMessage("frecklesmp4", "Hello World!");

    }

    /**
     * Lädt die Konfiguration aus der Datei config.properties.
     *
     * @return Ein Properties-Objekt mit den Konfigurationseinstellungen.
     */
    private Properties loadConfigProperties() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
        }
        return properties;
    }

    private void handleMessageEvent(ChannelMessageEvent event) {
        if (event.getMessage().toLowerCase().contains("mitsuki") || event.getMessage().toLowerCase().contains("koga")) {
            String roleGemini = properties.getProperty("roleGemini");
            System.out.println(
                    "Message from " + event.getUser().getName() + ": " + event.getMessage()
            );
            Main.getGemini().handleMessageEvent("Message from " + event.getUser().getName() + ": " + event.getMessage(), roleGemini  + " Versuche dich bitte kurz zuhalten ;)");
        }
    }

    public TwitchClient getTwitchClient() {
        return twitchClient;
    }
}
