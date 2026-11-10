# Plan de pruebas: tienda web y cobros recurrentes

Alcance: página de planes, tienda web (serp), contratos en cuotas con débito automático, cobro con tarjeta en caja,
servicio cybersource. Ambiente: empresa de pruebas RUDDY (tienda `99f14351…`), MID de pruebas de Linkser.

Estado al 2026-11-07: los casos marcados ✅ ya se probaron de punta a punta; el resto queda para esta campaña.

## Datos de prueba

| Dato | Valor |
|---|---|
| Tarjetas sin desafío | Visa 4000 0000 0000 2701 · Mastercard 5200 0000 0000 2235 |
| Tarjetas con desafío 3DS | Visa 4000 0000 0000 2503 · Mastercard 5200 0000 0000 2151 (código: cualquiera) |
| Vencimiento / CVV | 01/2029 · 123 |
| Planes RUDDY | Mensual (pago único) · Fidelidad Web Bs 199 × 12 · Total Web Bs 239 × 12 |
| Página | http://localhost:8080/web-paquetes/ (iframe) · http://192.168.3.4:8080/web-paquetes/ (redirección) |
| Rechazos | El sandbox de Linkser aprueba cualquier monto. En RUDDY (sandbox) poner en la observación del contrato `[simular:DECLINED:02]` (reintentable) o `[simular:DECLINED:01]` (no reintentar); producción lo ignora. Tarjeta inválida real: token inexistente → INVALID_DATA → mora directa (sin reintentos). |

Para adelantar una cuota sin esperar un mes: `update tienda_cobro set fecha_vencimiento = current_date where …` (solo en RUDDY).

## 1. Página web y compra

| ID | Caso | Esperado | Estado |
|---|---|---|---|
| T-WEB-01 | Catálogo carga precios del ERP | Precios y planes = tienda; si falla, nada se puede comprar | ✅ |
| T-WEB-02 | Plan Mensual, tarjeta sin desafío, iframe | Venta contado, suscripción, confirmación con número | ✅ |
| T-WEB-03 | Plan Mensual, tarjeta con desafío | Ventana del banco dentro del iframe, venta | ✅ |
| T-WEB-04 | Modo redirección (http con IP) | Va a la página de pago y vuelve con #orden-key | ✅ |
| T-WEB-05 | Fidelidad y Total, cuota 1 | Contrato 12 cuotas, cuota 1 pagada, textos con calendario | ✅ |
| T-WEB-06 | Validación del formulario de pago | Campos vacíos marcados en rojo con nombre | ✅ |
| T-WEB-07 | Tarjeta rechazada en el primer pago | Mensaje, reintento con otra tarjeta, no se crea venta | |
| T-WEB-08 | El cliente cierra la página tras pagar | La tarea registra la venta en < 1 min | |
| T-WEB-09 | Recarga en el paso 3 / volver atrás | Retoma la misma orden, no cobra dos veces | |
| T-WEB-10 | Celular (iOS Safari, Android Chrome), modo claro y oscuro | Iframe legible, alto correcto, desafío visible | ✅ emulado iPhone (falta equipo real) |
| T-WEB-11 | Plan "Próximamente" o sucursal sin el plan | No se puede comprar | |
| T-WEB-12 | Límite de órdenes por IP (15 / 10 min) | Mensaje "Demasiados intentos" | |

## 2. Cliente y venta en el ERP

| ID | Caso | Esperado | Estado |
|---|---|---|---|
| T-ERP-01 | CI nuevo | Crea cliente con empresa | ✅ |
| T-ERP-02 | CI existente (incluye 8 dígitos 6/7) | Reutiliza el cliente (buscar_ci) | ✅ |
| T-ERP-03 | Suscripción encadenada a una vigente | Empieza al día siguiente del fin | ✅ |
| T-ERP-04 | CRM caído al registrar | Orden queda PAGADA, reintenta, no duplica cliente | |
| T-ERP-05 | Caja/compra_venta caídos a mitad del contrato | Reintento sigue el paso pendiente, una sola venta | |
| T-ERP-06 | Orden en ERROR → "Reintentar" desde Órdenes web | Registra la venta sin cobrar de nuevo | |

## 3. Cobro automático (MIT)

| ID | Caso | Esperado | Estado |
|---|---|---|---|
| T-COB-01 | Cuota vencida, tarea 8:00–20:00 | Cobra con token, amortiza en caja, +1 mes suscripción | ✅ |
| T-COB-02 | "Cobrar ahora" desde el panel | Igual que T-COB-01 | ✅ |
| T-COB-03 | Cobrado pero falla el registro en caja | Queda COBRADO, la siguiente pasada registra sin cobrar | ✅ |
| T-COB-04 | Rechazo reintentable | REINTENTO, próximo intento según tienda (24/48/72 h) | ✅ simulado |
| T-COB-05 | Rechazo con categoryCode 01 | MORA directo, contrato MOROSO, sin reintentos | ✅ simulado |
| T-COB-06 | Agota reintentos (0, 1, 3 configurados) | MORA tras 1+N intentos | ✅ con 3 |
| T-COB-07 | Cuota con una anterior en mora | No se cobra hasta resolver la anterior | |
| T-COB-08 | Fuera de horario (antes de las 8:00) | No cobra; sí registra COBRADO pendientes | |
| T-COB-09 | Sin respuesta de Cybersource | INCIERTO, no se reintenta solo, aviso en panel | |
| T-COB-10 | Reinicio de serp con cuotas vencidas | Cobra una sola vez en la siguiente pasada | |
| T-COB-11 | Mismo key_solicitud repetido | cybersource devuelve el resultado anterior, no cobra | |
| T-COB-12 | Última cuota pagada | Contrato FINALIZADO | |
| T-COB-13 | Día 31 / 29-30 feb | Vence el último día del mes, sin correrse | |
| T-COB-14 | Tarjeta guardada inválida (token inexistente) | INVALID_DATA → MORA directa, sin reintentos | ✅ real |
| T-COB-15 | Aviso si la tarea de cobro se atrasa (>25 min) | Aviso rojo en Contratos y Cobros | ✅ |

## 4. Link de pago de cuota y cambio de tarjeta

| ID | Caso | Esperado | Estado |
|---|---|---|---|
| T-LNK-01 | Generar link de cuota en mora | Link con mensaje para WhatsApp | ✅ |
| T-LNK-02 | Cliente paga el link con otra tarjeta | Cuota PAGADA, contrato usa la tarjeta nueva en adelante | ✅ |
| T-LNK-03 | Link enviado y la tarea no cobra la tarjeta | Sin doble cobro | |
| T-LNK-04 | Cuota pagada por token con link pendiente | El link se anula | |
| T-LNK-05 | Contrato MOROSO que se pone al día | Vuelve a ACTIVO | |

## 5. Cancelación, anulación y devoluciones

| ID | Caso | Esperado | Estado |
|---|---|---|---|
| T-CAN-01 | Cancelar anulando restantes | CANCELADO, cobros ANULADO, no cobra más | |
| T-CAN-02 | Cancelar manteniendo restantes | CANCELADO, cuotas cobrables solo por link | |
| T-CAN-03 | Anular la amortización de una cuota con tarjeta | Devuelve el pago (void/refund) y revierte en caja | |
| T-CAN-04 | Anular la venta del contrato | Devuelve todos los pagos con tarjeta de la venta | ✅ botón Anular compra |
| T-CAN-05 | Venta en caja con tarjeta y anulación | Void/refund, caja revertida | ✅ |
| T-CAN-06 | Ajuste contable de cuotas anuladas al cancelar | Definir con contabilidad (hoy la deuda queda en compra_venta) | |

## 6. Paneles y configuración

| ID | Caso | Esperado | Estado |
|---|---|---|---|
| T-PAN-01 | Contratos: filtros, KPIs, tarjetas por vencer | Datos coinciden con la BD | |
| T-PAN-02 | Cobros: periodos y estados | Totales correctos | |
| T-PAN-03 | Tienda web: guardar config, reintentos 24/48/72 | Se guarda y aplica al siguiente rechazo | |
| T-PAN-04 | Publicar plan nuevo en cuotas / quitar plan | Catálogo de la página cambia | |
| T-PAN-05 | Usuario sin permiso /cybersource configurar | No ve acciones de cobro ni configuración | |

## 7. Seguridad

| ID | Caso | Esperado |
|---|---|---|
| T-SEG-01 | url_retorno de otro dominio | Ignorada (solo mismo origen que la orden) |
| T-SEG-02 | Tema del iframe con CSS inyectado | Valores inválidos descartados |
| T-SEG-03 | buscar_ci con comillas / SQL | Rechazado por validación |
| T-SEG-04 | Orden de otra empresa vía API | "Orden no encontrada" |
| T-SEG-05 | Tokens de tarjeta | Nunca salen del servicio cybersource |
