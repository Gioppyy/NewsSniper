package it.gioppy.storage;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class ChatStorage {
    private static final String FILE_NAME = "src/main/resources/chat_ids.txt";

    public static HashSet<Long> loadChatIds() {
        HashSet<Long> chatIds = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;
            while ((line = reader.readLine()) != null) {
                chatIds.add(Long.parseLong(line));
            }
        } catch (IOException e) {
            System.err.println("Errore durante il caricamento degli ID delle chat: " + e.getMessage());
        }
        return chatIds;
    }

    public static void saveChatIds(Set<Long> chatIds) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
            for (Long chatId : chatIds) {
                writer.write(chatId.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Errore durante il salvataggio degli ID delle chat: " + e.getMessage());
        }
    }
}
