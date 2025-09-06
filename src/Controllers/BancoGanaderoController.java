package Controllers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

import Component.SolicitudQr;
import Servisofts.SConsole;
import Servisofts.http.Status;
import Servisofts.http.Exception.*;
import Servisofts.http.annotation.*;

@RestController
@RequestMapping("/banco_ganadero")
public class BancoGanaderoController {

    @PostMapping("/callback")
    public String callback(@RequestBody String status) throws HttpException {
        try {
            SConsole.log("Entro al callback del banco ganadero");
            JSONObject obj = new JSONObject(status);
            SConsole.log("QRID= "+obj.getString("qrid"));
            // throw new HttpException(Status.BAD_REQUEST, "error");
            // throw new HttpException(Status.CONFLICT, "conflicto");
            // throw new HttpException(Status.ACCEPTED, "acecpted");
            System.out.println("********************** Entró al callback **");
            SolicitudQr.aprobarSolicitudQr(obj.getString("qrid"));

            if(obj.has("callback") && !obj.isNull("callback")){
                String callbackUrl = obj.getString("callback");
                JSONObject callbackPayload = new JSONObject();
                callbackPayload.put("status", "success");
                callbackPayload.put("message", "Solicitud aprobada");
                callbackPayload.put("qrid", obj.getString("qrid"));

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(callbackUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(callbackPayload.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                SConsole.log("Callback response: " + response.body());
            }

            return "exito";
        } catch (Exception e) {
            SConsole.error("Error en el callback del banco_ganadero", e.getMessage());
            throw new HttpException(Status.BAD_REQUEST, e.getLocalizedMessage());
        }

        // DOC-> Tengo que retornar el status 200;
        // return "exito";
    }

}