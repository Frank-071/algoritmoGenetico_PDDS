package com.tasf_b2b.planificador.nucleo;


public class Individuo implements Comparable<Individuo> {
    
    // El "ADN" del individuo: una ruta por índice de envío
    public Ruta[] asignaciones;
    
    // El puntaje de esta solución (Se calcula evaluando tiempos y SLA)
    public double fitness;

    public Individuo(int totalEnvios) {
        this.asignaciones = new Ruta[totalEnvios];
        this.fitness = Double.MAX_VALUE; // Inicia con el peor fitness posible
    }

    // Método para copiar un individuo (necesario cuando los cruzas y generas hijos)
    public Individuo clonar() {
        Individuo hijo = new Individuo(this.asignaciones.length);
        System.arraycopy(this.asignaciones, 0, hijo.asignaciones, 0, this.asignaciones.length);
        hijo.fitness = this.fitness;
        return hijo;
    }

    // Para que Java sepa cómo ordenar a los individuos (El de menor fitness gana)
    @Override
    public int compareTo(Individuo otro) {
        return Double.compare(this.fitness, otro.fitness);
    }
}
