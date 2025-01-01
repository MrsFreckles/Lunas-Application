package net.lunapp.commands;

import net.lunapp.Command;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

@Command
public class Ping extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event){
        String command = event.getName();
        if(command.equalsIgnoreCase("ping")){
            event.reply("Pong! " + event.getJDA().getGatewayPing() + "ms").queue();
        }
    }
}
