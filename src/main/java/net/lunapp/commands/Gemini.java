package net.lunapp.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.lunapp.Command;
import net.lunapp.Main;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Der Gemini-Command verarbeitet Anfragen via SlashCommands und Nachrichten,
 * speichert den Chatverlauf (Kurzzeit- und Langzeitspeicher) und kommuniziert
 * mit einem externen Gemini-Service.
 */
@Command
public class Gemini extends ListenerAdapter {

    private final List<Messages> userPrompts = new ArrayList<>();
    private final List<String> unicodeFaces = new ArrayList<>();
    private static final String SHORT_TERM_MEMORY_FILE = "short_term_memory.json";
    private static final String LONG_TERM_MEMORY_FILE = "long_term_memory.json";

    /**
     * Konstruktor. Lädt Unicode-Faces aus der Konfiguration und den Kurzzeitspeicher.
     */
    public Gemini() {
        loadUnicodeFaces();
        loadMemory();
    }

    /**
     * Lädt Unicode-Gesichter aus der Datei config.properties.
     */
    private void loadUnicodeFaces() {
        Properties properties = loadConfigProperties();
        String faces = properties.getProperty("unicodeFaces");
        if (faces != null) {
            for (String face : faces.split(",")) {
                unicodeFaces.add(face.trim());
            }
        }
    }

    /**
     * Lädt den Kurzzeitspeicher (userPrompts) aus der JSON-Datei.
     */
    private void loadMemory() {
        try {
            Path memoryPath = Paths.get(SHORT_TERM_MEMORY_FILE);
            if (Files.exists(memoryPath)) {
                String content = Files.readString(memoryPath);
                JSONArray jsonArray = new JSONArray(content);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    userPrompts.add(new Messages(jsonObject.getString("message"),
                            jsonObject.getString("author")));
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading short term memory: " + e.getMessage());
        }
    }

    /**
     * Speichert den Kurzzeitspeicher (userPrompts) in eine JSON-Datei.
     */
    private void saveMemory() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Messages message : userPrompts) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("message", message.getMessage());
                jsonObject.put("author", message.getAuthor());
                jsonArray.put(jsonObject);
            }
            Files.writeString(Paths.get(SHORT_TERM_MEMORY_FILE),
                    jsonArray.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error saving short term memory: " + e.getMessage());
        }
    }

    /**
     * Aktualisiert den Langzeitspeicher. Wird ein Text übergeben, der bereits existiert,
     * wird er bei remove=true gelöscht. Andernfalls wird der Text hinzugefügt.
     *
     * @param text   Der zu speichernde Text.
     * @param remove Flag, ob der Text entfernt werden soll.
     */
    private void updateLongTermMemory(String text, boolean remove) {
        try {
            JSONArray jsonArray;
            Path longTermPath = Paths.get(LONG_TERM_MEMORY_FILE);
            if (Files.exists(longTermPath)) {
                String content = Files.readString(longTermPath);
                jsonArray = new JSONArray(content);
            } else {
                jsonArray = new JSONArray();
            }

            if (remove) {
                boolean found = false;
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String storedText = jsonObject.getString("text");
                    if (calculateSimilarity(text, storedText) >= 0.9) {
                        jsonArray.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.err.println("Error: No matching entry found to forget.");
                }
            } else {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", jsonArray.length() + 1);
                jsonObject.put("text", text);
                jsonArray.put(jsonObject);
            }

            Files.writeString(longTermPath,
                    jsonArray.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error updating long term memory: " + e.getMessage());
        }
    }

    /**
     * Berechnet die Ähnlichkeit zweier Texte basierend auf gemeinsamen Wörtern.
     *
     * @param text1 Erster Text.
     * @param text2 Zweiter Text.
     * @return Verhältnis der übereinstimmenden Wörter zur maximalen Wortanzahl.
     */
    private double calculateSimilarity(String text1, String text2) {
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");
        int matches = 0;

        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equalsIgnoreCase(word2)) {
                    matches++;
                    break;
                }
            }
        }
        return (double) matches / Math.max(words1.length, words2.length);
    }

    /**
     * Verarbeitet Slash-Command-Interaktionen.
     *
     * @param event Das SlashCommandInteractionEvent.
     */
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();

        if (command.equalsIgnoreCase("ask")) {
            String prompt = event.getOption("prompt", OptionMapping::getAsString);
            String role = event.getOption("role", OptionMapping::getAsString);
            Boolean ephemeral = event.getOption("ephemeral", OptionMapping::getAsBoolean);
            Message.Attachment attachment = event.getOption("file", OptionMapping::getAsAttachment);
            handleAskCommand(event, prompt, role, ephemeral, attachment);
        } else if (command.equalsIgnoreCase("newchat")) {
            userPrompts.clear();
            saveMemory();
            event.reply("Chat log has been reset.").setEphemeral(true).queue();
        } else if (command.equalsIgnoreCase("togglelistener")) {
            Main.toggleListener();
            event.reply("Listener has been " + (Main.isListenerEnabled() ? "enabled" : "disabled") + ".")
                    .setEphemeral(true)
                    .queue();
        }
    }

    /**
     * Verarbeitet den "ask"-Befehl von Slash-Commands.
     *
     * @param event      Das SlashCommandInteractionEvent.
     * @param prompt     Die Benutzeranfrage.
     * @param role       Die gewünschte Rolle.
     * @param ephemeral  Ob die Antwort nur für den Benutzer sichtbar sein soll.
     * @param attachment Mögliche Dateianlage.
     */
    public void handleAskCommand(SlashCommandInteractionEvent event, String prompt, String role, Boolean ephemeral, Message.Attachment attachment) {
        if (ephemeral == null) {
            ephemeral = false;
        }

        if (event != null) {
            event.deferReply(ephemeral)
                    .addActionRow(Button.danger("cancel_ask", "Cancel"))
                    .queue();
        }
        handleAskCommand(prompt, role, ephemeral, attachment, event);
    }

    /**
     * Gemeinsame Logik zur Verarbeitung von "ask"-Befehlen (SlashCommand-Version).
     *
     * @param prompt     Die Benutzeranfrage.
     * @param role       Die gewünschte Rolle.
     * @param ephemeral  Ob die Antwort nur für den Benutzer sichtbar sein soll.
     * @param attachment Mögliche Dateianlage.
     * @param event      Das zugehörige SlashCommandInteractionEvent.
     */
    public void handleAskCommand(String prompt, String role, Boolean ephemeral, Message.Attachment attachment, SlashCommandInteractionEvent event) {
        Properties properties = loadConfigProperties();
        String gemini = properties.getProperty("gemini");
        String roleGemini = properties.getProperty("roleGemini");
        String apiKey = properties.getProperty("apiKey");

        if (role == null) {
            role = roleGemini;
        }

        userPrompts.add(new Messages(prompt, "user"));
        if (userPrompts.size() > 20) {
            userPrompts.remove(0);
        }
        saveMemory();

        final String finalRole = role;

        new Thread(() -> {
            // Baue den "contents"-Array der Konversation
            JSONArray contents = new JSONArray();
            for (Messages message : userPrompts) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", message.getAuthor());
                JSONArray parts = new JSONArray();
                JSONObject partObj = new JSONObject();
                partObj.put("text", message.getMessage());
                parts.put(partObj);
                msgObj.put("parts", parts);
                contents.put(msgObj);
            }

            // Erstelle systemInstruction-Objekt
            JSONObject systemInstruction = new JSONObject();
            systemInstruction.put("role", "user");
            JSONArray sysParts = new JSONArray();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", finalRole);
            sysParts.put(sysPart);
            systemInstruction.put("parts", sysParts);

            // Erstelle generationConfig-Objekt
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 1);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 10000);
            generationConfig.put("responseMimeType", "text/plain");

            // Erstelle das finale JSON-Payload
            JSONObject payload = new JSONObject();
            payload.put("contents", contents);
            payload.put("systemInstruction", systemInstruction);
            payload.put("generationConfig", generationConfig);

            try {
                String jsonInput = payload.toString();

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

                    userPrompts.add(new Messages(responseText, "model"));
                    saveMemory();

                    String[] parts = splitString(responseText, 2000);
                    if (event != null) {
                        event.getHook().editOriginal(parts[0]).setComponents().queue();
                        for (int i = 1; i < parts.length; i++) {
                            event.getHook().sendMessage(parts[i]).queue();
                        }
                    }

                    if (prompt.toLowerCase().contains("remember") || prompt.toLowerCase().contains("merke")) {
                        updateLongTermMemory(prompt, false);
                        if (event != null) {
                            String updatedContent = event.getHook().retrieveOriginal().complete().getContentRaw()
                                    + "\n-# " + prompt;
                            event.getHook().editOriginal(updatedContent).queue();
                        }
                    }

                    if (attachment != null) {
                        try {
                            String fileName = attachment.getFileName();
                            String suffix = "";
                            int dotIndex = fileName.lastIndexOf('.');
                            if (dotIndex >= 0) {
                                suffix = fileName.substring(dotIndex);
                            } else {
                                suffix = ".tmp";
                            }
                            File tempFile = File.createTempFile("discordUpload", suffix);
                            try (InputStream in = new URL(attachment.getProxyUrl()).openStream()) {
                                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            String fileUri = uploadFileToGemini(apiKey, tempFile, attachment.getContentType());
                            if (fileUri != null) {
                                event.getHook().sendMessage("File uploaded: " + fileUri).queue();
                            }
                            tempFile.delete();
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

    /**
     * Verarbeitet den "ask"-Befehl, wenn er als normale Nachricht (MessageReceivedEvent) auftritt.
     *
     * @param prompt     Die Benutzeranfrage.
     * @param role       Die gewünschte Rolle.
     * @param ephemeral  Ob die Antwort nur für den Benutzer sichtbar sein soll.
     * @param attachment Mögliche Dateianlage.
     * @param event      Das MessageReceivedEvent.
     */
    public void handleAskCommand(String prompt, String role, Boolean ephemeral, Message.Attachment attachment, MessageReceivedEvent event) {
        long loadingMessageId = 0;
        if (event != null) {
            Message loadingMessage = event.getChannel()
                    .sendMessage("<a:loading:1344750264703520799> \u200E thinking.. ( ´△｀)")
                    .complete();
            loadingMessageId = loadingMessage.getIdLong();
        }

        Properties properties = loadConfigProperties();
        String gemini = properties.getProperty("gemini");
        String roleGemini = properties.getProperty("roleGemini");
        String apiKey = properties.getProperty("apiKey");

        if (role == null) {
            role = roleGemini;
        }

        userPrompts.add(new Messages(prompt, "user"));
        if (userPrompts.size() > 20) {
            userPrompts.remove(0);
        }
        saveMemory();

        final String finalRole = role;
        long finalLoadingMessageId = loadingMessageId;

        new Thread(() -> {
            JSONArray contents = new JSONArray();
            for (Messages message : userPrompts) {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", message.getAuthor());
                JSONArray parts = new JSONArray();
                JSONObject partObj = new JSONObject();
                partObj.put("text", message.getMessage());
                parts.put(partObj);
                msgObj.put("parts", parts);
                contents.put(msgObj);
            }

            JSONObject systemInstruction = new JSONObject();
            systemInstruction.put("role", "user");
            JSONArray sysParts = new JSONArray();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", finalRole);
            sysParts.put(sysPart);
            systemInstruction.put("parts", sysParts);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 1);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 10000);
            generationConfig.put("responseMimeType", "text/plain");

            JSONObject payload = new JSONObject();
            payload.put("contents", contents);
            payload.put("systemInstruction", systemInstruction);
            payload.put("generationConfig", generationConfig);

            try {
                String jsonInput = payload.toString();

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

                    userPrompts.add(new Messages(responseText, "model"));
                    saveMemory();

                    String[] parts = splitString(responseText, 2000);
                    if (event != null) {
                        event.getChannel().editMessageById(finalLoadingMessageId, parts[0]).queue();
                        for (int i = 1; i < parts.length; i++) {
                            event.getChannel().sendMessage(parts[i]).queue();
                        }
                    }

                    if (prompt.toLowerCase().contains("remember") || prompt.toLowerCase().contains("merke")) {
                        updateLongTermMemory(prompt, false);
                        if (event != null) {
                            long messageId = event.getChannel().getLatestMessageIdLong();
                            String updatedContent = event.getChannel().retrieveMessageById(messageId)
                                    .complete().getContentRaw() + "\n-# " + prompt;
                            event.getChannel().editMessageById(messageId, updatedContent).queue();
                        }
                    }

                    if (attachment != null) {
                        try {
                            String fileName = attachment.getFileName();
                            String suffix = "";
                            int dotIndex = fileName.lastIndexOf('.');
                            if (dotIndex >= 0) {
                                suffix = fileName.substring(dotIndex);
                            } else {
                                suffix = ".tmp";
                            }
                            File tempFile = File.createTempFile("discordUpload", suffix);
                            try (InputStream in = new URL(attachment.getProxyUrl()).openStream()) {
                                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            String fileUri = uploadFileToGemini(apiKey, tempFile, attachment.getContentType());
                            if (fileUri != null) {
                                event.getChannel().sendMessage("File uploaded: " + fileUri).queue();
                            }
                            tempFile.delete();
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

    /**
     * Behandelt Button-Interaktionen (z. B. "Cancel" beim "ask"-Befehl).
     *
     * @param event Das ButtonInteractionEvent.
     */
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getComponentId().equals("cancel_ask")) {
            event.deferEdit().queue();
            event.getHook().editOriginal("The request has been canceled.").setComponents().queue();
        }
    }

    /**
     * Teilt einen langen Text in mehrere Teile auf, die jeweils höchstens maxLength Zeichen lang sind.
     *
     * @param text      Der zu teilende Text.
     * @param maxLength Maximale Zeichenlänge pro Teil.
     * @return Array mit den Textteilen.
     */
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

    /**
     * Lädt eine Datei zum Gemini-Service hoch.
     *
     * Hier wird ein Multipart/Related-Request verwendet, der den JSON-Header und
     * den Dateiinhalt als separaten Part übermittelt.
     *
     * @param apiKey   Der API-Schlüssel.
     * @param file     Die Datei, die hochgeladen werden soll.
     * @param mimeType Der MIME-Typ der Datei.
     * @return Die URI der hochgeladenen Datei.
     * @throws IOException Falls ein Fehler beim Hochladen auftritt.
     */
    private String uploadFileToGemini(String apiKey, File file, String mimeType) throws IOException {
        URL url = new URL("https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        String boundary = "----GeminiBoundary" + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        conn.setRequestProperty("X-Goog-Upload-Command", "start, upload, finalize");
        conn.setDoOutput(true);

        // Erstelle den JSON-Header
        JSONObject headerObj = new JSONObject();
        JSONObject fileObj = new JSONObject();
        fileObj.put("display_name", file.getName());
        headerObj.put("file", fileObj);
        String headerJson = headerObj.toString();

        String lineBreak = "\r\n";
        String boundaryPrefix = "--" + boundary;

        // Baue den Multipart-Body mithilfe eines ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Erster Part: JSON-Header
        dos.writeBytes(boundaryPrefix + lineBreak);
        dos.writeBytes("Content-Type: application/json; charset=UTF-8" + lineBreak);
        dos.writeBytes(lineBreak);
        dos.writeBytes(headerJson + lineBreak);

        // Zweiter Part: Dateiinhalt
        dos.writeBytes(boundaryPrefix + lineBreak);
        dos.writeBytes("Content-Type: " + mimeType + lineBreak);
        dos.writeBytes("Content-Disposition: attachment; filename=\"" + file.getName() + "\"" + lineBreak);
        dos.writeBytes(lineBreak);
        // Dateiinhalt schreiben
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, bytesRead);
        }
        fis.close();
        dos.writeBytes(lineBreak);

        // Abschließende Boundary
        dos.writeBytes(boundaryPrefix + "--" + lineBreak);
        dos.flush();
        byte[] multipartBody = baos.toByteArray();
        dos.close();

        // Setze die Content-Length des Requests
        conn.setFixedLengthStreamingMode(multipartBody.length);
        OutputStream os = conn.getOutputStream();
        os.write(multipartBody);
        os.flush();
        os.close();

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
}

/**
 * Hilfsklasse zur Verwaltung von Nachrichten im Chatverlauf.
 */
class Messages {
    private final String message;
    private final String author;

    /**
     * Konstruktor. Der übergebene Text wird vor der Speicherung bereinigt.
     *
     * @param message Die Nachricht.
     * @param author  Der Autor der Nachricht.
     */
    public Messages(String message, String author) {
        // Entferne Zeilenumbrüche und Tabs; spezielle Zeichen werden nicht manuell escaped,
        // da der JSON-Parser dies übernimmt.
        this.message = message.replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replace("_", "\\_");
        this.author = author;
    }

    /**
     * Gibt die Nachricht zurück.
     *
     * @return Die Nachricht.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gibt den Autor der Nachricht zurück.
     *
     * @return Der Autor.
     */
    public String getAuthor() {
        return author;
    }
}
