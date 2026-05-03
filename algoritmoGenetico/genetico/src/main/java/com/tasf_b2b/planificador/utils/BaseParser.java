package com.tasf_b2b.planificador.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

public class BaseParser {
    public int parsearEntero(String s) {
        String d = (s == null ? "" : s).replaceAll("[^0-9]", "");
        return d.isEmpty() ? 0 : Integer.parseInt(d);
    }

    public List<String> leerLineas(Path p) throws IOException {
        System.out.println("El sistema soporta codificaciones: UTF-8, UTF-16BE, UTF-16LE, UTF-16, Windows-1252 e ISO_8859_1");

        List<Charset> encodings = List.of(
            StandardCharsets.UTF_8,
            StandardCharsets.UTF_16BE,           // ← agregado: BE sin BOM
            StandardCharsets.UTF_16LE,           // ← agregado: LE sin BOM
            StandardCharsets.UTF_16,             // UTF-16 con BOM (detección automática)
            Charset.forName("Windows-1252"),     // ← agregado: común en Windows/español
            StandardCharsets.ISO_8859_1
        );

        for (Charset charset : encodings) {
            try {
                List<String> lineas = Files.readAllLines(p, charset);

                // Validar que las líneas no tengan caracteres nulos (síntoma de charset erróneo)
                boolean tieneNulos = lineas.stream()
                    .anyMatch(l -> l.contains("\u0000"));
                if (tieneNulos) continue;

                // ISO_8859_1 y Windows-1252 no lanzan excepción nunca,
                // se valida que no haya caracteres fuera de su rango esperado
                if (charset == StandardCharsets.ISO_8859_1 ||
                    charset.name().equalsIgnoreCase("Windows-1252")) {
                    boolean tieneCaracteresRaros = lineas.stream()
                        .anyMatch(l -> l.chars().anyMatch(c -> c > 0x00FF));
                    if (tieneCaracteresRaros) continue;
                }

                System.out.println("Encoding detectado: " + charset);
                return lineas;

            } catch (IOException ignored) {}
        }

        throw new IOException("Archivo con codificación no soportada: " + p.getFileName());
    }
}