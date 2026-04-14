package com.tasf_b2b.planificador.utils;

import com.tasf_b2b.planificador.dominio.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UtilArchivos {

    // Método seguro para convertir Strings a Enteros
    private static int parsearEntero(String s) {
        String d = (s == null ? "" : s).replaceAll("[^0-9]", "");
        return d.isEmpty() ? 0 : Integer.parseInt(d);
    }

    // 1. CARGAR AEROPUERTOS
    public static Map<String, Aeropuerto> cargarAeropuertos(Path p) throws IOException {
        Map<String, Aeropuerto> mapa = new HashMap<>();
        for (String linea : Files.readAllLines(p)) {
            // Saltamos la cabecera o líneas vacías
            if (linea.trim().isEmpty() || linea.startsWith("PDDS") || linea.startsWith("*") || linea.contains("GMT")) continue;
            
            // Separamos por 2 o más espacios (para no romper ciudades compuestas)
            String[] f = linea.trim().split("\\s{2,}"); 
            if (f.length < 5) continue;

            String oaci = f[1].trim();     // Ej. SKBO
            String ciudad = f[2].trim();   // Ej. Bogota
            String pais = f[3].trim();     // Ej. Colombia
            int gmt = Integer.parseInt(f[5].trim().replace("+", "")); // Ej. -5 o +2
            int cap = parsearEntero(f[6]); // Ej. 430

            mapa.put(oaci, new Aeropuerto(oaci, ciudad, pais, gmt, cap));
        }
        return mapa;
    }

    // 2. CARGAR VUELOS
    public static List<Vuelo> cargarVuelos(Path p, Set<String> iatasValidas) throws IOException {
        List<Vuelo> vuelos = new ArrayList<>();
        int id = 0;
        for (String linea : Files.readAllLines(p)) {
            if (linea.trim().isEmpty()) continue;
            
            // Tu archivo de vuelos usa guiones: SKBO-SEQM-03:34-04:21-0300
            String[] f = linea.trim().split("-");
            if (f.length < 5) continue;

            String o = f[0].trim();
            String d = f[1].trim();
            
            if (iatasValidas != null && (!iatasValidas.contains(o) || !iatasValidas.contains(d))) continue;

            // Extraemos horas y minutos usando split(":")
            String[] sal = f[2].split(":");
            String[] lle = f[3].split(":");
            
            int salidaMin = (Integer.parseInt(sal[0]) * 60) + Integer.parseInt(sal[1]);
            int llegadaMin = (Integer.parseInt(lle[0]) * 60) + Integer.parseInt(lle[1]);
            int cap = parsearEntero(f[4]);

            vuelos.add(new Vuelo(id++, o, d, salidaMin, llegadaMin, cap));
        }
        return vuelos;
    }

    // 3. CARGAR ENVÍOS (Reemplaza a cargarPedidos)
    public static List<Envio> cargarEnvios(Path p, Set<String> iatasValidas) throws IOException {
        List<Envio> envios = new ArrayList<>();
        if (p == null || !Files.exists(p)) return envios;

        // Extraer origen del nombre del archivo (ej. _envios_EBCI_.txt -> EBCI)
        String nombreArchivo = p.getFileName().toString();
        String origen = nombreArchivo.split("_")[2]; 

        for (String linea : Files.readAllLines(p)) {
            if (linea.trim().isEmpty()) continue;
            
            // Formato: 000000001-20260102-00-47-SUAA-002-0032535
            String[] f = linea.trim().split("-");
            if (f.length < 7) continue;

            String idPedido = f[0].trim();
            String fecha = f[1].trim();
            int hh = parsearEntero(f[2]);
            int mm = parsearEntero(f[3]);
            String destino = f[4].trim();
            
            if (iatasValidas != null && !iatasValidas.contains(destino)) continue;
            
            int cantidad = parsearEntero(f[5]);
            String idCliente = f[6].trim();

            envios.add(new Envio(idPedido, origen, destino, fecha, hh, mm, cantidad, idCliente));
        }
        return envios;
    }

    // Nota: Puedes conservar tus métodos escribirAsignacionesCSV o escribirPlanCsv tal como estaban.
}
