package boniec.code.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Tworzy polaczenia SQLite bez uzycia DriverManager.
 *
 * Sterownik NIE jest relokowany: biblioteka natywna sqlite-jdbc ma nazwy funkcji
 * zaszyte pod org.sqlite.*, wiec relokacja konczy sie UnsatisfiedLinkError.
 *
 * Bezposrednie new JDBC().connect(...) omija DriverManager, dzieki czemu
 * /plugman reload nie trafia w wiszaca referencje do starego classloadera.
 */
public final class SqliteConnector {

    private SqliteConnector() {}

    public static Connection connect(String absolutePath) throws SQLException {
        return new org.sqlite.JDBC().connect("jdbc:sqlite:" + absolutePath, new Properties());
    }
}
