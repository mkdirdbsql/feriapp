-- Quita "nombre comercial" y "descripción del comercio" de la tabla comerciante.
-- (La descripción del PUESTO, tabla puesto, se conserva.)
-- Ejecútalo y reinicia el backend con los archivos nuevos al mismo tiempo.
BEGIN;
ALTER TABLE comerciante DROP COLUMN IF EXISTS nombre_comercial;
ALTER TABLE comerciante DROP COLUMN IF EXISTS descripcion;
COMMIT;

SELECT column_name FROM information_schema.columns
WHERE table_name = 'comerciante' ORDER BY ordinal_position;
