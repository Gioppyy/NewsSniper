package it.gioppy;

import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import com.pengrad.telegrambot.TelegramBot;
import it.gioppy.rss.RssParser;
import it.gioppy.storage.SqliteManager;

import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActionManager {
    private final ExecutorService CLEAR_THREAD = Executors.newSingleThreadExecutor();
    private final HashSet<String> lastNews = new HashSet<>();
    private final SqliteManager db = NewsSniper.getDb();
    private final TelegramBot bot = NewsSniper.getBot();

    public ActionManager() {}

    public CompletableFuture<Void> clearMessages(long chatId, int size) {
        return CompletableFuture.runAsync(() -> {
            int start = 0;
            try {
                String query = "SELECT last_clear_id FROM users WHERE chat_id = ?";
                ResultSet rs = db.executeQuery(query, chatId);

                if (rs!= null && rs.next())
                    start = rs.getInt("last_clear_id");

                for (int i = start; i <= size; i++) {
                    try {
                        bot.execute(new DeleteMessage(chatId, i));
                    } catch (Exception e) {  /* ignore */ }
                }
            } catch (SQLException e) { /* ignore */ }
        }, CLEAR_THREAD).exceptionally(e -> {
            System.err.println("Errore nel task: " + e.getMessage());
            return null;
        });
    }

    public void StopBot(long chatId) {
        try {
            db.executeUpdate("DELETE FROM users WHERE chat_id = ?", chatId);
            NewsSniper.getChatIds().remove(chatId);
        } catch (Exception e) {
            System.out.println("[ERROR] Impossibile cancellare l'utente: " + e);
        }
    }

    public void sendNews(String url) {
        try {
            RssParser rp = new RssParser()
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
                    rp.getNews().getTitle(), rp.getNews().getDescription(),
                    rp.getNews().getUrl(), rp.getNews().getDate());

            NewsSniper.getChatIds().parallelStream().forEach(id -> {
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
