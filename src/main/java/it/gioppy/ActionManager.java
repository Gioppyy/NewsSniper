package it.gioppy;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import it.gioppy.rss.News;
import it.gioppy.rss.RssParser;
import it.gioppy.storage.ChatStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ActionManager {
    private final HashSet<Long> chatIds = NewsSniper.getChatIds();
    private final TelegramBot bot = NewsSniper.getBot();

    private final HashMap<Long, Integer> sentMessageIds = new HashMap<>();
    private final HashSet<String> lastNews = new HashSet<>();

    public ActionManager() {}

    public void clearMessages(long chatId) {
        sentMessageIds.entrySet().parallelStream()
                .filter((msgId) -> msgId.getKey() == chatId)
                .map(Map.Entry::getValue)
                .forEach((msgId) -> bot.execute(new DeleteMessage(chatId, msgId)));
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

            News n = pr.getNews();
            String msg = String.format("Titolo: \n%s\n\nDescrizione: \n%s\n\nUrl: \n%s\n\nData: \n%s",
                    n.getTitle(), n.getDescription(), n.getUrl(), n.getDate());

            chatIds.parallelStream().forEach(id -> {
                try {
                    if (!lastNews.contains(msg)) {
                        bot.execute(new SendMessage(id, msg));
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
