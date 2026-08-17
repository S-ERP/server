// Source code is decompiled from a .class file using FernFlower decompiler.
package Servisofts;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class SPGConectInstance {
   private Connection con;
   private String ip;
   private String puerto;
   private String usuario;
   private String contrasena;
   private String bd_name;
   private String ruta_pg_dump;
   private String ruta_pg_restore;

   public SPGConectInstance(JSONObject data_base) {
      this.setConexion(data_base);
      this.conectar();
   }

   public Connection getConexion() {
      return this.conectar();
   }

   public Connection setConexion(JSONObject data_base) {
      this.ip = data_base.getString("ip");
      this.bd_name = data_base.getString("bd_name");
      this.puerto = "" + data_base.getInt("puerto");
      this.usuario = data_base.getString("user");
      this.contrasena = data_base.getString("pass");
      SLog.put("PostgreSQL.status", "desconectado");
      SLog.put("PostgreSQL.host", "jdbc:postgresql://" + this.ip + ":" + this.puerto + "/" + this.bd_name);
      return this.conectar();
   }

   public Connection conectar() {
      String cadena = "jdbc:postgresql://" + this.ip + ":" + this.puerto + "/" + this.bd_name;

      try {
         Class.forName("org.postgresql.Driver");
         if (this.con != null && !this.con.isClosed()) {
            return this.con;
         } else {
            if (Servisofts.DEBUG) {
               SConsole.warning(new Object[]{"Initializing PostgreSQL DB", cadena, "usr:" + this.usuario, "pass:" + this.contrasena});
            }

            this.con = DriverManager.getConnection("jdbc:postgresql://" + this.ip + ":" + this.puerto + "/" + this.bd_name, this.usuario, this.contrasena);
            SConsole.succes(new Object[]{"PostgreSQL DB ", cadena, "usr:" + this.usuario, "pass:" + this.contrasena, "ready!"});
            SLog.put("PostgreSQL.status", "exito");
            return this.con;
         }
      } catch (Exception var3) {
         SLog.put("PostgreSQL.status", "desconectado");
         SConsole.error(new Object[]{"Failed to initializing PostgreSQL DB", cadena, "usr:" + this.usuario, "pass:" + this.contrasena});
         return null;
      }
   }

   public Connection newInstanceConnection() throws ClassNotFoundException, SQLException {
      String cadena = "jdbc:postgresql://" + this.ip + ":" + this.puerto + "/" + this.bd_name;
      Class.forName("org.postgresql.Driver");
      if (Servisofts.DEBUG) {
         SConsole.warning(new Object[]{"Initializing PostgreSQL DB", cadena, "usr:" + this.usuario, "pass:" + this.contrasena});
      }

      Connection newcon = DriverManager.getConnection("jdbc:postgresql://" + this.ip + ":" + this.puerto + "/" + this.bd_name, this.usuario, this.contrasena);
      SConsole.succes(new Object[]{"PostgreSQL DB ", cadena, "usr:" + this.usuario, "pass:" + this.contrasena, "ready!"});
      SLog.put("PostgreSQL.status", "exito");
      return newcon;
   }

   public void close(){
      try {
         this.con.close();
      } catch (SQLException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      this.con = null;
   }

   public void Transacction() {
      try {
         if (this.con.getAutoCommit()) {
            this.con.setAutoCommit(false);
         }
      } catch (SQLException var2) {
         var2.printStackTrace();
      }

   }

   public void Transacction_end() {
      try {
         if (!this.con.getAutoCommit()) {
            this.con.setAutoCommit(true);
         }
      } catch (SQLException var2) {
         var2.printStackTrace();
      }

   }

   public void commit() {
      try {
         this.con.commit();
      } catch (SQLException var2) {
         var2.printStackTrace();
      }

   }

   public void rollback() {
      try {
         this.con.rollback();
      } catch (SQLException var2) {
         var2.printStackTrace();
      }

   }

   public void rollback(Savepoint savepoint) {
      try {
         this.con.rollback(savepoint);
      } catch (SQLException var3) {
         var3.printStackTrace();
      }

   }

   public String escapeEspecialChar(String in) {
      StringBuilder output = new StringBuilder();

      for(int i = 0; i < in.length(); ++i) {
         char c = in.charAt(i);
         switch (c) {
            case '\'':
               output.append("''");
               break;
            case '\\':
               output.append("\\\\");
               break;
            default:
               output.append(c);
         }
      }

      return output.toString();
   }

   public boolean editObject(String nombre_tabla, JSONObject obj) throws SQLException {
      if (obj.isNull("key")) {
         return false;
      } else {
         String consulta = "SELECT public.desc_tabla('" + nombre_tabla + "') as json";
         JSONArray tabla = this.ejecutarConsultaArray(consulta);
         String aux = "";

         for(int i = 0; i < tabla.length(); ++i) {
            JSONObject tupla = tabla.getJSONObject(i);
            if (!tupla.getString("column_name").equals("key") && !tupla.getString("column_name").equals("key") && !obj.isNull(tupla.getString("column_name"))) {
               switch (tupla.getString("data_type")) {
                  case "character varying":
                     aux = aux + tupla.getString("column_name") + "='" + this.escapeEspecialChar(obj.getString(tupla.getString("column_name"))) + "',";
                     continue;
                  case "json[]":
                     if (obj.get(tupla.getString("column_name")).toString().length() <= 0) {
                        aux = aux + tupla.getString("column_name") + "= NULL,";
                     } else {
                        aux = aux + tupla.getString("column_name") + "='" + this.escapeEspecialChar(obj.get(tupla.getString("column_name")).toString()) + "',";
                     }
                     continue;
                  case "double precision":
                     aux = aux + tupla.getString("column_name") + "=" + obj.getDouble(tupla.getString("column_name")) + ",";
                     continue;
                  case "json":
                     if (obj.get(tupla.getString("column_name")).toString().length() <= 0) {
                        aux = aux + tupla.getString("column_name") + "= NULL,";
                     } else {
                        aux = aux + tupla.getString("column_name") + "='" + this.escapeEspecialChar(obj.get(tupla.getString("column_name")).toString()) + "',";
                     }
                     continue;
                  case "ARRAY":
                     if (obj.get(tupla.getString("column_name")).toString().length() <= 0) {
                        aux = aux + tupla.getString("column_name") + "= NULL,";
                     } else {
                        aux = aux + tupla.getString("column_name") + "='" + this.escapeEspecialChar(obj.get(tupla.getString("column_name")).toString()) + "',";
                     }
                     continue;
                  case "boolean":
                     aux = aux + tupla.getString("column_name") + "= " + obj.getBoolean(tupla.getString("column_name")) + " ,";
                     continue;
                  case "timestamp without time zone":
                     aux = aux + tupla.getString("column_name") + "='" + obj.getString(tupla.getString("column_name")) + "',";
                     continue;
                  case "integer":
                     aux = aux + tupla.getString("column_name") + "=" + obj.getInt(tupla.getString("column_name")) + ",";
                     continue;
               }

               aux = aux + tupla.getString("column_name") + "='" + this.escapeEspecialChar(obj.getString(tupla.getString("column_name"))) + "',";
            }
         }

         if (aux.length() == 0) {
            return false;
         } else {
            aux = aux.substring(0, aux.length() - 1);
            String funct = "update " + nombre_tabla + " set " + aux + " where key ='" + obj.getString("key") + "'";
            PreparedStatement ps = this.con.prepareStatement(funct);
            ps.executeUpdate();
            ps.close();
            return true;
         }
      }
   }

   public PreparedStatement preparedStatement(String query) throws SQLException {
      return this.getConexion().prepareStatement(query);
   }

   public void insertObject(String nombre_tabla, JSONObject json) throws SQLException {
      String funct = "insert into " + nombre_tabla + " (select * from json_populate_recordset(null::" + nombre_tabla + ", '" + (new JSONArray()).put(json).toString() + "')) RETURNING key";
      PreparedStatement ps = this.con.prepareStatement(funct);
      ps.executeQuery();
      ps.close();
   }

   public void insertArray(String nombre_tabla, JSONArray json) throws SQLException {
      String funct = "insert into " + nombre_tabla + " (select * from json_populate_recordset(null::" + nombre_tabla + ", '" + json.toString() + "')) RETURNING key";
      PreparedStatement ps = this.con.prepareStatement(funct);
      ps.executeQuery();
      ps.close();
   }

   public JSONArray ejecutarConsultaArray(String consulta) throws SQLException {
      PreparedStatement ps = this.getConexion().prepareStatement(consulta);
      ResultSet rs = ps.executeQuery();
      JSONArray arr = new JSONArray();
      if (rs.next()) {
         arr = rs.getString("json") == null ? new JSONArray() : new JSONArray(rs.getString("json"));
      }

      rs.close();
      ps.close();
      return arr;
   }

   public int ejecutarConsultaInt(String consulta) throws SQLException {
      PreparedStatement ps = this.getConexion().prepareStatement(consulta);
      ResultSet rs = ps.executeQuery();
      int resp = 0;
      if (rs.next()) {
         resp = rs.getInt(1);
      }

      rs.close();
      ps.close();
      return resp;
   }

   public String ejecutarConsultaString(String consulta) throws SQLException {
      PreparedStatement ps = this.getConexion().prepareStatement(consulta);
      ResultSet rs = ps.executeQuery();
      String resp = "";
      if (rs.next()) {
         resp = rs.getString(1);
      }

      rs.close();
      ps.close();
      return resp;
   }

   public void ejecutar(String consulta) {
      PreparedStatement ps = null;

      try {
         ps = this.con.prepareStatement(consulta);
         ps.executeUpdate();
      } catch (Exception var12) {
         var12.printStackTrace();
      } finally {
         if (ps != null) {
            try {
               ps.close();
            } catch (SQLException var11) {
               var11.printStackTrace();
            }
         }

      }

   }

   public void execute(String consulta) throws SQLException {
      PreparedStatement ps = this.con.prepareStatement(consulta);
      ps.execute();
      ps.close();
   }

   public void ejecutarUpdate(String consulta) {
      PreparedStatement ps = null;

      try {
         ps = this.con.prepareStatement(consulta);
         ps.executeUpdate();
      } catch (Exception var12) {
         var12.printStackTrace();
      } finally {
         if (ps != null) {
            try {
               ps.close();
            } catch (SQLException var11) {
               var11.printStackTrace();
            }
         }

      }

   }

   public void executeUpdate(String consulta) throws SQLException {
      PreparedStatement ps = this.con.prepareStatement(consulta);
      ps.executeUpdate();
      ps.close();
   }

   public JSONObject ejecutarConsultaObject(String consulta) throws SQLException {
      PreparedStatement ps = this.getConexion().prepareStatement(consulta);
      ResultSet rs = ps.executeQuery();
      JSONObject obj = new JSONObject();
      if (rs.next()) {
         obj = rs.getString("json") != null ? new JSONObject(rs.getString("json")) : new JSONObject();
      }

      rs.close();
      ps.close();
      return obj;
   }

   public boolean save_backup() {
      boolean hecho = false;
      String path = (new File(".")).getAbsolutePath();
      path = path.substring(0, path.length() - 1);
      path = path + this.bd_name;

      try {
         System.out.println("Guardando backup en " + path);
         File pgdump = new File(this.ruta_pg_dump);
         if (pgdump.exists()) {
            ProcessBuilder constructor;
            if (!path.equalsIgnoreCase("")) {
               constructor = new ProcessBuilder(new String[]{this.ruta_pg_dump, "--verbose", "--format", "custom", "-f", path});
            } else {
               constructor = new ProcessBuilder(new String[]{this.ruta_pg_dump, "--verbose", "--inserts", "--column-inserts", "-f", path});
               System.out.println("ERROR");
            }

            constructor.environment().put("PGHOST", this.ip);
            constructor.environment().put("PGPORT", this.puerto);
            constructor.environment().put("PGUSER", this.usuario);
            constructor.environment().put("PGPASSWORD", this.contrasena);
            constructor.environment().put("PGDATABASE", this.bd_name);
            constructor.redirectErrorStream(true);
            Process proceso = constructor.start();
            InputStream is = proceso.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String ll;
            while((ll = br.readLine()) != null) {
               System.out.println(ll);
            }

            System.out.println(proceso);
            System.out.println("terminado backup " + path);
            hecho = true;
         } else {
            System.out.println("Error en la ruta del pg_dump ingrese nuevamente");
            hecho = false;
         }
      } catch (Exception var10) {
         System.err.println(var10.getMessage() + "Error de backup");
         hecho = false;
      }

      return hecho;
   }

   public boolean restore_backup() {
      boolean hecho = false;
      String path = (new File(".")).getAbsolutePath();
      path = path.substring(0, path.length() - 1);
      path = path + this.bd_name;

      try {
         System.out.println("Restaurando Base de datos desde " + path + " ");
         File pgrestore = new File(this.ruta_pg_restore);
         if (pgrestore.exists()) {
            ProcessBuilder constructor = new ProcessBuilder(new String[]{this.ruta_pg_restore, "-h", this.ip, "-p", this.puerto, "-U", this.usuario, "-C", "-d", this.bd_name, "-v", path});
            constructor.environment().put("PGPASSWORD", this.contrasena);
            constructor.redirectErrorStream(true);
            Process proceso = constructor.start();
            InputStream is = proceso.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String ll;
            while((ll = br.readLine()) != null) {
               System.out.println(ll);
            }

            proceso.destroy();
            hecho = true;
         } else {
            System.out.println("Error en la ruta del pg_dump ingrese nuevamente");
            hecho = false;
         }
      } catch (Exception var10) {
         var10.printStackTrace();
         hecho = false;
      }

      return hecho;
   }

   public void setContrasena(String contrasena) {
      this.contrasena = contrasena;
   }

   public void setPuerto(String puerto) {
      this.puerto = puerto;
   }

   public void setIp(String ip) {
      this.ip = ip;
   }

   public void setUsuario(String usuario) {
      this.usuario = usuario;
   }

   public void setBd_name(String bd_name) {
      this.bd_name = bd_name;
   }

   public void setRuta_pg_dump(String ruta_pg_dump) {
      this.ruta_pg_dump = ruta_pg_dump;
   }

   public void setRuta_pg_restore(String ruta_pg_restore) {
      this.ruta_pg_restore = ruta_pg_restore;
   }

   public void historico(String key_usuario, String descripcion, JSONObject data) {
      try {
         String key = UUID.randomUUID().toString();
         JSONObject historico = new JSONObject();
         historico.put("key", key);
         historico.put("key_usuario", key_usuario);
         historico.put("data", data);
         historico.put("descripcion", descripcion);
         historico.put("fecha_on", "now()");
         historico.put("estado", 1);
         this.insertArray("historico", (new JSONArray()).put(historico));
      } catch (SQLException var6) {
         var6.printStackTrace();
      }

   }

   public void historico(String key_usuario, String key_aux, String descripcion, JSONObject data) {
      try {
         String key = UUID.randomUUID().toString();
         JSONObject historico = new JSONObject();
         historico.put("key", key);
         historico.put("key_usuario", key_usuario);
         historico.put("key_aux", key_aux);
         historico.put("data", data);
         historico.put("descripcion", descripcion);
         historico.put("fecha_on", "now()");
         historico.put("estado", 1);
         this.insertArray("historico", (new JSONArray()).put(historico));
      } catch (SQLException var7) {
         var7.printStackTrace();
      }

   }

   public boolean isLive() {
      String consulta = "SELECT 1";
      boolean isLive = false;
      try {
          isLive = ejecutarConsultaInt(consulta) == 1;
      } catch (Exception e) {
          
      }
      return isLive;
  }
}
