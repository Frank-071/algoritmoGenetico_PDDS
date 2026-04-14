package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Envio;
import java.util.HashMap;
import java.util.Map;

public class Individuo implements Comparable<Individuo> {
    
    // El "ADN" del individuo: Un mapa que dice "El Envío X tomará la Ruta Y"
    public Map<Envio, Ruta> asignaciones;
    
    // El puntaje de esta solución (Se calcula evaluando tiempos y SLA)
    public double fitness;

    public Individuo() {
        this.asignaciones = new HashMap<>();
        this.fitness = Double.MAX_VALUE; // Inicia con el peor fitness posible
    }

    // Método para copiar un individuo (necesario cuando los cruzas y generas hijos)
    public Individuo clonar() {
        Individuo hijo = new Individuo();
        hijo.asignaciones.putAll(this.asignaciones);
        hijo.fitness = this.fitness;
        return hijo;
    }

    // Para que Java sepa cómo ordenar a los individuos (El de menor fitness gana)
    @Override
    public int compareTo(Individuo otro) {
        return Double.compare(this.fitness, otro.fitness);
    }
}
