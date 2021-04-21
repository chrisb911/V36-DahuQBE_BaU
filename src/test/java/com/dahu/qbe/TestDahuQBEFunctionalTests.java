package com.dahu.qbe;

import org.testng.annotations.Test;


import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


/**
 * Unit test for simple App.
 */


public class TestDahuQBEFunctionalTests {

    // simple wrapper so we can return a custom failure message
    public void assertTrueContains(String wanted, String received){
        String failMsg = String.format("Wanted string to contain: %s, Actual string: '%s'\n", wanted, received);

        assertTrue(received.contains(wanted),failMsg);
    }
    public void assertTrueDoesNotContain(String wanted, String received){
        String failMsg = String.format("Wanted string to NOT contain: %s, Actual string: '%s'\n", wanted, received);

        assertFalse(received.contains(wanted),failMsg);
    }


    @Test
    //start up the tests
    public void test1(){
        
        // write tests here...
        assertTrueContains("xxx","xxx");
    }

}

