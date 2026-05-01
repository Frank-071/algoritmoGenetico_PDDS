package com.tasf_b2b.planificador.experimentos;

import com.tasf_b2b.planificador.dominio.Aeropuerto;
import com.tasf_b2b.planificador.dominio.Envio;
import com.tasf_b2b.planificador.dominio.Vuelo;
import com.tasf_b2b.planificador.nucleo.GrafoVuelos;
import com.tasf_b2b.planificador.nucleo.Individuo;
import com.tasf_b2b.planificador.nucleo.ParametrosGa;
import com.tasf_b2b.planificador.nucleo.PlanificadorGa;
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
 * Experimento numérico para el Algoritmo Genético (GA).
 * Ejecuta 30 réplicas con semilla incremental (1..30) y exporta
 * los resultados a data/resultados_ga.csv para análisis estadístico.
 *
 * Parámetros de experimentación (reducidos para reproducibilidad en laboratorio):
 *   - Población : 50 individuos
 *   - Generaciones: 50
 * Los parámetros de producción completos están en ParametrosGa.java
 */
public class experimentoGa {

    // ── Número de réplicas ────────────────────────────────────────────────────
    private static final int N_REPLICAS = 30;

    // ── Parámetros de experimentación (reducidos) ─────────────────────────────
    private static final int    TAM_POBLACION   = 100;
    private static final int    MAX_GENERACIONES = 200;
    private static final double TASA_CRUCE      = 0.85;
    private static final double TASA_MUTACION   = 0.05;
    private static final int    TAMANO_TORNEO   = 5;
    private static final double PENALIDAD_SLA   = 1000.0;

    public static void main(String[] args) {

        System.out.println("=== EXPERIMENTO NUMÉRICO — Algoritmo Genético (GA) ===");
        System.out.printf("Configuración: %d individuos x %d generaciones x %d réplicas%n",
                TAM_POBLACION, MAX_GENERACIONES, N_REPLICAS);
        System.out.println("─".repeat(70));

        // ── 1. Resolver rutas de archivos ─────────────────────────────────────
        String raiz = System.getProperty("user.dir");
        Path rutaAeropuertosTxt = resolverRuta(raiz, "aeropuertos.txt");
        Path rutaAeropuertosCsv = resolverRuta(raiz, "aeropuertos.csv");
        Path rutaVuelos         = resolverRuta(raiz, "planes_vuelo.txt");
        Path rutaEnvios         = resolverRuta(raiz, "_envios_EBCI_5000.txt");
        Path rutaSalida = Paths.get(raiz, "genetico", "data", "resultados_ga.csv");

        try {
            // ── 2. Cargar datos ───────────────────────────────────────────────
            UtilArchivos util = new UtilArchivos();
            Map<String, Aeropuerto> aeropuertos = util.cargarAeropuertos(rutaAeropuertosTxt, rutaAeropuertosCsv);
            List<Vuelo>  vuelos  = util.cargarVuelos(rutaVuelos, aeropuertos.keySet());
            List<Envio>  envios  = util.cargarEnvios(rutaEnvios, aeropuertos.keySet(), aeropuertos);
            GrafoVuelos  grafo   = new GrafoVuelos(vuelos, aeropuertos);

            System.out.printf("Datos cargados → Aeropuertos: %d | Vuelos: %d | Envíos: %d%n",
                    aeropuertos.size(), vuelos.size(), envios.size());

            if (envios.isEmpty() || vuelos.isEmpty()) {
                System.err.println("ERROR: No hay datos suficientes para experimentar.");
                return;
            }

            // ── 3. Preparar CSV de salida ─────────────────────────────────────
                try (PrintWriter csv = new PrintWriter(new FileWriter(rutaSalida.toFile()))) {

                    // Encabezado para el CSV (este se queda igual para Excel)
                    csv.println("Replica,Semilla,Funcion Objetivo,% Entregados con SLA,Violaciones SLA," +
                        "Violaciones Cap. Vuelo,Violaciones Cap. Almacen,Sin Ruta," +
                        "Tiempo Total (h),Hops Promedio,Tiempo Computo (s)");

                    // Encabezado de tabla para la Consola
                    String separadorTabla = "+------+---------+----------------+---------+-------+---------+-------+---------+-----------+----------+------------+";
                    System.out.println(separadorTabla);
                    System.out.printf("| %-4s | %-7s | %-14s | %-7s | %-5s | %-7s | %-5s | %-7s | %-9s | %-8s | %-10s |%n",
                            "Rep", "Semilla", "Fitness", "%SLA", "V.SLA", "V.Vuelo",
                            "V.Alm", "SinRuta", "T_total_h", "Esc.Prom", "T.Comp(ms)");
                    System.out.println(separadorTabla);

                // ── 4. Bucle de réplicas ──────────────────────────────────────
                for (int rep = 1; rep <= N_REPLICAS; rep++) {

                    // Configurar parámetros con semilla = número de réplica
                    ParametrosGa params = new ParametrosGa();
                    params.tamanoPoblacion  = TAM_POBLACION;
                    params.maxGeneraciones  = MAX_GENERACIONES;
                    params.tasaCruce        = TASA_CRUCE;
                    params.tasaMutacion     = TASA_MUTACION;
                    params.tamanoTorneo     = TAMANO_TORNEO;
                    params.penalidadSLA     = PENALIDAD_SLA;

                    PlanificadorGa planificador = new PlanificadorGa(grafo, envios, params);

                    long t0 = System.currentTimeMillis();
                    Individuo mejor = planificador.ejecutar();
                    long tiempoMs = System.currentTimeMillis() - t0;

                    // ── 5. Calcular métricas de la solución ───────────────────
                    ResultadoReplica r = calcularMetricas(mejor, envios);

                    // ── 6. Escribir fila CSV ──────────────────────────────────
                    csv.printf(Locale.US, "%d,%d,%.2f,%.2f,%d,%d,%d,%d,%.2f,%.2f,%d%n",
                            rep, rep,
                            mejor.fitness, r.pctSla,
                            r.violSla, r.violCapVuelo, r.violCapAlmacen,
                            r.sinRuta, r.tiempoTotalH, r.escalasProm, tiempoMs);

                    // ── 7. Imprimir en consola (Formato Tabla) ────────────────
                    System.out.printf(Locale.US, "| %-4d | %-7d | %-14.2f | %-7.2f | %-5d | %-7d | %-5d | %-7d | %-9.2f | %-8.2f | %-10d |%n",
                            rep, rep,
                            mejor.fitness, r.pctSla,
                            r.violSla, r.violCapVuelo, r.violCapAlmacen,
                            r.sinRuta, r.tiempoTotalH, r.escalasProm, tiempoMs);
                }

                System.out.println(separadorTabla);
                System.out.println("Resultados exportados → " + rutaSalida.toAbsolutePath());
            }

        } catch (Exception e) {
            System.err.println("Error durante el experimento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calcula todas las métricas secundarias a partir del Individuo solución
    // ─────────────────────────────────────────────────────────────────────────
    private static ResultadoReplica calcularMetricas(Individuo mejor, List<Envio> envios) {

        ResultadoReplica r = new ResultadoReplica();
        int totalEnvios = envios.size();

        // Contadores de carga acumulada por vuelo y almacén (igual que calcularFitness)
        java.util.Map<Integer, Integer> cargaVuelos     = new java.util.HashMap<>();
        java.util.Map<String,  Integer> ocupAlmacenes   = new java.util.HashMap<>();

        double tiempoAcum  = 0.0;
        int    escalasAcum = 0;
        int    conRuta     = 0;

        for (java.util.Map.Entry<Envio, Ruta> entry : mejor.asignaciones.entrySet()) {
            Envio envio = entry.getKey();
            Ruta  ruta  = entry.getValue();

            // Sin ruta asignada
            if (ruta == null || ruta.vuelos == null || ruta.vuelos.isEmpty()) {
                r.sinRuta++;
                continue;
            }

            // SLA
            if (!ruta.cumpleSLA) r.violSla++;

            // Tiempo y escalas
            tiempoAcum  += ruta.tiempoTotalHoras;
            escalasAcum += ruta.vuelos.size();
            conRuta++;

            // Capacidad vuelos y almacenes
            for (com.tasf_b2b.planificador.dominio.Vuelo v : ruta.vuelos) {
                int cargaActual = cargaVuelos.getOrDefault(v.id, 0) + envio.cantidad;
                cargaVuelos.put(v.id, cargaActual);
                if (cargaActual > v.capacidad) r.violCapVuelo++;

                int ocupActual = ocupAlmacenes.getOrDefault(v.destino, 0) + envio.cantidad;
                ocupAlmacenes.put(v.destino, ocupActual);
                if (ocupActual > 500) r.violCapAlmacen++;
            }
        }

        r.pctSla      = ((totalEnvios - r.violSla) * 100.0) / totalEnvios;
        r.tiempoTotalH = conRuta > 0 ? tiempoAcum  : 0.0;
        r.escalasProm  = conRuta > 0 ? (double) escalasAcum / conRuta : 0.0;

        return r;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Resolución de rutas (mismo patrón que Main.java)
    // ─────────────────────────────────────────────────────────────────────────
    private static Path resolverRuta(String raiz, String archivo) {
        Path directa = Paths.get(raiz, "data", archivo);
        if (Files.exists(directa)) return directa;
        Path conModulo = Paths.get(raiz, "genetico", "data", archivo);
        if (Files.exists(conModulo)) return conModulo;
        return directa;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO interno para las métricas de una réplica
    // ─────────────────────────────────────────────────────────────────────────
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
