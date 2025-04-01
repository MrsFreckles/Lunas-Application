# Lunas-Application 

Lunas-Application ist ein Discord-Bot, der die Gemini KI verwendet, um Fragen in Discord zu beantworten. Der Bot ist mit Java und Maven gebaut.

## Features
 
- **Ping Command**: Antwortet mit "Ping Pong!".
- **Ask Command**: Ermöglicht Benutzern, Fragen an Gemini zu stellen.
- **Watchlist Command**: Verwaltet eine Watchlist von Shows.
  - Füge eine Show zur Watchlist hinzu.
  - Entferne eine Show von der Watchlist.
  - Bearbeite eine Show in der Watchlist.
  - Leere die gesamte Watchlist (nur für Admins).
- **Say Command**: Lässt den Bot sagen, was du ihm sagst.

## Anforderungen

- Java 11 oder höher
- Maven
- Ein Discord-Bot-Token

## Setup

1. Klone das Repository:

   ```bash
   git clone https://github.com/yourusername/lunapp.git
   cd lunapp
   ```

2. Erstelle eine `config.properties`-Datei im Stammverzeichnis mit deinem Bot-Token:

   ```properties
   token=YOUR_DISCORD_BOT_TOKEN
   roleGemini=YOUR_ROLE_GEMINI
   twitchAccessToken=YOUR_TWITCH_ACCESS_TOKEN
   apiKey=YOUR_API_KEY
   unicodeFaces=YOUR_UNICODE_FACES
   gemini=YOUR_GEMINI
   ```

3. Baue das Projekt mit Maven:

   ```bash
   mvn clean install
   ```

4. Starte den Bot:

   ```bash
   java -jar target/lunapp-1.0-SNAPSHOT.jar
   ```

## Commands

### Ping Command

- **Beschreibung**: Antwortet mit "Ping Pong!".
- **Nutzung**: `/ping`

### Ask Command

- **Beschreibung**: Ermöglicht Benutzern, Fragen an Gemini zu stellen.
- **Nutzung**: `/ask prompt:<question> [role:<role>] [ephemeral:<true|false>]`

### Watchlist Command

- **Beschreibung**: Verwaltet eine Watchlist von Shows.
- **Nutzung**:
  - Füge eine Show hinzu: `/watchlist add:<show> [source:<source>]`
  - Entferne eine Show: `/watchlist remove:<show>`
  - Bearbeite eine Show: `/watchlist edit:<show> [newname:<new name>] [newsource:<new source>]`
  - Leere die Watchlist: `/watchlist clear:true` (nur für Admins)

### Say Command

- **Beschreibung**: Lässt den Bot sagen, was du ihm sagst.
- **Nutzung**: `/say content:<message>`

## Mitwirken

1. Forke das Repository.
2. Erstelle einen neuen Branch (`git checkout -b feature-branch`).
3. Mache deine Änderungen.
4. Committe deine Änderungen (`git commit -am 'Add new feature'`).
5. Push zum Branch (`git push origin feature-branch`).
6. Erstelle einen neuen Pull Request.

## Lizenz

Dieses Projekt ist unter der MIT-Lizenz lizenziert. Siehe die [LICENSE](https://github.com/MrsFreckles/Lunas-Application/blob/master/LICENSE) Datei für Details.

## Über

Lunas-Application ist ein Discord-Bot, der die Gemini KI verwendet, um Fragen in Discord zu beantworten. Der Bot ist mit Java und Maven gebaut.

### Ressourcen

- [Activity](https://github.com/MrsFreckles/Lunas-Application/activity)

### Sterne

- [**1** Stern](https://github.com/MrsFreckles/Lunas-Application/stargazers)

### Beobachter

- [**1** Beobachter](https://github.com/MrsFreckles/Lunas-Application/watchers)

### Forks

- [**0** Forks](https://github.com/MrsFreckles/Lunas-Application/forks)

## Vorgeschlagene Workflows

Basierend auf deinem Tech-Stack
```

### Hinweise:
- Stelle sicher, dass du die Platzhalter in der `config.properties`-Datei mit den tatsächlichen Werten ersetzt.
- Du kannst die Links zu den GitHub-Ressourcen nach Bedarf anpassen.
