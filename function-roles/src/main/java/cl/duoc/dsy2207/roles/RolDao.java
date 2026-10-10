package cl.duoc.dsy2207.roles;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos para la entidad Rol (tabla ROLES).
 * Cada metodo abre y cierra su propia conexion: la funcion es stateless,
 * tal como recomienda la guia de buenas practicas de la semana 3.
 */
public class RolDao {

    public List<Rol> listar() throws SQLException {
        String sql = "SELECT ID_ROL, NOMBRE_ROL FROM ROLES ORDER BY ID_ROL";
        List<Rol> roles = new ArrayList<>();
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roles.add(map(rs));
            }
        }
        return roles;
    }

    public Optional<Rol> buscarPorId(long id) throws SQLException {
        String sql = "SELECT ID_ROL, NOMBRE_ROL FROM ROLES WHERE ID_ROL = ?";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Agregar Rol. */
    public Rol agregar(Rol rol) throws SQLException {
        String sql = "INSERT INTO ROLES (NOMBRE_ROL) VALUES (?)";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_ROL"})) {
            ps.setString(1, rol.getNombreRol());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    rol.setIdRol(keys.getLong(1));
                }
            }
        }
        return rol;
    }

    /** Modificar Rol. */
    public boolean modificar(long id, Rol rol) throws SQLException {
        String sql = "UPDATE ROLES SET NOMBRE_ROL = ? WHERE ID_ROL = ?";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rol.getNombreRol());
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Eliminar Rol. */
    public boolean eliminar(long id) throws SQLException {
        String sql = "DELETE FROM ROLES WHERE ID_ROL = ?";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Eliminar Rol desasignando a sus usuarios (Semana 8).
     *
     * USUARIOS.ID_ROL tiene FK a ROLES, por lo que un DELETE directo fallaria si
     * el rol esta en uso. En una sola transaccion: (1) se guardan los ids de los
     * usuarios afectados, (2) se dejan con ID_ROL = NULL y (3) se borra el rol.
     * Los ids viajan en el evento RolEliminado; la funcion consumidora
     * confirma que quedaron sin rol y les notifica que el rol fue quitado.
     *
     * @return ids de los usuarios afectados, o null si el rol no existia.
     */
    public List<Long> eliminarDesasignando(long id) throws SQLException {
        try (Connection con = DbConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                List<Long> afectados = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ID_USUARIO FROM USUARIOS WHERE ID_ROL = ? ORDER BY ID_USUARIO")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            afectados.add(rs.getLong(1));
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE USUARIOS SET ID_ROL = NULL WHERE ID_ROL = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                int borrados;
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM ROLES WHERE ID_ROL = ?")) {
                    ps.setLong(1, id);
                    borrados = ps.executeUpdate();
                }
                if (borrados == 0) {
                    con.rollback();
                    return null;
                }
                con.commit();
                return afectados;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private Rol map(ResultSet rs) throws SQLException {
        return new Rol(rs.getLong("ID_ROL"), rs.getString("NOMBRE_ROL"));
    }
}
