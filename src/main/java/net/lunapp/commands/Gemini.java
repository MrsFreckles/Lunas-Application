package net.lunapp.commands;

import net.lunapp.Command;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Command
public class Gemini extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event){
        String command = event.getName();
        if(command.equalsIgnoreCase("ask")){
            String prompt = event.getOption("prompt", OptionMapping::getAsString);
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=AIzaSyC5W1JUijGZeSbVAWFj5gmHOyYQ29vlf78";
                String jsonInput = String.format("""
                {
                  "contents": [
                    {"parts": [{"text": "%s"}]}
                  ],
                  "systemInstruction": {
                    "role": "user",
                    "parts": [
                      {
                        "text": "Du bist eine Hilfsbereite Ki"
                      }
                    ]
                  },
                }
                """, prompt);

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

                event.reply(splitString(responseText, 2000)[0]).queue();
                for (int i = 1; i < splitString(responseText, 2000).length; i++) {
                    event.getHook().sendMessage(splitString(responseText, 20)[i]).queue();
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
