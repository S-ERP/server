package ServerHttp.Compressor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZIP {
    public static void zipFolder(Path sourceFolderPath, Path zipPath) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            Files.walk(sourceFolderPath)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(sourceFolderPath.relativize(path).toString().replace("\\", "/"));
                    try {
                        zipOutputStream.putNextEntry(zipEntry);
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (IOException e) {
                        System.err.println("No se pudo comprimir el archivo: " + path);
                        e.printStackTrace();
                    }
                });
        }
    }

    public static void zipFile(Path sourceFilePath, Path zipPath) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            ZipEntry zipEntry = new ZipEntry(sourceFilePath.getFileName().toString());
            // try {
                zipOutputStream.putNextEntry(zipEntry);
                Files.copy(sourceFilePath, zipOutputStream);
                zipOutputStream.closeEntry();
            // } catch (IOException e) {
            //     System.err.println("No se pudo comprimir el archivo: " + sourceFilePath);
            //     e.printStackTrace();
            // }
        }
    }
}
