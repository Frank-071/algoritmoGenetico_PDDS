package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.*;

import java.util.*;

public class PlanificadorGa {
    private GrafoVuelos grafo;
    private ParametrosGa params;
    private List<Envio> envios;
    private Random rnd = new Random(42);
    private final int[] cargaVuelos;
    private final int[] cargaVuelosStamp;
    private final int[] ocupacionAlmacenes;
    private final int[] ocupacionAlmacenesStamp;
    private final int[] vueloDestinoIndex;
    private final int[] capacidadAlmacen;
    private int cargaStamp = 1;
    private int ocupacionStamp = 1;

    public PlanificadorGa(GrafoVuelos grafo, List<Envio> envios, ParametrosGa params) {
        this.grafo = grafo;
        this.envios = envios;
        this.params = params;

        List<Vuelo> vuelos = grafo.obtenerVuelos();
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
    }

    public Individuo ejecutar() {
        // 1. Generar Población Inicial
        List<Individuo> poblacion = inicializarPoblacion();
        double mejorFitness = Double.MAX_VALUE;
        int sinMejora = 0;

        for (int gen = 0; gen < params.maxGeneraciones; gen++) {
            // 2. Evaluar Fitness
            poblacion.forEach(this::calcularFitness);
            Collections.sort(poblacion); // El mejor fitness (menor) va primero

            if (poblacion.get(0).fitness + params.toleranciaMejora < mejorFitness) {
                mejorFitness = poblacion.get(0).fitness;
                sinMejora = 0;
            } else {
                sinMejora++;
            }

            if (params.logGeneraciones && (gen % params.logCada == 0 || gen == params.maxGeneraciones - 1)) {
                System.out.println("Generación " + gen + " - Mejor Fitness: " + poblacion.get(0).fitness);
            }

            if (params.maxSinMejora > 0 && sinMejora >= params.maxSinMejora) {
                break;
            }

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
        cargaStamp = nextStamp(cargaVuelosStamp, cargaStamp);
        ocupacionStamp = nextStamp(ocupacionAlmacenesStamp, ocupacionStamp);
        int cargaStampLocal = cargaStamp++;
        int ocupacionStampLocal = ocupacionStamp++;

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta r = ind.asignaciones[i];

            // 1. Penalización Letal por no encontrar ruta
            if (r == null || r.vuelos == null || r.vuelos.isEmpty()) {
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
                int cargaActualVuelo = getStampedValue(cargaVuelos, cargaVuelosStamp, v.id, cargaStampLocal);
                int nuevaCargaVuelo = cargaActualVuelo + e.cantidad;
                if (nuevaCargaVuelo > v.capacidad) {
                    fitnessTotal += 50000; // Penalización por sobrecarga de vuelo
                }
                setStampedValue(cargaVuelos, cargaVuelosStamp, v.id, cargaStampLocal, nuevaCargaVuelo);

                // Sumar al aeropuerto de destino de este vuelo (escala o destino final)
                int destinoIndex = vueloDestinoIndex[v.id];
                if (destinoIndex >= 0) {
                    int ocupacionActualAlmacen = getStampedValue(ocupacionAlmacenes, ocupacionAlmacenesStamp, destinoIndex, ocupacionStampLocal);
                    int nuevaOcupacion = ocupacionActualAlmacen + e.cantidad;
                    if (nuevaOcupacion > capacidadAlmacen[destinoIndex]) {
                    fitnessTotal += 50000; // Penalización Letal por colapso de almacén
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
        List<Vuelo> nuevaRutaVuelos = grafo.buscarRutaAleatoria(e.origen, e.destino, e.horaIngresoMin, 3);
        if (nuevaRutaVuelos != null) {
            ind.asignaciones[idx] = new Ruta(nuevaRutaVuelos, e.horaIngresoMin, e.slaHoras);
        }
    }

    private List<Individuo> inicializarPoblacion() {
        List<Individuo> lista = new ArrayList<>();
        for (int i = 0; i < params.tamanoPoblacion; i++) {
            Individuo ind = new Individuo(envios.size());
            for (int j = 0; j < envios.size(); j++) {
                Envio e = envios.get(j);
                List<Vuelo> vRuta = grafo.buscarRutaAleatoria(e.origen, e.destino, e.horaIngresoMin, 3);
                ind.asignaciones[j] = new Ruta(vRuta, e.horaIngresoMin, e.slaHoras);
            }
            lista.add(ind);
        }
        return lista;
    }
}
