package com.tasf_b2b.planificador.experimentos;

import com.tasf_b2b.planificador.dominio.Aeropuerto;
import com.tasf_b2b.planificador.dominio.Envio;
import com.tasf_b2b.planificador.dominio.Vuelo;
import com.tasf_b2b.planificador.nucleo.GrafoVuelos;
import com.tasf_b2b.planificador.nucleo.Individuo;
import com.tasf_b2b.planificador.nucleo.ParametrosAco;
import com.tasf_b2b.planificador.nucleo.PlanificadorAco;
import com.tasf_b2b.planificador.nucleo.Ruta;
import com.tasf_b2b.planificador.utils.UtilArchivos;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Experimento numerico para el Algoritmo ACO.
 * Ejecuta 30 replicas y exporta resultados a data/resultados_aco.csv.
 */
public class experimentoAco {

    private static final int N_REPLICAS = 30;

    private static final int    NUM_HORMIGAS    = 100;
    private static final int    MAX_ITERACIONES = 300;
    private static final double ALPHA          = 0.7;
    private static final double BETA           = 2.5;
    private static final double EVAPORACION    = 0.25;
    private static final double Q              = 1000.0;
    private static final int    MAX_ESCALAS    = 12;
    private static final double PENALIDAD_SLA  = 100.0;
    private static final long   MAX_TIEMPO_MS  = 0;

    public static void main(String[] args) {

        System.out.println("=== EXPERIMENTO NUMERICO - Algoritmo ACO ===");
        System.out.printf("Configuracion: %d hormigas x %d iteraciones x %d replicas%n",
                NUM_HORMIGAS, MAX_ITERACIONES, N_REPLICAS);
        System.out.println("-".repeat(70));

        String raiz = System.getProperty("user.dir");
        String archivoEnvios = (args != null && args.length > 0 && !args[0].isBlank())
            ? args[0].trim()
            : "_envios_EBCI_5000.txt";

        Path rutaAeropuertosTxt = resolverRuta(raiz, "aeropuertos.txt");
        Path rutaAeropuertosCsv = resolverRuta(raiz, "aeropuertos.csv");
        Path rutaVuelos         = resolverRuta(raiz, "planes_vuelo.txt");
        Path rutaEnvios         = resolverRuta(raiz, archivoEnvios);
        Path rutaSalida         = resolverRutaSalida(raiz, "resultados_aco.csv");

        try {
            UtilArchivos util = new UtilArchivos();
            Map<String, Aeropuerto> aeropuertos = util.cargarAeropuertos(rutaAeropuertosTxt, rutaAeropuertosCsv);
            List<Vuelo>  vuelos  = util.cargarVuelos(rutaVuelos, aeropuertos.keySet());
            List<Envio>  envios  = util.cargarEnvios(rutaEnvios, aeropuertos.keySet(), aeropuertos);
            GrafoVuelos  grafo   = new GrafoVuelos(vuelos, aeropuertos);

                System.out.printf("Datos cargados (%s) -> Aeropuertos: %d | Vuelos: %d | Envios: %d%n",
                    archivoEnvios,
                    aeropuertos.size(), vuelos.size(), envios.size());

            if (envios.isEmpty() || vuelos.isEmpty()) {
                System.err.println("ERROR: No hay datos suficientes para experimentar.");
                return;
            }

            Files.createDirectories(rutaSalida.getParent());
            try (PrintWriter csv = new PrintWriter(new FileWriter(rutaSalida.toFile()))) {

                csv.println("Replica,Semilla,Funcion Objetivo,% Entregados con SLA,Violaciones SLA," +
                        "Violaciones Cap. Vuelo,Violaciones Cap. Almacen,Sin Ruta," +
                        "Tiempo Total (h),Hops Promedio,Tiempo Computo (s)");

                String separadorTabla = "+------+---------+----------------+---------+-------+---------+-------+---------+-----------+----------+------------+";
                System.out.println(separadorTabla);
                System.out.printf("| %-4s | %-7s | %-14s | %-7s | %-5s | %-7s | %-5s | %-7s | %-9s | %-8s | %-10s |%n",
                        "Rep", "Semilla", "Fitness", "%SLA", "V.SLA", "V.Vuelo",
                        "V.Alm", "SinRuta", "T_total_h", "Esc.Prom", "T.Comp(ms)");
                System.out.println(separadorTabla);

                for (int rep = 1; rep <= N_REPLICAS; rep++) {
                    ParametrosAco params = new ParametrosAco();
                    params.numeroHormigas = NUM_HORMIGAS;
                    params.maxIteraciones = MAX_ITERACIONES;
                    params.alpha = ALPHA;
                    params.beta = BETA;
                    params.evaporacion = EVAPORACION;
                    params.q = Q;
                    params.maxEscalas = MAX_ESCALAS;
                    params.penalidadSLA = PENALIDAD_SLA;
                    params.maxTiempoMs = MAX_TIEMPO_MS;
                    params.logIteraciones = false;

                    PlanificadorAco planificador = new PlanificadorAco(grafo, vuelos, envios, params);

                    long t0 = System.currentTimeMillis();
                    Individuo mejor = planificador.ejecutar();
                    long tiempoMs = System.currentTimeMillis() - t0;

                    ResultadoReplica r = calcularMetricas(mejor, envios);

                    csv.printf(Locale.US, "%d,%d,%.2f,%.2f,%d,%d,%d,%d,%.2f,%.2f,%d%n",
                            rep, rep,
                            mejor.fitness, r.pctSla,
                            r.violSla, r.violCapVuelo, r.violCapAlmacen,
                            r.sinRuta, r.tiempoTotalH, r.escalasProm, tiempoMs);

                    System.out.printf(Locale.US, "| %-4d | %-7d | %-14.2f | %-7.2f | %-5d | %-7d | %-5d | %-7d | %-9.2f | %-8.2f | %-10d |%n",
                            rep, rep,
                            mejor.fitness, r.pctSla,
                            r.violSla, r.violCapVuelo, r.violCapAlmacen,
                            r.sinRuta, r.tiempoTotalH, r.escalasProm, tiempoMs);
                }

                System.out.println(separadorTabla);
                System.out.println("Resultados exportados -> " + rutaSalida.toAbsolutePath());
            }

        } catch (Exception e) {
            System.err.println("Error durante el experimento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static ResultadoReplica calcularMetricas(Individuo mejor, List<Envio> envios) {

        ResultadoReplica r = new ResultadoReplica();
        int totalEnvios = envios.size();

        java.util.Map<Integer, Integer> cargaVuelos     = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<int[]>> eventosAlmacen = new java.util.HashMap<>();

        double tiempoAcum  = 0.0;
        int    escalasAcum = 0;
        int    conRuta     = 0;

        for (int i = 0; i < envios.size(); i++) {
            Envio envio = envios.get(i);
            Ruta  ruta  = mejor.asignaciones[i];

            if (ruta == null || ruta.vuelos == null || ruta.vuelos.isEmpty()) {
                r.sinRuta++;
                continue;
            }

            if (!ruta.cumpleSLA) r.violSla++;

            tiempoAcum  += ruta.tiempoTotalHoras;
            escalasAcum += ruta.vuelos.size();
            conRuta++;

            for (int j = 0; j < ruta.vuelos.size(); j++) {
                Vuelo v = ruta.vuelos.get(j);
                int cargaActual = cargaVuelos.getOrDefault(v.id, 0) + envio.cantidad;
                cargaVuelos.put(v.id, cargaActual);
                if (cargaActual > v.capacidad) r.violCapVuelo++;

                int llegada = v.llegadaMin;
                int duracion;
                if (j == ruta.vuelos.size() - 1) {
                    duracion = 10;
                } else {
                    Vuelo siguiente = ruta.vuelos.get(j + 1);
                    duracion = siguiente.salidaMin - v.llegadaMin;
                    if (duracion < 10) duracion = 10;
                }

                if (duracion > 0) {
                    java.util.List<int[]> evs = eventosAlmacen.computeIfAbsent(v.destino, k -> new java.util.ArrayList<>());
                    evs.add(new int[] {llegada, envio.cantidad});
                    evs.add(new int[] {llegada + duracion, -envio.cantidad});
                }
            }
        }

        for (java.util.Map.Entry<String, java.util.List<int[]>> entry : eventosAlmacen.entrySet()) {
            java.util.List<int[]> evs = entry.getValue();
            evs.sort(java.util.Comparator.comparingInt(a -> a[0]));
            int ocup = 0;
            for (int[] ev : evs) {
                ocup += ev[1];
                if (ocup > 500) r.violCapAlmacen++;
            }
        }

        r.pctSla       = ((totalEnvios - r.violSla) * 100.0) / totalEnvios;
        r.tiempoTotalH = conRuta > 0 ? tiempoAcum : 0.0;
        r.escalasProm  = conRuta > 0 ? (double) escalasAcum / conRuta : 0.0;

        return r;
    }

    private static Path resolverRuta(String raiz, String archivo) {
        Path directa = Paths.get(raiz, "data", archivo);
        if (Files.exists(directa)) return directa;
        Path conModulo = Paths.get(raiz, "genetico", "data", archivo);
        if (Files.exists(conModulo)) return conModulo;
        return directa;
    }

    private static Path resolverRutaSalida(String raiz, String archivo) {
        Path directa = Paths.get(raiz, "data", archivo);
        if (Files.exists(directa.getParent())) return directa;
        Path conModulo = Paths.get(raiz, "genetico", "data", archivo);
        if (Files.exists(conModulo.getParent())) return conModulo;
        return directa;
    }

    private static class ResultadoReplica {
        double pctSla       = 0.0;
        int    violSla      = 0;
        int    violCapVuelo = 0;
        int    violCapAlmacen = 0;
        int    sinRuta      = 0;
        double tiempoTotalH = 0.0;
        double escalasProm  = 0.0;
    }
}
