package com.tasf_b2b.planificador.utils;

import com.tasf_b2b.planificador.dominio.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UtilArchivos extends BaseParser {

    public Map<String, Aeropuerto> cargarAeropuertos(Path txt, Path csv) throws IOException {
        ParserAeropuertos parserAero = new ParserAeropuertos();

        if (Files.exists(csv) && Files.size(csv) > 0) {
            System.out.println("CSV de aeropuertos encontrado...");
        } else {
            parserAero.fromTXTtoCSV(txt, csv);
        }

        return parserAero.fromCSVToRuntimeObjects(csv);
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

    public List<Envio> cargarEnvios(Path p, Set<String> iatasValidas, Map<String, Aeropuerto> aeropuertos) throws IOException {
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
                
                Aeropuerto aeroOrigen = aeropuertos.get(origen);
                Aeropuerto aeroDestino = aeropuertos.get(destino);
                int sla = (aeroOrigen != null && aeroDestino != null)
                    ? aeroOrigen.calcularSla(aeroDestino)
                    : (obtenerContinente(origen).equals(obtenerContinente(destino)) ? 24 : 48);

                envios.add(new Envio(idPedido, origen, destino, fecha, hh, mm, cantidad, idCliente, sla, aeroOrigen));
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
