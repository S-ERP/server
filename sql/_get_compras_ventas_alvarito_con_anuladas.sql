-- Igual que _get_compras_ventas_alvarito(4 parámetros) pero con _incluir_anuladas: la lista de Ventas puede mostrar
-- las anuladas (en gris); las demás pantallas siguen usando la versión de 4 parámetros, que las excluye.
CREATE OR REPLACE FUNCTION public._get_compras_ventas_alvarito(_key_empresa character varying, _fecha_inicio character varying, _fecha_fin character varying, _tipo character varying, _incluir_anuladas boolean)
 RETURNS SETOF character varying
 LANGUAGE plpgsql
AS $function$
DECLARE
    rec RECORD;
    respuesta character varying;
    distancia_permitida character varying;
    s_consulta character varying;
    cant_in integer;
BEGIN

s_consulta :=E' SELECT
				
                json_agg( to_json(sq.*))::json as json 
				FROM (
				
				SELECT 
					    cv.*,
					    (
					        SELECT json_agg(cvd.*)
					        FROM compra_venta_detalle cvd
					        WHERE cvd.key_compra_venta = cv.key
					          AND cvd.estado > 0
					    ) AS detalles,
					    (
					        SELECT to_json(sq1)
					        FROM (
					            SELECT 
					                SUM(cuota.monto_base/cv.tipo_cambio) AS total,
									SUM(cuota.monto_base) AS total_base,
					                COUNT(cuota.key) AS cantidad
					            FROM cuota
					            WHERE cuota.key_compra_venta = cv.key
					        ) sq1
					    ) AS cuotas,
						 (
					        SELECT to_json(sq1)
					        FROM (
							
							    SELECT 
									SUM((coalesce(cuota.monto_base,0)-coalesce(cuota.total_amortizado_base,0))/cv.tipo_cambio) as monto,
									SUM((coalesce(cuota.monto_base,0)-coalesce(cuota.total_amortizado_base,0))) as monto_base,
									COUNT(cuota.key) as cantidad,
									MIN(cuota.fecha) as min_fecha
								FROM(
									SELECT cuota.*
									FROM cuota 
									WHERE  cuota.fecha::date <= now()::date
									AND cuota.fecha_pago is null
									AND cuota.key_compra_venta = cv.key
									group by cuota.key
								) cuota
					        ) sq1
					    ) AS cuotas_en_mora,
						(
							 SELECT 
								SUM(coalesce(cuota.total_amortizado_base,0)/cv.tipo_cambio) as monto_amortizado
					            FROM cuota 
					            WHERE cuota.key_compra_venta =  cv.key
								group by cuota.key_compra_venta
						) as monto_amortizado,
								(
							 SELECT 
								SUM(coalesce(cuota.total_amortizado_base,0)) as monto_amortizado_base
					            FROM cuota 
					            WHERE cuota.key_compra_venta =  cv.key
								group by cuota.key_compra_venta
						) as monto_amortizado_base
					FROM compra_venta cv
 					WHERE cv.key_empresa = \''||_key_empresa||E'\'
					  AND cv.fecha_on::date BETWEEN  \''||_fecha_inicio||E'\'::date and \''||_fecha_fin||E'\'::date
					  AND cv.tipo = \''||_tipo||E'\'
'||(CASE WHEN _incluir_anuladas THEN '' ELSE ' AND cv.estado <> 0' END)||E'
				) sq';
    EXECUTE s_consulta INTO respuesta;
    RETURN NEXT respuesta;
	
END;
$function$

;
