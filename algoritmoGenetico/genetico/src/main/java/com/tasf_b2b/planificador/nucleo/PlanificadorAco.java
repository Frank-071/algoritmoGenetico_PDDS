package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Aeropuerto;
import com.tasf_b2b.planificador.dominio.Envio;
import com.tasf_b2b.planificador.dominio.Vuelo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PlanificadorAco {
    private final GrafoVuelos grafo;
    private final List<Vuelo> vuelos;
    private final List<Envio> envios;
    private final ParametrosAco params;
    private final double[] feromonas;
    
    // OPTIMIZACIÓN: Precalcular feromonas^alpha para evitar millones de Math.pow
    private final double[] feromonasAlpha; 
    private final boolean betaEs1;
    private final boolean betaEs2;

    private final int[] vueloDestinoIndex;
    private final int[] capacidadAlmacen;
    private final int numAeropuertos;

    private final Map<String, Integer> aeropuertoIndex;
    private final String[] indexAeropuertoStr; // OPTIMIZACIÓN: Mapeo inverso rápido (int -> String)
    private final Map<String, Integer> capacidadPorAeropuerto;

    private final ThreadLocal<boolean[]>    bufferVisitados;
    private final ThreadLocal<int[]>        bufferCargaVuelos;
    private final ThreadLocal<int[]>        bufferOcupacion;
    private final ThreadLocal<List<Integer>> bufferIndicesUsados;
    private final ThreadLocal<List<Vuelo>>  bufferPosibles;
    private final ThreadLocal<double[]>     bufferPesos;
    private final ThreadLocal<List<Integer>> bufferVuelosModificados;
    private final ThreadLocal<long[][]>     bufferAirportEvents;
    private final ThreadLocal<int[]>        bufferAirportEventCount;
    private final ThreadLocal<List<Integer>> bufferActiveAirports;

    public PlanificadorAco(GrafoVuelos grafo, List<Vuelo> vuelos, List<Envio> envios, ParametrosAco params) {
        this.grafo  = grafo;
        this.vuelos = vuelos;
        this.envios = envios;
        this.params = params;

        // Banderas para salto rápido de Math.pow
        this.betaEs1 = Math.abs(params.beta - 1.0) < 0.001;
        this.betaEs2 = Math.abs(params.beta - 2.0) < 0.001;

        this.aeropuertoIndex = new HashMap<>();
        this.capacidadPorAeropuerto = new HashMap<>();
        int idx = 0;
        for (String codigo : grafo.obtenerAeropuertos().keySet()) {
            aeropuertoIndex.put(codigo, idx++);
        }
        this.numAeropuertos = aeropuertoIndex.size();

        // Mapeo inverso
        this.indexAeropuertoStr = new String[numAeropuertos];
        for (Map.Entry<String, Integer> entry : aeropuertoIndex.entrySet()) {
            indexAeropuertoStr[entry.getValue()] = entry.getKey();
        }

        this.capacidadAlmacen = new int[numAeropuertos];
        for (Map.Entry<String, Integer> entry : aeropuertoIndex.entrySet()) {
            Aeropuerto a = grafo.obtenerAeropuertos().get(entry.getKey());
            capacidadAlmacen[entry.getValue()] = (a != null) ? a.capacidad : 500;
            capacidadPorAeropuerto.put(entry.getKey(), (a != null) ? a.capacidad : 500);
        }

        this.vueloDestinoIndex = new int[vuelos.size()];
        for (int i = 0; i < vuelos.size(); i++) {
            Vuelo v    = vuelos.get(i);
            Integer aI = aeropuertoIndex.get(v.destino);
            vueloDestinoIndex[i] = (aI == null) ? -1 : aI;
        }

        this.feromonas = new double[vuelos.size()];
        this.feromonasAlpha = new double[vuelos.size()];
        Arrays.fill(feromonas, 1.0);
        Arrays.fill(feromonasAlpha, 1.0); // 1.0 ^ alpha siempre es 1.0

        final int nVuelos      = vuelos.size();
        final int nAeropuertos = numAeropuertos;
        final int maxEscalas   = params.maxEscalas;

        this.bufferVisitados    = ThreadLocal.withInitial(() -> new boolean[nAeropuertos]);
        this.bufferCargaVuelos  = ThreadLocal.withInitial(() -> new int[nVuelos]);
        this.bufferOcupacion    = ThreadLocal.withInitial(() -> new int[nAeropuertos]);
        this.bufferIndicesUsados = ThreadLocal.withInitial(() -> new ArrayList<>(maxEscalas + 1));
        this.bufferPosibles      = ThreadLocal.withInitial(ArrayList::new);
        this.bufferPesos         = ThreadLocal.withInitial(() -> new double[nVuelos]);
        this.bufferVuelosModificados = ThreadLocal.withInitial(() -> new ArrayList<>(nVuelos));

        this.bufferAirportEvents     = ThreadLocal.withInitial(() -> new long[nAeropuertos][]);
        this.bufferAirportEventCount = ThreadLocal.withInitial(() -> new int[nAeropuertos]);
        this.bufferActiveAirports    = ThreadLocal.withInitial(() -> new ArrayList<>(nAeropuertos));
    }

    public Individuo ejecutar() {
        Individuo mejorGlobal = null;
        long inicio   = System.currentTimeMillis();
        long deadline = params.maxTiempoMs > 0 ? (inicio + params.maxTiempoMs) : Long.MAX_VALUE;

        for (int iter = 0; iter < params.maxIteraciones; iter++) {
            if (System.currentTimeMillis() >= deadline) break;

            List<Individuo> colonia = IntStream.range(0, params.numeroHormigas)
                    .parallel()
                    .mapToObj(i -> {
                        Individuo h = construirSolucion();
                        calcularFitnessLocal(h);
                        return h;
                    })
                    .collect(Collectors.toList());

            Individuo mejorIteracion = colonia.stream()
                    .min((a, b) -> Double.compare(a.fitness, b.fitness))
                    .orElse(null);

            if (mejorGlobal == null || mejorIteracion.fitness < mejorGlobal.fitness) {
                mejorGlobal = mejorIteracion.clonar();
            }

            actualizarFeromonas(mejorIteracion, mejorGlobal);

            if (params.logIteraciones && (iter % params.logCada == 0 || iter == params.maxIteraciones - 1)) {
                System.out.println("Iteracion ACO " + iter + " - Mejor Fitness: " + mejorGlobal.fitness);
            }
            if (mejorGlobal.fitness < params.penalidadSLA) {
                //System.out.println("Solucion optima alcanzada temprano en iteracion: " + iter);
                break; 
            }
        }

        return mejorGlobal;
    }

    private Individuo construirSolucion() {
        Individuo ind = new Individuo(envios.size());
        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            List<Vuelo> rutaVuelos = construirRutaParaEnvio(e);
            ind.asignaciones[i] = new Ruta(rutaVuelos, e.horaIngresoMin, e.slaHoras, params.minRecojoMin);
        }
        return ind;
    }

    private List<Vuelo> construirRutaParaEnvio(Envio envio) {
        boolean[]     visitados     = bufferVisitados.get();
        List<Integer> indicesUsados = bufferIndicesUsados.get();
        indicesUsados.clear();

        List<Vuelo> ruta         = new ArrayList<>(params.maxEscalas);
        // OPTIMIZACIÓN: Trabajar 100% con índices enteros, 0 strings (O(1))
        int         actualIdx    = aeropuertoIndex.get(envio.origen);
        int         destinoIdx   = aeropuertoIndex.get(envio.destino);
        int         tiempoActual = envio.horaIngresoMin;

        visitados[actualIdx] = true;
        indicesUsados.add(actualIdx);

        for (int escala = 0; escala < params.maxEscalas; escala++) {
            final int   tiempoMinimoSalida = tiempoActual + params.minEscalaMin;
            List<Vuelo> posibles           = bufferPosibles.get();
            posibles.clear();

            String actualStr = indexAeropuertoStr[actualIdx]; 
            for (Vuelo v : grafo.obtenerVuelosDesde(actualStr)) {
                if (v.salidaMin < tiempoMinimoSalida)  continue;
                
                int destVueloIdx = vueloDestinoIndex[v.id];
                if (visitados[destVueloIdx]) continue; // Acceso directo a array O(1), adiós String.equals
                
                posibles.add(v);
            }

            if (posibles.isEmpty()) {
                limpiarVisitados(visitados, indicesUsados);
                return null;
            }

            Vuelo elegido = seleccionarVueloProbabilistico(posibles, destinoIdx, tiempoActual);
            ruta.add(elegido);
            
            actualIdx    = vueloDestinoIndex[elegido.id];
            tiempoActual = elegido.llegadaMin;
            
            visitados[actualIdx] = true;
            indicesUsados.add(actualIdx);

            if (actualIdx == destinoIdx) { // Comparar enteros O(1)
                limpiarVisitados(visitados, indicesUsados);
                return ruta;
            }
        }

        limpiarVisitados(visitados, indicesUsados);
        return null;
    }

    private void limpiarVisitados(boolean[] buf, List<Integer> usados) {
        for (int i : usados) buf[i] = false;
        usados.clear();
    }

    private Vuelo seleccionarVueloProbabilistico(List<Vuelo> candidatos, int destinoFinalIdx, int tiempoActual) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] pesos = bufferPesos.get();
        double suma = 0.0;

        for (int i = 0; i < candidatos.size(); i++) {
            Vuelo  v   = candidatos.get(i);
            
            // OPTIMIZACIÓN: Evitar Math.pow pre-calculando o usando multiplicaciones directas
            double tau_a = feromonasAlpha[v.id]; 
            double eta   = calcularHeuristica(v, destinoFinalIdx, tiempoActual);
            
            double eta_b;
            if (betaEs1) eta_b = eta;
            else if (betaEs2) eta_b = eta * eta;
            else eta_b = Math.pow(eta, params.beta);
            
            double p   = tau_a * eta_b;
            pesos[i]   = p;
            suma      += p;
        }

        if (suma <= 0.0) return candidatos.get(rnd.nextInt(candidatos.size()));

        double umbral    = rnd.nextDouble() * suma;
        double acumulado = 0.0;
        for (int i = 0; i < candidatos.size(); i++) {
            acumulado += pesos[i];
            if (acumulado >= umbral) return candidatos.get(i);
        }

        return candidatos.get(candidatos.size() - 1);
    }

    private double calcularHeuristica(Vuelo v, int destinoFinalIdx, int tiempoActual) {
        int esperaMin = v.salidaMin - tiempoActual;
        if (esperaMin < 0) esperaMin += 24 * 60;

        int duracionMin = v.llegadaMin - v.salidaMin;
        if (duracionMin < 0) duracionMin += 24 * 60;

        double costo = esperaMin + duracionMin;
        // O(1) array access en vez de equals()
        if (vueloDestinoIndex[v.id] == destinoFinalIdx) costo *= 0.1;

        return 1.0 / (1.0 + costo);
    }

    private void actualizarFeromonas(Individuo mejorIteracion, Individuo mejorGlobal) {
        double factorEvaporacion = Math.max(0.0, 1.0 - params.evaporacion);
        for (int i = 0; i < feromonas.length; i++) {
            feromonas[i] = Math.max(0.0001, feromonas[i] * factorEvaporacion);
        }
        depositarFeromona(mejorIteracion, params.q / (1.0 + mejorIteracion.fitness));
        depositarFeromona(mejorGlobal,    (params.q * 1.5) / (1.0 + mejorGlobal.fitness));

        // OPTIMIZACIÓN: Precalcular potencias de feromonas UNA vez por iteracion, no por hormiga
        for (int i = 0; i < feromonas.length; i++) {
            feromonasAlpha[i] = Math.pow(feromonas[i], params.alpha);
        }
    }

    private void depositarFeromona(Individuo ind, double deltaBase) {
        for (int i = 0; i < envios.size(); i++) {
            Envio e    = envios.get(i);
            Ruta  ruta = ind.asignaciones[i];

            if (ruta == null || ruta.vuelos == null || ruta.vuelos.isEmpty()) continue;

            double delta = deltaBase * Math.max(1.0, e.cantidad);
            for (Vuelo v : ruta.vuelos) {
                feromonas[v.id] += delta;
            }
        }
    }

    private void calcularFitnessLocal(Individuo ind) {
        int[] cargaLocal = bufferCargaVuelos.get();
        List<Integer> vuelosModificados = bufferVuelosModificados.get();
        vuelosModificados.clear();
        double fitnessTotal = 0;

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta  r = ind.asignaciones[i];

            if (r == null || r.vuelos == null || r.vuelos.isEmpty()) {
                fitnessTotal += params.penalidadSinRuta;
                continue;
            }

            fitnessTotal += (r.tiempoTotalHoras * params.pesoTiempo);
            if (!r.cumpleSLA) fitnessTotal += (r.tiempoTotalHoras * params.penalidadSLA);

            for (Vuelo v : r.vuelos) {
                if (cargaLocal[v.id] == 0) vuelosModificados.add(v.id);
                cargaLocal[v.id] += e.cantidad;
                if (cargaLocal[v.id] > v.capacidad) fitnessTotal += params.penalidadCapVuelo;
            }
        }

        int violacionesAlmacen = contarViolacionesAlmacen(ind);
        fitnessTotal += violacionesAlmacen * params.penalidadCapAlmacen;
        ind.fitness = fitnessTotal;

        for (int idx : vuelosModificados) cargaLocal[idx] = 0;
    }

    private int contarViolacionesAlmacen(Individuo ind) {
        long[][]      airportEvents  = bufferAirportEvents.get();
        int[]         eventCount     = bufferAirportEventCount.get();
        List<Integer> activeAirports = bufferActiveAirports.get();
        activeAirports.clear();

        for (int i = 0; i < envios.size(); i++) {
            Envio e = envios.get(i);
            Ruta  r = ind.asignaciones[i];
            if (r == null || r.vuelos == null || r.vuelos.isEmpty()) continue;

            List<Vuelo> vuelosRuta = r.vuelos;
            for (int j = 0; j < vuelosRuta.size(); j++) {
                Vuelo v       = vuelosRuta.get(j);
                int   llegada = v.llegadaMin;
                int   duracion;

                if (j == vuelosRuta.size() - 1) {
                    duracion = params.minRecojoMin;
                } else {
                    Vuelo siguiente = vuelosRuta.get(j + 1);
                    duracion = siguiente.salidaMin - v.llegadaMin;
                    if (duracion < params.minEscalaMin) duracion = params.minEscalaMin;
                }

                if (duracion <= 0) continue;

                // OPTIMIZACIÓN: Adiós HashMap.get(String) también aquí
                int airportIdx = vueloDestinoIndex[v.id];
                if (airportIdx == -1) continue;

                if (airportEvents[airportIdx] == null) {
                    airportEvents[airportIdx] = new long[64]; 
                }

                if (eventCount[airportIdx] == 0) activeAirports.add(airportIdx);

                long[] events = airportEvents[airportIdx];
                int    cnt    = eventCount[airportIdx];

                if (cnt + 2 > events.length) {
                    events = Arrays.copyOf(events, Math.max(events.length * 2, cnt + 2));
                    airportEvents[airportIdx] = events;
                }

                events[cnt]     = ((long) llegada            << 32) | ( e.cantidad  & 0xFFFFFFFFL);
                events[cnt + 1] = ((long)(llegada + duracion) << 32) | ((-e.cantidad) & 0xFFFFFFFFL);
                eventCount[airportIdx] = cnt + 2;
            }
        }

        int violaciones = 0;
        for (int airportIdx : activeAirports) {
            long[] events    = airportEvents[airportIdx];
            int    cnt       = eventCount[airportIdx];
            int    capacidad = capacidadAlmacen[airportIdx]; 

            Arrays.sort(events, 0, cnt);

            int ocupacion = 0;
            for (int k = 0; k < cnt; k++) {
                int delta = (int)(events[k] & 0xFFFFFFFFL);
                ocupacion += delta;
                if (ocupacion > capacidad) violaciones++;
            }

            eventCount[airportIdx] = 0;
        }

        return violaciones;
    }
}