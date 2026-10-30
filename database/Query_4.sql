ALTER TABLE usuario ADD COLUMN IF NOT EXISTS nombre_usuario VARCHAR(40) UNIQUE;

SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'usuario' ORDER BY ordinal_position;