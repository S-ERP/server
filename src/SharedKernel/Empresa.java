package SharedKernel;

import org.json.JSONObject;

import SocketCliente.SocketCliente;

public class  Empresa {
    
    public static JSONObject getByKey(String key){
        return  SocketCliente.sendSinc("empresa", 
            new JSONObject()
            .put("component", "empresa")
            .put("type", "getByKey")
            .put("key", key)
        ).getJSONObject("data");
    }
}
