package com.tasf_b2b.planificador.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BaseParser {
    private static final boolean LOG_ENCODING = false;

    public int parsearEntero(String s) {
        if (s == null) return 0;
        int value = 0;
        boolean found = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                value = (value * 10) + (c - '0');
                found = true;
            }
        }
        return found ? value : 0;
    }

    public List<String> leerLineas(Path p) throws IOException {
        List<String> lineas = new ArrayList<>();
        forEachLinea(p, (LineHandler) line -> {
            lineas.add(line);
            return true;
        });
        return lineas;
    }

    @FunctionalInterface
    public interface LineHandler {
        /**
         * @return true para continuar leyendo, false para detener.
         */
        boolean accept(String line);
    }

    public void forEachLinea(Path p, Consumer<String> consumer) throws IOException {
        forEachLinea(p, line -> {
            consumer.accept(line);
            return true;
        });
    }

    public void forEachLinea(Path p, LineHandler handler) throws IOException {
        List<Charset> encodings = List.of(
            StandardCharsets.UTF_8,
            StandardCharsets.UTF_16,
            StandardCharsets.ISO_8859_1
        );

        IOException lastError = null;
        for (Charset charset : encodings) {
            CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(p), decoder))) {
                if (LOG_ENCODING) {
                    System.out.println("Encoding detectado: " + charset);
                }
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!handler.accept(line)) {
                        return;
                    }
                }
                return;
            } catch (IOException e) {
                lastError = e;
            }
        }

        throw new IOException("Archivo con codificación no soportada: " + p.getFileName(), lastError);
    }
}