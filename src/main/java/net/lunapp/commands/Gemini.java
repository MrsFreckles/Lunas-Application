package net.lunapp.commands;

import net.lunapp.Command;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Command
public class Gemini extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event){
        String command = event.getName();
        String gemini = "";

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            gemini = properties.getProperty("gemini");
            //System.out.println("Gemini key: " + gemini);
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der config.properties: " + e.getMessage());
        }
        if(command.equalsIgnoreCase("ask")){
            String prompt = event.getOption("prompt", OptionMapping::getAsString);
            String parts = event.getOption("role", OptionMapping::getAsString);
            Boolean ephemeral = event.getOption("ephemeral", OptionMapping::getAsBoolean);

            if(parts == null) parts = "user";
            if(ephemeral == null) ephemeral = false;

            try {
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
                  },
                }
                """, prompt, parts);

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                conn.getOutputStream().write(jsonInput.getBytes(StandardCharsets.UTF_8));

                // Parse the response
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonResponse = new JSONObject(response);
                String responseText = jsonResponse
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");



                event.reply(splitString(responseText, 2000)[0]).setEphemeral(ephemeral).queue();
                for (int i = 1; i < splitString(responseText, 2000).length; i++) {
                    event.getHook().sendMessage(splitString(responseText, 20)[i]).setEphemeral(ephemeral).queue();
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error: " + e.getMessage());
            }
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
