package com.tasf_b2b.planificador.dominio;

public class Aeropuerto {
    public final String codigoOACI; // Ej. SKBO
    public final String ciudad;
    public final String pais;
    public final int gmt; // Ej. -5
    public final int capacidadMaxima; // Límite del almacén (Ej. 430)
    
    public int inventarioActual; // Mutable: Maletas actualmente en el almacén

    public Aeropuerto(String codigoOACI, String ciudad, String pais, int gmt, int capacidadMaxima) {
        this.codigoOACI = codigoOACI;
        this.ciudad = ciudad;
        this.pais = pais;
        this.gmt = gmt;
        this.capacidadMaxima = capacidadMaxima;
        this.inventarioActual = 0; // Todo aeropuerto inicia vacío
    }

    // Método útil para que tu Algoritmo Genético valide si hay espacio antes de enviar maletas
    public boolean puedeRecibir(int cantidadMaletas) {
        return (this.inventarioActual + cantidadMaletas) <= this.capacidadMaxima;
    }
}
