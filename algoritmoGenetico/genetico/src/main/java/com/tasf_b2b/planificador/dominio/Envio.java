package com.tasf_b2b.planificador.dominio;

public class Envio {
    public final String idPedido;
    public final String origen;       // Nombre del archivo TXT
    public final String destino;      // dest (ej. EBCI)
    public final String fecha;        // aaaammdd
    public final int horaIngresoMin;  // Convertimos hh y mm a minutos totales
    public final int cantidad;        // ### (ej. 6)
    public final String idCliente;    // IdClien (ej. 0007729)

    // Variable para tu Algoritmo Genético
    public boolean asignado; 

    // Constructor
    public Envio(String idPedido, String origen, String destino, String fecha, 
                 int hh, int mm, int cantidad, String idCliente) {
        
        this.idPedido = idPedido;
        this.origen = origen;
        this.destino = destino;
        this.fecha = fecha;
        
        // Convertimos la hora y minuto a un solo número
        // 01:38 = (1 * 60) + 38 = 98 minutos desde la medianoche.
        this.horaIngresoMin = (hh * 60) + mm; 
        
        this.cantidad = cantidad;
        this.idCliente = idCliente;
        this.asignado = false;
    }
}
