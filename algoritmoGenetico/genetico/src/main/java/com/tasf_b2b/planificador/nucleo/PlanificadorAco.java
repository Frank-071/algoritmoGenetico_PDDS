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
    
    private final double[] feromonasAlpha; 
    private final boolean betaEs1;
    private final boolean betaEs2;

    private final int[] vueloDestinoIndex;
    private final int[] capacidadAlmacen;
    private final int[] capacidadMaximaVuelo; // NUEVO: Para inicializar las capacidades rápidamente
    private final int numAeropuertos;

    private final Map<String, Integer> aeropuertoIndex;
    private final String[] indexAeropuertoStr; 
    private final Map<String, Integer> capacidadPorAeropuerto;

    private final Vuelo[][] vuelosDesdeAeropuerto;
    private final int[] duracionVueloMin;

    private final ThreadLocal<boolean[]>    bufferVisitados;
    private final ThreadLocal<int[]>        bufferCargaVuelos;
    private final ThreadLocal<int[]>        bufferOcupacion;
    private final ThreadLocal<List<Integer>> bufferIndicesUsados;
    private final ThreadLocal<List<Vuelo>>  bufferPosibles;
    private final ThreadLocal<List<Vuelo>>  bufferPosiblesSaturados; // NUEVO: Vuelos de emergencia
    private final ThreadLocal<double[]>     bufferPesos;
    private final ThreadLocal<List<Integer>> bufferVuelosModificados;
    private final ThreadLocal<long[][]>     bufferAirportEvents;
    private final ThreadLocal<int[]>        bufferAirportEventCount;
    private final ThreadLocal<List<Integer>> bufferActiveAirports;
    
    private final ThreadLocal<int[]>        bufferCapacidadRestante; // NUEVO: Tracker dinámico
    private final ThreadLocal<int[]>        bufferOrdenEnvios;       // NUEVO: Shuffle de prioridad

    public PlanificadorAco(GrafoVuelos grafo, List<Vuelo> vuelos, List<Envio> envios, ParametrosAco params) {
        this.grafo  = grafo;
        this.vuelos = vuelos;
        this.envios = envios;
        this.params = params;

        this.betaEs1 = Math.abs(params.beta - 1.0) < 0.001;
        this.betaEs2 = Math.abs(params.beta - 2.0) < 0.001;

        this.aeropuertoIndex = new HashMap<>();
        this.capacidadPorAeropuerto = new HashMap<>();
        int idx = 0;
        for (String codigo : grafo.obtenerAeropuertos().keySet()) {
            aeropuertoIndex.put(codigo, idx++);
        }
        this.numAeropuertos = aeropuertoIndex.size();

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
        this.capacidadMaximaVuelo = new int[vuelos.size()];
        for (int i = 0; i < vuelos.size(); i++) {
            Vuelo v    = vuelos.get(i);
            Integer aI = aeropuertoIndex.get(v.destino);
            vueloDestinoIndex[i] = (aI == null) ? -1 : aI;
            capacidadMaximaVuelo[v.id] = v.capacidad; // Guardamos la capacidad máxima
        }

        this.duracionVueloMin = new int[vuelos.size()];
        for (Vuelo v : vuelos) {
            int dur = v.llegadaMin - v.salidaMin;
            if (dur < 0) dur += 24 * 60;
            duracionVueloMin[v.id] = dur;
        }

        this.vuelosDesdeAeropuerto = new Vuelo[numAeropuertos][];
        for (int i = 0; i < numAeropuertos; i++) {
            String codigo = indexAeropuertoStr[i];
            List<Vuelo> desde = grafo.obtenerVuelosDesde(codigo);
            if (desde == null) desde = new ArrayList<>();
            vuelosDesdeAeropuerto[i] = desde.toArray(new Vuelo[0]);
        }

        this.feromonas = new double[vuelos.size()];
        this.feromonasAlpha = new double[vuelos.size()];
        Arrays.fill(feromonas, 1.0);
        Arrays.fill(feromonasAlpha, 1.0); 

        final int nVuelos      = vuelos.size();
        final int nAeropuertos = numAeropuertos;
        final int maxEscalas   = params.maxEscalas;
        final int nEnvios      = envios.size();

        this.bufferVisitados    = ThreadLocal.withInitial(() -> new boolean[nAeropuertos]);
        this.bufferCargaVuelos  = ThreadLocal.withInitial(() -> new int[nVuelos]);
        this.bufferOcupacion    = ThreadLocal.withInitial(() -> new int[nAeropuertos]);
        this.bufferIndicesUsados = ThreadLocal.withInitial(() -> new ArrayList<>(maxEscalas + 1));
        this.bufferPosibles      = ThreadLocal.withInitial(ArrayList::new);
        this.bufferPosiblesSaturados = ThreadLocal.withInitial(ArrayList::new); // Inicialización del nuevo buffer
        this.bufferPesos         = ThreadLocal.withInitial(() -> new double[nVuelos]);
        this.bufferVuelosModificados = ThreadLocal.withInitial(() -> new ArrayList<>(nVuelos));

        this.bufferAirportEvents     = ThreadLocal.withInitial(() -> new long[nAeropuertos][]);
        this.bufferAirportEventCount = ThreadLocal.withInitial(() -> new int[nAeropuertos]);
        this.bufferActiveAirports    = ThreadLocal.withInitial(() -> new ArrayList<>(nAeropuertos));
        
        // Inicialización de buffers de capacidad dinámica y orden
        this.bufferCapacidadRestante = ThreadLocal.withInitial(() -> new int[nVuelos]);
        this.bufferOrdenEnvios = ThreadLocal.withInitial(() -> {
            int[] arr = new int[nEnvios];
            for (int i = 0; i < nEnvios; i++) arr[i] = i;
            return arr;
        });
    }

    public Individuo ejecutar() {
        long inicio   = System.currentTimeMillis();
        long deadline = params.maxTiempoMs > 0 ? (inicio + params.maxTiempoMs) : Long.MAX_VALUE;

        Individuo mejorGlobal = construirSolucion(true);
        calcularFitnessLocal(mejorGlobal);
        
        if (params.logIteraciones) {
            System.out.println("Fitness Base (Greedy Inicial): " + mejorGlobal.fitness);
        }

        depositarFeromona(mejorGlobal, (params.q * 2.0) / (1.0 + mejorGlobal.fitness));
        for (int i = 0; i < feromonas.length; i++) {
            feromonasAlpha[i] = Math.pow(feromonas[i], params.alpha);
        }

        int iteracionesSinMejora = 0;
        double mejorFitnessHistorico = mejorGlobal.fitness;

        for (int iter = 0; iter < params.maxIteraciones; iter++) {
            if (System.currentTimeMillis() >= deadline) break;

            List<Individuo> colonia = IntStream.range(0, params.numeroHormigas)
                    .parallel()
                    .mapToObj(i -> {
                        Individuo h = construirSolucion(false); 
                        calcularFitnessLocal(h);
                        return h;
                    })
                    .collect(Collectors.toList());

            Individuo mejorIteracion = colonia.stream()
                    .min((a, b) -> Double.compare(a.fitness, b.fitness))
                    .orElse(null);

            if (mejorIteracion.fitness < mejorGlobal.fitness) {
                mejorGlobal = mejorIteracion.clonar();
            }

            actualizarFeromonas(mejorIteracion, mejorGlobal);

            if (mejorGlobal.fitness < mejorFitnessHistorico - 0.1) {
                mejorFitnessHistorico = mejorGlobal.fitness;
                iteracionesSinMejora = 0; 
            } else {
                iteracionesSinMejora++; 
            }

            if (params.logIteraciones && (iter % params.logCada == 0 || iter == params.maxIteraciones - 1)) {
                System.out.println("Iteracion ACO " + iter + " - Mejor Fitness: " + mejorGlobal.fitness);
            }
            
            if (iteracionesSinMejora >= 8) {
                if(params.logIteraciones) System.out.println("Convergencia óptima alcanzada. Deteniendo en iteración: " + iter);
                break; 
            }
        }

        return mejorGlobal;
    }

    private Individuo construirSolucion(boolean esGreedy) {
        Individuo ind = new Individuo(envios.size());
        
        // 1. Clonar las capacidades máximas al buffer de esta hormiga (Toma O(N) bajísimo)
        int[] capRestante = bufferCapacidadRestante.get();
        System.arraycopy(capacidadMaximaVuelo, 0, capRestante, 0, capacidadMaximaVuelo.length);

        // 2. Obtener y barajar el orden de procesamiento
        int[] orden = bufferOrdenEnvios.get();
        if (!esGreedy) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = orden.length - 1; i > 0; i--) {
                int index = rnd.nextInt(i + 1);
                int temp = orden[index];
                orden[index] = orden[i];
                orden[i] = temp;
            }
        }

        // 3. Procesar envíos respetando la capacidad en tiempo real
        for (int i = 0; i < orden.length; i++) {
            int originalIdx = orden[i];
            Envio e = envios.get(originalIdx);
            
            // Pasamos capRestante al constructor de ruta
            List<Vuelo> rutaVuelos = construirRutaConReintentos(e, 10, esGreedy, capRestante);
            ind.asignaciones[originalIdx] = new Ruta(rutaVuelos, e.horaIngresoMin, e.slaHoras, params.minRecojoMin);
            
            // Si encontró ruta, se resta la capacidad del avión en vivo para que los demás paquetes no lo usen
            if (rutaVuelos != null) {
                for (Vuelo v : rutaVuelos) {
                    capRestante[v.id] -= e.cantidad;
                }
            }
        }
        return ind;
    }

    private List<Vuelo> construirRutaConReintentos(Envio envio, int maxVidas, boolean esGreedy, int[] capRestante) {
        for (int intento = 0; intento < maxVidas; intento++) {
            boolean usarGreedy = esGreedy && (intento == 0);
            List<Vuelo> ruta = construirRutaParaEnvio(envio, usarGreedy, capRestante);
            if (ruta != null) return ruta; 
        }
        return null; 
    }

    private List<Vuelo> construirRutaParaEnvio(Envio envio, boolean esGreedy, int[] capRestante) {
        boolean[]     visitados     = bufferVisitados.get();
        List<Integer> indicesUsados = bufferIndicesUsados.get();
        indicesUsados.clear();

        List<Vuelo> ruta         = new ArrayList<>(params.maxEscalas);
        int         actualIdx    = aeropuertoIndex.get(envio.origen);
        int         destinoIdx   = aeropuertoIndex.get(envio.destino);
        int         tiempoActual = envio.horaIngresoMin;

        visitados[actualIdx] = true;
        indicesUsados.add(actualIdx);

        for (int escala = 0; escala < params.maxEscalas; escala++) {
            final int   tiempoMinimoSalida = tiempoActual + params.minEscalaMin;
            List<Vuelo> posibles           = bufferPosibles.get();
            List<Vuelo> saturados          = bufferPosiblesSaturados.get();
            posibles.clear();
            saturados.clear();

            for (Vuelo v : vuelosDesdeAeropuerto[actualIdx]) {
                if (v.salidaMin < tiempoMinimoSalida)  continue;
                
                int destVueloIdx = vueloDestinoIndex[v.id];
                if (visitados[destVueloIdx]) continue; 
                
                // MAGIA AQUI: Separamos los vuelos con espacio disponible de los llenos
                if (capRestante[v.id] >= envio.cantidad) {
                    posibles.add(v);
                } else {
                    saturados.add(v); // Plan de emergencia (Evitar Sin Ruta a toda costa)
                }
            }

            List<Vuelo> candidatos;
            if (!posibles.isEmpty()) {
                candidatos = posibles; // Primero intenta usar aviones que sí tienen espacio
            } else if (!saturados.isEmpty()) {
                candidatos = saturados; // Si todos están llenos, sobrevéndelo para salvar el paquete
            } else {
                limpiarVisitados(visitados, indicesUsados);
                return null;
            }

            Vuelo elegido;
            if (esGreedy) {
                elegido = candidatos.get(0);
                double mejorEta = -1.0;
                for (Vuelo v : candidatos) {
                    double eta = calcularHeuristica(v, destinoIdx, tiempoActual);
                    if (eta > mejorEta) {
                        mejorEta = eta;
                        elegido = v;
                    }
                }
            } else {
                elegido = seleccionarVueloProbabilistico(candidatos, destinoIdx, tiempoActual);
            }
            
            ruta.add(elegido);
            
            actualIdx    = vueloDestinoIndex[elegido.id];
            tiempoActual = elegido.llegadaMin;
            
            visitados[actualIdx] = true;
            indicesUsados.add(actualIdx);

            if (actualIdx == destinoIdx) { 
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

        int duracionMin = duracionVueloMin[v.id];

        double costo = esperaMin + duracionMin;
        if (vueloDestinoIndex[v.id] == destinoFinalIdx) costo *= 0.1;

        return 1.0 / (1.0 + costo);
    }

    private void actualizarFeromonas(Individuo mejorIteracion, Individuo mejorGlobal) {
        double factorEvaporacion = Math.max(0.0, 1.0 - params.evaporacion);
        for (int i = 0; i < feromonas.length; i++) {
            feromonas[i] = Math.max(0.1, feromonas[i] * factorEvaporacion);
        }
        depositarFeromona(mejorIteracion, params.q / (1.0 + mejorIteracion.fitness));
        depositarFeromona(mejorGlobal,    (params.q * 1.5) / (1.0 + mejorGlobal.fitness));

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