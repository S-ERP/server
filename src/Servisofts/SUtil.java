package Servisofts;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public class SUtil {
    private static final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateFormat formatter2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static String formatTimestamp(Date date) {
        formatter.setTimeZone(TimeZone.getTimeZone("GMT-4"));
        return formatter.format(date);
    }

    public static String now() {
        formatter.setTimeZone(TimeZone.getTimeZone("GMT-4"));
        return formatter.format(new Date());
    }
    
    public static String nowMas(int minutes) {
        formatter.setTimeZone(TimeZone.getTimeZone("GMT-4"));
        return formatter.format(new Date(new Date().getTime() + (minutes) * 60000));
    }

    public static Date parseTimestamp(String timestamp) throws ParseException {
        timestamp = timestamp.trim();
         // Validar y añadir zona horaria predeterminada si no está presente
        if (!timestamp.endsWith("Z") && !timestamp.matches(".*[+-]\\d{2}:\\d{2}$")) {
            timestamp += "-04:00"; // Añadir zona horaria predeterminada
        }
        formatter.setTimeZone(TimeZone.getTimeZone("GMT-4"));
        formatter2.setTimeZone(TimeZone.getTimeZone("GMT-4"));
        if (timestamp.contains(".")) {
            return formatter.parse(timestamp);
        }
        return formatter2.parse(timestamp);
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }
    
    // ******* TEST PARSE FECHA **********
    // public static void main(String[] args) throws ParseException {
    //     String fecha1 = "2023-03-01T00:00:00";
    //     String fecha2 = "2023-03-01T00:00:00.000";
    //     String fecha3 = "2023-03-01T00:00:00.000000";
    //     System.out.println(SUtil.parseTimestamp(fecha1).toString());
    //     System.out.println(SUtil.parseTimestamp(fecha2).toString());
    //     System.out.println(SUtil.parseTimestamp(fecha3).toString());
    // }
}
