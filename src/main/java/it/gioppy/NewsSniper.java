package it.gioppy;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import com.pengrad.telegrambot.request.SendMessage;
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
            "https://feeds.bbci.co.uk/news/world/rss.xml"
    );

    public static void main(String[] args) {
        final ActionManager am = new ActionManager();

        try {
            db.connect();
            System.out.println("[SUCCE] Connected to database");
            db.createUserTable();
            System.out.println("[SUCCE] Created table");
            CompletableFuture.runAsync(() -> {
                chatIds = db.getAllIds();
            });
        } catch (SQLException e) {
            System.out.println("[ERROR] Impossibile connettersi al db: " + e);
        }

        sex.scheduleAtFixedRate(() -> {
            urls.parallelStream().forEach(am::sendNews);
        }, 0, 20, TimeUnit.SECONDS);

        bot.setUpdatesListener((updates) -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    String messageText = update.message().text();
                    long chatId = update.message().chat().id();

                    switch (messageText.toLowerCase()) {
                        case "/start":
                            bot.execute(new SendMessage(chatId, "Benvenuto!"));
                            if (!chatIds.contains(chatId)) {
                                try {
                                    db.executeUpdate("INSERT INTO users(chat_id) VALUES (?)", chatId);
                                } catch (SQLException e) {
                                    System.out.println("[ERROR] Impossibile inserire l'id: " + e);
                                }
                                chatIds.add(chatId);
                            }
                            break;
                        case "/clear":
                            final int size = update.message().messageId();
                            am.clearMessages(chatId, size).join();
                            try {
                                db.executeUpdate("UPDATE users SET last_clear_id = ? WHERE chat_id = ?", size, chatId);
                            } catch (SQLException e) {
                                System.out.println("[ERROR] Impossibile aggiornare l'id: " + e);
                            }
                            bot.execute(new SendMessage(chatId, "Tutta la chat è stata cancellata!"));
                            break;
                        case "/stop":
                            bot.execute(new SendMessage(chatId, "Bot fermato!"));
                            am.StopBot(chatId);
                            break;
                        default:
                            bot.execute(new SendMessage(chatId, "[DEBUG] Il bot funziona!"));
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
                System.out.println("Errore durante la lettura della update: " + e.getMessage());
            }
        });
    }

}
