package net.lunapp.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.lunapp.Command;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Command
public class Watchlist extends ListenerAdapter {

    private static final String WATCHLIST_FILE = "watchlist.json";
    private ArrayList<String> media = new ArrayList<>();
    private ArrayList<String> source = new ArrayList<>();

    public Watchlist() {
        loadWatchlist();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        if (command.equalsIgnoreCase("watchlist")) {
            String addMedia = event.getOption("add", OptionMapping::getAsString);
            String removeMedia = event.getOption("remove", OptionMapping::getAsString);
            String editMedia = event.getOption("edit", OptionMapping::getAsString);
            String newName = event.getOption("newname", OptionMapping::getAsString);
            String newSource = event.getOption("newsource", OptionMapping::getAsString);

            if (addMedia != null) {
                String mediaSource = event.getOption("source", OptionMapping::getAsString);
                if (mediaSource == null || mediaSource.isEmpty()) {
                    mediaSource = "none";
                }
                addMedia(addMedia, mediaSource);
                event.reply("Added \"" + addMedia + "\" to your watchlist with source \"" + mediaSource + "\".").queue();
            } else if (removeMedia != null) {
                removeMedia(removeMedia, event);
            } else if (editMedia != null) {
                editMedia(editMedia, newName, newSource, event);
            } else {
                printWatchlist(event);
            }
        }
    }

    public void addMedia(String newMedia, String mediaSource) {
        media.add(newMedia);
        source.add(mediaSource);
        saveWatchlist();
    }

    public void removeMedia(String mediaToRemove, SlashCommandInteractionEvent event) {
        int index = media.indexOf(mediaToRemove);
        if (index != -1) {
            media.remove(index);
            source.remove(index);
            saveWatchlist();
            event.reply("Removed \"" + mediaToRemove + "\" from your watchlist.").queue();
        } else {
            event.reply("Error: \"" + mediaToRemove + "\" was not found in your watchlist.").queue();
        }
    }

    public void editMedia(String mediaToEdit, String newName, String newSource, SlashCommandInteractionEvent event) {
        int index = media.indexOf(mediaToEdit);
        if (index != -1) {
            if (newName != null && !newName.isEmpty()) {
                media.set(index, newName);
            }
            if (newSource != null && !newSource.isEmpty()) {
                source.set(index, newSource);
            }
            saveWatchlist();
            event.reply("Edited \"" + mediaToEdit + "\" in your watchlist.").queue();
        } else {
            event.reply("Error: \"" + mediaToEdit + "\" was not found in your watchlist.").queue();
        }
    }

    public void printWatchlist(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Watchlist");
        if (media.isEmpty()) {
            embed.addField("Your watchlist is empty!", "", true);
        } else {
            for (int i = 0; i < media.size(); i++) {
                embed.addField(media.get(i), source.get(i), true);
            }
        }
        embed.setColor(new Color(68, 178, 227));

        event.replyEmbeds(embed.build()).queue();
    }

    private void saveWatchlist() {
        Map<String, ArrayList<String>> watchlist = new HashMap<>();
        watchlist.put("media", media);
        watchlist.put("source", source);

        try (FileWriter writer = new FileWriter(WATCHLIST_FILE)) {
            new Gson().toJson(watchlist, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadWatchlist() {
        try (FileReader reader = new FileReader(WATCHLIST_FILE)) {
            Type type = new TypeToken<Map<String, ArrayList<String>>>() {}.getType();
            Map<String, ArrayList<String>> watchlist = new Gson().fromJson(reader, type);
            if (watchlist != null) {
                media = watchlist.get("media");
                source = watchlist.get("source");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}