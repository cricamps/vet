package cl.duoc.dsy2207.usuarios;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utilidad de conexion a la base de datos Oracle.
 * Las credenciales se leen desde variables de entorno / local.settings.json
 * para no dejar datos sensibles hardcodeados en el codigo fuente.
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

        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("No se encontro el driver JDBC de Oracle", e);
        }

        return DriverManager.getConnection(url, user, password);
    }
}
