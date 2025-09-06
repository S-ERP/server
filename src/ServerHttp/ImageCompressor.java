package ServerHttp;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.tika.Tika;

import Servisofts.SConsole;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

public class ImageCompressor {

    // Magic numbers for different image formats
    private static final byte[] JPG_MAGIC = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC = new byte[] { (byte) 0x89, 'P', 'N', 'G' };
    private static final byte[] GIF_MAGIC = new byte[] { 'G', 'I', 'F' };
    private static final byte[] BMP_MAGIC = new byte[] { 'B', 'M' };
    private static final byte[] TIFF_MAGIC_LE = new byte[] { 'I', 'I', 0x2A, 0x00 }; // Little-endian
    private static final byte[] TIFF_MAGIC_BE = new byte[] { 'M', 'M', 0x00, 0x2A }; // Big-endian
    private static final byte[] WBMP_MAGIC = new byte[] { 0x00, 0x00, 0x00, 0x00 }; // WBMP (simplified check)

    public static String detectImageFormat(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] header = new byte[8];
        int bytesRead = fis.read(header);
        fis.close();

        if (bytesRead < 8) {
            return null; // Not enough data to determine format
        }

        if (startsWith(header, JPG_MAGIC)) {
            return "jpeg"; // JPEG can be jpg or jpeg
        } else if (startsWith(header, PNG_MAGIC)) {
            return "png";
        } else if (startsWith(header, GIF_MAGIC)) {
            return "gif";
        } else if (startsWith(header, BMP_MAGIC)) {
            return "bmp";
        } else if (startsWith(header, TIFF_MAGIC_LE) || startsWith(header, TIFF_MAGIC_BE)) {
            return "tiff"; // TIFF can be tiff or tif
        } else if (startsWith(header, WBMP_MAGIC)) {
            return "wbmp";
        }

        return null; // Unknown format
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private static String getFormatName(File file) throws IOException {
        // Leer la imagen
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("No se pudo leer la imagen del archivo: " + file);
        }

        // Identificar el formato
        String[] formatNames = ImageIO.getReaderFormatNames();
        for (String format : formatNames) {
            if (file.getName().toLowerCase().endsWith("." + format.toLowerCase())) {
                return format;
            }
        }
        return null; // Retorna null si no se puede determinar el formato
    }

    private static String getFormatFile(File file) throws IOException {
        String formatFile = detectImageFormat(file);
        if (formatFile == null) {
            return null;
        }

        // Identificar el formato
        String[] formatNames = ImageIO.getReaderFormatNames();
        for (String format : formatNames) {
            if (format.toLowerCase().equals(formatFile.toLowerCase())) {
                return format;
            }
        }
        return null; // Retorna null si no se puede determinar el formato
    }

    public static void resizeAndCompressImage(File inputFile, File outputFile, int maxWidth, int maxHeight,
            float quality) throws IOException {
        // Lee la imagen desde el archivo
        BufferedImage originalImage = ImageIO.read(inputFile);

        // Calcula las nuevas dimensiones manteniendo la relación de aspecto
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        float aspectRatio = (float) originalWidth / originalHeight;

        int newWidth = maxWidth;
        int newHeight = maxHeight;

        if (newWidth / aspectRatio <= maxHeight) {
            newHeight = (int) (newWidth / aspectRatio);
        } else {
            newWidth = (int) (newHeight * aspectRatio);
        }

        String formatName = getFormatName(inputFile);
        SConsole.info("getFormatName", formatName);
        if (formatName == null) {
            formatName = getFormatFile(inputFile);
            SConsole.info("getFormatFile", formatName);
        }

        // Crea una nueva imagen redimensionada
        BufferedImage resizedImage;
        // JPEG, BMP, WBMP
        switch (formatName.toLowerCase()) {
            case "jpeg":
            case "jpg":
            case "bmp":
            case "wbmp":
                resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                break;

            default:
                resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                break;
        }
        // BufferedImage resizedImage = new BufferedImage(newWidth, newHeight,
        // BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // Obtiene el escritor de imágenes para el formato JPEG

        // if(formatName == null) {
        // return null;
        // }

        ImageWriter writer = ImageIO.getImageWritersByFormatName(formatName).next();
        // ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();

        // Configura la salida del escritor
        ImageOutputStream ios = ImageIO.createImageOutputStream(new FileOutputStream(outputFile));
        writer.setOutput(ios);

        // Configura los parámetros de compresión
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        // param.setCompressionQuality(quality);

        // Escribe la imagen comprimida
        writer.write(null, new javax.imageio.IIOImage(resizedImage, null, null), param);

        // Limpia los recursos
        ios.close();
        writer.dispose();
    }

    /*
     * private static ImageWriter getWriter(File inputFile, BufferedImage image)
     * throws IOException {
     * String formatName = getFormatName(inputFile);
     * if(formatName == null) {
     * formatName = getFormatFile(inputFile);
     * }
     * 
     * if(formatName == null) {
     * return null;
     * }
     * 
     * ImageTypeSpecifier typeSpecifier =
     * ImageTypeSpecifier.createFromRenderedImage(image);
     * 
     * Iterator<ImageWriter> writers = ImageIO.getImageWriters(typeSpecifier,
     * formatName);
     * if (!writers.hasNext()) {
     * throw new
     * IllegalArgumentException("No se encontró un escritor para el formato de la imagen: "
     * + formatName);
     * }
     * 
     * }
     */

    private static final Tika tika = new Tika();

    public static boolean isImageFile(File file) {
        try {
            String mimeType = tika.detect(file);
            return mimeType.startsWith("image");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean recorrerDirectorio(String url) {
        File f = new File(url);
        if (!f.exists())
            return false;

        if (f.isFile()) {

            if (isImageFile(f)) {
                long totalSpaceInMB = f.length() / (1024);
                if (totalSpaceInMB > 700) {
                    System.out.println(url + " --> " + totalSpaceInMB + " kb");
                }
            }
            return true;
        }

        if (f.isDirectory()) {
            // System.out.println(url+": Directorio");
            for (int i = 0; i < f.list().length; i++) {
                recorrerDirectorio(url + "" + f.list()[i] + "/");
            }
            return true;
        }

        return true;
    }

    public static boolean compress(String url, boolean recursivo, boolean replaceCompressed, int size) {
        return compress(url, recursivo, replaceCompressed, false, size, null);
    }

    public static boolean compress(String url, boolean recursivo, boolean replaceCompressed, boolean skipHidden,
            int size, String[] skipFolders) {
        String prefijoCompressed = "." + size + "_";

        File f = new File(url);
        if (!f.exists())
            return false;

        if (skipHidden && f.getName().startsWith(".")) {
            SConsole.info("skipHidden", f.getPath());
            return false;
        }
       
        

        if (f.isFile()) {

            if (isImageFile(f)) {

                // si es una imagen comprimida
                if (isImageCompressed(f, prefijoCompressed)) {
                    SConsole.info("isImageCompressed", f.getPath());
                    return true;
                }

                // si tiene ya una imagen comprimida
                if (!replaceCompressed && hasCompressed(f, prefijoCompressed)) {
                    SConsole.info("hasCompressed", f.getPath());
                    return true;
                }

                long totalSpaceInMB = f.length() / (1024);
                // if(totalSpaceInMB > 700) {
                // System.out.println(url + " --> " + totalSpaceInMB + " kb");
                try {
                    File fOut = new File(f.getParent(), prefijoCompressed + f.getName());

                    resizeAndCompressImage(f, fOut, size, size, 0.8f);

                    long totalSpaceOutMB = fOut.length() / (1024);
                    fOut.setLastModified(f.lastModified());
                    System.out.println(fOut.getAbsolutePath() + " --> " + totalSpaceOutMB + " kb");
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // }
            }
            // La copio como es pero con un prefijo
            // prefijoCompressed
            File fOut = new File(f.getParent(), prefijoCompressed + f.getName());
            fOut.setLastModified(f.lastModified());
            try {
                Files.copy(f.toPath(), fOut.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                long totalSpaceOutMB = fOut.length() / (1024);
                System.out.println(fOut.getAbsolutePath() + " --> " + totalSpaceOutMB + " kb");
            } catch (IOException e) {
                e.printStackTrace();
            }

            return true;
        }

        if (f.isDirectory() && recursivo) {
            // System.out.println(url+": Directorio");
            if (skipFolders != null && skipFolders.length > 0) {
                for (String skipFolder : skipFolders) {
                    if (f.getName().toLowerCase() == skipFolder.toLowerCase()) {
                        SConsole.info("skipFolder", f.getPath());
                        return false;
                    }
                }
            }

            String[] listContent = f.list();
            for (int i = 0; i < listContent.length; i++) {
                compress(url + "/" + listContent[i], recursivo, replaceCompressed, skipHidden, size, skipFolders);
            }
            return true;
        }

        return true;
    }

    private static boolean isImageCompressed(File file, String prefijoCompressed) {
        String fileName = file.getName();
        if (!fileName.startsWith(prefijoCompressed)) {
            return false;
        }

        String fileNameOriginal = fileName.substring(prefijoCompressed.length(), fileName.length());
        File fileOriginal = new File(file.getParent(), fileNameOriginal);

        return fileOriginal.exists() && fileOriginal.isFile() && isImageFile(fileOriginal);
    }

    private static boolean hasCompressed(File file, String prefijoCompressed) {
        String fileName = file.getName();

        String fileNameCompressed = prefijoCompressed + fileName;
        File fileCompressed = new File(file.getParent(), fileNameCompressed);

        return fileCompressed.exists() && fileCompressed.isFile() && isImageFile(fileCompressed);
    }

    private boolean isImageCompressedSufijo(File file, String sufijoCompressed) {
        String fileName = file.getName();
        if (!fileName.endsWith(sufijoCompressed)) {
            return false;
        }

        String fileNameOriginal = fileName.substring(0, fileName.length() - sufijoCompressed.length());
        File fileOriginal = new File(file.getParent(), fileNameOriginal);

        return fileOriginal.exists() && fileOriginal.isFile() && isImageFile(fileOriginal);
    }

    private boolean hasCompressedSufijo(File file, String sufijoCompressed) {
        String fileName = file.getName();

        String fileNameCompressed = fileName + sufijoCompressed;
        File fileCompressed = new File(file.getParent(), fileNameCompressed);

        return fileCompressed.exists() && fileCompressed.isFile() && isImageFile(fileCompressed);
    }

    public static void main(String[] args) {
        String url = "/u01/servisoftsFiles/tapeke";
        // String url = "/u01/servisoftsFiles/tapeke_test/restaurant";
        String[] skipFolders = { "gpx" };
        SConsole.info("Comprimiendo a 128");
        ImageCompressor.compress(url, true, false, true, 128, skipFolders);
        SConsole.info("Comprimiendo a 512");
        ImageCompressor.compress(url, true, false, true, 512, skipFolders);

        // String[] formatNames = ImageIO.getReaderFormatNames();
        // for (String format : formatNames) {
        // System.out.println(format);
        // }

    }
}
