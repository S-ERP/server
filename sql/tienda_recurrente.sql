-- Tienda web: planes con débito automático (Fidelidad / Total). Base servisofts.serp.
-- Un contrato = una venta a crédito con N cuotas mensuales; cada cuota se cobra con la tarjeta guardada en el primer
-- pago (token del servicio cybersource) y, al cobrarse, extiende la suscripción un mes.

alter table tienda add column if not exists key_empresa_tipo_pago_credito varchar;  -- tipo de pago "crédito" de la venta

alter table tienda_producto add column if not exists modalidad varchar default 'unico';  -- unico | cuotas
alter table tienda_producto add column if not exists cuotas integer default 1;           -- meses del contrato (12)

alter table tienda_orden add column if not exists modalidad varchar default 'unico';
alter table tienda_orden add column if not exists key_contrato varchar;

create table if not exists tienda_contrato (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_usuario varchar,
    key_tienda varchar not null,
    key_empresa varchar not null,
    key_orden varchar,
    key_tienda_producto varchar,
    key_modelo varchar,
    key_producto varchar,              -- producto de inventario de la venta (lo usan las suscripciones de cada mes)
    key_sucursal varchar,
    key_cliente varchar,
    cliente json,
    key_compra_venta varchar,
    descripcion varchar,
    monto_cuota numeric(12,2) not null,
    cuotas integer not null,
    dia_cobro integer not null,        -- día del mes del primer pago (si el mes es más corto, el último día)
    fecha_inicio date not null,
    key_tarjeta varchar,               -- pago (link) con la tarjeta guardada vigente; cambia si el cliente paga con otra
    tarjeta varchar,
    tarjeta_vence varchar,             -- MM/AAAA
    state varchar not null default 'ACTIVO',   -- ACTIVO | MOROSO | CANCELADO | FINALIZADO
    fecha_cancelacion timestamp,
    motivo_cancelacion varchar,
    key_usuario_cancelacion varchar,
    observacion varchar
);
create index if not exists tienda_contrato_empresa on tienda_contrato (key_empresa, state);
create unique index if not exists tienda_contrato_orden on tienda_contrato (key_orden);

create table if not exists tienda_cobro (
    key varchar primary key,
    fecha_on timestamp default now(),
    estado integer default 1,
    key_contrato varchar not null,
    key_empresa varchar not null,
    numero integer not null,           -- 1..cuotas
    key_cuota varchar,                 -- cuota de compra_venta
    fecha_vencimiento date not null,
    monto numeric(12,2) not null,
    -- PENDIENTE | REINTENTO | COBRADO (cobrado, falta registrarlo en caja) | PAGADO | MORA | INCIERTO | ANULADO
    state varchar not null default 'PENDIENTE',
    intentos integer default 0,
    proximo_intento timestamp,
    key_transaccion varchar,           -- último cobro (automático o link) en el servicio cybersource
    key_link varchar,                  -- link de pago manual enviado al cliente
    url_link varchar,
    categoria varchar,                 -- categoryCode del último rechazo ("01" = no reintentar)
    error varchar,
    fecha_pago timestamp,
    key_suscripcion varchar,
    key_usuario varchar
);
create unique index if not exists tienda_cobro_numero on tienda_cobro (key_contrato, numero) where estado > 0;
create index if not exists tienda_cobro_pendientes on tienda_cobro (state, fecha_vencimiento);

-- Reintentos de cobro configurables por empresa (Linkser: máximo 3 reintentos, al menos cada 24 h).
alter table tienda add column if not exists reintento_horas integer default 24;  -- 24 | 48 | 72
alter table tienda add column if not exists reintentos integer default 3;        -- 0..3 reintentos después del primer intento

-- Movimiento de caja del pago de cada cuota (para anularlo y devolver el dinero a la tarjeta).
alter table tienda_cobro add column if not exists key_caja_detalle varchar;
-- Anulación de compras (Órdenes web → "Anular compra"): quién y por qué.
alter table tienda_orden add column if not exists fecha_anulacion timestamp;
alter table tienda_orden add column if not exists motivo_anulacion varchar;
alter table tienda_orden add column if not exists key_usuario_anulacion varchar;

-- Última pasada de cada tarea (TiendaTask = órdenes, TiendaCobrosTask = cobros): los paneles avisan si se atrasa.
create table if not exists tienda_tarea (
    nombre varchar primary key,
    ultima_ejecucion timestamp,
    procesados integer default 0,
    errores integer default 0,
    ultimo_error varchar,
    fecha_ultimo_error timestamp
);
