package com.tasf_b2b.planificador.genetico;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.tasf_b2b.planificador.Main;

@SpringBootApplication(scanBasePackages = "com.tasf_b2b.planificador")
public class GeneticoApplication {

    public static void main(String[] args) {
        Main.main(args);
    }
}
