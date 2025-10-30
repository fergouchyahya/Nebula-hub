package nebula.alloc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourcePoolSmokeTest {
    @Test
    void capacity_and_available_init() {
        ResourcePool p = new ResourcePoolDirect(4);
        assertEquals(4, p.capacity());
        assertEquals(4, p.available());
    }
}
