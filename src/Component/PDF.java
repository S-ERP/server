package Component;


import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConfig;

public class PDF {
    
    private PDDocument document;
    private PDPage page;
    private PDPageContentStream contentStream;
    private PDType0Font font;
    private float y, ancho;
    private int fontSize;
    private int espacios[];
    private int ANCHO = 612;
    private int ALTO = 792;
    private int ALTO_MARGEN = 752;

    private NumeroLiteral numero_literal;
    private String pattern = "###,###,###.##";
    private String pattern3 = "###,###,###.###";
    private String rutaFontBold = "./font/Helvetica-Bold.ttf";
    private String rutaFont = "./font/Helvetica.ttf";
    private DecimalFormat myFormatter = new DecimalFormat(pattern,DecimalFormatSymbols.getInstance(Locale.US));
    private DecimalFormat myFormatter3 = new DecimalFormat(pattern3,DecimalFormatSymbols.getInstance(Locale.US));
    
  
    public PDF(){
        try{
            document = new PDDocument();
            //page = new PDPage(PDRectangle.LETTER);
            //contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true);
            generarFacturaRollo(new JSONObject(), "nombre.pdf", "#ff00ff");
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }

    public String perfilUsuario(String key_usuario){
        try{
            String key = "test";

            fontSize = 25;
            font = PDType0Font.load(document, new File("./font/Helvetica.ttf"));
            contentStream.setFont(font, fontSize);
            document.addPage(page);

            y = page.getMediaBox().getHeight();
            ancho = page.getMediaBox().getWidth();

            y-=50; //margen
            
            String text = "Perfil de usuario";
            setBold();
            write(getTextCenterX(text), y, text);
            setNormal();
            //writeImg("./img/tierra.jpg");
            
            y-=10;
            writeImgUrl("https://ruddypazd.com/imagesAdmin/6340999", ANCHO/2);

            setFontSize(10);
            y-=10;
            writeText(key_usuario);
            y-=10;
            writeText("Hola asasd con mil mierdas mas  como esytan oasodsa asdasd espero quie bien , esta es solñ una priena de cuelaiuqoer cosa que se me venaga la caebnza");
            

            contentStream.close();

            document.save(SConfig.getJSON("files").getString("url")+"pdf/"+key);
            document.close();
            return key;
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public float getTextCenterX(String text) throws IOException{
        float tamanoFontX = font.getStringWidth(text) / 1000 * fontSize;
        float startX = (page.getMediaBox().getWidth() - tamanoFontX) / 2;
        return startX;
    }
    public float getTextCenterY(String text) throws IOException{
        float tamanoFontY = font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;
        float startY = (page.getMediaBox().getHeight() - tamanoFontY) / 2;
        return startY;
    }

    private void writeImgUrl(String url, float center){
        try{
            URL url_ = new URL(url);
            PDImageXObject pdImage = null;
            BufferedImage image_ = ImageIO.read(url_);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image_, "PNG", baos);
            byte[] bytes = baos.toByteArray();
            pdImage = PDImageXObject.createFromByteArray(document, bytes, "prueba");
            y-=100;
            contentStream.drawImage(pdImage, center-(100/2), y, 100, 100);
            if(y<=100){
                newPage();
                y = page.getMediaBox().getHeight()-50;
            }
        }catch(Exception e){
            System.out.println("Imagen registrada");
        }
    }
    
    private void writeText(String contenido){
        try{
            String txt = "";
            for (int i = 0; i < 1; i++) {
                y-=20;
                txt = contenido;
                        
                while((txt.length()*6)>ancho){
                    write(25, y, txt.substring(0, ((int)ancho/6)));
                    txt = txt.substring(((int)ancho/6), txt.length());
                    y-=12;
                }            
                write(25, y, txt);
            }
            y-=12;
            if(y<=100){
                newPage();
                y = page.getMediaBox().getHeight()-50;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        
    }

    private void write(float x, float y, String text) throws IOException {
        //String line = new String(text.getBytes("ISO-8859-1"), "UTF-8");.
        String line = new String(text.getBytes("ISO-8859-1"), "UTF-8");
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        try {
            contentStream.showText(line);
        } catch (Exception e) {
            // line = new String(text.getBytes("ISO-8859-1"), "UTF-8");
            contentStream.showText(text);
        }
        contentStream.endText();
    }
    public static Color getColorHex(String colorStr) {
        return new Color(
                Integer.valueOf( colorStr.substring( 1, 3 ), 16 ),
                Integer.valueOf( colorStr.substring( 3, 5 ), 16 ),
                Integer.valueOf( colorStr.substring( 5, 7 ), 16 ) );
    }
    public void newPage() throws IOException{
        contentStream.setLineWidth(1f);   
        contentStream.stroke();  
        contentStream.close();
        page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        setFontSize(fontSize);
    }
    public void newPageOffSeT() throws IOException{
        contentStream.setLineWidth(1f);   
        contentStream.stroke();  
        contentStream.close();
        //page = new PDPage(new PDRectangle(ANCHO, ALTO));
        page = new PDPage(new PDRectangle());
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        setFontSize(fontSize);
    }
    
    public void setFontSize(int fontSize) throws IOException {
        this.fontSize = fontSize;
        contentStream.setFont(font, fontSize);
    }

    public void setColor(Color color) throws IOException{
        contentStream.setNonStrokingColor(color); 
    }
    public String getRutaFont() {
        return rutaFont;
    }

    public void setRutaFont(String rutaFont) {
        this.rutaFont = rutaFont;
    }

    public String getRutaFontBold() {
        return rutaFontBold;
    }

    public void setRutaFontBold(String rutaFontBold) {
        this.rutaFontBold = rutaFontBold;
    }

    public void setBold() throws IOException{
        font = PDType0Font.load(document, new File(getRutaFontBold()));
        contentStream.setFont(font, fontSize);
    }
    public void setNormal() throws IOException{
        font = PDType0Font.load(document, new File(getRutaFont()));
        contentStream.setFont(font, fontSize);
    }
    public int PositionMid(int x,int y){
        int resultado;
        resultado=y-x;
        resultado=y-(resultado/2);
        return resultado;
    }
  
    public int configWidth(int width){
        int restar =0;
        int aux=0;
        if(width>ALTO_MARGEN){
            aux=width-ALTO_MARGEN;
            restar=aux/espacios.length;
        }
        return restar;
    }
    
    public static ArrayList<String> limitText(String text,int tamano){
        ArrayList list = new ArrayList<>();
        String mesenge = "";
        String[] list_aux = text.split(" ");
        for (int i = 0; i < list_aux.length; i++) {
            mesenge += list_aux[i]+" ";
            if (tamano <= mesenge.length()) {
                list.add(mesenge);
                mesenge = "";
            } else {
                if (i == list_aux.length - 1) {
                    list.add(mesenge);
                }
            }
        }
        return list;
    }
    public static ArrayList<String> truncarText(String text,int tamano){
        ArrayList list = new ArrayList<>();
        String mesenge = "";
        for (int i = 0; i < text.length(); i++) {
            mesenge += text.charAt(i);
            if((mesenge.length()>=tamano)){
                list.add(mesenge);
                mesenge="";
            }
            if(i>=(text.length()-1)){
                list.add(mesenge);
            }
        }
        return list;
    }
    public static String getMesNum(int num) {
        String mes ="";
        switch (num) {
            case 1:
                mes = "ENERO";
                break;
            case 2:
                mes = "FEBRERO";
                break;
            case 3:
                mes = "MARZO";
                break;
            case 4:
                mes = "ABRIL";
                break;
            case 5:
                mes = "MAYO";
                break;
            case 6:
                mes = "JUNIO";
                break;
            case 7:
                mes = "JULIO";
                break;
            case 8:
                mes = "AGOSTO";
                break;
            case 9:
                mes = "SEPTIEMBRE";
                break;
            case 10:
                mes = "OCTUBRE";
                break;
            case 11:
                mes = "NOVIEMBRE";
                break;
            case 12:
                mes = "DICIEMBRE";
                break;    
        }
        return mes;
    }

    public static String  getFechaLiteral(String fecha_date){
        String fecha ="";
        //2022-03-29T19:08:24
        String aux[]=fecha_date.split("T");
        aux = aux[0].split("-");
        fecha = aux[2]+" DE "+getMesNum(Integer.parseInt(aux[1]))+" DE "+aux[0];
        return fecha;
    }
    public static String  getFechaFormat(String fecha_date){
        String fecha ="";
        //2022-03-29T19:08:24
        String aux[]=fecha_date.split("T");
        String hora[]=aux[1].split(":");
        aux = aux[0].split("-");
        String estado="AM";
        if(Integer.parseInt(hora[0])>12){
            estado="PM";
        }
        fecha = aux[2]+"/"+aux[1]+"/"+aux[0] +" "+hora[0]+":"+hora[1]+" "+estado;
        return fecha;
    }
    public String getNumFormat(double monto){
        String monto_total="";
        monto_total=myFormatter.format(monto);
        myFormatter.setMaximumFractionDigits(2);
        myFormatter.setMinimumFractionDigits(2);
       if (monto_total.equals("0")) {
            monto_total = monto_total + ".00";
        }
        String[] tamano = monto_total.split("\\.");
        if (tamano.length == 1) {
            monto_total = monto_total + ".00";
        } else {
            if (tamano[1].length() == 1) {
                monto_total = monto_total + "0";
            }
        }
        return monto_total;
    }
    
    public String getNumFormat3(double monto){
        String monto_total="";
        monto_total=myFormatter3.format(monto);
        myFormatter3.setMaximumFractionDigits(3);
        myFormatter3.setMinimumFractionDigits(3);
       if (monto_total.equals("0")) {
            monto_total = monto_total + ".000";
        }
        String[] tamano = monto_total.split("\\.");
        if (tamano.length == 1) {
            monto_total = monto_total + ".000";
        } else {
            if (tamano[1].length() == 1) {
                monto_total = monto_total + "0";
            }
        }
        return monto_total;
    }


    public void drawLinePoint(float posy) throws IOException{
        int posLine=15;
       for(int m=0 ;m<23; m++){
           contentStream.moveTo(posLine, posy);
           contentStream.lineTo(posLine+6,posy);
           contentStream.stroke(); 
           posLine+=9;
       }
   }

   public void initOffSet(int cal) throws IOException{
        ALTO=671;
        ANCHO=241;
        ALTO_MARGEN = 874;
        numero_literal= new NumeroLiteral();
        document = new PDDocument();
        //page = new PDPage(new PDRectangle(ANCHO, cal));
        page = new PDPage(new PDRectangle(ANCHO, ALTO));
        document.addPage(page);
        font = PDType0Font.load(document, new File(getRutaFont()));
        contentStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true);
    }

   public void drawPoints(float posy,int ancho) throws IOException{
        String puntos="";
        for(int m=0 ;m<ancho; m++){
            puntos+=".";
        }
        write(15, posy, puntos);
    }


    public String generarFacturaRollo( JSONObject contenido, String nombre_, String color)    throws IOException{
        int alto_cal = getHeight_RolloDPF(contenido);
        initOffSet(alto_cal);        
        setFontSize(8);
        
        String titulo_factura = "FACTURA";
        String sub_titulo_factura = "CON DERECHO A CRÉDITO FISCAL";
        
        setBold();
        write(getTextCenterX(titulo_factura), y, titulo_factura);
        y-=9;
        write(getTextCenterX(sub_titulo_factura), y, sub_titulo_factura);
        y-=9;
        
        contentStream.setLineWidth(1f);   
        contentStream.stroke();  
        contentStream.close();
        document.save(SConfig.getJSON("files").getString("url")+"pdf/"+nombre_);
        document.close();
        return nombre_;
    }

    public int getHeight_RolloDPF(JSONObject contenido){
        espacios = new int[8];
        espacios[0] = 20;
        espacios[1] = 90;
        espacios[2] = 150;  
        espacios[3] = 230;
        espacios[4] = 410;
        espacios[5] = 470;
        espacios[6] = 530;
        espacios[7] = 592;
        numero_literal= new NumeroLiteral();
        
//        JSONObject cabeza = contenido.getJSONObject("CABEZA");
//        JSONArray cuerpo = contenido.getJSONArray("CUERPO");
//        JSONObject casa = contenido.getJSONObject("CASA");


        float y = 0;
        y += 30;
        int codigo = 0;//codigo sucursal

        String nombre = "NOMBRERAZONSOCIAL";
        String cuf = "CUF";
        String leyenda = "LEYENDA";
        String estado = "ESTADO";
        String casa_descripcion = "N/A";
        String casa_telefono = "N/A";

        double monto_total = 0.0;
        double descuento = 0.0;
        double monto_subTotal = 0.0;
        double monto_IBCF = 0.0;
        double gifCard = 0.0;

     /*    if (!cabeza.isNull("MONTOTOTAL")) {
            monto_total = cabeza.getDouble("MONTOTOTAL");
        }
        if (!cabeza.isNull("DESCUENTOADICIONAL")) {
            descuento = cabeza.getDouble("DESCUENTOADICIONAL");
        }

        if (!cabeza.isNull("MONTOTOTALSUJETOIVA")) {
            monto_IBCF = cabeza.getDouble("MONTOTOTALSUJETOIVA");
        }
        if (!cabeza.isNull("MONTOGIFCARD")) {
            gifCard = cabeza.getDouble("MONTOGIFCARD");
        }

        if (!casa.isNull("DIRECCION")) {
            casa_descripcion = casa.getString("DIRECCION");
        }
        if (!casa.isNull("TELEFONO")) {
            casa_telefono = casa.getString("TELEFONO");
        }
*/
        int complemento = 0;

/*         if (!cabeza.isNull("COMPLEMENTO")) {
            complemento = cabeza.getInt("COMPLEMENTO");
        }
*/
        ArrayList<String> direccion = limitText("DIRECCION", 30);
        ArrayList<String> list_casa_direccion = limitText(casa_descripcion, 30);

        String titulo_factura = "FACTURA";
        String sub_titulo_factura = "CON DERECHO A CRÉDITO FISCAL";

        //titulo_factura
        y += 9;
        //sub_titulo_factura
        y += 9;
 
        if (codigo == 0) {
            //razon_social
            y += 10;
            //casa_matriz

            y += 8;
            for (int j = 0; j < direccion.size(); j++) {
                y += 8;
            }
        } else {
            //razon_social
            y += 10;
            //sucursal
            y += 8;
            //telefono
            y += 8;
            for (int j = 0; j < list_casa_direccion.size(); j++) {
                //direccion
                y += 8;
            }
            y += 7;
            //razon_social
            y += 10;
            //casa
            y += 8;
            for (int j = 0; j < direccion.size(); j++) {
                y += 8;
            }
            y += 8;
        }
        y += 8;
        y += 8;
        y += 16;

        y = y + 9;
        y = y + 18;
        y = y + 9;
        ArrayList<String> array_cuf = truncarText(cuf, 30);
        float pos_cuf = y;
        for (int j = 0; j < array_cuf.size(); j++) {
            pos_cuf += 9;
        }
        y = pos_cuf;
        y += 9;
        y += 17;

        ArrayList<String> list_NOMBRE = limitText(nombre, 15);

        float aux_pos_nombre = y;

        for (int j = 0; j < list_NOMBRE.size(); j++) {
            aux_pos_nombre += 8;
        }
        y = aux_pos_nombre + 2;
        y += 10;
        y += 10;
        y += 9;
        y += 17;
        y += 10;

        for (int i = 0; i < 1; i++) {
            
            double monto_descuento = 0.0;
            /*if (!cuerpo.getJSONObject(i).isNull("MONTODESCUENTO")) {
                monto_descuento = cuerpo.getJSONObject(i).getDouble("MONTODESCUENTO");
            }*/

            int codigo_producto = 1;
            int cantidad = 1;
            double precio_unitario = 100;
            double subtotal = 100;
            String descrip = "Productaso";
            String descrip_medida = "Metros";

            monto_subTotal += subtotal;

            ArrayList<String> list_descrip = limitText(codigo_producto + "-" + descrip, 35);

            float aux_pos = y;

            for (int j = 0; j < list_descrip.size(); j++) {
                aux_pos += 8;
            }
            y = aux_pos;
           
            y += 7;

        }
        y += 8;
        y += 10;

        y += 9;

        y += 9;
        y += 9;

        
        y += 9;

        
        y += 9;
        
        y += 9;
      

        y += 18;
        String literal_num = numero_literal.Convertir(myFormatter.format(monto_total), true);
        ArrayList<String> lista_literal = limitText("SON :" + literal_num, 60);
        float pos_num = y;
        for (int i = 0; i < lista_literal.size(); i++) {
            pos_num += 8;
        }
        y = pos_num;
        y += 17;

        String mensaje = "ESTA FACTURA CONTRIBUYE AL DESARROLLO DEL PAÍS, EL USO ILÍCITO SERÁ SANCIONADO PENALMENTE DE ACUERDO A LEY";
        ArrayList<String> lis_aux = limitText(mensaje, 40);
        float pos_aux = y;
        for (int i = 0; i < lis_aux.size(); i++) {
            pos_aux += 8;
        }
        y = pos_aux;
        y += 10;
        ArrayList<String> list_leyenda = limitText(leyenda, 40);
        for (int i = 0; i < list_leyenda.size(); i++) {
            y += 10;
        }

        String documento = "'Este Documento es la Representación Gráfica de un Documento Fiscal Digital emitido en una modalidad de facturacion en linea.'";
        String enlace = "www.impuestos.gob.bo";
        y += 2;
        if (estado.equals("enviado")) {
            documento = "Este documento es la Representación Gráfica de un Documento Fiscal Digital emitido fuera de linea, ";
            lis_aux = limitText(documento, 40);
            pos_aux = y;
            for (int i = 0; i < lis_aux.size(); i++) {
                pos_aux += 8;
            }
            y = pos_aux;
            y += 8;
            y += 8;

        } else {
            lis_aux = limitText(documento, 40);
            pos_aux = y;
            for (int i = 0; i < lis_aux.size(); i++) {
                pos_aux += 8;
            }
            y = pos_aux;
        }
        return Math.round(y+150);
    }
}
