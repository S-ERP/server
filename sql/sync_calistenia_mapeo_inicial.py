# Fase 0: puntos de venta "Calistenia (sistema antiguo)" por sucursal + mapeo inicial de sucursales y tipos de pago.
# Todo queda APAGADO (sync_cal_sucursal.activo = false). Uso: python3 mapeo_inicial.py [--aplicar]
import sys, uuid, re, unicodedata, psycopg2
APLICAR = '--aplicar' in sys.argv
K = '5b583605-d81e-4447-8083-8f2d1e01a246'
CAL_DB = 'servisofts.calistenia_pruebas'
PV_NOMBRE = 'Calistenia (sistema antiguo)'
# Cuentas de efectivo con otro nombre en S-ERP
EFECTIVO_ALIAS = {'Guaracachi': 'Efectivo en Bolivianos Guaracahi'}
def con(h, d): return psycopg2.connect(host=h, dbname=d, user='postgres', password='postgres')
def n(s): return re.sub(r'[^a-z0-9]', '', unicodedata.normalize('NFKD', s or '').encode('ascii', 'ignore').decode().lower())
cal, emp, inv, caja, serp = con('192.168.5.18', CAL_DB), con('192.168.5.29', 'servisofts.empresa'), con('192.168.5.39', 'servisofts.inventario'), con('192.168.5.45', 'servisofts.caja'), con('192.168.5.48', 'servisofts.serp')
cc, ce, ci, ck, cs = cal.cursor(), emp.cursor(), inv.cursor(), caja.cursor(), serp.cursor()
cc.execute("select key, descripcion from sucursal where estado > 0"); scal = {n(d): (k, d.strip()) for k, d in cc.fetchall()}
ce.execute("select key, descripcion from sucursal where key_empresa = %s and estado > 0 order by descripcion", (K,)); sserp = ce.fetchall()
ck.execute("select key, descripcion from empresa_tipo_pago where key_empresa = %s and estado > 0", (K,)); etp = {d.strip(): k for k, d in ck.fetchall()}
cs.execute("select key_sucursal_cal from sync_cal_sucursal where key_empresa = %s and estado > 0", (K,)); ya = {r[0] for r in cs.fetchall()}
print('== Sucursales')
for ks, d in sserp:
    c = scal.get(n(d))
    if not c: print(f'  {d}: SIN pareja en Calistenia'); continue
    ce.execute("select key from punto_venta where key_sucursal = %s and descripcion = %s and estado > 0", (ks, PV_NOMBRE))
    pv = ce.fetchone()
    ci.execute("select key, descripcion from almacen where key_sucursal = %s and estado > 0 order by fecha_on limit 1", (ks,))
    alm = ci.fetchone()
    efe = etp.get(EFECTIVO_ALIAS.get(d.strip(), '')) or etp.get('Efectivo en Bolivianos ' + d.strip()) or next((v for k, v in etp.items() if n(k) == n('Efectivo en Bolivianos ' + d)), None)
    print(f"  {d.strip():24} cal={c[1]:24} PV={'existe' if pv else 'CREAR'} almacen={alm[1] if alm else 'NINGUNO'} efectivo={'propio' if efe else 'genérico'}")
    if not APLICAR: continue
    if not pv:
        kpv = uuid.uuid4().hex
        ce.execute("""insert into punto_venta (key, key_sucursal, fecha_on, estado, descripcion, observacion, fraccionar_moneda, codigo_facturacion)
                      values (%s, %s, now(), 1, %s, 'Caja automática de las ventas del sistema antiguo (sync)', false, '0')""", (kpv, ks, PV_NOMBRE))
    else: kpv = pv[0]
    if c[0] not in ya:
        cs.execute("""insert into sync_cal_sucursal (key, key_empresa, key_sucursal_cal, key_sucursal, key_punto_venta, key_almacen, activo, molinete)
                      values (%s, %s, %s, %s, %s, %s, false, 'calistenia')""", (str(uuid.uuid4()), K, c[0], ks, kpv, alm[0] if alm else None))
    if efe:
        cs.execute("""insert into sync_cal_tipo_pago (key, key_empresa, key_sucursal_cal, key_tipo_pago_cal, key_empresa_tipo_pago, sentido)
                      values (%s, %s, %s, '1', %s, 'ambos') on conflict do nothing""", (str(uuid.uuid4()), K, c[0], efe))
# Generales: (tipo Calistenia, tipo S-ERP, sentido)
GEN = [('1', 'Efectivo en Bolivianos', 'ambos'), ('2', 'Tarjeta (Sistema antiguo)', 'ambos'), ('3', 'Transferencia (Sistema antiguo)', 'ambos'),
       ('5', 'QR (Sistema antiguo)', 'ambos'), ('2', 'Tarjeta (Cybersource)', 'serp_a_cal'), ('3', 'Transferencia en bolivianos', 'serp_a_cal'),
       ('5', 'QR en bolivianos', 'serp_a_cal')]
print('== Tipos de pago generales')
for tc, nombre, sentido in GEN:
    k = etp.get(nombre); print(f"  Calistenia {tc} {'<->' if sentido == 'ambos' else '<- '} {nombre} {'OK' if k else 'NO EXISTE'}")
    if APLICAR and k:
        cs.execute("""insert into sync_cal_tipo_pago (key, key_empresa, key_sucursal_cal, key_tipo_pago_cal, key_empresa_tipo_pago, sentido)
                      values (%s, %s, null, %s, %s, %s) on conflict do nothing""", (str(uuid.uuid4()), K, tc, k, sentido))
print('  Calistenia 4 (cheque): sin mapeo -> esas ventas quedan en REVISAR')
if APLICAR:
    emp.commit(); serp.commit(); print('APLICADO')
else: print('(simulación: no se escribió nada)')
