package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Envio;

public class Asignacion {
    public Envio envio;           // El envío original
    public Ruta ruta;             // La ruta que el Algoritmo Genético le asignó
    public String estado;         // "ENTREGADO", "EN_ALMACEN" o "SIN_RUTA"
    public double retrasoHoras;   // Diferencia entre llegada y el SLA

    public Asignacion(Envio envio, Ruta ruta) {
        this.envio = envio;
        this.ruta = ruta;
        
        // ¡Aquí está el parche! Añadimos ruta.vuelos == null
        if (ruta == null || ruta.vuelos == null || ruta.vuelos.isEmpty()) {
            this.estado = "SIN_RUTA";
            this.retrasoHoras = 0;
        } else {
            this.estado = ruta.cumpleSLA ? "ENTREGADO" : "CON_RETRASO";
            // Si el SLA era 24h y tardó 26h, el retraso es 2.
            this.retrasoHoras = Math.max(0, ruta.tiempoTotalHoras - 24); 
        }
    }
}
