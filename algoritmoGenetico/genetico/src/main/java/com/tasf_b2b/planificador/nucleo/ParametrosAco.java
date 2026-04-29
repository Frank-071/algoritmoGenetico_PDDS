package com.tasf_b2b.planificador.nucleo;

public class ParametrosAco {
    // Cuantas hormigas (soluciones candidatas) se generan por iteracion
    public int numeroHormigas = 100;

    // Cuantas iteraciones de aprendizaje de feromonas se ejecutan
    public int maxIteraciones = 300;

    // Peso de la feromona en la probabilidad de escoger un vuelo
    public double alpha = 0.7;

    // Peso de la heuristica (tiempo estimado) en la seleccion de vuelos
    public double beta = 2.5;

    // Tasa de evaporacion de feromonas por iteracion
    public double evaporacion = 0.25;

    // Intensidad base de deposito de feromonas
    public double q = 1000.0;

    // CLAVE: Maximo de saltos (vuelos) permitidos por ruta - AUMENTADO
    public int maxEscalas = 12;

    // Penalidad por romper SLA
    public double penalidadSLA = 100.0;

    // Control de logging por iteración
    public boolean logIteraciones = true;
    public int logCada = 10;

    // Tiempo maximo de ejecucion en milisegundos (0 = sin limite)
    public long maxTiempoMs = 0;
}