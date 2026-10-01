package cl.duoc.dsy2207.eventos.usuariosroles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexion a la misma base Oracle que usan function-usuarios y function-roles.
 * Credenciales desde App Settings (ORACLE_DB_URL / ORACLE_DB_USER / ORACLE_DB_PASSWORD),
 * nunca en el codigo fuente.
 */
public final class DbConnection {

    private DbConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("ORACLE_DB_URL");
        String user = System.getenv("ORACLE_DB_USER");
        String password = System.getenv("ORACLE_DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new IllegalStateException(
                "Faltan variables de entorno de conexion a Oracle: ORACLE_DB_URL, ORACLE_DB_USER, ORACLE_DB_PASSWORD");
        }

        // Acepta tanto una URL JDBC completa como un "connect string" de Oracle
        // (descriptor TNS o alias), igual que function-usuarios y function-roles.
        if (!url.startsWith("jdbc:")) {
            url = "jdbc:oracle:thin:@" + url;
        }

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontro el driver JDBC de Oracle", e);
        }

        return DriverManager.getConnection(url, user, password);
    }
}
