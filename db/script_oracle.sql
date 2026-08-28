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
INSERT INTO USUARIOS (NOMBRE_USUARIO, PROFESION_USUARIO, PAIS, ID_ROL) VALUES ('Ignacio Pastenet', 'Analista Programador', 'Chile', 2);

COMMIT;

--------------------------------------------------------------------------------
-- 7. CONSULTAS DE VERIFICACION
--------------------------------------------------------------------------------
-- SELECT * FROM ROLES;
-- SELECT * FROM USUARIOS;
-- SELECT u.NOMBRE_USUARIO, u.PROFESION_USUARIO, u.PAIS, r.NOMBRE_ROL
-- FROM USUARIOS u
-- JOIN ROLES r ON u.ID_ROL = r.ID_ROL;
