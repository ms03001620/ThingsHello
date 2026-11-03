package org.mark.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mark.lib_unit_socket.bean.CmdConstant

class KotlinSupportTest {

    @Test
    fun testKotlinSupport() {
        val message = "Hello from Kotlin"
        val uppercase = message.uppercase()
        assertEquals("HELLO FROM KOTLIN", uppercase)

        // Verify Java class interaction
        assertEquals(0, CmdConstant.WHEEL)
    }
}
