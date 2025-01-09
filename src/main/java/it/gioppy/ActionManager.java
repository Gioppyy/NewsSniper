package it.gioppy;

import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.pengrad.telegrambot.TelegramBot;
import it.gioppy.storage.ChatStorage;
import it.gioppy.rss.RssParser;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActionManager {
    private final ConcurrentHashMap<Long, Integer> lastMessagesSize = new ConcurrentHashMap<>();
    private final HashSet<Long> chatIds = NewsSniper.getChatIds();
    private final HashSet<String> lastNews = new HashSet<>();
    private final TelegramBot bot = NewsSniper.getBot();

    private final ExecutorService CLEAR_THREAD = Executors.newWorkStealingPool(
            Math.min(Runtime.getRuntime().availableProcessors(), chatIds.size())
    );

    public ActionManager() {
        chatIds.forEach(chatId -> lastMessagesSize.putIfAbsent(chatId, 0));
    }

    public CompletableFuture<Void> clearMessages(long chatId, int size) {
        return CompletableFuture.runAsync(() -> {
            int start = lastMessagesSize.getOrDefault(chatId, 1);
            System.out.printf("Start %s | id: %s", start, chatId);

            for (int i = start; i <= size; i++) {
                try {
                    bot.execute(new DeleteMessage(chatId, i));
                } catch (Exception e) {
                    System.err.println("Errore nell'eliminazione del messaggio " + i + ": " + e.getMessage());
                }
            }

            lastMessagesSize.put(chatId, size);
        }, CLEAR_THREAD).exceptionally(e -> {
            System.err.println("Errore nel task: " + e.getMessage());
            return null;
        });
    }

    public void StopBot(long chatId) {
        if (chatIds.contains(chatId)) {
            chatIds.remove(chatId);
            ChatStorage.saveChatIds(chatIds);
        }
    }

    public void sendNews(String url) {
        try {
            RssParser pr = new RssParser()
                    .setUrl(url)
                    .build();

            String msg = String.format("""
                            <b>Titolo:</b>\s
                            <em>%s</em>

                            <b>Descrizione:</b>\s
                            <em>%s</em>
                            
                            <b>Url:</b>\s
                            <a href='%s'><em>Link</em></a>
                            
                            <b>Data:</b>\s
                            <em>%s</em>""",
                    pr.getNews().getTitle(), pr.getNews().getDescription(),
                    pr.getNews().getUrl(), pr.getNews().getDate());

            chatIds.parallelStream().forEach(id -> {
                try {
                    if (!lastNews.contains(msg)) {
                        bot.execute(new SendMessage(id, msg)
                                .parseMode(ParseMode.HTML));
                        lastNews.add(msg);
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] Errore durante l'invio del messaggio: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("Errore durante la lettura delle news all'url: " + url);
        }
    }
}
