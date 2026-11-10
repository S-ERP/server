-- Liquidaciones de Linkser: conciliación de los depósitos de los cobros con tarjeta (base servisofts.serp).
-- En servisofts.cybersource, tabla transaction: ver cybersource/server/sql/liquidacion.sql.

create table if not exists cybersource_liquidacion_config (
    key_empresa varchar primary key,
    key_cuenta_linkser varchar not null,   -- cuenta por cobrar a Linkser (la del tipo de pago con tarjeta)
    key_cuenta_banco varchar not null,     -- banco donde deposita Linkser
    key_cuenta_comision varchar not null,  -- gasto por la comisión que descuenta Linkser
    porcentaje_comision numeric(6,3) not null default 0,
    tolerancia numeric(12,2) not null default 1,
    cuentas json,                          -- descripción de las cuentas (solo para mostrar)
    key_usuario varchar,
    fecha_on timestamp default now()
);

create table if not exists cybersource_liquidacion (
    key varchar primary key,
    key_empresa varchar not null,
    key_usuario varchar,
    fecha_on timestamp default now(),
    estado integer default 1,
    state varchar not null,                -- PROCESANDO, REGISTRADA, ANULADA
    fecha_deposito date not null,
    monto_depositado numeric(14,2) not null,
    referencia varchar,
    observacion varchar,
    cantidad integer,
    cantidad_reembolsos integer,
    cobrado numeric(14,2),
    devuelto numeric(14,2),
    bruto numeric(14,2),
    comision numeric(14,2),
    comision_esperada numeric(14,2),
    key_cuenta_linkser varchar,
    key_cuenta_banco varchar,
    key_cuenta_comision varchar,
    key_asiento varchar,
    codigo_asiento varchar,
    fecha_anulacion timestamp,
    key_usuario_anulacion varchar,
    motivo_anulacion varchar,
    key_asiento_anulacion varchar,
    codigo_asiento_anulacion varchar
);
create index if not exists cybersource_liquidacion_empresa on cybersource_liquidacion (key_empresa, fecha_on desc);
