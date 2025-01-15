package it.gioppy;

import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.pengrad.telegrambot.TelegramBot;
import it.gioppy.rss.RssParser;
import it.gioppy.storage.SqliteManager;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActionManager {
    private final ExecutorService CLEAR_THREAD = Executors.newSingleThreadExecutor();
    private final Map<String, Set<Long>> lastNews = new ConcurrentHashMap<>();
    private final SqliteManager db = NewsSniper.getDb();
    private final TelegramBot bot = NewsSniper.getBot();

    public ActionManager() {}

    public CompletableFuture<Void> clearMessages(long chatId, int size) {
        return CompletableFuture.runAsync(() -> {
            int start = 0;
            try {
                String query = "SELECT last_clear_id FROM users WHERE chat_id = ?";
                ResultSet rs = db.executeQuery(query, chatId);

                start = rs.getInt("last_clear_id");
                rs.close();

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
        final HashSet<Long> chatIds = NewsSniper.getChatIds();
        if (!chatIds.contains(chatId)) {
            try {
                db.executeUpdate("INSERT INTO users(chat_id) VALUES (?)", chatId);
            } catch (SQLException e) {
                error("Impossibile inserire l'id: " + e);
            } finally {
                chatIds.add(chatId);
                bot.execute(new SendMessage(chatId, "Benvenuto!"));
                success("Aggiunto un nuovo utente! " + chatId);
            }
        }
    }

    public HashSet<Long> getAllIds() {
        final HashSet<Long> ids = new HashSet<>();
        try (ResultSet s = db.executeQuery("SELECT chat_id FROM users;")){
            if (s == null) {
                error("Errore nell'esecuzione della query");
                return ids;
            }

            while (s.next()) {
                ids.add(s.getLong(1));
            }

            s.close();
            return ids;
        } catch (SQLException e) {
            System.out.println("Errore: " + e.getMessage());
            return ids;
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
            final MessageDigest md = MessageDigest.getInstance("SHA-256");

            RssParser rp = new RssParser()
                    .setUrl(url)
                    .build();

            if (rp.getNews() == null)
                return;
            
            String descrizione = rp.getNews().getDescription();
            descrizione = descrizione.isEmpty() ? "Nessuna descrizione." : descrizione;
            String msg = String.format("""
                            📣 <a href='%s'><b>%s</b></a> 📣\s
                            
                            <b>Descrizione:</b> <em>%s</em>\s
                            
                            <b><em>%s</em></b>""",
                    rp.getNews().getUrl(), rp.getNews().getTitle(),
                    descrizione, rp.getNews().getDate());

            byte[] bytes = md.digest(msg.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            String hash = sb.toString();

            NewsSniper.getChatIds().parallelStream().forEach(id -> {
                try {
                    Set<Long> ids = lastNews.computeIfAbsent(hash, (err) -> new HashSet<>());
                    if (ids.add(id)) {
                        bot.execute(new SendMessage(id, msg)
                                .parseMode(ParseMode.HTML));
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
