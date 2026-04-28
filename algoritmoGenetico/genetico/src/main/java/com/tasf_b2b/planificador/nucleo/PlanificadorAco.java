package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Aeropuerto;
import com.tasf_b2b.planificador.dominio.Envio;
import com.tasf_b2b.planificador.dominio.Vuelo;

import java.util.ArrayList;
import java.util.Arrays;
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
    private final int[] cargaVuelos;
    private final int[] cargaVuelosStamp;
    private final int[] ocupacionAlmacenes;
    private final int[] ocupacionAlmacenesStamp;
    private final int[] vueloDestinoIndex;
    private final int[] capacidadAlmacen;
    private int cargaStamp = 1;
    private int ocupacionStamp = 1;

    public PlanificadorAco(GrafoVuelos grafo, List<Vuelo> vuelos, List<Envio> envios, ParametrosAco params) {
        this.grafo = grafo;
        this.vuelos = vuelos;
        this.envios = envios;
        this.params = params;

        Map<String, Integer> aeropuertoIndex = new HashMap<>();
        int idx = 0;
        for (String codigo : grafo.obtenerAeropuertos().keySet()) {
            aeropuertoIndex.put(codigo, idx++);
        }

        this.capacidadAlmacen = new int[aeropuertoIndex.size()];
        for (Map.Entry<String, Integer> entry : aeropuertoIndex.entrySet()) {
            Aeropuerto a = grafo.obtenerAeropuertos().get(entry.getKey());
            capacidadAlmacen[entry.getValue()] = (a != null) ? a.capacidad : 500;
        }

        this.vueloDestinoIndex = new int[vuelos.size()];
        for (int i = 0; i < vuelos.size(); i++) {
            Vuelo v = vuelos.get(i);
            Integer aIdx = aeropuertoIndex.get(v.destino);
            vueloDestinoIndex[i] = (aIdx == null) ? -1 : aIdx;
        }

        this.cargaVuelos = new int[vuelos.size()];
        this.cargaVuelosStamp = new int[vuelos.size()];
        this.ocupacionAlmacenes = new int[aeropuertoIndex.size()];
        this.ocupacionAlmacenesStamp = new int[aeropuertoIndex.size()];

        for (Vuelo v : vuelos) {
            feromonaPorVuelo.put(v.id, 1.0);
        }
    }

    public Individuo ejecutar() {
        Individuo mejorGlobal = null;
        long inicio = System.currentTimeMillis();
        long deadline = params.maxTiempoMs > 0 ? (inicio + params.maxTiempoMs) : Long.MAX_VALUE;

        for (int iter = 0; iter < params.maxIteraciones; iter++) {
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            List<Individuo> colonia = new ArrayList<>();
            Individuo mejorIteracion = null;

            for (int i = 0; i < params.numeroHormigas; i++) {
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
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
            if (params.logIteraciones && (iter % params.logCada == 0 || iter == params.maxIteraciones - 1)) {
                System.out.println("Iteracion ACO " + iter + " - Mejor Fitness: " + mejorGlobal.fitness);
            }
        }

        return mejorGlobal;
    }

    private Individuo construirSolucion() {
        Individuo ind = new Individuo(envios.size());

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            List<Vuelo> rutaVuelos = construirRutaParaEnvio(e);
            ind.asignaciones[i] = new Ruta(rutaVuelos, e.horaIngresoMin, e.slaHoras);
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
            List<Vuelo> posibles = new ArrayList<>();
            for (Vuelo v : grafo.obtenerVuelosDesde(actual)) {
                if (v.salidaMin < tiempoMinimoSalida) continue;
                if (visitados.contains(v.destino)) continue;
                posibles.add(v);
            }

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
        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta ruta = ind.asignaciones[i];

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
        cargaStamp = nextStamp(cargaVuelosStamp, cargaStamp);
        ocupacionStamp = nextStamp(ocupacionAlmacenesStamp, ocupacionStamp);
        int cargaStampLocal = cargaStamp++;
        int ocupacionStampLocal = ocupacionStamp++;

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta r = ind.asignaciones[i];

            if (r == null || r.vuelos == null || r.vuelos.isEmpty()) {
                fitnessTotal += 100000;
                continue;
            }

            if (!r.cumpleSLA) {
                fitnessTotal += (r.tiempoTotalHoras * params.penalidadSLA);
            }

            for (Vuelo v : r.vuelos) {
                int cargaActualVuelo = getStampedValue(cargaVuelos, cargaVuelosStamp, v.id, cargaStampLocal);
                int nuevaCargaVuelo = cargaActualVuelo + e.cantidad;
                if (nuevaCargaVuelo > v.capacidad) {
                    fitnessTotal += 50000;
                }
                setStampedValue(cargaVuelos, cargaVuelosStamp, v.id, cargaStampLocal, nuevaCargaVuelo);

                int destinoIndex = vueloDestinoIndex[v.id];
                if (destinoIndex >= 0) {
                    int ocupacionActualAlmacen = getStampedValue(ocupacionAlmacenes, ocupacionAlmacenesStamp, destinoIndex, ocupacionStampLocal);
                    int nuevaOcupacion = ocupacionActualAlmacen + e.cantidad;
                    if (nuevaOcupacion > capacidadAlmacen[destinoIndex]) {
                        fitnessTotal += 50000;
                    }
                    setStampedValue(ocupacionAlmacenes, ocupacionAlmacenesStamp, destinoIndex, ocupacionStampLocal, nuevaOcupacion);
                }
            }
        }

        ind.fitness = fitnessTotal;
    }

    private int nextStamp(int[] stampArray, int currentStamp) {
        if (currentStamp == Integer.MAX_VALUE) {
            Arrays.fill(stampArray, 0);
            return 1;
        }
        return currentStamp;
    }

    private int getStampedValue(int[] values, int[] stamps, int index, int stamp) {
        if (stamps[index] != stamp) {
            stamps[index] = stamp;
            values[index] = 0;
        }
        return values[index];
    }

    private void setStampedValue(int[] values, int[] stamps, int index, int stamp, int value) {
        if (stamps[index] != stamp) {
            stamps[index] = stamp;
        }
        values[index] = value;
    }
}