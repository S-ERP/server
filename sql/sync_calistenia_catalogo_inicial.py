# Carga inicial del catálogo de planes Calistenia <-> S-ERP (empresa TEST + copia de la base antigua).
# Decisiones: pares existentes -> S-ERP queda igual a Calistenia; solo en un lado -> se crea en el otro,
# vendible en caja si lo es en su origen. Plan = paquete con molinete y días > 0.
import sys, uuid, psycopg2
APLICAR = '--aplicar' in sys.argv
K = '5b583605-d81e-4447-8083-8f2d1e01a246'
CAL_DB = 'servisofts.calistenia_pruebas'
TP_SERP, MARCA, MONEDA = 'e2054a5e575caf8cbf8a8fd09166d709', 'efcc5840e9858b4d84962a483f277691', '8ff73ab24c85dccfc3451d86c7dd3b70'
USR_SERP = 'b2aa9d81-5f63-40ce-ae35-31fbb1417745'
# Pares existentes con distinto nombre (Calistenia -> S-ERP)
PARES = {'PLAN MENSUAL': 'PLAN MENSUAL', 'PAQUETE ESPECIAL': 'PAQUETE ESPECIAL', 'PLAN DIARIO': 'Pago por 1 dia',
         'PLAN SEMANAL': 'Pago por 1 semana', 'Convenio': 'Convenio'}
cal = psycopg2.connect(host='192.168.5.18', dbname=CAL_DB, user='postgres', password='postgres')
inv = psycopg2.connect(host='192.168.5.39', dbname='servisofts.inventario', user='postgres', password='postgres')
serp = psycopg2.connect(host='192.168.5.48', dbname='servisofts.serp', user='postgres', password='postgres')
cc, ci, cs = cal.cursor(), inv.cursor(), serp.cursor()
cc.execute('select current_database()'); assert cc.fetchone()[0] == CAL_DB
cc.execute("set local sync.origen = 'serp'")
cc.execute("""select key, trim(descripcion), precio, dias, participantes, estado_caja from paquete
              where estado > 0 and dias > 0 and is_molinete and (estado_caja or estado_app) order by descripcion""")
paquetes = cc.fetchall()
ci.execute("""select m.key, trim(m.descripcion), m.precio_venta, m.duracion, m.duracion_medida, m.cantidad_suscriptores, m.venta_caja
              from modelo m where m.key_tipo_producto = %s and m.estado > 0""", (TP_SERP,))
modelos = {r[1]: r for r in ci.fetchall()}
mapa = []   # (key_paquete_cal, key_modelo, origen)
usados = set()
print('== S-ERP: corregir / crear')
for kp, d, precio, dias, part, caja in paquetes:
    nombre_serp = PARES.get(d)
    if nombre_serp and nombre_serp in modelos:
        m = modelos[nombre_serp]; usados.add(nombre_serp)
        print(f'  CORREGIR {nombre_serp!r}: {m[1]} {m[2]} {m[3]}{m[4]} x{m[5]} caja={m[6]}  ->  {d} {precio:g} {dias}dias x{part} caja={caja}')
        if APLICAR:
            ci.execute("""update modelo set descripcion=%s, precio_venta=%s, duracion=%s, duracion_medida='dias', cantidad_suscriptores=%s,
                          precio_venta_moneda=coalesce(precio_venta_moneda,%s), venta_caja=%s, fecha_edit=now() where key=%s""",
                       (d, precio, dias, part, MONEDA, caja, m[0]))
        mapa.append((kp, m[0], 'calistenia'))
    else:
        km = uuid.uuid4().hex
        print(f'  CREAR    {d} {precio:g} {dias}dias x{part} caja={caja}')
        if APLICAR:
            ci.execute("""insert into modelo (key, key_usuario, fecha_on, estado, descripcion, key_marca, key_tipo_producto, precio_compra,
                          precio_venta, duracion, duracion_medida, cantidad_suscriptores, precio_venta_moneda, precio_compra_moneda, venta_caja)
                          values (%s,%s,now(),1,%s,%s,%s,0,%s,%s,'dias',%s,%s,%s,%s)""",
                       (km, USR_SERP, d, MARCA, TP_SERP, precio, dias, part, MONEDA, MONEDA, caja))
        mapa.append((kp, km, 'calistenia'))
print('== Calistenia: crear espejos de planes que solo están en S-ERP')
cc.execute("select key from paquete where trim(descripcion)='PLAN MENSUAL' and estado>0")
k_mensual = cc.fetchone()[0]
for nombre, m in sorted(modelos.items()):
    if nombre in usados or nombre in PARES.values() or not m[3] or m[3] <= 0 or m[4] not in ('dias', 'meses'):
        continue
    dias = m[3] if m[4] == 'dias' else m[3] * 30
    kp = str(uuid.uuid4())
    print(f'  CREAR    {nombre} {m[2]:g} {dias}dias x{m[5] or 1} caja={m[6]}')
    if APLICAR:
        cc.execute("""insert into paquete (key, descripcion, precio, dias, participantes, fecha_on, estado, observacion, requiere_motivo,
                      is_molinete, estado_app, estado_caja, key_erp) values (%s,%s,%s,%s,%s,now(),1,'Creado desde S-ERP',false,true,false,%s,%s)""",
                   (kp, nombre, m[2], dias, m[5] or 1, bool(m[6]), m[0]))
        cc.execute("""insert into paquete_servicio (key, key_paquete, key_servicio, estado)
                      select gen_random_uuid()::varchar, %s, key_servicio, 1 from paquete_servicio where key_paquete=%s and estado>0
                      group by key_servicio""", (kp, k_mensual))
        cc.execute("""insert into sucursal_paquete (key, key_sucursal, key_paquete, estado)
                      select gen_random_uuid()::varchar, key_sucursal, %s, 1 from sucursal_paquete where key_paquete=%s and estado>0""",
                   (kp, k_mensual))
    mapa.append((kp, m[0], 'serp'))
print(f'== sync_cal_paquete: {len(mapa)} pares')
if APLICAR:
    for kp, km, origen in mapa:
        cc.execute("update paquete set key_erp=%s where key=%s", (km, kp))
        cs.execute("""insert into sync_cal_paquete (key, key_empresa, key_paquete_cal, key_modelo, origen, fecha_sync)
                      values (%s,%s,%s,%s,%s,now())""", (str(uuid.uuid4()), K, kp, km, origen))
    inv.commit(); cal.commit(); serp.commit(); print('APLICADO')
else:
    print('(simulación: no se escribió nada)')
