-- Sincronización Calistenia <-> S-ERP: lado del sistema ANTIGUO (base servisofts.calistenia o su copia de pruebas).
-- Ver sync_calistenia.sql (base servisofts.serp) para las reglas.
--
-- Los triggers anotan en sync_outbox cada cambio relevante, venga de donde venga (caja, QR, migrador, MCP...).
-- La tarea de S-ERP lee el outbox por id y procesa con el estado ACTUAL de las tablas: el Java del sistema antiguo
-- guarda una venta en varios INSERT sin transacción (paquete_venta, caja_movimiento, paquete_venta_usuario), por eso
-- S-ERP espera unos segundos antes de procesar un evento.
--
-- Anti-eco: lo que escribe S-ERP lo hace dentro de una transacción con
--     set local sync.origen = 'serp';
-- y el outbox lo guarda con origen 'serp' para que la tarea lo ignore.

create table if not exists sync_outbox (
    id bigserial primary key,
    fecha_on timestamp not null default now(),
    tabla varchar not null,
    operacion char(1) not null,                -- I | U
    key varchar not null,
    cambios varchar[],                         -- columnas que cambiaron (solo en U)
    origen varchar                             -- null = sistema antiguo | serp = lo escribió el sync
);
create index if not exists sync_outbox_fecha on sync_outbox (fecha_on);

-- Columnas que importan por tabla; un UPDATE que no toca ninguna no se anota (p. ej. datos de factura).
create or replace function sync_outbox_trg() returns trigger language plpgsql as $$
declare
    _cols varchar[];
    _cambios varchar[] := '{}';
    _c varchar;
    _old jsonb;
    _new jsonb;
begin
    _cols := case tg_table_name
        when 'paquete' then array['descripcion', 'precio', 'dias', 'participantes', 'estado', 'estado_caja', 'estado_app']
        when 'paquete_venta' then array['key_paquete', 'monto', 'estado']
        when 'paquete_venta_usuario' then array['key_usuario', 'fecha_inicio', 'fecha_fin', 'key_caja', 'estado']
        when 'prorroga_paquete_venta_usuario' then array['dias', 'fecha_fin', 'estado']
        else array[]::varchar[]
    end;
    if tg_op = 'UPDATE' then
        _old := to_jsonb(old);
        _new := to_jsonb(new);
        foreach _c in array _cols loop
            if _old -> _c is distinct from _new -> _c then
                _cambios := _cambios || _c;
            end if;
        end loop;
        if array_length(_cambios, 1) is null then
            return new;
        end if;
    end if;
    insert into sync_outbox (tabla, operacion, key, cambios, origen)
    values (tg_table_name, left(tg_op, 1), new.key,
            case when tg_op = 'UPDATE' then _cambios end,
            nullif(current_setting('sync.origen', true), ''));
    return new;
end;
$$;

drop trigger if exists sync_outbox on paquete;
create trigger sync_outbox after insert or update on paquete
    for each row execute function sync_outbox_trg();

drop trigger if exists sync_outbox on paquete_venta;
create trigger sync_outbox after insert or update on paquete_venta
    for each row execute function sync_outbox_trg();

drop trigger if exists sync_outbox on paquete_venta_usuario;
create trigger sync_outbox after insert or update on paquete_venta_usuario
    for each row execute function sync_outbox_trg();

drop trigger if exists sync_outbox on prorroga_paquete_venta_usuario;
create trigger sync_outbox after insert or update on prorroga_paquete_venta_usuario
    for each row execute function sync_outbox_trg();
