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
        
        int duracionMinutos;
        // Tiempo desde que entró al sistema hasta que llegó al destino final
        if(horaIngresoMin > tiempoLlegada) {
            // ejemplo: tiempoLlegada (1am) - horaIngreso (6pm del día anterior) entonces
            // 1 - 18 + 24 = 7 horas. Que es la diferencia entre 6pm y 1am del día siguiente.
            duracionMinutos = tiempoLlegada - horaIngresoMin + 24*60;
        }
        else {
            duracionMinutos = tiempoLlegada - horaIngresoMin;
        }
        this.tiempoTotalHoras = duracionMinutos / 60.0;
        this.cumpleSLA = this.tiempoTotalHoras <= slaHoras;
    }
}
