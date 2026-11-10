-- Sincronización de paquetes/suscripciones entre el sistema antiguo de Calistenia (base servisofts.calistenia)
-- y S-ERP. Base servisofts.serp. Solo empresas configuradas en sync_cal_config.
--
-- Reglas acordadas:
--  * Venta hecha en Calistenia -> en S-ERP se registra la venta completa (caja automática del punto de venta
--    configurado en la sucursal; contabilidad solo existe en S-ERP).
--  * Venta hecha en S-ERP -> en Calistenia se registra el acceso Y los movimientos de caja/banco en una caja
--    virtual "S-ERP" por sucursal y día (como la "Caja qr"), para que cuadren sus reportes.
--  * Nada se devuelve a su origen (anti-eco): lo que escribe el sync en Calistenia queda marcado en su outbox.
--  * Se activa sucursal por sucursal (sync_cal_sucursal.activo + fecha_desde).
--  * Fechas de acceso se copian tal cual (ambos sistemas toman fecha_fin inclusive).

-- Una fila por empresa de S-ERP enlazada con una base de Calistenia (la real o la copia de pruebas).
create table if not exists sync_cal_config (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_usuario varchar,
    key_empresa varchar not null,              -- empresa de S-ERP
    db_host varchar not null,                  -- Postgres de Calistenia
    db_port integer not null default 5432,
    db_name varchar not null,                  -- servisofts.calistenia | servisofts.calistenia_pruebas
    activo boolean not null default false,     -- interruptor general (la tarea no hace nada si está en false)
    key_usuario_serp varchar,                  -- usuario de S-ERP con el que se registran las ventas que vienen de Calistenia
    key_usuario_cal varchar,                   -- usuario de Calistenia dueño de las cajas virtuales "S-ERP"
    key_moneda varchar,                        -- moneda de S-ERP de las ventas
    ultimo_outbox bigint not null default 0,   -- último sync_outbox.id leído de Calistenia
    observacion varchar
);
create unique index if not exists sync_cal_config_empresa on sync_cal_config (key_empresa) where estado > 0;

-- Sucursales: mapeo + interruptor por sucursal.
create table if not exists sync_cal_sucursal (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_usuario varchar,
    key_empresa varchar not null,
    key_sucursal_cal varchar not null,         -- sucursal.key de Calistenia
    key_sucursal varchar not null,             -- sucursal de S-ERP
    key_punto_venta varchar,                   -- PV de S-ERP cuya caja automática recibe las ventas de Calistenia
    key_almacen varchar,                       -- almacén de S-ERP de esas ventas
    activo boolean not null default false,     -- sincroniza las ventas de esta sucursal
    fecha_desde timestamp,                     -- solo ventas desde esta fecha (lo anterior se carga aparte)
    molinete varchar not null default 'calistenia'  -- dueño del molinete de la sucursal: calistenia | serp
);
create unique index if not exists sync_cal_sucursal_cal on sync_cal_sucursal (key_empresa, key_sucursal_cal) where estado > 0;
create unique index if not exists sync_cal_sucursal_serp on sync_cal_sucursal (key_empresa, key_sucursal) where estado > 0;

-- Tipos de pago. Calistenia: "1" efectivo, "2" tarjeta, "3" transferencia, "4" cheque, "5" QR.
-- key_sucursal_cal vacío = vale para todas las sucursales; el efectivo de S-ERP es una cuenta por sucursal,
-- por eso se mapea por sucursal. De S-ERP a Calistenia se busca por key_empresa_tipo_pago.
create table if not exists sync_cal_tipo_pago (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_usuario varchar,
    key_empresa varchar not null,
    key_sucursal_cal varchar,
    key_tipo_pago_cal varchar not null,
    key_empresa_tipo_pago varchar not null,
    sentido varchar not null default 'ambos'   -- ambos | cal_a_serp | serp_a_cal
);
create unique index if not exists sync_cal_tipo_pago_cal on sync_cal_tipo_pago (key_empresa, coalesce(key_sucursal_cal, ''), key_tipo_pago_cal)
    where estado > 0 and sentido in ('ambos', 'cal_a_serp');
create unique index if not exists sync_cal_tipo_pago_serp on sync_cal_tipo_pago (key_empresa, key_empresa_tipo_pago)
    where estado > 0 and sentido in ('ambos', 'serp_a_cal');

-- Catálogo: paquete de Calistenia <-> modelo (servicio) de S-ERP. El sistema donde nació es el dueño de sus datos.
create table if not exists sync_cal_paquete (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_usuario varchar,
    key_empresa varchar not null,
    key_paquete_cal varchar not null,
    key_modelo varchar not null,
    origen varchar not null default 'manual',  -- calistenia | serp | manual (mapeo inicial)
    fecha_sync timestamp                       -- última vez que se copiaron los datos del dueño
);
create unique index if not exists sync_cal_paquete_cal on sync_cal_paquete (key_empresa, key_paquete_cal) where estado > 0;
create unique index if not exists sync_cal_paquete_serp on sync_cal_paquete (key_empresa, key_modelo) where estado > 0;

-- Clientes: usuario de Calistenia (servicio usuario) <-> cliente del CRM de S-ERP.
create table if not exists sync_cal_cliente (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_usuario varchar,
    key_empresa varchar not null,
    key_usuario_cal varchar not null,
    key_cliente varchar not null,
    metodo varchar not null,                   -- ci | telefono | creado_en_serp | creado_en_cal | manual
    ci varchar
);
create unique index if not exists sync_cal_cliente_cal on sync_cal_cliente (key_empresa, key_usuario_cal) where estado > 0;
create unique index if not exists sync_cal_cliente_serp on sync_cal_cliente (key_empresa, key_cliente) where estado > 0;

-- Bitácora: una fila por cosa a sincronizar (venta, anulación, prórroga...). Sirve para no duplicar, no hacer eco,
-- reintentar y mostrar el panel. El procesamiento siempre lee el estado ACTUAL del origen.
create table if not exists sync_cal_evento (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_empresa varchar not null,
    origen varchar not null,                   -- calistenia | serp
    tipo varchar not null,                     -- venta | anulacion | prorroga | suscriptor | cuota | paquete
    key_origen varchar not null,               -- paquete_venta.key / compra_venta.key / prorroga.key / paquete.key ...
    key_destino varchar,                       -- lo creado en el otro sistema
    key_sucursal_cal varchar,
    -- PENDIENTE | HECHO | ERROR (se reintenta) | REVISAR (necesita a una persona) | OMITIDO (sucursal inactiva, etc.)
    state varchar not null default 'PENDIENTE',
    intentos integer default 0,
    proximo_intento timestamp default now(),
    error varchar,
    data json,
    fecha_proceso timestamp
);
create unique index if not exists sync_cal_evento_origen on sync_cal_evento (key_empresa, origen, tipo, key_origen) where estado > 0;
create index if not exists sync_cal_evento_pendientes on sync_cal_evento (state, proximo_intento);

-- Monitoreo de la tarea (como tienda_tarea): última corrida y su resultado.
create table if not exists sync_cal_tarea (
    key_empresa varchar primary key,
    fecha_inicio timestamp,
    fecha_fin timestamp,
    procesados integer default 0,
    errores integer default 0,
    error varchar
);

-- ---- v2: lado S-ERP -> Calistenia, catálogo y conciliación de accesos ----
alter table sync_cal_config add column if not exists ultimo_serp timestamp;          -- última venta de S-ERP revisada
alter table sync_cal_config add column if not exists key_tipo_producto varchar;      -- tipo de producto de los planes en S-ERP
alter table sync_cal_config add column if not exists key_marca varchar;              -- marca de los planes creados en S-ERP
alter table sync_cal_config add column if not exists key_paquete_plantilla varchar;  -- paquete de Calistenia del que se copian servicios y sucursales
alter table sync_cal_config add column if not exists fecha_catalogo timestamp;       -- última pasada del catálogo

-- Acceso de un cliente: paquete_venta_usuario (Calistenia) <-> suscripcion (S-ERP), con el último estado acordado
-- (fechas, cliente, activo). En cada pasada: si cambió un solo lado se copia al otro; si cambiaron los dos, gana el
-- sistema donde nació la venta.
create table if not exists sync_cal_acceso (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_empresa varchar not null,
    origen varchar not null,                   -- calistenia | serp
    key_compra_venta varchar,
    key_paquete_venta varchar,
    key_pvu varchar not null,
    key_suscripcion varchar not null,
    key_producto varchar,
    fecha_inicio date,
    fecha_fin date,
    key_usuario_cal varchar,
    key_cliente varchar,
    activo boolean default true,
    fecha_sync timestamp default now()
);
create unique index if not exists sync_cal_acceso_pvu on sync_cal_acceso (key_empresa, key_pvu) where estado > 0;
create unique index if not exists sync_cal_acceso_sus on sync_cal_acceso (key_empresa, key_suscripcion) where estado > 0;

-- Pago de S-ERP (caja_detalle de venta o de cuota) copiado como movimiento de la caja "S-ERP" de Calistenia.
create table if not exists sync_cal_pago (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_empresa varchar not null,
    key_compra_venta varchar not null,
    key_caja_detalle varchar not null,
    key_caja_movimiento varchar,
    key_paquete_venta varchar,
    key_tipo_pago_cal varchar,
    monto numeric(12,2)
);
create unique index if not exists sync_cal_pago_detalle on sync_cal_pago (key_empresa, key_caja_detalle) where estado > 0;
