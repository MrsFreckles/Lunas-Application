# Lunas-Application

Lunas-Application is a Discord bot that uses the Gemini AI to answer questions in Discord. The bot is built using Java and Maven.

## Features

- **Ping Command**: Responds with "Pong!" and the bot's gateway ping.
- **Ask Command**: Allows users to ask questions to the Gemini AI and receive responses.

## Installation

1. **Clone the repository**:
    ```sh
    git clone https://github.com/yourusername/Lunas-Application.git
    cd Lunas-Application
    ```

2. **Configure the bot**:
    - Create a `config.properties` file in the root directory with the following content:
        ```properties
        token=YOUR_DISCORD_BOT_TOKEN
        gemini=YOUR_GEMINI_API_URL
        ```

3. **Build the project**:
    ```sh
    mvn clean package
    ```

4. **Run the bot**:
    ```sh
    java -jar target/Lunas-Application-1.0-SNAPSHOT.jar
    ```

## Usage

- **Ping Command**: Type `/ping` in any Discord channel where the bot is present.
- **Ask Command**: Type `/ask prompt:<your question> role:<optional role> ephemeral:<true/false>` to ask a question to the Gemini AI.

## Dependencies

- [JDA](https://github.com/DV8FromTheWorld/JDA) - Java Discord API
- [Reflections](https://github.com/ronmamo/reflections) - Java runtime metadata analysis
- [Gson](https://github.com/google/gson) - JSON library for Java
- [JSON](https://github.com/stleary/JSON-java) - JSON in Java
