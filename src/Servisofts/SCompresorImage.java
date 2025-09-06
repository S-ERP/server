package Servisofts;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class SCompresorImage {
    

    public static void compressImage(File inputFile, File outputFile, float quality) throws IOException {
        // Leer la imagen
        BufferedImage image = ImageIO.read(inputFile);
        if (image == null) {
            throw new IOException("No se pudo leer la imagen del archivo: " + inputFile);
        }

        // Obtener el formato de la imagen
        String formatName = getFormatName(inputFile);
        if (formatName == null) {
            throw new IOException("No se pudo determinar el formato de la imagen: " + inputFile);
        }

        // Crear ImageTypeSpecifier
        ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image);

        // Obtener un ImageWriter para el formato de la imagen
        Iterator<ImageWriter> writers = ImageIO.getImageWriters(typeSpecifier, formatName);
        if (!writers.hasNext()) {
            throw new IllegalArgumentException("No se encontró un escritor para el formato de la imagen: " + formatName);
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        // Comprimir a la calidad especificada (si es posible)
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        } else {
            System.out.println("El formato " + formatName + " no soporta compresión.");

        }

        // Crear el stream de salida
        ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile);
        writer.setOutput(ios);

        // Escribir la imagen comprimida
        writer.write(null, new IIOImage(image, null, null), param);

        // Limpiar recursos
        ios.close();
        writer.dispose();

        // Imprimir información para depuración
        System.out.println("Imagen comprimida: " + inputFile.getName() + " -> " + outputFile.getName());
        System.out.println("Tamaño original: " + inputFile.length() + " bytes");
        System.out.println("Tamaño comprimido: " + outputFile.length() + " bytes");
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
            if (file.getName().toLowerCase().endsWith(format.toLowerCase())) {
                return format;
            }
        }
        return null; // Retorna null si no se puede determinar el formato
    }

    public static void compressImagesInDirectory(File directory, float quality) {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(directory + " no es un directorio.");
        }

        File[] files = directory.listFiles();
        if (files == null) {
            System.out.println("No se encontraron archivos en el directorio " + directory);
            return;
        }

        for (File file : files) {
            if (file.isFile()) {
                try {
                    String outputFileName = getOutputFileName(file);
                    File outputFile = new File(file.getParent(), outputFileName);
                    if(outputFile.exists()) {
                        continue;
                    }
                    compressImage(file, outputFile, quality);
                    System.out.println("Imagen comprimida: " + file.getName());
                } catch (IOException e) {
                    System.err.println("Error al comprimir la imagen " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private static String getOutputFileName(File inputFile) {
        String inputFileName = inputFile.getName();
        int lastDot = inputFileName.lastIndexOf('.');
        if (lastDot == -1) {
            return inputFileName + "_sm"; // Sin extensión
        } else {
            String name = inputFileName.substring(0, lastDot);
            String ext = inputFileName.substring(lastDot);
            return name + "_sm" + ext;
        }
    }

    public static void compressImagesInDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        float quality = 0.5f;

        compressImagesInDirectory(directory, quality);
    }

    public static void main(String[] args) {
        //compressImagesInDirectory("/home/servisofts/Documents/GitHub/drive/server/img/popup_inicio");

        // File inputFile = new File("/home/servisofts/Documents/GitHub/drive/server/img/popup_inicio/1a5eba4b-22d3-4b5b-8baa-5de97dcb77ee.png");
        // File outputFile = new File("/home/servisofts/Documents/GitHub/drive/server/img/popup_inicio/1a5eba4b-22d3-4b5b-8baa-5de97dcb77ee_sm.png");
        // long targetSizeKB = 600; // Tamaño objetivo en KB

        // try {
        //     compressImageToTargetSize(inputFile, outputFile, targetSizeKB);
        //     System.out.println("Imagen comprimida con éxito");
        // } catch (IOException e) {
        //     System.err.println("Error al comprimir la imagen: " + e.getMessage());
        // }
    }



      public static void compressImageToTargetSize(File inputFile, File outputFile, long targetSizeKB) throws IOException {
        BufferedImage image = ImageIO.read(inputFile);
        String formatName = getFormatName(inputFile);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);

        if (!writers.hasNext()) {
            throw new IllegalStateException("No writer found for format: " + formatName);
        }
        ImageWriter writer = writers.next();

        float quality = 1.0f;
        long targetSizeBytes = targetSizeKB * 1024;
        long imageSize = Long.MAX_VALUE;

        while (quality > 0 && imageSize > targetSizeBytes) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(byteArrayOutputStream)) {
                writer.setOutput(outputStream);

                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }

                writer.write(null, new IIOImage(image, null, null), param);
                imageSize = byteArrayOutputStream.size();
            }
            
            if (imageSize > targetSizeBytes) {
                quality -= 0.05f; // Reducir la calidad gradualmente
            } else {
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    byteArrayOutputStream.writeTo(fos);
                }
                break;
            }
        }
        writer.dispose();

        if (imageSize > targetSizeBytes) {
            throw new IOException("No se pudo alcanzar el tamaño objetivo con la calidad deseada.");
        }
    }
}
