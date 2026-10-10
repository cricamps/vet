--------------------------------------------------------------------------------
-- DSY2207 - EFT (Semana 9) - Migracion sobre la base de la S8 (usuariosroles)
-- Requerimiento 2: al eliminar un rol se QUITA a sus usuarios (ID_ROL = NULL)
-- y la consumidora ProcesarEventoRol registra la notificacion ROL_QUITADO.
-- Se amplia el CHECK de UR_NOTIFICACIONES.TIPO para aceptar ese tipo.
--------------------------------------------------------------------------------
ALTER TABLE UR_NOTIFICACIONES DROP CONSTRAINT CK_UR_NOTIF_TIPO;
ALTER TABLE UR_NOTIFICACIONES ADD CONSTRAINT CK_UR_NOTIF_TIPO
    CHECK (TIPO IN ('BIENVENIDA', 'ROL_ASIGNADO', 'CAMBIO_ROL', 'BAJA',
                    'REASIGNACION_ROL', 'ROL_RENOMBRADO', 'ROL_QUITADO'));

-- Verificacion
SELECT CONSTRAINT_NAME, SEARCH_CONDITION_VC FROM USER_CONSTRAINTS
 WHERE TABLE_NAME = 'UR_NOTIFICACIONES' AND CONSTRAINT_TYPE = 'C' AND CONSTRAINT_NAME = 'CK_UR_NOTIF_TIPO';
