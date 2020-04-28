package org.mark.thingshello.ctrl.servo;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by mark on 2020/4/20
 */
public class CameraServoTest {

    @Test
    public void convert() {

        Assert.assertEquals(0, Float.compare(1, CameraServo.convert(1, 8, 0)));
        Assert.assertEquals(0, Float.compare(8, CameraServo.convert(1, 8, 100)));
    }
}