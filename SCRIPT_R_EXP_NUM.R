# ---- 0) Rutas (ajusta segun tu PC) ----
ga_path  <- "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_ga_1000.csv"
aco_path <- "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_aco_1000.csv"

# ---- 1) Cargar datos ----
ga  <- read.csv(ga_path, stringsAsFactors = FALSE)
aco <- read.csv(aco_path, stringsAsFactors = FALSE)

# ---- 2) Elegir la metrica a comparar ----
# Opciones comunes: "Funcion Objetivo", "% Entregados con SLA", "Tiempo Computo (s)"
# Si el nombre tiene espacios o %, se usa con backticks: df$`Funcion Objetivo`
metric <- "Funcion Objetivo"

x_ga  <- ga[[metric]]
x_aco <- aco[[metric]]

# ---- 3) Resumen descriptivo ----
cat("Resumen GA:\n");  print(summary(x_ga))
cat("Resumen ACO:\n"); print(summary(x_aco))

# ---- 4) Normalidad ----
# Shapiro-Wilk (recomendado para n<=50)
sh_ga  <- shapiro.test(x_ga)
sh_aco <- shapiro.test(x_aco)
cat("\nShapiro GA p-value:", sh_ga$p.value, "\n")
cat("Shapiro ACO p-value:", sh_aco$p.value, "\n")

# (Opcional) Kolmogorov-Smirnov contra normal con media/SD muestral
ks_ga  <- ks.test(x_ga,  "pnorm", mean(x_ga),  sd(x_ga))
ks_aco <- ks.test(x_aco, "pnorm", mean(x_aco), sd(x_aco))
cat("\nKS GA p-value:", ks_ga$p.value, "\n")
cat("KS ACO p-value:", ks_aco$p.value, "\n")

# ---- 5) Varianzas (si ambas son normales) ----
# F-test
f_var <- var.test(x_ga, x_aco)
cat("\nF-test p-value:", f_var$p.value, "\n")

# ---- 6) Comparacion de medias ----
# Si normalidad y varianzas iguales -> t-test con var.equal=TRUE
# Si normalidad pero varianzas distintas -> t-test de Welch
# Si NO normalidad -> Mann-Whitney (wilcox.test)

if (sh_ga$p.value > 0.05 && sh_aco$p.value > 0.05) {
  if (f_var$p.value > 0.05) {
    t_res <- t.test(x_ga, x_aco, var.equal = TRUE)
    cat("\nT-test (var iguales):\n"); print(t_res)
  } else {
    t_res <- t.test(x_ga, x_aco, var.equal = FALSE)
    cat("\nT-test (Welch, var distintas):\n"); print(t_res)
  }
} else {
  w_res <- wilcox.test(x_ga, x_aco)
  cat("\nMann-Whitney (no normal):\n"); print(w_res)
}

# ---- 7) Diferencia de medias y efecto ----
diff_mean <- mean(x_ga) - mean(x_aco)
cat("\nDiferencia de medias (GA - ACO):", diff_mean, "\n")
























# =========================================================================
# EXPERIMENTACIÓN NUMÉRICA TASF.B2B - ACO vs GA (Dataset: 1000 Envíos)
# =========================================================================

# ---- 1) Rutas ----
ga_path  <- "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_ga_1000.csv"
aco_path <- "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_aco_1000.csv"

# ---- 2) Cargar datos ----
# Usamos check.names=FALSE para que R no cambie los espacios por puntos
ga  <- read.csv(ga_path, stringsAsFactors = FALSE, check.names = FALSE)
aco <- read.csv(aco_path, stringsAsFactors = FALSE, check.names = FALSE)

# ---- 3) Función de Análisis Automático ----
analizar_metrica <- function(metric) {
  
  cat("\n=======================================================\n")
  cat("ANALIZANDO MÉTRICA:", toupper(metric), "\n")
  cat("=======================================================\n")
  
  x_ga  <- ga[[metric]]
  x_aco <- aco[[metric]]
  
  # A) Resumen descriptivo
  cat(">>> Promedio GA: ", mean(x_ga), " | Desviación Std: ", sd(x_ga), "\n")
  cat(">>> Promedio ACO:", mean(x_aco), " | Desviación Std: ", sd(x_aco), "\n\n")
  
  # B) Prueba de Normalidad (Shapiro-Wilk)
  sh_ga  <- shapiro.test(x_ga)
  sh_aco <- shapiro.test(x_aco)
  
  cat("--- Prueba de Normalidad (Shapiro-Wilk) ---\n")
  cat("P-valor GA: ", sh_ga$p.value, "\n")
  cat("P-valor ACO:", sh_aco$p.value, "\n")
  
  # Evaluar normalidad (p > 0.05 significa que ES normal)
  normalidad_ga  <- sh_ga$p.value > 0.05
  normalidad_aco <- sh_aco$p.value > 0.05
  
  # C) Comparación de Medias / Medianas
  cat("\n--- Prueba de Contraste de Hipótesis ---\n")
  if (normalidad_ga && normalidad_aco) {
    cat("Ambas muestras son NORMALES. Usando pruebas paramétricas...\n")
    
    # Prueba de homogeneidad de varianzas (F-test de Fisher)
    f_var <- var.test(x_ga, x_aco)
    
    if (f_var$p.value > 0.05) {
      cat("Varianzas HOMOGÉNEAS. Aplicando T-Test Estándar:\n")
      t_res <- t.test(x_ga, x_aco, var.equal = TRUE)
      print(t_res)
    } else {
      cat("Varianzas DIFERENTES. Aplicando T-Test de Welch:\n")
      t_res <- t.test(x_ga, x_aco, var.equal = FALSE)
      print(t_res)
    }
    
  } else {
    cat("Las muestras NO SON NORMALES. Aplicando prueba No Paramétrica (Mann-Whitney U):\n")
    # wilcox.test en R es el equivalente a Mann-Whitney
    w_res <- wilcox.test(x_ga, x_aco)
    print(w_res)
  }
  
  # D) Conclusión directa de la diferencia
  diff_mean <- mean(x_ga) - mean(x_aco)
  cat("\n>>> Diferencia de medias (GA - ACO):", diff_mean, "\n")
  if(diff_mean < 0) {
    cat(">>> GA obtuvo un valor MENOR (Mejor ya que estamos minimizando).\n")
  } else if (diff_mean > 0) {
    cat(">>> ACO obtuvo un valor MENOR (Mejor ya que estamos minimizando).\n")
  } else {
    cat(">>> Ambos tienen exactamente el mismo promedio.\n")
  }
}

# ---- 4) Ejecutar el análisis para las 2 métricas clave ----
analizar_metrica("Funcion Objetivo")
analizar_metrica("Tiempo Computo (s)")































# =========================================================================
# FASE 1: ANÁLISIS DESCRIPTIVO Y DE ESCALABILIDAD (TASF.B2B)
# =========================================================================

# Si nunca has usado ggplot2, quita el "#" de la siguiente línea para instalarlo:
# install.packages("ggplot2")

library(ggplot2)

# ---- 1. Definir los tamaños de los datasets ----
tamanos <- c(100, 200, 500, 1000)

# ---- 2. Configurar las rutas de tus 8 archivos CSV ----
# ¡IMPORTANTE! Reemplaza estas rutas con las tuyas. 
# Usa barras diagonales normales "/"
rutas_ga <- c(
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_ga_100.csv",
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_ga_200.csv",
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_ga_500.csv",
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_ga_1000.csv"
)

rutas_aco <- c(
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_aco_100.csv",
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_aco_200.csv",
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_aco_500.csv",
  "E:/Programación/DP1/EXPNum/algoritmoGenetico_PDDS_GA/algoritmoGenetico/genetico/data/resultados_aco_1000.csv"
)

# Vectores vacíos para guardar los promedios
prom_fo_ga <- numeric(4)
prom_tiempo_ga <- numeric(4)
prom_fo_aco <- numeric(4)
prom_tiempo_aco <- numeric(4)

# ---- 3. Calcular los promedios automáticamente ----
for (i in 1:4) {
  # Leer los CSVs respetando los espacios en los nombres de las columnas
  datos_ga <- read.csv(rutas_ga[i], stringsAsFactors = FALSE, check.names = FALSE)
  datos_aco <- read.csv(rutas_aco[i], stringsAsFactors = FALSE, check.names = FALSE)
  
  # Extraer promedios (Asegúrate de que los nombres coincidan exactamente con tus CSV)
  prom_fo_ga[i] <- mean(datos_ga[["Funcion Objetivo"]])
  prom_tiempo_ga[i] <- mean(datos_ga[["Tiempo Computo (s)"]])
  
  prom_fo_aco[i] <- mean(datos_aco[["Funcion Objetivo"]])
  prom_tiempo_aco[i] <- mean(datos_aco[["Tiempo Computo (s)"]])
}

# ---- 4. Generar la TABLA RESUMEN ----
tabla_resumen <- data.frame(
  Envios = tamanos,
  FO_Promedio_ACO = prom_fo_aco,
  FO_Promedio_GA = prom_fo_ga,
  Tiempo_Promedio_ACO = prom_tiempo_aco,
  Tiempo_Promedio_GA = prom_tiempo_ga
)

cat("\n============================================\n")
cat("      TABLA RESUMEN DE ESCALABILIDAD\n")
cat("============================================\n")
print(tabla_resumen)
cat("\n")

# ---- 5. Preparar datos para el GRÁFICO ESTRELLA ----
# Convertimos los datos a un formato que ggplot2 pueda entender fácilmente
datos_grafico <- data.frame(
  Envios = rep(tamanos, 2),
  Tiempo = c(prom_tiempo_ga, prom_tiempo_aco),
  Algoritmo = rep(c("Genético (GA)", "Colonia de Hormigas (ACO)"), each = 4)
)

# ---- 6. Dibujar el GRÁFICO ESTRELLA ----
grafico_estrella <- ggplot(datos_grafico, aes(x = Envios, y = Tiempo, color = Algoritmo, group = Algoritmo)) +
  geom_line(linewidth = 1.2) +     # Grosor de la línea
  geom_point(size = 3) +           # Puntos en las intersecciones
  scale_color_manual(values = c("Colonia de Hormigas (ACO)" = "#E74C3C", "Genético (GA)" = "#2980B9")) +
  labs(
    title = "Escalabilidad del Tiempo de Cómputo (TASF.B2B)",
    subtitle = "Impacto del volumen de envíos en el rendimiento de GA vs ACO",
    x = "Cantidad de Envíos",
    y = "Tiempo de Cómputo Promedio"
  ) +
  theme_minimal() +
  theme(
    plot.title = element_text(face = "bold", size = 14),
    legend.position = "bottom",
    legend.title = element_blank()
  )

# Mostrar el gráfico en el panel de RStudio
print(grafico_estrella)