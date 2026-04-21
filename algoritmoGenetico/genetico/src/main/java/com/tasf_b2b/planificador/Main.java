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

    public static void main(String[] args) {
        String algoritmo = (args != null && args.length > 0) ? args[0].trim().toLowerCase(Locale.ROOT) : "ga";
        System.out.println("=== Iniciando Planificador Tasf.B2B (" + algoritmo.toUpperCase(Locale.ROOT) + ") ===");

        // 1. Obtener la ruta dinámica donde se está ejecutando el proyecto
        String directorioRaiz = System.getProperty("user.dir");
        System.out.println("Directorio de trabajo actual: " + directorioRaiz);

        // 2. Construir las rutas relativas de forma segura usando Paths.get()
        // NOTA: Asegúrate de que tu carpeta se llame exactamente "data" (o cámbialo a "datos" aquí)
        Path rutaAeropuertos = resolverRutaData(directorioRaiz, "aeropuertos.txt");
        Path rutaVuelos = resolverRutaData(directorioRaiz, "planes_vuelo.txt");
        Path rutaEnvios = resolverRutaData(directorioRaiz, "_envios_EBCI_.txt");

        try {
            // 2. Cargar los datos usando tu Utils
            System.out.println("Cargando datos...");
            Map<String, Aeropuerto> mapaAeropuertos = UtilArchivos.cargarAeropuertos(rutaAeropuertos);
            Set<String> iatasValidas = mapaAeropuertos.keySet();

            List<Vuelo> vuelos = UtilArchivos.cargarVuelos(rutaVuelos, iatasValidas);
            List<Envio> envios = UtilArchivos.cargarEnvios(rutaEnvios, iatasValidas);

            System.out.println("✅ Aeropuertos cargados: " + mapaAeropuertos.size());
            System.out.println("✅ Vuelos cargados: " + vuelos.size());
            System.out.println("✅ Envíos cargados: " + envios.size());

            if (envios.isEmpty() || vuelos.isEmpty()) {
                System.out.println("⚠️ No hay suficientes datos para planificar.");
                return;
            }

            Individuo mejorSolucion;
            if ("aco".equals(algoritmo)) {
                ParametrosAco parametrosAco = new ParametrosAco();
                parametrosAco.numeroHormigas = 50;
                parametrosAco.maxIteraciones = 100;

                System.out.println("\nIniciando optimizacion con Ant Colony Optimization...");
                PlanificadorAco planificadorAco = new PlanificadorAco(vuelos, envios, parametrosAco);
                mejorSolucion = planificadorAco.ejecutar();
            } else {
                // 3. Configurar los parámetros del Genético
                ParametrosGa parametros = new ParametrosGa();
                // Para una prueba rápida, bajamos un poco las generaciones y población
                parametros.tamanoPoblacion = 50;
                parametros.maxGeneraciones = 100;

                // 4. Iniciar el motor evolutivo
                System.out.println("\nIniciando evolución del Algoritmo Genético...");
                PlanificadorGa planificador = new PlanificadorGa(vuelos, envios, parametros);
                mejorSolucion = planificador.ejecutar();
            }

            // 5. Mostrar los resultados
            System.out.println("\n=== RESULTADOS DE LA PLANIFICACIÓN ===");
            System.out.println("Mejor Fitness alcanzado: " + mejorSolucion.fitness);
            
            int enviosExitosos = 0;
            
            for (Map.Entry<Envio, Ruta> entry : mejorSolucion.asignaciones.entrySet()) {
                Envio envio = entry.getKey();
                Ruta ruta = entry.getValue();
                
                // Usamos la clase Asignacion para empaquetar el resultado
                Asignacion asignacion = new Asignacion(envio, ruta);
                
                System.out.print("Pedido: " + envio.idPedido + " | Origen: " + envio.origen + " -> Destino: " + envio.destino);
                
                if (asignacion.estado.equals("ENTREGADO") || asignacion.estado.equals("CON_RETRASO")) {
                    System.out.print(" | Estado: " + asignacion.estado + " | T. Vuelo: " + String.format("%.2f", ruta.tiempoTotalHoras) + "h");
                    System.out.println(" | Vuelos tomados: " + ruta.vuelos.size());
                    enviosExitosos++;
                } else {
                    System.out.println(" | Estado: SIN_RUTA_ENCONTRADA");
                }
            }

            System.out.println("\nResumen: Se encontraron rutas para " + enviosExitosos + " de " + envios.size() + " envíos.");

        } catch (Exception e) {
            System.err.println("❌ Error durante la ejecución: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
