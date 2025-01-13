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
    private final HashSet<Long> chatIds = NewsSniper.getChatIds();

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
                    } catch (Exception e) { 
                        error("Impossibile cancellare il messaggio: " + e.getMessage());
                    }
                }

                db.executeUpdate("UPDATE users SET last_clear_id = ? WHERE chat_id = ?", size, chatId);
            } catch (SQLException e) {
                error("Impossibile eseguire la query: " + e.getMessage());
            } finally {
                bot.execute(new SendMessage(chatId, "Tutta la chat e' stata cancellata!"));
            }
        }, CLEAR_THREAD).exceptionally(e -> {
            error("Errore nel task: " + e.getMessage());
            return null;
        });
    }

    public void addChatId(long chatId) {
        if (!chatIds.contains(chatId)) {
            try {
                db.executeUpdate("INSERT INTO users(chat_id) VALUES (?)", chatId);
            } catch (SQLException e) {
                error("Impossibile inserire l'id: " + e);
            } finally {
                chatIds.add(chatId);
                bot.execute(new SendMessage(chatId, "Benvenuto!"));
            }
        }
    }

    public void stopBot(long chatId, int size) {
        try {
            clearMessages(chatId, size).join();
            db.executeUpdate("DELETE FROM users WHERE chat_id = ?", chatId);
            NewsSniper.getChatIds().remove(chatId);
            bot.execute(new SendMessage(chatId, "Il bot e' stato fermato e la chat verra' pulita a breve!"));
        } catch (Exception e) {
            error("Impossibile cancellare l'utente: " + e);
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
                    error("Errore durante l'invio del messaggio: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            error("Errore durante la lettura delle news all'url: " + url);
        }
    }

    public void sendCmd(long chatId){
        String commands = """
                <b>COMANDI DEL BOT:</b>
                <blockquote>- <em><b>/start</b></em>               
                    <em>Avvio del bot</em>\s
                - <em><b>/clear</b></em>
                    <em>Pulisce la chat del bot, ma rimane in esecuzione.</em>\s
                - <em><b>/stpcls</b></em>
                    <em>Ferma l'esecuzione del bot, e cancella la chat.</em>
                </blockquote>

                                         """;

        try {
            bot.execute(new SendMessage(chatId, commands).parseMode(ParseMode.HTML));
        } catch(Exception e) {
            error("Impossibile inviare i comandi alla chat: " + chatId);
        }
    }

    public void error(String str) { System.out.println("\\033[31m[ERROR]" + str + "\\033[0m"); }
    public void success(String str) { System.out.println("\\033[32m[SUCCE]" + str + "\\033[0m"); }
}
