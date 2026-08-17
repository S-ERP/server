## Getting Started

Welcome to the VS Code Java world. Here is a guideline to help you get started to write Java code in Visual Studio Code.

## Folder Structure

The workspace contains two folders by default, where:

- `src`: the folder to maintain sources
- `lib`: the folder to maintain dependencies

Meanwhile, the compiled output files will be generated in the `bin` folder by default.

> If you want to customize the folder structure, open `.vscode/settings.json` and update the related settings there.

## Dependency Management Una letra

The `JAVA PROJECTS` view allows you to manage your dependencies. More details can be found [here](https://github.com/microsoft/vscode-java-dependency#manage-dependencies).



```java


import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class FileUploadServer {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/upload", new FileUploadHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server started on port 8080");
    }
}

class FileUploadHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            InputStream is = exchange.getRequestBody();
            OutputStream os = new FileOutputStream(new File("uploaded_file"));

            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesRead = 0;
            long fileSize = exchange.getRequestHeaders().getFirst("Content-Length") != null ? Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")) : -1;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (fileSize != -1) {
                    double progress = (double) totalBytesRead / fileSize * 100;
                    // Simula un callback o log del progreso de carga en el servidor.
                    System.out.printf("Progress: %.2f%%%n", progress);
                }
            }
            os.close();
            is.close();

            String response = "File uploaded successfully.";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream osResponse = exchange.getResponseBody();
            osResponse.write(response.getBytes(StandardCharsets.UTF_8));
            osResponse.close();
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
}



```


``` html
<!DOCTYPE html>
<html>
<head>
    <title>Upload with Progress</title>
</head>
<body>
    <input type="file" id="fileInput">
    <button id="uploadButton">Upload</button>
    <progress id="progressBar" value="0" max="100"></progress>

    <script>
        const progressBar = document.getElementById('progressBar');
        const uploadButton = document.getElementById('uploadButton');
        const fileInput = document.getElementById('fileInput');

        uploadButton.addEventListener('click', () => {
            const file = fileInput.files[0];
            if (file) {
                const uploadId = Date.now().toString();
                const formData = new FormData();
                formData.append('file', file);

                const xhr = new XMLHttpRequest();
                xhr.open('POST', 'http://localhost:8000/upload/path/to/save', true);
                xhr.setRequestHeader('Upload-ID', uploadId);

                xhr.upload.addEventListener('progress', (event) => {
                    if (event.lengthComputable) {
                        const progress = (event.loaded / event.total) * 100;
                        progressBar.value = progress;
                    }
                });

                xhr.onreadystatechange = () => {
                    if (xhr.readyState === 4 && xhr.status === 200) {
                        console.log('Upload complete');
                    }
                };

                xhr.send(formData);
            }
        });
    </script>
</body>
</html>


```







``` java

Entiendo, parece que necesitas ajustar la implementación para manejar las cabeceras personalizadas adecuadamente. Vamos a asegurarnos de que el servidor acepte las cabeceras personalizadas y las maneje correctamente.

Aquí está el código ajustado para manejar las cabeceras personalizadas:

Servidor HTTP con manejo de CORS y cabeceras personalizadas:
java
Copy code
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class FileUploadServer {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/upload", new FileUploadHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server started on port 8080");
    }
}


class FileUploadHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Configuración de CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Upload-ID, Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(200, 0);
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            InputStream is = exchange.getRequestBody();
            OutputStream os = new FileOutputStream(new File("uploaded_file"));

            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesRead = 0;
            long fileSize = exchange.getRequestHeaders().getFirst("Content-Length") != null ? Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length")) : -1;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (fileSize != -1) {
                    double progress = (double) totalBytesRead / fileSize * 100;
                    // Simula un callback o log del progreso de carga en el servidor.
                    System.out.printf("Progress: %.2f%%%n", progress);
                }
            }
            os.close();
            is.close();

            String response = "File uploaded successfully.";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream osResponse = exchange.getResponseBody();
            osResponse.write(response.getBytes(StandardCharsets.UTF_8));
            osResponse.close();
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
}
```