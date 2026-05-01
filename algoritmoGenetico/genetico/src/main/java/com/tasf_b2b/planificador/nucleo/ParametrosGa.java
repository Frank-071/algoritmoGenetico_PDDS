package com.tasf_b2b.planificador.nucleo;

public class ParametrosGa {
    // Tamaño de la población (cuántas soluciones candidatas compiten por generación) 
    public int tamanoPoblacion = 100;

    // Cuántas generaciones (iteraciones) va a evolucionar el algoritmo
    public int maxGeneraciones = 200; 
    
    // Probabilidad de cruce (Crossover) - Típicamente alta en los papers (80% - 90%)
    public double tasaCruce = 0.85; 
    
    // Probabilidad de mutación - Típicamente baja para no perder buenas soluciones (1% - 5%)
    public double tasaMutacion = 0.05; 
    
    // Cuántos individuos compiten en el torneo para ser padres
    public int tamanoTorneo = 5; 
    
    // Penalidad por romper el SLA (entregar tarde) o exceder capacidad
    public double penalidadSLA = 1000.0; 
}
