package com.tasf_b2b.planificador.dominio;


public class Envio {
 
    public final String idPedido;
    public final String origen;         // código OACI extraído del nombre del archivo
    public final String destino;        // código OACI destino (ej. EBCI)
    public final String fecha;          // aaaammdd
    public final int    horaIngresoLocal; // minutos desde 00:00 hora LOCAL del origen
    public final int    horaIngresoMin; // minutos desde 00:00 UTC
    public final int    cantidad;
    public final String idCliente;
    public boolean      asignado;
 
    /**
     * Constructor principal — requiere el aeropuerto origen para normalizar a UTC.
     */
    public Envio(String idPedido, String origen, String destino, String fecha,
                 int hh, int mm, int cantidad, String idCliente,
                 Aeropuerto aeropuertoOrigen) {
 
        this.idPedido      = idPedido;
        this.origen        = origen;
        this.destino       = destino;
        this.fecha         = fecha;
        this.cantidad      = cantidad;
        this.idCliente     = idCliente;
        this.asignado      = false;
 
        this.horaIngresoLocal = (hh * 60) + mm;
        this.horaIngresoMin = horaIngresoLocal - (aeropuertoOrigen.gmt * 60);
    }
 
    @Override
    public String toString() {
        return String.format("Envio[%s] %s→%s cant:%d", idPedido, origen, destino, cantidad);
    }
}
