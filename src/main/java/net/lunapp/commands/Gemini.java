package net.lunapp.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.IEventManager;
import net.dv8tion.jda.internal.entities.ReceivedMessage;
import net.lunapp.Command;
import net.lunapp.Main;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Command
public class Gemini extends ListenerAdapter {
    private final List<Messages> userPrompts = new ArrayList<>();
    private final List<String> unicodeFaces = new ArrayList<>();

    public Gemini() {
        loadUnicodeFaces();
    }

    private void loadUnicodeFaces() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            String faces = properties.getProperty("unicodeFaces");
            if (faces != null) {
                for (String face : faces.split(",")) {
                    unicodeFaces.add(face.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();

        System.out.println("Command: " + command); // Debug-Ausgabe
        System.out.println(SlashCommandInteractionEvent.class); // Debug-Ausgabe
        System.out.println(event); // Debug-Ausgabe
        System.out.println(event.getResponseNumber());

        if (command.equalsIgnoreCase("ask")) {
            String prompt = event.getOption("prompt", OptionMapping::getAsString);
            String role = event.getOption("role", OptionMapping::getAsString);
            Boolean ephemeral = event.getOption("ephemeral", OptionMapping::getAsBoolean);
            Message.Attachment attachment = event.getOption("file", OptionMapping::getAsAttachment);

            handleAskCommand(event, prompt, role, ephemeral, attachment);
        } else if (command.equalsIgnoreCase("newchat")) {
            userPrompts.clear();
            event.reply("Chat log has been reset.").setEphemeral(true).queue();
        } else if (command.equalsIgnoreCase("togglelistener")) {
            Main.toggleListener();
            event.reply("Listener has been " + (Main.isListenerEnabled() ? "enabled" : "disabled") + ".").setEphemeral(true).queue();
        }
    }

    public void handleAskCommand(SlashCommandInteractionEvent event, String prompt, String role, Boolean ephemeral, Message.Attachment attachment) {
        if (ephemeral == null) ephemeral = false; // Set default value if null

        if (event != null) {
            event.deferReply(ephemeral).addActionRow(
                    Button.danger("cancel_ask", "Cancel")
            ).queue();
        }

        handleAskCommand(prompt, role, ephemeral, attachment, event);
    }

    public void handleAskCommand(String prompt, String role, Boolean ephemeral, Message.Attachment attachment, SlashCommandInteractionEvent event) {
        System.out.println("handleAskCommand called with prompt: " + prompt); // Debug-Ausgabe

        final String gemini;
        final String roleGemini;
        final String apiKey;

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            gemini = properties.getProperty("gemini");
            roleGemini = properties.getProperty("roleGemini");
            apiKey = properties.getProperty("apiKey");
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
            return;
        }

        if (role == null) role = roleGemini; // Set default role if null

        userPrompts.add(new Messages(prompt, "user"));

        final String finalRole = role;

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (Messages message : userPrompts) {
                sb.append(String.format("""
            {
              "role": "%s",
              "parts": [
                {"text": "%s"}
              ]
            },
            """, message.getAuthor(), message.getMessage()));
            }

            try {
                String jsonInput = String.format("""
            {
              "contents": [
                %s
              ],
              "systemInstruction": {
                 "role": "user",
                 "parts": [
                   {
                     "text": "%s"
                   }
                 ]
               },
               "generationConfig": {
                 "temperature": 1,
                 "topK": 40,
                 "topP": 0.95,
                 "maxOutputTokens": 10000,
                 "responseMimeType": "text/plain"
               }
            }
            """, sb.toString(), finalRole);

                HttpURLConnection conn = (HttpURLConnection) new URL(gemini).openConnection();
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

                    // Debug-Ausgabe der Antwort von Gemini
                    System.out.println("Gemini response: " + responseText);

                    userPrompts.add(new Messages(responseText, "model"));

                    if (event != null) {
                        event.getHook().editOriginal(splitString(responseText, 2000)[0]).setComponents().queue();
                        for (int i = 1; i < splitString(responseText, 2000).length; i++) {
                            event.getHook().sendMessage(splitString(responseText, 2000)[i]).queue();
                        }
                    }

                    if (attachment != null) {
                        File file = new File(attachment.getFileName());
                        try (InputStream in = new URL(attachment.getProxyUrl()).openStream()) {
                            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            String fileUri = uploadFileToGemini(apiKey, file, attachment.getContentType());
                            if (fileUri != null) {
                                event.getHook().sendMessage("File uploaded: " + fileUri).queue();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    String errorResponse = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    System.err.println("Error response: " + errorResponse);
                    if (event != null) {
                        event.getHook().editOriginal("Error: " + errorResponse).setComponents().queue();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (event != null) {
                    event.getHook().editOriginal("Error: " + e.getMessage()).setComponents().queue();
                }
            }
        }).start();
    }

    public void handleAskCommand(String prompt, String role, Boolean ephemeral, Message.Attachment attachment, MessageReceivedEvent event) {
        System.out.println("handleAskCommand called with prompt: " + prompt); // Debug-Ausgabe
        long temp;
        if (event != null) {
                Message loadingMessage = event.getChannel().sendMessage("<a:loading:1344750264703520799> \u200E thinking.. ( ´△｀)").complete();
                temp = loadingMessage.getIdLong();
            } else {
                temp = 0;
            }

        final String gemini;
        final String roleGemini;
        final String apiKey;

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
            gemini = properties.getProperty("gemini");
            roleGemini = properties.getProperty("roleGemini");
            apiKey = properties.getProperty("apiKey");
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
            return;
        }

        if (role == null) role = roleGemini; // Set default role if null

        userPrompts.add(new Messages(prompt, "user"));

        final String finalRole = role;

        long finalTemp = temp;
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            for (Messages message : userPrompts) {
                sb.append(String.format("""
            {
              "role": "%s",
              "parts": [
                {"text": "%s"}
              ]
            },
            """, message.getAuthor(), message.getMessage()));
            }

            try {
                String jsonInput = String.format("""
            {
              "contents": [
                %s
              ],
              "systemInstruction": {
                 "role": "user",
                 "parts": [
                   {
                     "text": "%s"
                   }
                 ]
               },
               "generationConfig": {
                 "temperature": 1,
                 "topK": 40,
                 "topP": 0.95,
                 "maxOutputTokens": 10000,
                 "responseMimeType": "text/plain"
               }
            }
            """, sb.toString(), finalRole);

                HttpURLConnection conn = (HttpURLConnection) new URL(gemini).openConnection();
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

                    // Debug-Ausgabe der Antwort von Gemini
                    System.out.println("Gemini response: " + responseText);

                    userPrompts.add(new Messages(responseText, "model"));


                    if (event != null) {
                        event.getChannel().editMessageById(temp, splitString(responseText, 2000)[0]).queue();
                        for (int i = 1; i < splitString(responseText, 2000).length; i++) {
                            event.getChannel().sendMessage(splitString(responseText, 2000)[i]).queue();
                        }
                    }

                    if (attachment != null) {
                        File file = new File(attachment.getFileName());
                        try (InputStream in = new URL(attachment.getProxyUrl()).openStream()) {
                            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            String fileUri = uploadFileToGemini(apiKey, file, attachment.getContentType());
                            if (fileUri != null) {
                                event.getChannel().sendMessage("File uploaded: " + fileUri).queue();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    String errorResponse = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    System.err.println("Error response: " + errorResponse);
                    if (event != null) {
                        event.getChannel().sendMessage("Error: " + errorResponse).setComponents().queue();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (event != null) {
                    event.getChannel().sendMessage("Error: " + e.getMessage()).setComponents().queue();
                }
            }
        }).start();
    }

    private String uploadFileToGemini(String apiKey, File file, String mimeType) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + apiKey).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-Goog-Upload-Command", "start, upload, finalize");
        conn.setRequestProperty("X-Goog-Upload-Header-Content-Length", String.valueOf(file.length()));
        conn.setRequestProperty("X-Goog-Upload-Header-Content-Type", mimeType);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(("{\"file\": {\"display_name\": \"" + file.getName() + "\"}}").getBytes(StandardCharsets.UTF_8));
            os.write(Files.readAllBytes(file.toPath()));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonResponse = new JSONObject(response);
            if (jsonResponse.has("fileUri")) {
                return jsonResponse.getString("fileUri");
            } else {
                System.err.println("Error: fileUri not found in response");
                return null;
            }
        } else {
            String errorResponse = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("Error uploading file: " + errorResponse);
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

class Messages {
    private String message;
    private String author;

    public Messages(String message, String author) {
        this.message = message.replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replace("_", "\\_");
        this.author = author;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }
}