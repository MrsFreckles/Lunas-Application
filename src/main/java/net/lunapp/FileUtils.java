package net.lunapp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;

public class FileUtils {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void writeToJsonFile(String fileName, Object data) {
        try (FileWriter writer = new FileWriter("config\\" + fileName)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public <T> T readFromJsonFile(String fileName, Class<T> type) {
        try (Reader reader = new FileReader("config\\" + fileName)) {
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean fileExists(String fileName) {
        File file = new File("config\\" + fileName);
        return file.exists();
    }

    public void deleteFile(String fileName) {
        File file = new File("config\\" + fileName);
        file.delete();
    }

}
