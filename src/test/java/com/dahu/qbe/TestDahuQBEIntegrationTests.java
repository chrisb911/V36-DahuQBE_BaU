package com.dahu.qbe;

        import org.testng.annotations.AfterSuite;
        import org.testng.annotations.BeforeSuite;
        import org.testng.annotations.Test;


        import static com.dahu.qbe.TestUtils.*;
        import static org.testng.Assert.assertFalse;
        import static org.testng.Assert.assertTrue;


/**
 * Unit test for simple App.
 */


public class TestDahuQBEIntegrationTests {

    // simple wrapper so we can return a custom failure message
    public void assertTrueContains(String wanted, String received){
        String failMsg = String.format("Wanted string to contain: %s, Actual string: '%s'\n", wanted, received);

        assertTrue(received.contains(wanted),failMsg);
    }
    public void assertTrueDoesNotContain(String wanted, String received){
        String failMsg = String.format("Wanted string to NOT contain: %s, Actual string: '%s'\n", wanted, received);

        assertFalse(received.contains(wanted),failMsg);
    }

    private Process process1;
    private Process process2;
    private Process process3;

    @BeforeSuite
    public void setupServers(){
        System.out.println("          ***********************************************");
        System.out.println("          *                                             *");
        System.out.println("          *  NOTE:                                      *");
        System.out.println("          *  you need a current, valid PES license      *");
        System.out.println("          *  from Hyland to successfully run the tests. *");
        System.out.println("          *  The license is set in the config file for  *");
        System.out.println("          *  the processor:                             *");
        System.out.println("          *  DEFConfig_Processor_Filter_pipeline.json   *");
        System.out.println("          *                                             *");
        System.out.println("          ***********************************************");
        System.out.println();


        System.out.println("starting Translator server on 10101");
        process1 = startTestServer("../src/test/resources/testConfig","DEFConfig_Translator_QBE.json");

        System.out.println("starting Processor server on 10102");
        process2 = startTestServer("../src/test/resources/testConfig","DEFConfig_Processor_QBE.json");

        System.out.println("starting Fake WCC server on 10103");
        process3 = startTestServer("../src/test/resources/testConfig","DEFConfig_FakeWCC_QBE.json");

    }

    @AfterSuite
    public void teardownServers(){

        System.out.println("stopping Translator server on 10101");
        stopServer(process1);
        System.out.println("stopping Processor server on 10102");
        stopServer(process2);
        System.out.println("stopping FakeWCC server on 10103");
        stopServer(process3);
    }


    @Test
    //start up the tests
    public void testI1(){

        // first test - push a doc and check it gets written to the output folders
        String response = testServerUrl("https://localhost:10103/api/send/addNewTest1");
        System.out.println("response = " + response);
        assertTrueContains(response,"{\"DEFResponse\": {\"version\": \"2.0\",\"requestStatus\": \"OK\", \"authenticationStatus\": \"unsecure\",\"authToken\": \"null\",\"shortMessage\":\"null\",\"payload\":{\"FakeWCCSendCommand\":\"id=addNewTest1\"}}}");
    }

}

