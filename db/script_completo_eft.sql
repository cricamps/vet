--------------------------------------------------------------------------------
-- DSY2207 - Evaluacion Final Transversal (Semana 9)
-- Sistema de Gestion de Usuarios y Roles - script COMPLETO de la base Oracle
--
-- = script_oracle.sql (S3: ROLES, USUARIOS, secuencias, datos)
-- + script_eventos_s8.sql (S8: UR_AUDITORIA_EVENTOS, UR_EVENTOS_PROCESADOS,
--   UR_NOTIFICACIONES y rol por defecto CONSULTA)
--
-- USUARIOS.ID_ROL admite NULL: un usuario puede quedar sin rol cuando se
-- elimina el rol que tenia (requerimiento 2 de eventos).
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Script Base de Datos Oracle (Oracle OCI)
-- Sistema de Gestion de Usuarios y Roles
-- DSY2207 - Desarrollo Cloud Native II - Semana 3
--
-- Campos alineados al diagrama de arquitectura acordado con el equipo
-- (ARQ-USUARIOS-ROLES): tabla USUARIOS con Nombre usuario, Profesion del
-- usuario y Pais; tabla ROLES con Nombre del rol; relacion simple 1:N
-- (un usuario tiene un rol) mediante FK ID_ROL en USUARIOS.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1. LIMPIEZA (ejecutar solo si se necesita recrear el esquema)
--------------------------------------------------------------------------------
-- DROP TABLE USUARIOS CASCADE CONSTRAINT;
-- DROP TABLE ROLES CASCADE CONSTRAINT;
-- DROP SEQUENCE SEQ_ROLES;
-- DROP SEQUENCE SEQ_USUARIOS;

--------------------------------------------------------------------------------
-- 2. SECUENCIAS
--------------------------------------------------------------------------------
CREATE SEQUENCE SEQ_ROLES START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_USUARIOS START WITH 1 INCREMENT BY 1 NOCACHE;

--------------------------------------------------------------------------------
-- 3. TABLA ROLES
--------------------------------------------------------------------------------
CREATE TABLE ROLES (
    ID_ROL         NUMBER(10)      NOT NULL,
    NOMBRE_ROL     VARCHAR2(50)    NOT NULL,
    CONSTRAINT PK_ROLES PRIMARY KEY (ID_ROL),
    CONSTRAINT UQ_ROLES_NOMBRE UNIQUE (NOMBRE_ROL)
);

--------------------------------------------------------------------------------
-- 4. TABLA USUARIOS
--------------------------------------------------------------------------------
CREATE TABLE USUARIOS (
    ID_USUARIO       NUMBER(10)      NOT NULL,
    NOMBRE_USUARIO   VARCHAR2(100)   NOT NULL,
    PROFESION_USUARIO VARCHAR2(100),
    PAIS             VARCHAR2(60),
    ID_ROL           NUMBER(10),
    CONSTRAINT PK_USUARIOS PRIMARY KEY (ID_USUARIO),
    CONSTRAINT FK_USUARIOS_ROL FOREIGN KEY (ID_ROL) REFERENCES ROLES (ID_ROL)
);

--------------------------------------------------------------------------------
-- 5. TRIGGERS PARA AUTOINCREMENTAR LAS LLAVES PRIMARIAS
--------------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER TRG_ROLES_PK
BEFORE INSERT ON ROLES
FOR EACH ROW
WHEN (NEW.ID_ROL IS NULL)
BEGIN
    :NEW.ID_ROL := SEQ_ROLES.NEXTVAL;
END;
/

CREATE OR REPLACE TRIGGER TRG_USUARIOS_PK
BEFORE INSERT ON USUARIOS
FOR EACH ROW
WHEN (NEW.ID_USUARIO IS NULL)
BEGIN
    :NEW.ID_USUARIO := SEQ_USUARIOS.NEXTVAL;
END;
/

--------------------------------------------------------------------------------
-- 6. DATOS DE EJEMPLO
--------------------------------------------------------------------------------
INSERT INTO ROLES (NOMBRE_ROL) VALUES ('ADMINISTRADOR');
INSERT INTO ROLES (NOMBRE_ROL) VALUES ('OPERADOR');
INSERT INTO ROLES (NOMBRE_ROL) VALUES ('CONSULTA');

INSERT INTO USUARIOS (NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL) VALUES ('Cristobal Camps', 'Analista Programador', 'Chile', 1);
INSERT INTO USUARIOS (NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL) VALUES ('Cynthia Torres Leal', 'Analista Programador', 'Chile', 1);
INSERT INTO USUARIOS (NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL) VALUES ('Usuario Demo Operador', 'Analista Programador', 'Chile', 2);

COMMIT;

--------------------------------------------------------------------------------
-- 7. CONSULTAS DE VERIFICACION
--------------------------------------------------------------------------------
-- SELECT * FROM ROLES;
-- SELECT * FROM USUARIOS;
-- SELECT u.NOMBRE_USUARIO, u.PROFESION_USUARIO, u.PAIS, r.NOMBRE_ROL
-- FROM USUARIOS u
-- JOIN ROLES r ON u.ID_ROL = r.ID_ROL;


--------------------------------------------------------------------------------
-- DSY2207 - Semana 8 - Actividad Sumativa 3
-- "Aplicando tecnologias de eventos en arquitecturas cloud"
-- Sistema de Gestion de Usuarios y Roles + Azure Event Grid
--
-- Ejecutar en la base Oracle "usuariosroles" (OCI), esquema ADMIN, la misma
-- que usan function-usuarios y function-roles. Solo AGREGA tablas: no modifica
-- USUARIOS ni ROLES. Prefijo UR_ para no chocar con tablas de otras actividades.
--------------------------------------------------------------------------------

-- Limpieza (solo si se necesita recrear)
-- DROP TABLE UR_NOTIFICACIONES PURGE;
-- DROP TABLE UR_EVENTOS_PROCESADOS PURGE;
-- DROP TABLE UR_AUDITORIA_EVENTOS PURGE;

-- 1. Auditoria: TODOS los eventos del topic (suscripcion sub-auditoria, sin filtro)
CREATE TABLE UR_AUDITORIA_EVENTOS (
    ID_EVENTO       VARCHAR2(64)    NOT NULL,
    TIPO_EVENTO     VARCHAR2(100)   NOT NULL,
    SUBJECT         VARCHAR2(200),
    FECHA_EVENTO    TIMESTAMP,
    FECHA_REGISTRO  TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    PAYLOAD_JSON    CLOB,
    CONSTRAINT PK_UR_AUDITORIA PRIMARY KEY (ID_EVENTO)
);

-- 2. Idempotencia: un evento se procesa una sola vez por consumidor
CREATE TABLE UR_EVENTOS_PROCESADOS (
    ID_EVENTO       VARCHAR2(64)    NOT NULL,
    CONSUMIDOR      VARCHAR2(60)    NOT NULL,
    TIPO_EVENTO     VARCHAR2(100)   NOT NULL,
    SUBJECT         VARCHAR2(200),
    RESULTADO       VARCHAR2(20)    NOT NULL,
    DETALLE         VARCHAR2(500),
    FECHA_PROCESO   TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_UR_PROCESADOS PRIMARY KEY (ID_EVENTO, CONSUMIDOR),
    CONSTRAINT CK_UR_PROCESADOS_RES CHECK (RESULTADO IN ('EN_PROCESO', 'PROCESADO', 'SIN_ACCION', 'RECHAZADO'))
);

-- 3. Notificaciones generadas por las consumidoras (sin FK a USUARIOS: la
--    notificacion de BAJA se guarda aunque el usuario ya no exista)
CREATE TABLE UR_NOTIFICACIONES (
    ID_NOTIFICACION NUMBER(10)      GENERATED BY DEFAULT AS IDENTITY,
    ID_USUARIO      NUMBER(10)      NOT NULL,
    ID_EVENTO       VARCHAR2(64)    NOT NULL,
    TIPO            VARCHAR2(30)    NOT NULL,
    MENSAJE         VARCHAR2(500)   NOT NULL,
    FECHA           TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_UR_NOTIFICACIONES PRIMARY KEY (ID_NOTIFICACION),
    CONSTRAINT CK_UR_NOTIF_TIPO CHECK (TIPO IN ('BIENVENIDA', 'ROL_ASIGNADO', 'CAMBIO_ROL', 'BAJA',
                                                'REASIGNACION_ROL', 'ROL_RENOMBRADO', 'ROL_QUITADO'))
);
CREATE INDEX IX_UR_NOTIF_USUARIO ON UR_NOTIFICACIONES (ID_USUARIO);

-- 4. El rol por defecto (App Setting ROL_POR_DEFECTO) debe existir.
--    Si ya existe 'CONSULTA' este INSERT no hace nada.
INSERT INTO ROLES (NOMBRE_ROL)
SELECT 'CONSULTA' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ROLES WHERE UPPER(NOMBRE_ROL) = 'CONSULTA');

COMMIT;

-- Verificacion
SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'UR\_%' ESCAPE '\' ORDER BY TABLE_NAME;
SELECT ID_ROL, NOMBRE_ROL FROM ROLES ORDER BY ID_ROL;
