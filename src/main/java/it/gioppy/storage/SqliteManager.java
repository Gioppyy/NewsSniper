package it.gioppy.storage;

import java.sql.*;
import java.util.HashSet;

public class SqliteManager {

    private final String PROJECT_PATH = System.getProperty("user.dir");
    private final String DB_PATH = PROJECT_PATH + "/storage.db";
    private Connection connection;

    public SqliteManager() {}

    public void connect() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        }
    }

    public void createUserTable() throws SQLException {
        String query = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id BIGINT NOT NULL,
                    last_clear_id INT NOT NULL DEFAULT 0
                )
                """;
        executeUpdate(query);
    }

    public HashSet<Long> getAllIds() {
        final HashSet<Long> ids = new HashSet<>();
        try {
            ResultSet s = executeQuery("SELECT chat_id FROM users;");

            while (s != null && s.next()) {
                long chatId = s.getLong(1);
                System.out.println("Trovato chat_id: " + chatId);
                ids.add(chatId);
            }

            System.out.println("Totale ID trovati: " + ids.size());
            return ids;
        } catch (SQLException e) {
            System.out.println("Errore: " + e.getMessage());
            return ids;
        }
    }


    public ResultSet executeQuery(String query, Object... params) throws SQLException {
        try (PreparedStatement statement = prepareStatement(query, params)){
            return statement.executeQuery();
        } catch (Exception exp) {
            System.out.println("[ERROR] Errore nell'esecuzione della query: " + query);
            System.out.println("[ERROR] " + exp.getMessage());
            return null;
        }
    }

    public int executeUpdate(String query, Object... params) throws SQLException {
        try (PreparedStatement statement = prepareStatement(query, params)) {
            return statement.executeUpdate();
        } catch (Exception exp) {
            System.out.println("[ERROR] Errore nell'esecuzione della query: " + query);
            System.out.println("[ERROR] " + exp.getMessage());
            return 99999;
        }
    }

    private PreparedStatement prepareStatement(String query, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        for (int i = 0; i < params.length; i++)
            statement.setObject(i + 1, params[i]);
        return statement;
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

}
