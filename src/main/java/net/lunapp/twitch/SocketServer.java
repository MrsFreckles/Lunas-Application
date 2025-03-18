package net.lunapp.twitch;

import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class SocketServer {

    // Liste aller verbundenen Clients (synchronisiert)
    private List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

    private ServerSocket serverSocket;

    /**
     * Konstruktor: Startet den Server auf dem angegebenen Port.
     *
     * @param port Der Port, auf dem der WebSocket-Server lauschen soll.
     * @throws IOException wenn das Binden an den Port fehlschlägt.
     */
    public SocketServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server gestartet auf Port " + port);

        // Starte einen Thread, der ständig auf neue Verbindungen wartet.
        new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Neue Verbindung: " + clientSocket.getInetAddress());
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    clientHandler.start();  // startet den Client-Thread
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Sendet eine Nachricht an alle verbundenen Clients.
     *
     * @param message Die zu sendende Nachricht.
     */
    public void broadcast(JSONObject message) {
        broadcast(message, "serverTick");
    }

    public void broadcast(String message) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("message", message);
        broadcast(jsonObject, "serverTick");
    }

    public void broadcast(JSONObject message, String type) {
        synchronized (clients) {
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("type", type);
            jsonMessage.put("payload", message);
            for (ClientHandler client : clients) {
                client.sendMessage(jsonMessage.toString());
            }
        }
    }

    /**
     * Interne Klasse, die einen einzelnen Client behandelt.
     */
    private class ClientHandler extends Thread {
        private Socket socket;
        private InputStream in;
        private OutputStream out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();

                // ===== WebSocket Handshake =====
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                String line = reader.readLine();
                String webSocketKey = null;

                // Die erste Zeile enthält z. B. "GET / HTTP/1.1". Danach folgen die Header.
                while (line != null && !line.isEmpty()) {
                    // Header "Sec-WebSocket-Key" herausfiltern:
                    if (line.startsWith("Sec-WebSocket-Key:")) {
                        webSocketKey = line.substring("Sec-WebSocket-Key:".length()).trim();
                    }
                    line = reader.readLine();
                }

                if (webSocketKey == null) {
                    System.out.println("Kein WebSocket-Key gefunden. Verbindung wird geschlossen.");
                    socket.close();
                    return;
                }

                // Erzeuge den Accept-Key gemäß dem Protokoll (SHA-1 + Base64)
                String acceptKey = generateAcceptKey(webSocketKey);

                // Sende die Antwort für den Handshake
                String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
                out.write(response.getBytes("UTF-8"));
                out.flush();
                System.out.println("Handshake abgeschlossen mit " + socket.getInetAddress());

                // ===== Hier könnte man eingehende Nachrichten verarbeiten =====
                // Dieser einfache Handler liest hier nur aus dem InputStream, ohne die Frames zu verarbeiten.
                while (!socket.isClosed()) {
                    int b = in.read();
                    if (b == -1) {
                        break;
                    }
                    // Optional: Hier könnte man den WebSocket-Frame weiter parsen.
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignorieren
                }
                clients.remove(this);
                System.out.println("Verbindung geschlossen: " + socket.getInetAddress());
            }
        }

        /**
         * Sendet eine Textnachricht an diesen Client als WebSocket-Frame.
         *
         * @param message Die Nachricht als String.
         */
        public void sendMessage(String message) {
            try {
                byte[] messageBytes = message.getBytes("UTF-8");
                int length = messageBytes.length;
                ByteArrayOutputStream frame = new ByteArrayOutputStream();

                // Erster Byte: FIN = 1 und opcode 0x1 (Textframe)
                frame.write(0x81);

                // Bestimme die Länge des Payloads
                if (length <= 125) {
                    frame.write(length);
                } else if (length <= 65535) {
                    frame.write(126);
                    frame.write((length >> 8) & 0xFF);
                    frame.write(length & 0xFF);
                } else {
                    frame.write(127);
                    // Für Nachrichten > 65535 Bytes: 8-Byte Länge. Hier wird vorausgesetzt, dass die Nachricht nicht
                    // länger als 2^32 ist, deshalb werden die oberen 4 Bytes als 0 geschrieben.
                    frame.write(new byte[]{0, 0, 0, 0});
                    frame.write((length >> 24) & 0xFF);
                    frame.write((length >> 16) & 0xFF);
                    frame.write((length >> 8) & 0xFF);
                    frame.write(length & 0xFF);
                }

                // Payload-Daten schreiben
                frame.write(messageBytes);
                out.write(frame.toByteArray());
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Generiert den "Sec-WebSocket-Accept"-Headerwert aus dem übergebenen Key.
         *
         * @param key Der im Handshake empfangene WebSocket-Key.
         * @return Der berechnete Accept-Key.
         */
        private String generateAcceptKey(String key) {
            try {
                String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                String acceptSeed = key + magic;
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                byte[] hash = sha1.digest(acceptSeed.getBytes("UTF-8"));
                return Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException("Fehler beim Generieren des Accept-Key", e);
            }
        }

        // Beispiel: Main-Methode zum Starten des Servers
        public static void main(String[] args) {
            try {
                // Starte den WebSocket-Server auf Port 8080
                SocketServer server = new SocketServer(8080);

                // Beispiel-Broadcast: Alle 10 Sekunden wird eine Nachricht an alle Clients gesendet.
                new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(10000);
                            server.broadcast("Hallo an alle Clients!");
                            System.out.println("Broadcast gesendet.");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
