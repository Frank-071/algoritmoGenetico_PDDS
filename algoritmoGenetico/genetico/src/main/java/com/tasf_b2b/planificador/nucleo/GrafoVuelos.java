package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Aeropuerto;
import com.tasf_b2b.planificador.dominio.Vuelo;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Random;


public class GrafoVuelos {

    private final Map<String, List<Vuelo>>   adyacencia;
    private final Map<String, Aeropuerto>    aeropuertos;
    private final List<Vuelo>                vuelos;
    private final Random                     rnd = new Random();

    public GrafoVuelos(List<Vuelo> vuelos, Map<String, Aeropuerto> aeropuertos) {
        this.vuelos = vuelos;
        this.aeropuertos = aeropuertos;
        this.adyacencia  = new HashMap<>();
        for (Vuelo v : vuelos) {
            adyacencia.computeIfAbsent(v.origen, k -> new ArrayList<>()).add(v);
        }
    }

    public Map<String, Aeropuerto> obtenerAeropuertos() {
        return Collections.unmodifiableMap(aeropuertos);
    }

    public List<Vuelo> obtenerVuelos() {
        return vuelos;
    }

    public List<Vuelo> obtenerVuelosDesde(String codigoOaci) {
        return adyacencia.getOrDefault(codigoOaci, Collections.emptyList());
    }

    /**
     * Busca una ruta aleatoria válida desde origen hasta destino.
     * Aplica filtro de acercamiento por distancia haversine para evitar rutas absurdas.
     *
     * @return lista de vuelos que forman la ruta, o null si no encontró ninguna
     */
    // En GrafoVuelos.java reemplaza tu método actual por este:

    public List<Vuelo> buscarRutaAleatoria(String origen, String destino, int horaSalidaMin, int maxEscalas) {
        return buscarRutaAleatoria(origen, destino, horaSalidaMin, maxEscalas, 10);
    }

    public List<Vuelo> buscarRutaAleatoria(String origen, String destino, int horaSalidaMin, int maxEscalas, int minEscalaMin) {
        for (int intento = 0; intento < 100; intento++) {
            List<Vuelo> ruta = new ArrayList<>();
            String actual = origen;
            int tiempoActual = horaSalidaMin;
            boolean llego = false;

            for (int i = 0; i < maxEscalas; i++) {
                int tiempoMinimoSalida = tiempoActual + minEscalaMin;
                
                // --- ESTO ES LO QUE SOLUCIONA EL ERROR ---
                final String refActual = actual; 
                // -----------------------------------------

                List<Vuelo> validos = obtenerVuelosDesde(actual).stream()
                    .filter(v -> v.salidaMin >= tiempoMinimoSalida)
                    // USAMOS refActual AQUÍ:
                    .filter(v -> esAcercamiento(refActual, v.destino, destino))
                    .toList();

                if (validos.isEmpty()) {
                    // Fallback: si el filtro de acercamiento deja sin opciones, reintenta sin filtro
                    validos = obtenerVuelosDesde(actual).stream()
                        .filter(v -> v.salidaMin >= tiempoMinimoSalida)
                        .toList();
                }

                if (validos.isEmpty()) break;

                Optional<Vuelo> directo = validos.stream()
                    .filter(v -> v.destino.equals(destino))
                    .findFirst();

                Vuelo elegido;
                if (directo.isPresent()) {
                    elegido = directo.get();
                    llego = true;
                } else {
                    elegido = validos.get(rnd.nextInt(validos.size()));
                }

                ruta.add(elegido);
                actual = elegido.destino; // Aquí actual cambia para la próxima escala
                tiempoActual = elegido.llegadaMin;

                if (llego) return ruta;
            }
        }
        return null;
    }

    /**
     * Devuelve true si ir a 'candidato' nos acerca al destino respecto a estar en 'actual'.
     * Tolera un 20% de desvío para permitir escalas necesarias.
     */
    private boolean esAcercamiento(String actual, String candidato, String destino) {
        // Si no tenemos coordenadas del candidato, lo dejamos pasar
        if (!aeropuertos.containsKey(candidato)) return true;

        Aeropuerto aActual    = aeropuertos.get(actual);
        Aeropuerto aCandidato = aeropuertos.get(candidato);
        Aeropuerto aDestino   = aeropuertos.get(destino);

        double distActual    = aActual.distanciaKm(aDestino);
        double distCandidato = aCandidato.distanciaKm(aDestino);

        return distCandidato < distActual * 1.5;
    }
}