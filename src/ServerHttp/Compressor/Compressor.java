package ServerHttp.Compressor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ServerHttp.ImageCompressor;
import ServerHttp.PDFPreview;
import Servisofts.SConsole;

public class Compressor {

    public static void compress(File file, String mimetype) {
        Compressor.compress(file, mimetype, false);
    }

    public static void compress(File file, String minetype, boolean force) {
        String mime;
        try {
            mime = getMimeType(file);
            if (mime == null) {
                mime = minetype;
            }
            SConsole.info("mime", mime);
            if (mime != null && mime.indexOf("video") > -1) {
                try {
                    VideoCompressor.createMiniature(file);
                    // VideoCompressor.convertMOVtoMP4(SConfig.getJSON("files").getString("url") +
                    // ruta + "/" + fileName, SConfig.getJSON("files").getString("url") + ruta + "/"
                    // + fileName+".mp4");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // // VideoConverter.convertToMp4(SConfig.getJSON("files").getString("url") +
                // ruta + "/" + fileName, SConfig.getJSON("files").getString("url") + ruta +
                // "/__" + fileName);
                return;
            }

            if (mime != null && mime.indexOf("application/pdf") > -1) {
                try {
                    PDFPreview.createMiniature(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            if (force == true || (mime != null && mime.indexOf("image") > -1)) {
                try {
                    ImageCompressor.compress(file.getPath(), false, true, 512);
                    ImageCompressor.compress(file.getPath(), false, true, 128);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    public static String getMimeType(java.io.File file) throws IOException {
        Path path = Paths.get(file.getAbsolutePath());
        return Files.probeContentType(path);
    }

}
