package com.ucentral.desarrollos.backendparkspotter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendParkSpotterApplicationTests {

    @Test
    void contextLoads() {
    }
    @Test
    void testApplicationStarts() {
        // Puerto aleatorio: así la prueba no falla si la app ya está corriendo en el 8080.
        BackendParkSpotterApplication.main(new String[]{"--server.port=0"});

    }
}
