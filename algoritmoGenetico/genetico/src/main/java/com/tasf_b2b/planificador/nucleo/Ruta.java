package com.tasf_b2b.planificador.nucleo;

import com.tasf_b2b.planificador.dominio.Vuelo;
import java.util.*;

public class Ruta {
    public List<Vuelo> vuelos = new ArrayList<>();
    public double tiempoTotalHoras;
    public boolean cumpleSLA;

    public Ruta(List<Vuelo> vuelos, int horaIngresoMin, int slaHoras) {
        this.vuelos = vuelos;
        if (vuelos == null || vuelos.isEmpty()) {
            this.tiempoTotalHoras = Double.MAX_VALUE;
            this.cumpleSLA = false;
            return;
        }
        
        Vuelo ultimoVuelo = vuelos.get(vuelos.size() - 1);
        int tiempoLlegada = ultimoVuelo.llegadaMin;
        
        // Tiempo desde que entró al sistema hasta que llegó al destino final
        int duracionMinutos = tiempoLlegada - horaIngresoMin;
        this.tiempoTotalHoras = duracionMinutos / 60.0;
        this.cumpleSLA = this.tiempoTotalHoras <= slaHoras;
    }
}
