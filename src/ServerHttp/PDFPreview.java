package ServerHttp;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PDFPreview {

    public static void createMiniature(File inputFile) throws Exception {

        String inputFilePath = inputFile.getPath();
        String outputFilePath = inputFile.getParent() + File.separator + "." + inputFile.getName() + ".png";

        try {
            PDDocument document = Loader.loadPDF(new File(inputFilePath));

            // Renderiza la primera página
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage image = pdfRenderer.renderImageWithDPI(0, 300, ImageType.RGB);

            // Guarda la imagen como un archivo PNG
            ImageIO.write(image, "png", new File(outputFilePath));

            // Cierra el documento PDF
            document.close();

            // Comprime
            ImageCompressor.compress(outputFilePath, false, true, 512);
            ImageCompressor.compress(outputFilePath, false, true, 128);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
