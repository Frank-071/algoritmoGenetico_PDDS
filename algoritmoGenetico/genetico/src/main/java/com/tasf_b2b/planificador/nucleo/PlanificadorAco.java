package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Envio;
import com.tasf_b2b.planificador.dominio.Vuelo;
import com.tasf_b2b.planificador.utils.UtilArchivos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class PlanificadorAco {
    private final GrafoVuelos grafo;
    private final List<Vuelo> vuelos;
    private final List<Envio> envios;
    private final ParametrosAco params;
    private final Random rnd = new Random();
    private final Map<Integer, Double> feromonaPorVuelo = new HashMap<>();

    public PlanificadorAco(GrafoVuelos grafo, List<Vuelo> vuelos, List<Envio> envios, ParametrosAco params) {
        this.grafo = grafo;
        this.vuelos = vuelos;
        this.envios = envios;
        this.params = params;

        for (Vuelo v : vuelos) {
            feromonaPorVuelo.put(v.id, 1.0);
        }
    }

    public Individuo ejecutar() {
        Individuo mejorGlobal = null;

        for (int iter = 0; iter < params.maxIteraciones; iter++) {
            List<Individuo> colonia = new ArrayList<>();
            Individuo mejorIteracion = null;

            for (int i = 0; i < params.numeroHormigas; i++) {
                Individuo hormiga = construirSolucion();
                calcularFitness(hormiga);
                colonia.add(hormiga);

                if (mejorIteracion == null || hormiga.fitness < mejorIteracion.fitness) {
                    mejorIteracion = hormiga;
                }
            }

            if (mejorGlobal == null || mejorIteracion.fitness < mejorGlobal.fitness) {
                mejorGlobal = mejorIteracion.clonar();
            }

            actualizarFeromonas(mejorIteracion, mejorGlobal);
            System.out.println("Iteracion ACO " + iter + " - Mejor Fitness: " + mejorGlobal.fitness);
        }

        return mejorGlobal;
    }

    private Individuo construirSolucion() {
        Individuo ind = new Individuo();

        for (Envio e : envios) {
            List<Vuelo> rutaVuelos = construirRutaParaEnvio(e);
            String contOrigen = UtilArchivos.obtenerContinente(e.origen);
            String contDestino = UtilArchivos.obtenerContinente(e.destino);
            int sla = contOrigen.equals(contDestino) ? 24 : 48;
            ind.asignaciones.put(e, new Ruta(rutaVuelos, e.horaIngresoMin, sla));
        }

        return ind;
    }

    private List<Vuelo> construirRutaParaEnvio(Envio envio) {
        List<Vuelo> ruta = new ArrayList<>();
        Set<String> visitados = new HashSet<>();
        String actual = envio.origen;
        int tiempoActual = envio.horaIngresoMin;
        visitados.add(actual);

        for (int escala = 0; escala < params.maxEscalas; escala++) {
            final int tiempoMinimoSalida = tiempoActual + 60;
            List<Vuelo> posibles = grafo.obtenerVuelosDesde(actual).stream()
                .filter(v -> v.salidaMin >= tiempoMinimoSalida)
                .filter(v -> !visitados.contains(v.destino))
                .toList();

            if (posibles.isEmpty()) {
                return null;
            }

            Vuelo elegido = seleccionarVueloProbabilistico(posibles, envio.destino, tiempoActual);
            ruta.add(elegido);
            actual = elegido.destino;
            tiempoActual = elegido.llegadaMin;
            visitados.add(actual);

            if (actual.equals(envio.destino)) {
                return ruta;
            }
        }

        return null;
    }

    private Vuelo seleccionarVueloProbabilistico(List<Vuelo> candidatos, String destinoFinal, int tiempoActual) {
        List<Double> pesos = new ArrayList<>(candidatos.size());
        double suma = 0.0;

        for (Vuelo v : candidatos) {
            double tau = feromonaPorVuelo.getOrDefault(v.id, 1.0);
            double eta = calcularHeuristica(v, destinoFinal, tiempoActual);
            double peso = Math.pow(tau, params.alpha) * Math.pow(eta, params.beta);
            pesos.add(peso);
            suma += peso;
        }

        if (suma <= 0.0) {
            return candidatos.get(rnd.nextInt(candidatos.size()));
        }

        double umbral = rnd.nextDouble() * suma;
        double acumulado = 0.0;
        for (int i = 0; i < candidatos.size(); i++) {
            acumulado += pesos.get(i);
            if (acumulado >= umbral) {
                return candidatos.get(i);
            }
        }

        return candidatos.get(candidatos.size() - 1);
    }

    private double calcularHeuristica(Vuelo v, String destinoFinal, int tiempoActual) {
        int esperaMin = v.salidaMin - tiempoActual;
        if (esperaMin < 0) {
            esperaMin += 24 * 60;
        }

        int duracionMin = v.llegadaMin - v.salidaMin;
        if (duracionMin < 0) {
            duracionMin += 24 * 60;
        }

        double costo = esperaMin + duracionMin;
        if (v.destino.equals(destinoFinal)) {
            costo *= 0.35;
        }

        return 1.0 / (1.0 + costo);
    }

    private void actualizarFeromonas(Individuo mejorIteracion, Individuo mejorGlobal) {
        double factorEvaporacion = Math.max(0.0, 1.0 - params.evaporacion);
        for (Vuelo v : vuelos) {
            double tau = feromonaPorVuelo.getOrDefault(v.id, 1.0);
            feromonaPorVuelo.put(v.id, Math.max(0.0001, tau * factorEvaporacion));
        }

        depositarFeromona(mejorIteracion, params.q / (1.0 + mejorIteracion.fitness));
        depositarFeromona(mejorGlobal, (params.q * 1.5) / (1.0 + mejorGlobal.fitness));
    }

    private void depositarFeromona(Individuo ind, double deltaBase) {
        for (Map.Entry<Envio, Ruta> entrada : ind.asignaciones.entrySet()) {
            Envio e = entrada.getKey();
            Ruta ruta = entrada.getValue();

            if (ruta == null || ruta.vuelos == null || ruta.vuelos.isEmpty()) {
                continue;
            }

            double delta = deltaBase * Math.max(1.0, e.cantidad);
            for (Vuelo v : ruta.vuelos) {
                double actual = feromonaPorVuelo.getOrDefault(v.id, 1.0);
                feromonaPorVuelo.put(v.id, actual + delta);
            }
        }
    }

    private void calcularFitness(Individuo ind) {
        double fitnessTotal = 0;
        Map<Integer, Integer> cargaVuelos = new HashMap<>();
        Map<String, Integer> ocupacionAlmacenes = new HashMap<>();

        for (Map.Entry<Envio, Ruta> entrada : ind.asignaciones.entrySet()) {
            Envio e = entrada.getKey();
            Ruta r = entrada.getValue();

            if (r.vuelos == null || r.vuelos.isEmpty()) {
                fitnessTotal += 100000;
                continue;
            }

            if (!r.cumpleSLA) {
                fitnessTotal += (r.tiempoTotalHoras * params.penalidadSLA);
            }

            for (Vuelo v : r.vuelos) {
                int cargaActualVuelo = cargaVuelos.getOrDefault(v.id, 0);
                if (cargaActualVuelo + e.cantidad > v.capacidad) {
                    fitnessTotal += 50000;
                }
                cargaVuelos.put(v.id, cargaActualVuelo + e.cantidad);

                int ocupacionActualAlmacen = ocupacionAlmacenes.getOrDefault(v.destino, 0);
                int nuevaOcupacion = ocupacionActualAlmacen + e.cantidad;
                if (nuevaOcupacion > 500) {
                    fitnessTotal += 50000;
                }
                ocupacionAlmacenes.put(v.destino, nuevaOcupacion);
            }
        }

        ind.fitness = fitnessTotal;
    }
}