package net.lunapp.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.lunapp.Command;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
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
        if (!event.getName().equals("sort")) {
            return; // Ignore other commands
        }

        event.deferReply().addActionRow(Button.danger("cancel_sort", "Cancel")).queue();
        String algorithm = event.getOption("algorithm", OptionMapping::getAsString);
        int amount = event.getOption("amount", OptionMapping::getAsInt);
        this.event = event;

        if ("help".equalsIgnoreCase(algorithm)) {
            sendHelpEmbed();
            return;
        }

        if (amount <= 32 && amount > 0) {
            this.amount = amount;
        } else if (amount > 32){
            event.reply("The maximum amount of elements is 32.").queue();
            return;
        } else {
            event.reply("The minimum amount of elements is 1.").queue();
            return;
        }

        if (elements.isEmpty()) {
            fillArrayList();
        }
        if (algorithm == null) {
            visualize();
        } else {
            startSorting(algorithm);
        }
    }

    private void sendHelpEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Available Sorting Algorithms");
        embed.setDescription("Here is a list of all available sorting algorithms:");
        embed.addField("Algorithms", "shuffle, bubblesort, insertionsort, selectionsort, quicksort, heapsort, countingsort, mergesort, radixsort, shellsort, cocktailsort, pancakesort", false);
        embed.setColor(new Color(0, 150, 136));
        event.getHook().editOriginalEmbeds(embed.build()).queue();
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
                case "insertionsort":
                    insertionSort();
                    break;
                case "selectionsort":
                    selectionSort();
                    break;
                case "quicksort":
                    quickSort(0, elements.size() - 1);
                    break;
                case "heapsort":
                    heapSort();
                    break;
                case "countingsort":
                    countingSort();
                    break;
                case "mergesort":
                    mergeSort(0, elements.size() - 1);
                    break;
                case "radixsort":
                    radixSort();
                    break;
                case "shellsort":
                    shellSort();
                    break;
                case "cocktailsort":
                    cocktailSort();
                    break;
                case "pancakesort":
                    pancakeSort();
                    break;
                default:
                    event.getHook().editOriginal("Unknown algorithm: " + algorithm + ". To get a list of available algorithm input \"help\".").queue();
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
                visualize();
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
                    visualize();
                }
            }
            visualize();
        }
    }

    private void insertionSort() {
        synchronized (elements) {
            int n = elements.size();
            for (int i = 1; i < n; ++i) {
                int key = elements.get(i);
                int j = i - 1;
                while (j >= 0 && elements.get(j) > key) {
                    elements.set(j + 1, elements.get(j));
                    j = j - 1;
                }
                elements.set(j + 1, key);
                visualize();
            }
            visualize();
        }
    }

    private void selectionSort() {
        synchronized (elements) {
            int n = elements.size();
            for (int i = 0; i < n - 1; i++) {
                int minIdx = i;
                for (int j = i + 1; j < n; j++) {
                    if (elements.get(j) < elements.get(minIdx)) {
                        minIdx = j;
                    }
                }
                int temp = elements.get(minIdx);
                elements.set(minIdx, elements.get(i));
                elements.set(i, temp);
                visualize();
            }
            visualize();
        }
    }

    private void quickSort(int low, int high) {
        if (low < high) {
            int pi = partition(low, high);
            quickSort(low, pi - 1);
            quickSort(pi + 1, high);
        }
    }

    private int partition(int low, int high) {
        int pivot = elements.get(high);
        int i = (low - 1);
        for (int j = low; j < high; j++) {
            if (cancelRequested) {
                event.getHook().editOriginal("Sorting canceled.").queue();
                return i;
            }
            if (elements.get(j) < pivot) {
                i++;
                int temp = elements.get(i);
                elements.set(i, elements.get(j));
                elements.set(j, temp);
                visualize();
            }
        }
        int temp = elements.get(i + 1);
        elements.set(i + 1, elements.get(high));
        elements.set(high, temp);
        visualize();
        return i + 1;
    }

    private void heapSort() {
        synchronized (elements) {
            int n = elements.size();
            for (int i = n / 2 - 1; i >= 0; i--) {
                heapify(n, i);
            }
            for (int i = n - 1; i > 0; i--) {
                if (cancelRequested) {
                    event.getHook().editOriginal("Sorting canceled.").queue();
                    return;
                }
                int temp = elements.get(0);
                elements.set(0, elements.get(i));
                elements.set(i, temp);
                heapify(i, 0);
                visualize();
            }
            visualize();
        }
    }

    private void heapify(int n, int i) {
        int largest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;
        if (left < n && elements.get(left) > elements.get(largest)) {
            largest = left;
        }
        if (right < n && elements.get(right) > elements.get(largest)) {
            largest = right;
        }
        if (largest != i) {
            int swap = elements.get(i);
            elements.set(i, elements.get(largest));
            elements.set(largest, swap);
            heapify(n, largest);
        }
    }

    private void countingSort() {
        synchronized (elements) {
            int max = Collections.max(elements);
            int min = Collections.min(elements);
            int range = max - min + 1;
            int count[] = new int[range];
            int output[] = new int[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                count[elements.get(i) - min]++;
            }
            for (int i = 1; i < count.length; i++) {
                count[i] += count[i - 1];
            }
            for (int i = elements.size() - 1; i >= 0; i--) {
                output[count[elements.get(i) - min] - 1] = elements.get(i);
                count[elements.get(i) - min]--;
            }
            for (int i = 0; i < elements.size(); i++) {
                elements.set(i, output[i]);
                visualize();
            }
            visualize();
        }
    }

    private void mergeSort(int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;
            mergeSort(left, mid);
            mergeSort(mid + 1, right);
            merge(left, mid, right);
        }
    }

    private void merge(int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;
        List<Integer> L = new ArrayList<>(n1);
        List<Integer> R = new ArrayList<>(n2);
        for (int i = 0; i < n1; ++i) {
            L.add(elements.get(left + i));
        }
        for (int j = 0; j < n2; ++j) {
            R.add(elements.get(mid + 1 + j));
        }
        int i = 0, j = 0;
        int k = left;
        while (i < n1 && j < n2) {
            if (cancelRequested) {
                event.getHook().editOriginal("Sorting canceled.").queue();
                return;
            }
            if (L.get(i) <= R.get(j)) {
                elements.set(k, L.get(i));
                i++;
            } else {
                elements.set(k, R.get(j));
                j++;
            }
            k++;
            visualize();
        }
        while (i < n1) {
            elements.set(k, L.get(i));
            i++;
            k++;
            visualize();
        }
        while (j < n2) {
            elements.set(k, R.get(j));
            j++;
            k++;
            visualize();
        }
    }

    private void radixSort() {
        synchronized (elements) {
            int max = Collections.max(elements);
            for (int exp = 1; max / exp > 0; exp *= 10) {
                countSortByDigit(exp);
            }
            visualize();
        }
    }

    private void countSortByDigit(int exp) {
        int n = elements.size();
        int output[] = new int[n];
        int count[] = new int[10];
        for (int i = 0; i < n; i++) {
            count[(elements.get(i) / exp) % 10]++;
        }
        for (int i = 1; i < 10; i++) {
            count[i] += count[i - 1];
        }
        for (int i = n - 1; i >= 0; i--) {
            output[count[(elements.get(i) / exp) % 10] - 1] = elements.get(i);
            count[(elements.get(i) / exp) % 10]--;
        }
        for (int i = 0; i < n; i++) {
            elements.set(i, output[i]);
            visualize();
        }
    }

    private void shellSort() {
        synchronized (elements) {
            int n = elements.size();
            for (int gap = n / 2; gap > 0; gap /= 2) {
                for (int i = gap; i < n; i++) {
                    int temp = elements.get(i);
                    int j;
                    for (j = i; j >= gap && elements.get(j - gap) > temp; j -= gap) {
                        elements.set(j, elements.get(j - gap));
                    }
                    elements.set(j, temp);
                    visualize();
                }
            }
            visualize();
        }
    }

    private void cocktailSort() {
        synchronized (elements) {
            boolean swapped = true;
            int start = 0;
            int end = elements.size() - 1;
            while (swapped) {
                swapped = false;
                for (int i = start; i < end; ++i) {
                    if (cancelRequested) {
                        event.getHook().editOriginal("Sorting canceled.").queue();
                        return;
                    }
                    if (elements.get(i) > elements.get(i + 1)) {
                        int temp = elements.get(i);
                        elements.set(i, elements.get(i + 1));
                        elements.set(i + 1, temp);
                        swapped = true;
                        visualize();
                    }
                }
                if (!swapped) {
                    break;
                }
                swapped = false;
                --end;
                for (int i = end - 1; i >= start; --i) {
                    if (elements.get(i) > elements.get(i + 1)) {
                        int temp = elements.get(i);
                        elements.set(i, elements.get(i + 1));
                        elements.set(i + 1, temp);
                        swapped = true;
                        visualize();
                    }
                }
                ++start;
            }
            visualize();
        }
    }

    private void pancakeSort() {
        synchronized (elements) {
            int n = elements.size();
            for (int curr_size = n; curr_size > 1; --curr_size) {
                int mi = findMax(curr_size);
                if (mi != curr_size - 1) {
                    flip(mi);
                    flip(curr_size - 1);
                }
                visualize();
            }
            visualize();
        }
    }

    private int findMax(int n) {
        int mi = 0;
        for (int i = 0; i < n; i++) {
            if (elements.get(i) > elements.get(mi)) {
                mi = i;
            }
        }
        return mi;
    }

    private void flip(int i) {
        int start = 0;
        while (start < i) {
            int temp = elements.get(start);
            elements.set(start, elements.get(i));
            elements.set(i, temp);
            start++;
            i--;
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