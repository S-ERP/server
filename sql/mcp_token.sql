-- Base de datos: servisofts.serp
-- Tokens de acceso al MCP. Cada token pertenece a UNA empresa y actúa en nombre del usuario que lo creó.
-- El token en claro solo se muestra al crearlo; aquí se guarda su SHA-256.
-- Se usa character varying (sin límite) porque SPGConect.editObject no maneja el tipo text.

CREATE TABLE IF NOT EXISTS public.mcp_token (
    key character varying PRIMARY KEY,
    estado integer DEFAULT 1,
    fecha_on timestamp without time zone DEFAULT now(),
    key_usuario character varying,          -- creador; el MCP actúa con sus permisos
    key_empresa character varying NOT NULL,
    descripcion character varying,
    alcance character varying DEFAULT 'lectura', -- 'lectura' | 'escritura'
    token_hash character varying NOT NULL,  -- sha256 hex del token
    token_prefijo character varying,        -- primeros caracteres, para reconocerlo en la lista
    fecha_vencimiento timestamp without time zone,
    fecha_ultimo_uso timestamp without time zone,
    key_usuario_anulacion character varying,
    fecha_anulacion timestamp without time zone
);
CREATE UNIQUE INDEX IF NOT EXISTS mcp_token_hash_idx ON public.mcp_token (token_hash);
CREATE INDEX IF NOT EXISTS mcp_token_key_empresa_idx ON public.mcp_token (key_empresa);

-- 2026-10-31: dueño vs creador y token recuperable (cifrado con config mcp.token_secret).
ALTER TABLE public.mcp_token ADD COLUMN IF NOT EXISTS key_usuario_creador character varying;
ALTER TABLE public.mcp_token ADD COLUMN IF NOT EXISTS token_cifrado character varying;
UPDATE public.mcp_token SET key_usuario_creador = key_usuario WHERE key_usuario_creador IS NULL;
