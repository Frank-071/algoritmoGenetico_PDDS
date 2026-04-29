package com.tasf_b2b.planificador.nucleo;

public class ParametrosGa {
    // Tamaño de la población (cuántas soluciones candidatas compiten por generación)
    public int tamanoPoblacion = 50; 
    //public int tamanoPoblacion = 100;

    // Cuántas generaciones (iteraciones) va a evolucionar el algoritmo
    public int maxGeneraciones = 100;
    //public int maxGeneraciones = 200; 
    
    // Probabilidad de cruce (Crossover) - Típicamente alta en los papers (80% - 90%)
    public double tasaCruce = 0.85; 
    
    // Probabilidad de mutación - Típicamente baja para no perder buenas soluciones (1% - 5%)
    public double tasaMutacion = 0.1;
    //public double tasaMutacion = 0.05; 
    
    // Cuántos individuos compiten en el torneo para ser padres
    public int tamanoTorneo = 5; 
    
    // Penalidad por romper el SLA (entregar tarde) o exceder capacidad
    public double penalidadSLA = 1000.0; 

    // Control de logging por generación
    public boolean logGeneraciones = true;
    public int logCada = 10;

    // Early stop si no mejora en N generaciones (0 = desactivado)
    public int maxSinMejora = 30;
    public double toleranciaMejora = 0.0;
}
