package Controllers;

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

            return "exito";
        } catch (Exception e) {
            SConsole.error("Error en el callback del banco_ganadero", e.getMessage());
            throw new HttpException(Status.BAD_REQUEST, e.getLocalizedMessage());
        }

        // DOC-> Tengo que retornar el status 200;
        // return "exito";
    }

}