package ServerHttp.Compressor;

import java.io.File;
import java.io.IOException;

import ServerHttp.ImageCompressor;

public class VideoCompressor {

    public static void convertMOVtoMP4(String inputFile, String outputFile) throws Exception {
        // String inputFilePath = "input.mov";
        // String outputFilePath = "output.mp4";

        // Comando FFmpeg
        String[] command = {
                "ffmpeg",
                "-y", // Fuerza el reemplazo del archivo de salida si ya existe
                "-i", inputFile,
                "-vcodec", "h264",
                "-acodec", "aac",
                "-movflags", "+faststart",
                outputFile
        };

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.inheritIO(); // Redirige la salida del proceso a la consola

            Process process = processBuilder.start();
            int exitCode = process.waitFor(); // Espera a que el proceso termine

            if (exitCode == 0) {
                System.out.println("Conversión completada con éxito.");
            } else {
                System.err.println("Error en la conversión. Código de salida: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void createMiniature(File inputFile) throws Exception {
        // String inputFilePath = "input.mov";
        // String outputFilePath = "output.mp4";

        String inputFilePath = inputFile.getPath();
        String outputFilePath = inputFile.getParent() + File.separator + "." + inputFile.getName() + ".png";

        // Comando FFmpeg
        String[] command = {
                "ffmpeg",
                "-y", // Fuerza el reemplazo del archivo de salida si ya existe
                "-i", inputFilePath,
                "-ss", "00:00:01.000", // Extrae el frame en el segundo 1
                "-vframes", "1",
                "-q:v", "2", // Establece la calidad de la imagen
                outputFilePath
        };

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.inheritIO(); // Redirige la salida del proceso a la consola

            Process process = processBuilder.start();
            int exitCode = process.waitFor(); // Espera a que el proceso termine

            if (exitCode == 0) {
                System.out.println("Miniatura creada con éxito.");

                ImageCompressor.compress(outputFilePath, false, true, 512);
                ImageCompressor.compress(outputFilePath, false, true, 128);

            } else {
                System.err.println("Error al crear la miniatura. Código de salida: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
