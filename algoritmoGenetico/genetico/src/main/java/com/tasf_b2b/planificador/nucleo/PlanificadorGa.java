package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.*;
import java.util.*;

public class PlanificadorGa {
    private GrafoVuelos grafo;
    private ParametrosGa params;
    private List<Envio> envios;
    private Random rnd = new Random(42);
    private final Map<String, Integer> capacidadPorAeropuerto;

    public PlanificadorGa(GrafoVuelos grafo, List<Envio> envios, ParametrosGa params, long semilla) {
        this.grafo = grafo;
        this.envios = envios;
        this.params = params;
        this.rnd    = new Random(semilla);
        this.capacidadPorAeropuerto = new HashMap<>();

        for (Map.Entry<String, Aeropuerto> entry : grafo.obtenerAeropuertos().entrySet()) {
            Aeropuerto a = entry.getValue();
            int cap = (a != null) ? a.capacidad : 500;
            capacidadPorAeropuerto.put(entry.getKey(), cap);
        }
    }

    public Individuo ejecutar() {
        // 1. Generar Población Inicial
        List<Individuo> poblacion = inicializarPoblacion();

        for (int gen = 0; gen < params.maxGeneraciones; gen++) {
            // 2. Evaluar Fitness
            poblacion.forEach(this::calcularFitness);
            Collections.sort(poblacion); // El mejor fitness (menor) va primero

            System.out.println("Generación " + gen + " - Mejor Fitness: " + poblacion.get(0).fitness);

            // 3. Crear nueva generación
            List<Individuo> nuevaPoblacion = new ArrayList<>();
            
            // Elitismo: Pasamos al mejor directo
            nuevaPoblacion.add(poblacion.get(0).clonar());

            while (nuevaPoblacion.size() < params.tamanoPoblacion) {
                // SELECCIÓN (Torneo)
                Individuo padre1 = seleccionarPadre(poblacion);
                Individuo padre2 = seleccionarPadre(poblacion);

                // CRUCE
                Individuo hijo = (rnd.nextDouble() < params.tasaCruce) ? 
                                 cruzar(padre1, padre2) : padre1.clonar();

                // MUTACIÓN
                if (rnd.nextDouble() < params.tasaMutacion) {
                    mutar(hijo);
                }
                nuevaPoblacion.add(hijo);
            }
            poblacion = nuevaPoblacion;
        }
        return poblacion.get(0);
    }

    private void calcularFitness(Individuo ind) {
        double fitnessTotal = 0;
        Map<Integer, Integer> cargaVuelos = new HashMap<>(); 

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta r = ind.asignaciones[i];

            // 1. Penalización Letal por no encontrar ruta
            if (r.vuelos == null || r.vuelos.isEmpty()) {
                fitnessTotal += params.penalidadSinRuta;
                continue;
            }

            // Costo base por tiempo total de la ruta
            fitnessTotal += (r.tiempoTotalHoras * params.pesoTiempo);

            // 2. Penalización por SLA
            if (!r.cumpleSLA) {
                fitnessTotal += (r.tiempoTotalHoras * params.penalidadSLA);
            }

            // 3 y 4. Validación de Capacidad de Vuelos y Almacenes
            for (Vuelo v : r.vuelos) {
                // Sumar al vuelo
                int cargaActualVuelo = cargaVuelos.getOrDefault(v.id, 0);
                if (cargaActualVuelo + e.cantidad > v.capacidad) {
                    fitnessTotal += params.penalidadCapVuelo;
                }
                cargaVuelos.put(v.id, cargaActualVuelo + e.cantidad);
            }
        }

        int violacionesAlmacen = contarViolacionesAlmacen(ind);
        fitnessTotal += violacionesAlmacen * params.penalidadCapAlmacen;

        ind.fitness = fitnessTotal;
    }

    private int contarViolacionesAlmacen(Individuo ind) {
        Map<String, List<int[]>> eventos = new HashMap<>();

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta r = ind.asignaciones[i];
            if (r == null || r.vuelos == null || r.vuelos.isEmpty()) continue;

            List<Vuelo> vuelosRuta = r.vuelos;
            for (int j = 0; j < vuelosRuta.size(); j++) {
                Vuelo v = vuelosRuta.get(j);
                int llegada = v.llegadaMin;
                int duracion;

                if (j == vuelosRuta.size() - 1) {
                    duracion = params.minRecojoMin;
                } else {
                    Vuelo siguiente = vuelosRuta.get(j + 1);
                    duracion = siguiente.salidaMin - v.llegadaMin;
                    if (duracion < params.minEscalaMin) duracion = params.minEscalaMin;
                }

                if (duracion <= 0) continue;
                List<int[]> evs = eventos.computeIfAbsent(v.destino, k -> new ArrayList<>());
                evs.add(new int[] {llegada, e.cantidad});
                evs.add(new int[] {llegada + duracion, -e.cantidad});
            }
        }

        int violaciones = 0;
        for (Map.Entry<String, List<int[]>> entry : eventos.entrySet()) {
            List<int[]> evs = entry.getValue();
            evs.sort(Comparator.comparingInt(a -> a[0]));

            int ocupacion = 0;
            int capacidad = capacidadPorAeropuerto.getOrDefault(entry.getKey(), 500);
            for (int[] ev : evs) {
                ocupacion += ev[1];
                if (ocupacion > capacidad) {
                    violaciones++;
                }
            }
        }

        return violaciones;
    }

    private Individuo seleccionarPadre(List<Individuo> poblacion) {
        Individuo mejor = null;
        for (int i = 0; i < params.tamanoTorneo; i++) {
            Individuo comp = poblacion.get(rnd.nextInt(poblacion.size()));
            if (mejor == null || comp.fitness < mejor.fitness) mejor = comp;
        }
        return mejor;
    }

    private Individuo cruzar(Individuo p1, Individuo p2) {
        Individuo hijo = new Individuo(envios.size());
        int puntoCorte = rnd.nextInt(envios.size());
        for (int i = 0; i < envios.size(); i++) {
            hijo.asignaciones[i] = (i < puntoCorte) ? p1.asignaciones[i] : p2.asignaciones[i];
        }
        return hijo;
    }

    private void mutar(Individuo ind) {
        // Elegimos un envío al azar y le buscamos una ruta nueva
        int idx = rnd.nextInt(envios.size());
        Envio e = envios.get(idx);
        List<Vuelo> nuevaRutaVuelos = grafo.buscarRutaAleatoria(
            e.origen,
            e.destino,
            e.horaIngresoMin,
            5,
            params.minEscalaMin
        );
        if (nuevaRutaVuelos != null) {
            ind.asignaciones[idx] = new Ruta(nuevaRutaVuelos, e.horaIngresoMin, e.slaHoras, params.minRecojoMin);
        }
    }

    private List<Individuo> inicializarPoblacion() {
        List<Individuo> lista = new ArrayList<>();
        for (int i = 0; i < params.tamanoPoblacion; i++) {
            Individuo ind = new Individuo(envios.size());
            for (int j = 0; j < envios.size(); j++) {
                Envio e = envios.get(j);
                List<Vuelo> vRuta = grafo.buscarRutaAleatoria(
                    e.origen,
                    e.destino,
                    e.horaIngresoMin,
                    4,
                    params.minEscalaMin
                );
                ind.asignaciones[j] = new Ruta(vRuta, e.horaIngresoMin, e.slaHoras, params.minRecojoMin);
            }
            lista.add(ind);
        }
        return lista;
    }
}
