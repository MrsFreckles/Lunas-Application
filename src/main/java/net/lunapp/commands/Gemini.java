package net.lunapp.commands;

import com.github.twitch4j.TwitchClient;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

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
                    String message = jsonObject.getString("message");
                    String author = jsonObject.getString("author");
                    String channelId = jsonObject.optString("channelId", "");
                    String timestamp = jsonObject.optString("timestamp", "");
                    userPrompts.add(new Messages(message, author, channelId, timestamp));
                }
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Laden des Kurzzeitspeichers: " + e.getMessage());
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
                jsonObject.put("channelId", message.getChannelId());
                jsonObject.put("timestamp", message.getTimestamp());
                jsonArray.put(jsonObject);
            }
            Files.writeString(Paths.get(SHORT_TERM_MEMORY_FILE),
                    jsonArray.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern des Kurzzeitspeichers: " + e.getMessage());
        }
    }

    private void processMemoryControl(String prompt) {
        // Angepasste Memory-Control-Anweisung:
        String memoryInstructions = "Überlege, ob der User eine Erinnerung speichern oder löschen möchte. "
                + "Wenn der User etwas speichern möchte, extrahiere genau die Zeichenfolge oder den Text, den der User zum Speichern vorgibt, "
                + "und gib diesen als JSON-Objekt mit dem Feld 'text' zurück, z. B.: {\"text\": \"<der Text>\"}. "
                + "Wenn der Nutzer nichts speichern oder löschen möchte, dann gib exakt \"noNewMemory\" zurück. "
                + "Wenn der User etwas löschen möchte, dann gib exakt \"delete:<ID>\" zurück. "
                + "Keinen zusätzlichen Text, keine weitere Antwort. Deine einzige Aufgabe ist es, das Speichern zu kontrollieren.";

        if (!prompt.isEmpty()) {
            JSONObject payload = buildPayload(prompt, memoryInstructions, null);
            String response = sendGeminiRequest(payload).trim();
            System.out.println("(Memory Control - log): " + response);

            // Falls die Antwort exakt "noNewMemory" lautet
            if (response.equals("noNewMemory")) {
                return;
            }
            // Falls ein Löschbefehl im Format "delete:<ID>" vorliegt
            else if (response.startsWith("delete:")) {
                String idOrText = response.substring(7).trim();
                updateLongTermMemory(idOrText, true);
            } else {
                // Versuch, den JSON-Teil aus der Antwort zu extrahieren
                int startIndex = response.indexOf("{");
                int endIndex = response.lastIndexOf("}");
                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    String jsonSubstring = response.substring(startIndex, endIndex + 1);
                    try {
                        JSONObject jsonResponse = new JSONObject(jsonSubstring);
                        // Prüfe, ob es sich um einen Löschbefehl handelt
                        if (jsonResponse.has("action") && jsonResponse.getString("action").equalsIgnoreCase("delete")) {
                            String idOrText = jsonResponse.optString("id", "").trim();
                            if (idOrText.isEmpty()) {
                                idOrText = jsonResponse.optString("text", "").trim();
                            }
                            updateLongTermMemory(idOrText, true);
                        } else {
                            // Andernfalls handelt es sich um einen Speicherbefehl.
                            // Wichtig: Es wird genau der Text gespeichert, den der Service im Feld "text" zurückliefert.
                            String text = jsonResponse.getString("text");
                            updateLongTermMemory(text, false);
                        }
                    } catch (JSONException e) {
                        System.err.println("Fehler beim Verarbeiten der JSON-Antwort: " + e.getMessage());
                    }
                } else {
                    System.err.println("Unexpected response format. Ignoring memory control.");
                }
            }
        }
    }

    private void updateLongTermMemory(String text, boolean remove) {
        Properties properties = loadConfigProperties();
        boolean memoryEnabled = Boolean.parseBoolean(properties.getProperty("longTermMemoryEnabled", "false"));
        if (!memoryEnabled) {
            // Falls die Speicherung von Erinnerungen deaktiviert ist, wird nichts unternommen.
            return;
        }

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
                // Wenn der Text ausschließlich aus Ziffern besteht, lösche anhand der numerischen ID.
                if (text.matches("\\d+")) {
                    int idToDelete = Integer.parseInt(text);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        if (jsonObject.getInt("id") == idToDelete) {
                            jsonArray.remove(i);
                            break;
                        }
                    }
                } else {
                    // Ansonsten lösche anhand eines exakten Textvergleichs.
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        if (jsonObject.getString("text").equals(text)) {
                            jsonArray.remove(i);
                            break;
                        }
                    }
                }
            } else {
                JSONObject jsonObject = new JSONObject();
                // Beim Speichern wird die ID fortlaufend vergeben
                jsonObject.put("id", jsonArray.length() + 1);
                jsonObject.put("text", text);
                jsonArray.put(jsonObject);
            }

            Files.writeString(longTermPath,
                    jsonArray.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Fehler beim Aktualisieren des Langzeitspeichers: " + e.getMessage());
        }
    }





    /**
     * Baut das JSON-Payload für die Anfrage an den Gemini-Service auf.
     * Falls systemInstructionText nicht null ist, wird dieser als systemPrompt eingebunden.
     *
     * @param promptText            Die Textnachricht des Benutzers.
     * @param systemInstructionText Der systemPrompt (optional).
     * @param channelId             Die Channel-ID zur Filterung des Gesprächsverlaufs (optional).
     * @return Das erstellte JSONObject.
     */
    private JSONObject buildPayload(String promptText, String systemInstructionText, String channelId) {
        JSONArray contents = new JSONArray();
        // Filtere den Kurzzeitspeicher nach Channel, falls channelId gesetzt ist
        for (Messages message : userPrompts) {
            if (channelId != null && !message.getChannelId().equals(channelId)) {
                continue;
            }
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", message.getAuthor());
            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();
            partObj.put("text", message.getMessage());
            parts.put(partObj);
            msgObj.put("parts", parts);
            contents.put(msgObj);
        }
        // Füge den aktuellen Benutzerprompt hinzu
        JSONObject currentMsg = new JSONObject();
        currentMsg.put("role", "user");
        JSONArray currentParts = new JSONArray();
        JSONObject currentPart = new JSONObject();
        currentPart.put("text", promptText);
        currentParts.put(currentPart);
        currentMsg.put("parts", currentParts);
        contents.put(currentMsg);

        JSONObject payload = new JSONObject();
        payload.put("contents", contents);

        // Falls ein systemPrompt angegeben wurde, wird er eingebunden.
        if (systemInstructionText != null) {
            JSONObject systemInstruction = new JSONObject();
            systemInstruction.put("role", "user");
            JSONArray sysParts = new JSONArray();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", systemInstructionText);
            sysParts.put(sysPart);
            systemInstruction.put("parts", sysParts);
            payload.put("systemInstruction", systemInstruction);
        }

        // Konfiguriere die Generationseinstellungen
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 1);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 10000);
        generationConfig.put("responseMimeType", "text/plain");
        payload.put("generationConfig", generationConfig);

        return payload;
    }

    /**
     * Sendet das übergebene JSON-Payload an den Gemini-Service und gibt den Antworttext zurück.
     *
     * @param payload Das zu sendende JSON-Objekt.
     * @return Die Antwort der KI als String.
     */
    private String sendGeminiRequest(JSONObject payload) {
        Properties properties = loadConfigProperties();
        String endpoint = properties.getProperty("gemini");
        try {
            String jsonInput = payload.toString();
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
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
                return jsonResponse
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");
            } else {
                String errorResponse = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                System.err.println("Fehlerhafte Antwort: " + errorResponse);
                return "Fehler: " + errorResponse;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Exception: " + e.getMessage();
        }
    }

    /**
     * Zentrale Methode zur Verarbeitung von "ask"-Befehlen, sowohl für Slash-Commands als auch normale Nachrichten.
     *
     * @param prompt    Die Benutzeranfrage.
     * @param role      Die gewünschte Rolle.
     * @param channelId Die Channel-ID.
     * @param timestamp Zeitstempel der Nachricht.
     * @param callback  Callback, um die Antwort zurückzugeben.
     */
    private void handleAsk(String prompt, String role, String channelId, String timestamp, Consumer<String> callback) {
        userPrompts.add(new Messages(prompt, "user", channelId, timestamp));
        if (userPrompts.size() > 20) {
            userPrompts.remove(0);
        }
        saveMemory();

        processMemoryControl(prompt);

        Properties properties = loadConfigProperties();
        String systemPrompt = properties.getProperty("systemPrompt", "");
        JSONObject payload = buildPayload(prompt, systemPrompt, channelId);

        new Thread(() -> {
            String responseText = sendGeminiRequest(payload);
            System.out.println("(Final AI Answer - log): " + responseText);
            userPrompts.add(new Messages(responseText, "model", channelId, Instant.now().toString()));
            saveMemory();
            callback.accept(responseText);
        }).start();
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
            event.deferReply().addActionRow(Button.danger("cancel_ask", "Cancel")).queue();
            String channelId;
            if (event.getChannelType().isGuild()) {
                channelId = event.getGuildChannel().getId();
            } else {
                channelId = event.getChannel().getId();
            }
            String timestamp = event.getTimeCreated().toString();
            handleAsk(prompt, role, channelId, timestamp, responseText -> {
                String[] parts = splitString(responseText, 2000);
                event.getHook().editOriginal(parts[0]).setComponents().queue();
                for (int i = 1; i < parts.length; i++) {
                    event.getHook().sendMessage(parts[i]).queue();
                }
            });
        } else if (command.equalsIgnoreCase("newchat")) {
            userPrompts.clear();
            saveMemory();
            event.reply("Chat log wurde zurückgesetzt.").setEphemeral(true).queue();
        } else if (command.equalsIgnoreCase("togglelistener")) {
            Main.toggleListener();
            event.reply("Listener wurde " + (Main.isListenerEnabled() ? "aktiviert" : "deaktiviert") + ".")
                    .setEphemeral(true)
                    .queue();
        }
    }

    /**
     * Verarbeitet den "ask"-Befehl, wenn er als normale Nachricht (MessageReceivedEvent) auftritt.
     *
     * @param prompt Die Benutzeranfrage.
     * @param role   Die gewünschte Rolle.
     * @param event  Das MessageReceivedEvent.
     */
    public void handleAskCommand(String prompt, String role, MessageReceivedEvent event) {
        event.getChannel().sendTyping().queue();
        String channelId = event.getChannel().getId();
        String timestamp = event.getMessage().getTimeCreated().toString();
        handleAsk(prompt, role, channelId, timestamp, responseText -> {
            String[] parts = splitString(responseText, 2000);
            event.getChannel().sendMessage(parts[0]).queue();
            for (int i = 1; i < parts.length; i++) {
                event.getChannel().sendMessage(parts[i]).queue();
            }
        });
    }

    /**
     * Neue Methode zur Verarbeitung von Twitch-Nachrichten.
     *
     * @param prompt   Die Twitch-Nachricht.
     * @param role     Die gewünschte Rolle.
     * @param callback Callback zur Rückgabe der Antwort.
     */
    public void handleTwitchMessage(String prompt, String role, Consumer<String> callback) {
        // Verwende die zentrale Methode handleAsk mit "twitch" als Channel-ID und aktuellem Zeitstempel.
        handleAsk(prompt, role + "Du bist gut gelaunt und freundlich zu allen.", "twitch", Instant.now().toString(), callback);
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
            event.getHook().editOriginal("Anfrage wurde abgebrochen.").setComponents().queue();
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
}

/**
 * Hilfsklasse zur Verwaltung von Nachrichten im Chatverlauf.
 */
class Messages {
    private final String message;
    private final String author;
    private final String channelId;
    private final String timestamp;

    /**
     * Konstruktor. Der übergebene Text wird vor der Speicherung bereinigt.
     *
     * @param message   Die Nachricht.
     * @param author    Der Autor.
     * @param channelId Die ID des Channels.
     * @param timestamp Der Zeitstempel.
     */
    public Messages(String message, String author, String channelId, String timestamp) {
        this.message = message.replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replace("_", "\\_");
        this.author = author;
        this.channelId = channelId;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
