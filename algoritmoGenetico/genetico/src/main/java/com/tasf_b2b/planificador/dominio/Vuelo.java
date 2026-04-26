package com.tasf_b2b.planificador.dominio;

public class Vuelo {
    public final int id;
    public final String origen;
    public final String destino;
    public final int salidaMin;   // minutos desde 00:00
    public final int llegadaMin;  // minutos desde 00:00 (con wrap si cruza medianoche)
    public int capacidad;         // mutable: se consume al asignar
    public final double horasDuracion;

    public Vuelo(int id, String origen, String destino, int salidaMin, int llegadaMin, int capacidad) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.salidaMin = salidaMin;
        this.llegadaMin = llegadaMin;
        this.capacidad = capacidad;
        int durMin = (llegadaMin >= salidaMin) ? (llegadaMin - salidaMin) : (llegadaMin + 24*60 - salidaMin);
        this.horasDuracion = Math.max(0.01, durMin / 60.0);
    }

    @Override
    public String toString() {
        return String.format("Vuelo[%d] %s→%s (%.1fh)", id, origen, destino, horasDuracion);
    }
}