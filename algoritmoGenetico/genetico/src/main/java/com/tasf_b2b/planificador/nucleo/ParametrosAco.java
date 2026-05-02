package com.tasf_b2b.planificador.nucleo;

public class ParametrosAco {
    // Cuantas hormigas (soluciones candidatas) se generan por iteracion
    public int numeroHormigas = 25;

    // Cuantas iteraciones de aprendizaje de feromonas se ejecutan
    public int maxIteraciones = 80;

    // Peso de la feromona en la probabilidad de escoger un vuelo
    public double alpha = 1.2;

    // Peso de la heuristica (tiempo estimado) en la seleccion de vuelos
    public double beta = 1.5;

    // Tasa de evaporacion de feromonas por iteracion
    public double evaporacion = 0.10;

    // Intensidad base de deposito de feromonas
    public double q = 200.0;

    // Maximo de saltos (vuelos) permitidos por ruta
    public int maxEscalas = 3;

    // --- Tiempos Mínimos (en minutos) ---
    public int minEscalaMin = 60; // Tiempo mínimo en tierra durante una escala
    public int minRecojoMin = 120; // Tiempo desde la llegada final hasta que se considera "recogido"

    // --- Pesos y Penalidades para el Fitness ---
    public double penalidadSLA = 15.0;
    public double penalidadSinRuta = 10000.0;
    public double penalidadCapVuelo = 1000.0;
    public double penalidadCapAlmacen = 1000.0;
    public double pesoTiempo = 1.0; // Costo base por hora de viaje

    // Control de logging por iteración
    public boolean logIteraciones = true;
    public int logCada = 10;

    // Tiempo maximo de ejecucion en milisegundos (0 = sin limite)
    public long maxTiempoMs = 0;
}