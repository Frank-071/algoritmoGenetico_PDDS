package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Vuelo;
import java.util.*;

public class GrafoVuelos {
    // Mapa: Origen -> Lista de vuelos que salen de ahí
    private final Map<String, List<Vuelo>> adyacencia = new HashMap<>();

    public GrafoVuelos(List<Vuelo> vuelos) {
        for (Vuelo v : vuelos) {
            adyacencia.computeIfAbsent(v.origen, k -> new ArrayList<>()).add(v);
        }
    }

    public List<Vuelo> obtenerVuelosDesde(String codigoOACI) {
        return adyacencia.getOrDefault(codigoOACI, Collections.emptyList());
    }
    
    // Método útil para que el GA genere rutas aleatorias válidas (DFS simple)
    public List<Vuelo> buscarRutaAleatoria(String origen, String destino, int horaSalidaMin, int maxEscalas) {
        List<Vuelo> ruta = new ArrayList<>();
        String actual = origen;
        int tiempoActual = horaSalidaMin;
        Random rnd = new Random();

        for (int i = 0; i < maxEscalas; i++) {
            List<Vuelo> posibles = obtenerVuelosDesde(actual);
            
            // ¡LA SOLUCIÓN! 
            // Congelamos el tiempo calculado en una variable local y "final" 
            // para que el lambda la pueda usar sin lanzar error de compilación.
            final int tiempoMinimoSalida = tiempoActual + 60;
            
            // Filtramos vuelos que salen después de que llegamos (dejando margen de 1h para trasbordo)
            List<Vuelo> validos = posibles.stream()
                .filter(v -> v.salidaMin >= tiempoMinimoSalida)
                .toList();

            if (validos.isEmpty()) break;

            Vuelo elegido = validos.get(rnd.nextInt(validos.size()));
            ruta.add(elegido);
            actual = elegido.destino;
            tiempoActual = elegido.llegadaMin; // Ahora esto es seguro

            if (actual.equals(destino)) return ruta; // ¡Llegamos!
        }
        return null; // No encontró ruta válida en esta iteración
    }
}
