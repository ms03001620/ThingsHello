package org.mark.lib_unit_socket;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Mark on 2018/8/23
 */
public class ConnectedThreadTest {

    @Test
    public void int2byte() {
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
            Assert.assertEquals(ConnectedThread.bytesToInt(ConnectedThread.int2byte(i), 0), i);
        }
    }

    @Test
    public void packageData(){
        String message = "hello";
        int type = 1;

        byte[] bytes = ConnectedThread.packageData(message.getBytes(), type);
        byte[] heads = new byte[4];
        byte[] types = new byte[1];


        for (int i = 0; i < heads.length; i++) {
            heads[i] = bytes[i];
        }

        int datalength = ConnectedThread.bytesToInt(heads, 0);


        assertTrue(datalength>0);

    }

}