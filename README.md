# Lunapp Discord Bot

Lunapp is a Discord bot built using Java and JDA (Java Discord API). It provides various commands to interact with users and manage a watchlist.

## Features

- **Ping Command**: Responds with "Ping Pong!".
- **Ask Command**: Allows users to ask questions to Gemini.
- **Watchlist Command**: Manages a watchlist of shows.
  - Add a show to the watchlist.
  - Remove a show from the watchlist.
  - Edit a show in the watchlist.
  - Clear the entire watchlist (admin only).
- **Say Command**: Makes the bot say what you tell it to.

## Requirements

- Java 11 or higher
- Maven
- A Discord bot token

## Setup

1. Clone the repository:
   ```sh
   git clone https://github.com/yourusername/lunapp.git
   cd lunapp
   ```

2. Create a `config.properties` file in the root directory with your bot token:
   ```properties
   token=YOUR_DISCORD_BOT_TOKEN
   ```

3. Build the project using Maven:
   ```sh
   mvn clean package
   ```

4. Run the bot:
   ```sh
   java -jar target/lunapp-1.0-SNAPSHOT.jar
   ```

## Commands

### Ping Command
- **Description**: Responds with "Ping Pong!".
- **Usage**: `/ping`

### Ask Command
- **Description**: Allows users to ask questions to Gemini.
- **Usage**: `/ask prompt:<question> [role:<role>] [ephemeral:<true|false>]`

### Watchlist Command
- **Description**: Manages a watchlist of shows.
- **Usage**:
  - Add a show: `/watchlist add:<show> [source:<source>]`
  - Remove a show: `/watchlist remove:<show>`
  - Edit a show: `/watchlist edit:<show> [newname:<new name>] [newsource:<new source>]`
  - Clear the watchlist: `/watchlist clear:true` (admin only)

### Say Command
- **Description**: Makes the bot say what you tell it to.
- **Usage**: `/say content:<message>`

## Contributing

1. Fork the repository.
2. Create a new branch (`git checkout -b feature-branch`).
3. Make your changes.
4. Commit your changes (`git commit -am 'Add new feature'`).
5. Push to the branch (`git push origin feature-branch`).
6. Create a new Pull Request.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
