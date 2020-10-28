package org.mark.prework;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实现重复执行测试方法的注解
 *
 * 如何使用
 *     1, 需要在在测试类中初始化成员变量。如下，注意需要注解@Rule
 *     @Rule
 *     public final RepeatRule repeatRule = new RepeatRule();
 *
 *     2, 在需要重复执行的方法上增加注解。例如重复执行 test1() 5次
 *     @RepeatRule.Repeat(count = 5)
 *     @Test
 *     public void test1(){
 *         Assert.assertEquals(2, 1+1);
 *     }
 *     3, 如果只想执行一次，那么可以不使用注解，或count设置为1。
 *        如果count设置为0程序会抛出IllegalArgumentException please set repe..
 */
public class RepeatRule implements TestRule {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})

    public @interface Repeat {
        int count();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                int count = 0;
                Repeat repeat = description.getAnnotation(Repeat.class);
                if (repeat == null) {
                    count = 1;
                } else if (repeat.count() == 0) {
                    throw new IllegalArgumentException("please set repeat times > 0");
                } else {
                    count = repeat.count();
                }

                for (int i = 0; i < count; i++) {
                    base.evaluate();
                }
            }
        };
    }
}