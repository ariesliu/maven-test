package com.sitinspring;

import junit.framework.TestCase;

public class TestCalculator extends TestCase{
    public void testAdd() {
        Calculator calculator = new Calculator();
        int result = calculator.add(50, 20);
        assertEquals(70, result);
    }

    public void testSub() {
        Calculator calculator = new Calculator();
        int result = calculator.sub(50, 20);
        assertEquals(30, result);
    }
}