package com.tasf_b2b.planificador.utils;

import com.tasf_b2b.planificador.dominio.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UtilArchivos extends BaseParser {

    public Map<String, Aeropuerto> cargarAeropuertos(Path p) throws IOException {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (String linea : leerLineas(p)) {
            String t = linea.trim();

            if (!t.matches("^[0-9]{1,2}\\s+.*")) continue;
            
            String[] f = t.split("\\s+");
            if (f.length < 5) continue;

            String oaci = f[1]; 

            int idxLat = -1;
            for (int i = 0; i < f.length; i++) {
                if (f[i].startsWith("Lat")) {
                    idxLat = i;
                    break;
                }
            }

            if (idxLat >= 2) {
                try {
                    int gmt = Integer.parseInt(f[idxLat - 2].replace("+", ""));
                    int cap = parsearEntero(f[idxLat - 1]);
                    mapa.put(oaci, new Aeropuerto(oaci, "Ciudad", "Pais", gmt, cap));
                } catch (Exception e) {}
            }
        }
        return mapa;
    }

    public List<Vuelo> cargarVuelos(Path p, Set<String> iatasValidas) throws IOException {
        List<Vuelo> vuelos = new ArrayList<>();
        int id = 0;
        for (String linea : leerLineas(p)) {
            String t = linea.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;

            String[] f = t.split("-");
            if (f.length < 5) continue;

            String o = f[0].trim();
            String d = f[1].trim();

            if (!f[2].contains(":") || !f[3].contains(":")) continue;

            if (iatasValidas != null && (!iatasValidas.contains(o) || !iatasValidas.contains(d))) continue;

            try {
                String[] sal = f[2].split(":");
                String[] lle = f[3].split(":");
                
                int salidaMin = (Integer.parseInt(sal[0]) * 60) + Integer.parseInt(sal[1]);
                int llegadaMin = (Integer.parseInt(lle[0]) * 60) + Integer.parseInt(lle[1]);
                int cap = parsearEntero(f[4]);

                vuelos.add(new Vuelo(id++, o, d, salidaMin, llegadaMin, cap));
            } catch (Exception e) {}
        }
        return vuelos;
    }

    public List<Envio> cargarEnvios(Path p, Set<String> iatasValidas) throws IOException {
        List<Envio> envios = new ArrayList<>();
        if (p == null || !Files.exists(p)) return envios;

        String nombreArchivo = p.getFileName().toString();
        String origen = nombreArchivo.contains("_") ? nombreArchivo.split("_")[2] : "EBCI"; 

        for (String linea : leerLineas(p)) {
            String t = linea.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            
            String[] f = t.split("-");
            if (f.length < 7) continue;

            try {
                String idPedido = f[0].trim();
                String fecha = f[1].trim();
                int hh = parsearEntero(f[2]);
                int mm = parsearEntero(f[3]);
                String destino = f[4].trim();
                
                if (iatasValidas != null && !iatasValidas.contains(destino)) continue;
                
                int cantidad = parsearEntero(f[5]);
                String idCliente = f[6].trim();
                
                //if (envios.size() >= 50) break;

                envios.add(new Envio(idPedido, origen, destino, fecha, hh, mm, cantidad, idCliente));
            } catch (Exception e) {}
        }
        return envios;
    }

    public static String obtenerContinente(String codigoOACI) {
        char inicial = codigoOACI.toUpperCase().charAt(0);
        if (inicial == 'S' || inicial == 'K') return "AMERICA";
        if (inicial == 'E' || inicial == 'L') return "EUROPA";
        return "ASIA"; // Para O, V, U, etc.
    }
}
