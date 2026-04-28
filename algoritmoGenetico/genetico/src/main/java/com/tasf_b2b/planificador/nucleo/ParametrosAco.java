package com.tasf_b2b.planificador.nucleo;

public class ParametrosAco {
    // Cuantas hormigas (soluciones candidatas) se generan por iteracion
    public int numeroHormigas = 80;

    // Cuantas iteraciones de aprendizaje de feromonas se ejecutan
    public int maxIteraciones = 200;

    // Peso de la feromona en la probabilidad de escoger un vuelo
    public double alpha = 1.0;

    // Peso de la heuristica (tiempo estimado) en la seleccion de vuelos
    public double beta = 2.0;

    // Tasa de evaporacion de feromonas por iteracion
    public double evaporacion = 0.20;

    // Intensidad base de deposito de feromonas
    public double q = 2000.0;

    // Maximo de saltos (vuelos) permitidos por ruta
    public int maxEscalas = 3;

    // Penalidad por romper SLA o capacidades
    public double penalidadSLA = 1000.0;

    // Control de logging por iteración
    public boolean logIteraciones = true;
    public int logCada = 10;

    // Tiempo maximo de ejecucion en milisegundos (0 = sin limite)
    public long maxTiempoMs = 0;
}