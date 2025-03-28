package net.lunapp.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import net.lunapp.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TwitchBot {

    private static final Logger log = LoggerFactory.getLogger(TwitchBot.class);
    private Properties properties = loadConfigProperties();
    private TwitchClient twitchClient;

    public TwitchBot() {
        String twitchAccessToken = properties.getProperty("twitchAccessToken");
        OAuth2Credential credential = new OAuth2Credential("twitch", twitchAccessToken);
        twitchClient = TwitchClientBuilder.builder()
                .withEnableHelix(true)
                .withEnableChat(true)
                .withChatAccount(credential)
                .build();

        // Tritt dem Twitch-Channel bei
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
            System.err.println("Fehler beim Laden der config.properties: " + e.getMessage());
        }
        return properties;
    }

    /**
     * Behandelt ChannelMessageEvents aus dem Twitch-Chat.
     * Erkennt Nachrichten, die "mitsuki" oder "koga" enthalten,
     * und leitet sie an Gemini weiter.
     *
     * Hier wird nun die neue Methode handleTwitchMessage verwendet.
     *
     * @param event Das ChannelMessageEvent.
     */
    private void handleMessageEvent(ChannelMessageEvent event) {
        String messageLower = event.getMessage().toLowerCase();
        if (messageLower.contains("mitsuki") || messageLower.contains("koga")) {
            String roleGemini = properties.getProperty("roleGemini");
            String prompt = "Message from " + event.getUser().getName() + ": " + event.getMessage();
            log.info("Received Twitch message: " + prompt);

            // Aufruf von Gemini, der auch den Memory-Control-Prozess triggert
            Main.getGemini().handleTwitchMessage(
                    prompt,
                    roleGemini + " Versuche dich bitte kurz zu halten. Du bist oft im Twitch-Chat von frecklesmp4 (Luna).",
                    response -> {
                        log.info("Received response from Gemini: " + response);
                        if (response == null || response.trim().isEmpty()) {
                            log.warn("Gemini response is empty. Skipping message send.");
                            return;
                        }

                        String channel = "frecklesmp4";
                        int maxLength = 500;
                        List<String> messages = new ArrayList<>();

                        // Nachricht in max. 500-Zeichen lange Teile aufsplitten
                        for (int i = 0; i < response.length(); i += maxLength) {
                            messages.add(response.substring(i, Math.min(i + maxLength, response.length())));
                        }

                        // Sicherstellen, dass der Socket-Server vorhanden ist
                        if (Main.getSocketServer() == null) {
                            log.error("SocketServer ist NULL! Broadcast nicht möglich.");
                            return;
                        }

                        // Sende jeden Nachrichten-Teil an Twitch und an den WebSocket
                        new Thread(() -> {
                            for (String msg : messages) {
                                twitchClient.getChat().sendMessage(channel, msg);
                                Main.getSocketServer().broadcast(msg);
                                log.info("Sent message to Twitch & WebSocket: " + msg);
                                try {
                                    Thread.sleep(1000); // 1 Sekunde Pause zwischen Nachrichten
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }).start();
                    }
            );
        }
    }



    public TwitchClient getTwitchClient() {
        return twitchClient;
    }
}
