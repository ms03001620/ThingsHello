package org.mark.thingshello.ctrl.comp.bind;

import android.content.res.AssetFileDescriptor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by mark on 2020/10/13
 */
public class ExclusiveBindTest {
    MockTask a;
    MockTask b;

    ExclusiveBind tool;

    @Before
    public void init(){
        tool = new ExclusiveBind();

        a = new MockTask();
        b = new MockTask();
    }

    @Test
    public void activeBindable() {
        Assert.assertEquals(0, a.count.intValue());
        tool.activeBindable(a);
        Assert.assertEquals(1, a.count.intValue());
        tool.activeBindable(b);
        Assert.assertEquals(1, b.count.intValue());
        Assert.assertEquals(0, a.count.intValue());
    }

    @Test
    public void activeBindableV1() {
        tool.activeBindable(a);
        tool.activeBindable(a);
        Assert.assertEquals(1, a.count.intValue());
    }

    @Test(expected= IllegalArgumentException.class)
    public void activeBindableV2() {
        tool.activeBindable(null);
    }

    class MockTask implements Bindable{
        public Integer count = 0;
        @Override
        public void onBind() {
            count++;
        }

        @Override
        public void onUnBind() {
            count--;
        }
    }
}