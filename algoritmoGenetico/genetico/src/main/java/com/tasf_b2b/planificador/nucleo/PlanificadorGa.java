package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.*;
import com.tasf_b2b.planificador.utils.UtilArchivos;

import java.util.*;

public class PlanificadorGa {
    private GrafoVuelos grafo;
    private ParametrosGa params;
    private List<Envio> envios;
    private Random rnd = new Random();

    public PlanificadorGa(List<Vuelo> vuelos, List<Envio> envios, ParametrosGa params) {
        this.grafo = new GrafoVuelos(vuelos);
        this.envios = envios;
        this.params = params;
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
        Map<String, Integer> ocupacionAlmacenes = new HashMap<>();

        for (Map.Entry<Envio, Ruta> entrada : ind.asignaciones.entrySet()) {
            Envio e = entrada.getKey();
            Ruta r = entrada.getValue();

            // 1. Penalización Letal por no encontrar ruta
            if (r.vuelos == null || r.vuelos.isEmpty()) {
                fitnessTotal += 100000;
                continue;
            }

            // 2. Penalización por SLA
            if (!r.cumpleSLA) {
                fitnessTotal += (r.tiempoTotalHoras * params.penalidadSLA);
            }

            // 3 y 4. Validación de Capacidad de Vuelos y Almacenes
            for (Vuelo v : r.vuelos) {
                // Sumar al vuelo
                int cargaActualVuelo = cargaVuelos.getOrDefault(v.id, 0);
                if (cargaActualVuelo + e.cantidad > v.capacidad) {
                    fitnessTotal += 50000; // Penalización por sobrecarga de vuelo
                }
                cargaVuelos.put(v.id, cargaActualVuelo + e.cantidad);

                // Sumar al aeropuerto de destino de este vuelo (escala o destino final)
                int ocupacionActualAlmacen = ocupacionAlmacenes.getOrDefault(v.destino, 0);
                int nuevaOcupacion = ocupacionActualAlmacen + e.cantidad;
                
                // NOTA: Como límite asumimos 500 (el mínimo del alcance) para simplificar, 
                // o podrías cruzarlo con tu objeto Aeropuerto si lo pasas al Planificador.
                if (nuevaOcupacion > 500) { 
                    fitnessTotal += 50000; // Penalización Letal por colapso de almacén
                }
                ocupacionAlmacenes.put(v.destino, nuevaOcupacion);
            }
        }
        ind.fitness = fitnessTotal;
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
        Individuo hijo = new Individuo();
        int puntoCorte = rnd.nextInt(envios.size());
        int i = 0;
        for (Envio e : envios) {
            // CAMBIO AQUÍ: Usamos 'e' en el put y en los get
            hijo.asignaciones.put(e, (i < puntoCorte) ? 
                p1.asignaciones.get(e) : p2.asignaciones.get(e));
            i++;
        }
        return hijo;
    }

    private void mutar(Individuo ind) {
        // Elegimos un envío al azar y le buscamos una ruta nueva
        Envio e = envios.get(rnd.nextInt(envios.size()));
        List<Vuelo> nuevaRutaVuelos = grafo.buscarRutaAleatoria(e.origen, e.destino, e.horaIngresoMin, 3);
        if (nuevaRutaVuelos != null) {
            // CAMBIO AQUÍ: Pasamos 'e' directamente
            String contOrigen = UtilArchivos.obtenerContinente(e.origen);
            String contDestino = UtilArchivos.obtenerContinente(e.destino);
            int sla = contOrigen.equals(contDestino) ? 24 : 48;

            ind.asignaciones.put(e, new Ruta(nuevaRutaVuelos, e.horaIngresoMin, sla));
        }
    }

    private List<Individuo> inicializarPoblacion() {
        List<Individuo> lista = new ArrayList<>();
        for (int i = 0; i < params.tamanoPoblacion; i++) {
            Individuo ind = new Individuo();
            for (Envio e : envios) {
                List<Vuelo> vRuta = grafo.buscarRutaAleatoria(e.origen, e.destino, e.horaIngresoMin, 3);
                String contOrigen = UtilArchivos.obtenerContinente(e.origen);
                String contDestino = UtilArchivos.obtenerContinente(e.destino);
                int sla = contOrigen.equals(contDestino) ? 24 : 48;

                ind.asignaciones.put(e, new Ruta(vRuta, e.horaIngresoMin, sla));
            }
            lista.add(ind);
        }
        return lista;
    }
}
