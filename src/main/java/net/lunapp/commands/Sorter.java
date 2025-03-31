package net.lunapp.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.lunapp.Command;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Command
public class Sorter extends ListenerAdapter {

    private int amount = 15;
    private final List<Integer> elements = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean cancelRequested = false;
    private SlashCommandInteractionEvent event;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        event.deferReply().addActionRow(Button.danger("cancel_sort", "Cancel")).queue();
        String algorithm = event.getOption("algorithm", OptionMapping::getAsString);
        this.event = event;
        if (elements.isEmpty()) {
            fillArrayList();
        }
        if (algorithm == null) {
            visualize();
        } else {
            startSorting( algorithm);
        }
    }

    private void fillArrayList() {
        synchronized (elements) {
            elements.clear();
            for (int i = 1; i <= amount; i++) {
                elements.add(i);
            }
        }
    }

    private void visualize() {
        StringBuilder visualization = new StringBuilder();
        synchronized (elements) {
            for (int j = amount; j >= 1; j--) {
                for (int i = 0; i < elements.size(); i++) {
                    if (elements.get(i) >= j) {
                        visualization.append("█");
                    } else {
                        visualization.append("░");
                    }
                }
                visualization.append("\n");
            }
        }
        event.getHook().editOriginal(visualization.toString()).queue();
    }

    private void startSorting(String algorithm) {
        new Thread(() -> {
            cancelRequested = false;
            switch (algorithm.toLowerCase()) {
                case "shuffle":
                    shuffle();
                    break;
                case "bubblesort":
                    bubbleSort();
                    break;
                default:
                    event.getHook().editOriginal("Unknown algorithm: " + algorithm).queue();
                    break;
            }
        }).start();
    }

    private void shuffle() {
        synchronized (elements) {
            for (int i = elements.size() - 1; i > 0; i--) {
                if (cancelRequested) {
                    event.getHook().editOriginal("Sorting canceled.").queue();
                    return;
                }
                int j = (int) (Math.random() * (i + 1));
                int temp = elements.get(i);
                elements.set(i, elements.get(j));
                elements.set(j, temp);
                if (i % 10 == 0) { // Update visualization every 10 iterations
                    visualize();
                }
            }
            visualize(); // Final update
        }
    }

    private void bubbleSort() {
        synchronized (elements) {
            int n = elements.size();
            for (int i = 0; i < n - 1; i++) {
                for (int j = 0; j < n - i - 1; j++) {
                    if (cancelRequested) {
                        event.getHook().editOriginal("Sorting canceled.").queue();
                        return;
                    }
                    if (elements.get(j) > elements.get(j + 1)) {
                        int temp = elements.get(j);
                        elements.set(j, elements.get(j + 1));
                        elements.set(j + 1, temp);
                    }
                    if (j % 10 == 0) { // Update visualization every 10 iterations
                        visualize();
                    }
                }
            }
            visualize(); // Final update
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getComponentId().equals("cancel_sort")) {
            cancelRequested = true;
            event.deferEdit().queue();
            event.getHook().editOriginal("Sorting canceled.").queue();
        }
    }
}