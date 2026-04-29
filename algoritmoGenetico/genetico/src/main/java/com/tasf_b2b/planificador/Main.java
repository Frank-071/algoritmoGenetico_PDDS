package com.tasf_b2b.planificador;

import com.tasf_b2b.planificador.dominio.*;
import com.tasf_b2b.planificador.nucleo.*;
import com.tasf_b2b.planificador.utils.UtilArchivos;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;

public class Main {
    private static Path resolverRutaData(String directorioRaiz, String nombreArchivo) {
        // Caso 1: ejecución desde el módulo `genetico`
        Path rutaDirecta = Paths.get(directorioRaiz, "data", nombreArchivo);
        if (Files.exists(rutaDirecta)) {
            return rutaDirecta;
        }

        // Caso 2: ejecución desde la raíz del repositorio
        Path rutaConModulo = Paths.get(directorioRaiz, "genetico", "data", nombreArchivo);
        if (Files.exists(rutaConModulo)) {
            return rutaConModulo;
        }

        // Fallback para que el error muestre una ruta coherente
        return rutaDirecta;
    }

    private static int resolverModoEnvios(String[] args) {
        if (args == null || args.length < 2) {
            return 0;
        }

        try {
            int modo = Integer.parseInt(args[1].trim());
            return (modo >= 0 && modo <= 2) ? modo : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void main(String[] args) {
        String algoritmo = (args != null && args.length > 0) ? args[0].trim().toLowerCase(Locale.ROOT) : "ga";
        int modoEnvios = resolverModoEnvios(args);
        System.out.println("=== Iniciando Planificador Tasf.B2B (" + algoritmo.toUpperCase(Locale.ROOT) + ") ===");
        System.out.println("Modo de carga de envíos: " + modoEnvios);

        // 1. Obtener la ruta dinámica donde se está ejecutando el proyecto
        String directorioRaiz = System.getProperty("user.dir");
        System.out.println("Directorio de trabajo actual: " + directorioRaiz);

        // 2. Construir las rutas relativas de forma segura usando Paths.get()
        Path rutaAeropuertos1 = resolverRutaData(directorioRaiz, "aeropuertos.txt");
        Path rutaAeropuertos2 = resolverRutaData(directorioRaiz, "aeropuertos.csv");
        Path rutaVuelos = resolverRutaData(directorioRaiz, "planes_vuelo.txt");
        Path rutaEnvios = resolverRutaData(directorioRaiz, "_envios_EBCI_.txt");
        Path rutaEnviosPreliminares = Paths.get(directorioRaiz, "data", "_envios_preliminar_");
        if (!Files.exists(rutaEnviosPreliminares)) {
            rutaEnviosPreliminares = Paths.get(directorioRaiz, "genetico", "data", "_envios_preliminar_");
        }

        try {
            // 2. Cargar los datos usando Utils
            System.out.println("Cargando datos...");
            long inicioCarga = System.currentTimeMillis();
            UtilArchivos uArch = new UtilArchivos();
            Map<String, Aeropuerto> mapaAeropuertos = uArch.cargarAeropuertos(rutaAeropuertos1, rutaAeropuertos2);
            Set<String> iatasValidas = mapaAeropuertos.keySet();

            List<Vuelo> vuelos = uArch.cargarVuelos(rutaVuelos, iatasValidas);
            List<Envio> envios = uArch.cargarEnvios(rutaEnvios, rutaEnviosPreliminares, iatasValidas, mapaAeropuertos, modoEnvios);
            if (args.length > 2) {
                try {
                    int limite = Integer.parseInt(args[2].trim());
                    if (limite > 0 && limite < envios.size()) {
                        envios = envios.subList(0, limite);
                    }
                } catch (NumberFormatException ignored) {}
            }
            
            long finCarga = System.currentTimeMillis();

            System.out.println("- Aeropuertos cargados: " + mapaAeropuertos.size());
            System.out.println("- Vuelos cargados: " + vuelos.size());
            System.out.println("- Envíos cargados: " + envios.size());
            System.out.println("- Tiempo de carga: " + (finCarga - inicioCarga) + " ms");

            if (envios.isEmpty() || vuelos.isEmpty()) {
                System.out.println("No hay suficientes datos para planificar.");
                return;
            }

            GrafoVuelos grafo = new GrafoVuelos(vuelos, mapaAeropuertos);

            Individuo mejorSolucion;
            long inicioEjecucion = System.currentTimeMillis();
            if ("aco".equals(algoritmo)) {
                ParametrosAco parametrosAco = new ParametrosAco();
                if (envios.size() >= 1_000_000) {
                    parametrosAco.numeroHormigas = 10;
                    parametrosAco.maxIteraciones = 20;
                    parametrosAco.logIteraciones = false;
                    parametrosAco.maxTiempoMs = 90L * 60L * 1000L;
                } else if (envios.size() >= 300_000) {
                    parametrosAco.numeroHormigas = 20;
                    parametrosAco.maxIteraciones = 40;
                    parametrosAco.logCada = 5;
                    parametrosAco.maxTiempoMs = 20L * 60L * 1000L;
                } else {
                    parametrosAco.numeroHormigas = 50;
                    parametrosAco.maxIteraciones = 100;
                    parametrosAco.logCada = 10;
                }

                System.out.println("\nIniciando optimizacion con Ant Colony Optimization...");
                PlanificadorAco planificadorAco = new PlanificadorAco(grafo, vuelos, envios, parametrosAco);
                mejorSolucion = planificadorAco.ejecutar();
            } else {
                // 3. Configurar los parámetros del Genético
                ParametrosGa parametros = new ParametrosGa();
                if (envios.size() >= 1_000_000) {
                    parametros.tamanoPoblacion = 4;
                    parametros.maxGeneraciones = 5;
                    parametros.tamanoTorneo = 2;
                    parametros.tasaMutacion = 0.02;
                    parametros.logGeneraciones = false;
                    parametros.maxSinMejora = 2;
                } else if (envios.size() >= 300_000) {
                    parametros.tamanoPoblacion = 10;
                    parametros.maxGeneraciones = 20;
                    parametros.tamanoTorneo = 3;
                    parametros.logCada = 5;
                    parametros.maxSinMejora = 8;
                } else if (envios.size() >= 50_000) {
                    parametros.tamanoPoblacion = 20;
                    parametros.maxGeneraciones = 40;
                    parametros.logCada = 5;
                } else {
                    parametros.tamanoPoblacion = 50;
                    parametros.maxGeneraciones = 100;
                    parametros.logCada = 10;
                }

                // 4. Iniciar el motor evolutivo
                System.out.println("\nIniciando evolución del Algoritmo Genético...");
                PlanificadorGa planificador = new PlanificadorGa(grafo, envios, parametros, 42);
                mejorSolucion = planificador.ejecutar();
            }
            long finEjecucion = System.currentTimeMillis();
            long tiempoEjecucionMs = finEjecucion - inicioEjecucion;

            // 5. Mostrar los resultados
            System.out.println("\n=== RESULTADOS DE LA PLANIFICACIÓN ===");
            System.out.println("Tiempo total de ejecución: " + tiempoEjecucionMs + " ms (" + String.format(Locale.ROOT, "%.2f", tiempoEjecucionMs / 1000.0) + " s)");
            System.out.println("Mejor Fitness alcanzado: " + mejorSolucion.fitness);
            
            int enviosExitosos = 0;
            int enviosConRetraso = 0;
            int enviosSinRuta = 0;
            int totalVuelos = 0;
            int maxVuelos = 0;
            double sumaTiempo = 0.0;

            boolean imprimirDetalle = modoEnvios == 0 && envios.size() <= 1000;
            for (int i = 0; i < envios.size(); i++) {
                Envio envio = envios.get(i);
                Ruta ruta = mejorSolucion.asignaciones[i];

                // Usamos la clase Asignacion para empaquetar el resultado
                Asignacion asignacion = new Asignacion(envio, ruta);

                if (asignacion.estado.equals("ENTREGADO") || asignacion.estado.equals("CON_RETRASO")) {
                    enviosExitosos++;
                    sumaTiempo += ruta.tiempoTotalHoras;
                    if (asignacion.estado.equals("CON_RETRASO")) {
                        enviosConRetraso++;
                    }
                    if (ruta.vuelos != null) {
                        totalVuelos += ruta.vuelos.size();
                        maxVuelos = Math.max(maxVuelos, ruta.vuelos.size());
                    }
                } else {
                    enviosSinRuta++;
                }

                if (imprimirDetalle) {
                    System.out.print("Pedido: " + envio.idPedido + " | Origen: " + envio.origen + " -> Destino: " + envio.destino);
                    if (asignacion.estado.equals("ENTREGADO") || asignacion.estado.equals("CON_RETRASO")) {
                        System.out.print(" | Estado: " + asignacion.estado + " | T. Vuelo: " + String.format("%.2f", ruta.tiempoTotalHoras) + "h");
                        System.out.println(" | Vuelos tomados: " + ruta.vuelos.size());
                    } else {
                        System.out.println(" | Estado: SIN_RUTA_ENCONTRADA");
                    }
                }
            }

            double porcentajeExito = envios.isEmpty() ? 0.0 : (enviosExitosos * 100.0 / envios.size());
            double tiempoPromedio = enviosExitosos == 0 ? 0.0 : (sumaTiempo / enviosExitosos);
            double vuelosPromedio = enviosExitosos == 0 ? 0.0 : (totalVuelos * 1.0 / enviosExitosos);

            System.out.println("\nResumen: Se encontraron rutas para " + enviosExitosos + " de " + envios.size() + " envíos (" + String.format(Locale.ROOT, "%.2f", porcentajeExito) + "%).");
            System.out.println("- Con retraso: " + enviosConRetraso + " | Sin ruta: " + enviosSinRuta);
            System.out.println("- Tiempo promedio por envío exitoso: " + String.format(Locale.ROOT, "%.2f", tiempoPromedio) + " h");
            System.out.println("- Vuelos promedio por envío exitoso: " + String.format(Locale.ROOT, "%.2f", vuelosPromedio) + " | Max vuelos: " + maxVuelos);

        } catch (Exception e) {
            System.err.println("Error durante la ejecución: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
