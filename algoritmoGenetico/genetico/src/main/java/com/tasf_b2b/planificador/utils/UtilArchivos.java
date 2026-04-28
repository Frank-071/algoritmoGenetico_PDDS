package com.tasf_b2b.planificador.utils;

import com.tasf_b2b.planificador.dominio.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UtilArchivos extends BaseParser {

    public Map<String, Aeropuerto> cargarAeropuertos(Path txt, Path csv) throws IOException {
        ParserAeropuertos parserAero = new ParserAeropuertos();
        parserAero.fromTXTtoCSV(txt, csv);
        return parserAero.fromCSVToRuntimeObjects(csv);
    }

    public List<Vuelo> cargarVuelos(Path p, Set<String> iatasValidas) throws IOException {
        List<Vuelo> vuelos = new ArrayList<>();
        final int[] id = {0};
        forEachLinea(p, linea -> {
            String t = linea.trim();
            if (t.isEmpty() || t.startsWith("#")) return;

            String[] f = t.split("-");
            if (f.length < 5) return;

            String o = f[0].trim();
            String d = f[1].trim();

            if (!f[2].contains(":") || !f[3].contains(":")) return;

            if (iatasValidas != null && (!iatasValidas.contains(o) || !iatasValidas.contains(d))) return;

            try {
                String[] sal = f[2].split(":");
                String[] lle = f[3].split(":");

                int salidaMin = (Integer.parseInt(sal[0]) * 60) + Integer.parseInt(sal[1]);
                int llegadaMin = (Integer.parseInt(lle[0]) * 60) + Integer.parseInt(lle[1]);
                int cap = parsearEntero(f[4]);

                vuelos.add(new Vuelo(id[0]++, o, d, salidaMin, llegadaMin, cap));
            } catch (Exception ignored) {
            }
        });
        return vuelos;
    }

    public List<Envio> cargarEnvios(Path archivoPrincipal, Path directorioPreliminar, Set<String> iatasValidas, Map<String, Aeropuerto> aeropuertos, int modoCarga) throws IOException {
        List<Envio> envios = new ArrayList<>();

        if (modoCarga == 2) {
            cargarEnviosDesdeDirectorio(directorioPreliminar, iatasValidas, aeropuertos, envios);
            return envios;
        }

        if (archivoPrincipal == null || !Files.exists(archivoPrincipal)) {
            return envios;
        }

        String nombreArchivo = archivoPrincipal.getFileName().toString();
        String origen = nombreArchivo.contains("_") ? nombreArchivo.split("_")[2] : "EBCI";
        int limite = (modoCarga == 0) ? 100 : Integer.MAX_VALUE;

        forEachLinea(archivoPrincipal, linea -> {
            if (envios.size() >= limite) return false;
            String t = linea.trim();
            if (t.isEmpty() || t.startsWith("#")) return true;

            String[] f = t.split("-");
            if (f.length < 7) return true;

            try {
                String idPedido = f[0].trim();
                String fecha = f[1].trim();
                int hh = parsearEntero(f[2]);
                int mm = parsearEntero(f[3]);
                String destino = f[4].trim();

                if (iatasValidas != null && !iatasValidas.contains(destino)) return true;

                int cantidad = parsearEntero(f[5]);
                String idCliente = f[6].trim();

                Aeropuerto aeroOrigen = aeropuertos.get(origen);
                Aeropuerto aeroDestino = aeropuertos.get(destino);
                int sla = (aeroOrigen != null && aeroDestino != null)
                    ? aeroOrigen.calcularSla(aeroDestino)
                    : (obtenerContinente(origen).equals(obtenerContinente(destino)) ? 24 : 48);

                envios.add(new Envio(idPedido, origen, destino, fecha, hh, mm, cantidad, idCliente, sla, aeroOrigen));
            } catch (Exception ignored) {
            }
            return true;
        });
        return envios;
    }

    private void cargarEnviosDesdeDirectorio(Path directorioPreliminar, Set<String> iatasValidas, Map<String, Aeropuerto> aeropuertos, List<Envio> envios) throws IOException {
        if (directorioPreliminar == null || !Files.isDirectory(directorioPreliminar)) {
            return;
        }

        List<Path> archivos = new ArrayList<>();
        try (var stream = Files.list(directorioPreliminar)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                .sorted()
                .forEach(archivos::add);
        }

        for (Path archivo : archivos) {
            String nombreArchivo = archivo.getFileName().toString();
            String origen = nombreArchivo.contains("_") ? nombreArchivo.split("_")[2] : "EBCI";

            forEachLinea(archivo, linea -> {
                String t = linea.trim();
                if (t.isEmpty() || t.startsWith("#")) return;

                String[] f = t.split("-");
                if (f.length < 7) return;

                try {
                    String idPedido = f[0].trim();
                    String fecha = f[1].trim();
                    int hh = parsearEntero(f[2]);
                    int mm = parsearEntero(f[3]);
                    String destino = f[4].trim();

                    if (iatasValidas != null && !iatasValidas.contains(destino)) return;

                    int cantidad = parsearEntero(f[5]);
                    String idCliente = f[6].trim();

                    Aeropuerto aeroOrigen = aeropuertos.get(origen);
                    Aeropuerto aeroDestino = aeropuertos.get(destino);
                    int sla = (aeroOrigen != null && aeroDestino != null)
                        ? aeroOrigen.calcularSla(aeroDestino)
                        : (obtenerContinente(origen).equals(obtenerContinente(destino)) ? 24 : 48);

                    envios.add(new Envio(idPedido, origen, destino, fecha, hh, mm, cantidad, idCliente, sla, aeroOrigen));
                } catch (Exception ignored) {
                }
            });
        }
    }

    public static String obtenerContinente(String codigoOACI) {
        char inicial = codigoOACI.toUpperCase().charAt(0);
        if (inicial == 'S' || inicial == 'K') return "AMERICA";
        if (inicial == 'E' || inicial == 'L') return "EUROPA";
        return "ASIA"; // Para O, V, U, etc.
    }
}
