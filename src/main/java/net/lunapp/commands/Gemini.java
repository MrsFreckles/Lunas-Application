package net.lunapp.commands;

import net.lunapp.Command;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Command
public class Gemini extends ListenerAdapter {
    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("your-persistence-unit");

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        final String gemini;

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            gemini = properties.getProperty("gemini");
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
            return;
        }

        if (command.equalsIgnoreCase("ask")) {
            String prompt = event.getOption("prompt", OptionMapping::getAsString);
            String role = event.getOption("role", OptionMapping::getAsString);
            Boolean ephemeral = event.getOption("ephemeral", OptionMapping::getAsBoolean);

            if (role == null) role = "user";
            if (ephemeral == null) ephemeral = false;

            final String finalRole = role;
            event.deferReply(ephemeral).addActionRow(
                    Button.danger("cancel_ask", "Cancel")
            ).queue();

            new Thread(() -> {
                EntityManager em = emf.createEntityManager();
                try {
                    em.getTransaction().begin();

                    String url = gemini;
                    String jsonInput = String.format("""
                    {
                      "contents": [
                        {"parts": [{"text": "%s"}]}
                      ],
                      "systemInstruction": {
                        "role": "user",
                        "parts": [
                          {
                            "text": "%s"
                          }
                        ]
                      }
                    }
                    """, prompt, finalRole);

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        JSONObject jsonResponse = new JSONObject(response);
                        String responseText = jsonResponse
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        event.getHook().editOriginal(splitString(responseText, 2000)[0]).setComponents().queue();
                        for (int i = 1; i < splitString(responseText, 2000).length; i++) {
                            event.getHook().sendMessage(splitString(responseText, 2000)[i]).queue();
                        }
                    } else {
                        String errorResponse = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                        System.err.println("Error response: " + errorResponse);
                        event.getHook().editOriginal("Error: " + errorResponse).setComponents().queue();
                    }

                    em.getTransaction().commit();
                } catch (Exception e) {
                    em.getTransaction().rollback();
                    e.printStackTrace();
                    event.getHook().editOriginal("Error: " + e.getMessage()).setComponents().queue();
                } finally {
                    em.close();
                }
            }).start();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getComponentId().equals("cancel_ask")) {
            event.deferEdit().queue();
            event.getHook().editOriginal("The request has been canceled.").setComponents().queue();
        }
    }

    public static String[] splitString(String text, int maxLength) {
        int length = text.length();
        int numberOfParts = (int) Math.ceil((double) length / maxLength);

        String[] parts = new String[numberOfParts];

        for (int i = 0; i < numberOfParts; i++) {
            int start = i * maxLength;
            int end = Math.min((i + 1) * maxLength, length);
            parts[i] = text.substring(start, end);
        }
        return parts;
    }
}