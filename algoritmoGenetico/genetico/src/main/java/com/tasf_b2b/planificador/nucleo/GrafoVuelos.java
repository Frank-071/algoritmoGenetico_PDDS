package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Aeropuerto;
import com.tasf_b2b.planificador.dominio.Vuelo;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
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
    public List<Vuelo> buscarRutaAleatoria(String origen, String destino, int horaSalidaMin, int maxEscalas) {
        // Si no tenemos coordenadas de alguno de los dos, no podemos filtrar por acercamiento
        boolean puedeFiltrear = aeropuertos.containsKey(origen) && aeropuertos.containsKey(destino);

        List<Vuelo> ruta         = new ArrayList<>();
        String      actual       = origen;
        int         tiempoActual = horaSalidaMin;

        for (int i = 0; i < maxEscalas; i++) {
            final int    tiempoMinimoSalida = tiempoActual + 10;
            final String actualFinal        = actual;

            List<Vuelo> vuelosDesde = obtenerVuelosDesde(actual);
            Vuelo elegido = null;
            int validos = 0;
            for (Vuelo v : vuelosDesde) {
                if (v.salidaMin < tiempoMinimoSalida) continue;
                if (puedeFiltrear && !esAcercamiento(actualFinal, v.destino, destino)) continue;
                validos++;
                if (rnd.nextInt(validos) == 0) {
                    elegido = v;
                }
            }

            if (elegido == null) break;
            ruta.add(elegido);

            actual       = elegido.destino;
            tiempoActual = elegido.llegadaMin;

            // llegada a destino
            if (actual.equals(destino)) return ruta;
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

        return distCandidato < distActual * 1.2;
    }
}