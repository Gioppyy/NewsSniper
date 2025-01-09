package it.gioppy;

import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.TelegramBot;
import it.gioppy.storage.ChatStorage;
import it.gioppy.rss.RssParser;
import java.util.HashSet;

public class ActionManager {
    private final HashSet<Long> chatIds = NewsSniper.getChatIds();
    private final HashSet<String> lastNews = new HashSet<>();
    private final TelegramBot bot = NewsSniper.getBot();

    public ActionManager() {}

    public void clearMessages(long chatId) {
        // bot.execute(new DeleteMessage(chatId, msgId));
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
                                .parseMode(ParseMode.HTML)
                                .disableWebPagePreview(true));
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
