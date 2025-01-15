package it.gioppy;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.TelegramBot;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;

import it.gioppy.storage.SqliteManager;
import lombok.Getter;

public class NewsSniper {
    @Getter
    private static final ScheduledExecutorService sex = new ScheduledThreadPoolExecutor(2);
    @Getter
    private static final TelegramBot bot = new TelegramBot(Secret.token);
    @Getter
    private static final SqliteManager db = new SqliteManager();
    @Getter
    private static HashSet<Long> chatIds = new HashSet<>();

    private static final List<String> urls = List.of(
            "https://feeds.bbci.co.uk/news/world/rss.xml",
            "http://rss.cnn.com/rss/edition_world.rss",
            "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
            "https://feeds.nbcnews.com/nbcnews/public/world",
            "https://www.rainews.it/rss/ultimora",
            "https://www.rainews.it/rss/esteri"
    );

    public static void main(String[] args) {
        final ActionManager am = new ActionManager();

        try {
            db.connect();
            am.success("Connected to database");
            db.createUserTable();
            chatIds = am.getAllIds();
            am.success("Utenti registrati: " + chatIds);
        } catch (SQLException e) {
            am.error("Impossibile connettersi al db: " + e);
        }

        sex.scheduleAtFixedRate(() -> {
            urls.parallelStream().forEach(am::sendNews);
        }, 0, 5, TimeUnit.MINUTES);

        bot.setUpdatesListener((updates) -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    final String messageText = update.message().text();
                    final long chatId = update.message().chat().id();
                    final int size = update.message().messageId();

                    if (!chatIds.contains(chatId) && !messageText.equalsIgnoreCase("/start")) {
                        return UpdatesListener.CONFIRMED_UPDATES_ALL;
                    }

                    switch (messageText.toLowerCase()) {
                        case "/start":
                            am.addChatId(chatId);
                            break;
                        case "/clear":
                            am.clearMessages(chatId, size);
                            break;
                        case "/stpcls":
                            am.stopBot(chatId, size);
                            break;
                        default:
                            am.sendCmd(chatId);
                            break;
                    }
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, e -> {
            if (e.response() != null) {
                e.response().errorCode();
                e.response().description();
            } else {
                am.error("Errore durante la lettura della update: " + e.getMessage());
            }
        });
    }

}
