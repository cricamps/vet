package cl.duoc.dsy2207.usuarios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos para la entidad Usuario (tabla USUARIOS).
 * Cada metodo abre y cierra su propia conexion: la funcion es stateless,
 * tal como recomienda la guia de buenas practicas de la semana 3.
 */
public class UsuarioDao {

    public List<Usuario> listar() throws SQLException {
        String sql = "SELECT ID_USUARIO, NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL FROM USUARIOS ORDER BY ID_USUARIO";
        List<Usuario> usuarios = new ArrayList<>();
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                usuarios.add(map(rs));
            }
        }
        return usuarios;
    }

    public Optional<Usuario> buscarPorId(long id) throws SQLException {
        String sql = "SELECT ID_USUARIO, NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL FROM USUARIOS WHERE ID_USUARIO = ?";
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

    /** Agregar Usuario. */
    public Usuario agregar(Usuario usuario) throws SQLException {
        String sql = "INSERT INTO USUARIOS (NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL) VALUES (?, ?, ?, ?)";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID_USUARIO"})) {
            ps.setString(1, usuario.getNombreUsuario());
            ps.setString(2, usuario.getProfesionUsuario());
            ps.setString(3, usuario.getPais());
            if (usuario.getIdRol() != null) {
                ps.setLong(4, usuario.getIdRol());
            } else {
                ps.setNull(4, Types.NUMERIC);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    usuario.setIdUsuario(keys.getLong(1));
                }
            }
        }
        return usuario;
    }

    /** Modificar Usuario. */
    public boolean modificar(long id, Usuario usuario) throws SQLException {
        String sql = "UPDATE USUARIOS SET NOMBRE_USUARIO = ?, PROFESION_USUARIO = ?, PAIS = ?, ID_ROL = ? WHERE ID_USUARIO = ?";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario.getNombreUsuario());
            ps.setString(2, usuario.getProfesionUsuario());
            ps.setString(3, usuario.getPais());
            if (usuario.getIdRol() != null) {
                ps.setLong(4, usuario.getIdRol());
            } else {
                ps.setNull(4, Types.NUMERIC);
            }
            ps.setLong(5, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Eliminar Usuario. */
    public boolean eliminar(long id) throws SQLException {
        String sql = "DELETE FROM USUARIOS WHERE ID_USUARIO = ?";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Usuario map(ResultSet rs) throws SQLException {
        long idRol = rs.getLong("ID_ROL");
        return new Usuario(
            rs.getLong("ID_USUARIO"),
            rs.getString("NOMBRE_USUARIO"),
            rs.getString("PROFESION_USUARIO"),
            rs.getString("PAIS"),
            rs.wasNull() ? null : idRol
        );
    }
}
