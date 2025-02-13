package net.lunapp.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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
    private static final String ADMIN_USER_ID = "464424249877331969";
    private ArrayList<String> media = new ArrayList<>();
    private ArrayList<String> source = new ArrayList<>();

    public Watchlist() {
        loadWatchlist();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String command = event.getName();
        String userId = event.getUser().getId();

        if (command.equalsIgnoreCase("watchlist")) {
            String addMedia = event.getOption("add", OptionMapping::getAsString);
            String removeMedia = event.getOption("remove", OptionMapping::getAsString);
            String editMedia = event.getOption("edit", OptionMapping::getAsString);
            String newName = event.getOption("newname", OptionMapping::getAsString);
            String newSource = event.getOption("newsource", OptionMapping::getAsString);
            Boolean clear = event.getOption("clear", OptionMapping::getAsBoolean);

            if (userId.equals(ADMIN_USER_ID)) {
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
                } else if (clear != null && clear) {
                    event.reply("Are you sure you want to clear the entire watchlist? This action cannot be undone.")
                            .addActionRow(
                                    Button.danger("clear_confirm", "Yes, clear it"),
                                    Button.secondary("clear_cancel", "No, cancel")
                            ).queue();
                } else {
                    printWatchlist(event, 0);
                }
            } else {
                if (addMedia != null || removeMedia != null || editMedia != null || (clear != null && clear)) {
                    event.reply("You do not have permission to modify the watchlist.").queue();
                } else {
                    printWatchlist(event, 0);
                }
            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] idParts = event.getComponentId().split(":");
        if (idParts[0].equals("watchlist")) {
            int page = Integer.parseInt(idParts[1]);
            printWatchlist(event, page);
        } else if (event.getComponentId().equals("clear_confirm")) {
            clearWatchlist(event);
        } else if (event.getComponentId().equals("clear_cancel")) {
            event.reply("Clearing the watchlist has been canceled.").setEphemeral(true).queue();
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

    public void clearWatchlist(ButtonInteractionEvent event) {
        media.clear();
        source.clear();
        saveWatchlist();
        event.reply("The watchlist has been cleared.").queue();
    }

    public void printWatchlist(SlashCommandInteractionEvent event, int page) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Watchlist");
        embed.setColor(new Color(133, 201, 0));

        int start = page * 10;
        int end = Math.min(start + 10, media.size());

        if (media.isEmpty()) {
            embed.addField("Your watchlist is empty!", "", false);
        } else {
            for (int i = start; i < end; i++) {
                embed.addField(media.get(i), source.get(i), false);
            }
        }

        int totalPages = (int) Math.ceil((double) media.size() / 10);
        if (totalPages > 1) {
            embed.setFooter("Page " + (page + 1) + " of " + totalPages);
            event.replyEmbeds(embed.build())
                    .addActionRow(
                            Button.primary("watchlist:" + (page - 1), "Previous").withDisabled(page == 0),
                            Button.primary("watchlist:" + (page + 1), "Next").withDisabled(page == totalPages - 1)
                    ).queue();
        } else {
            event.replyEmbeds(embed.build()).queue();
        }
    }

    public void printWatchlist(ButtonInteractionEvent event, int page) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Watchlist");
        embed.setColor(new Color(133, 201, 0));

        int start = page * 10;
        int end = Math.min(start + 10, media.size());

        if (media.isEmpty()) {
            embed.addField("Your watchlist is empty!", "", false);
        } else {
            for (int i = start; i < end; i++) {
                embed.addField(media.get(i), source.get(i), false);
            }
        }

        int totalPages = (int) Math.ceil((double) media.size() / 10);
        if (totalPages > 1) {
            embed.setFooter("Page " + (page + 1) + " of " + totalPages);
            event.editMessageEmbeds(embed.build())
                    .setActionRow(
                            Button.primary("watchlist:" + (page - 1), "Previous").withDisabled(page == 0),
                            Button.primary("watchlist:" + (page + 1), "Next").withDisabled(page == totalPages - 1)
                    ).queue();
        } else {
            event.editMessageEmbeds(embed.build()).queue();
        }
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