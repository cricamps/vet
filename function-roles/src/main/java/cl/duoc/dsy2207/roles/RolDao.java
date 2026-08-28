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

    private Rol map(ResultSet rs) throws SQLException {
        return new Rol(rs.getLong("ID_ROL"), rs.getString("NOMBRE_ROL"));
    }
}
