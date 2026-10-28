package org.mark.carpi;
import org.junit.jupiter.api.Test;
import org.mark.carpi.camera.CameraController;
import org.mark.carpi.runtime.FixtureVerification;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class FixtureTest {
    @Test void tenCyclesReleaseUdp() throws Exception { assertEquals("PASS",FixtureVerification.run().get("status")); }
    @Test void udpSizeLimit() throws Exception {
        assertEquals(65507,CameraController.packet(new byte[65506]).length);
        assertThrows(IOException.class,()->CameraController.packet(new byte[65507]));
    }
}
