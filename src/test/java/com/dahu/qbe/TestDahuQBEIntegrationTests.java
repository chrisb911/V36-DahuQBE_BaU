package com.dahu.qbe;

        import org.testng.annotations.AfterSuite;
        import org.testng.annotations.BeforeSuite;
        import org.testng.annotations.Test;


        import java.io.File;

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
        System.out.println("          *  DEFConfig_Processor_Vector_settings.json   *");
        System.out.println("          *                                             *");
        System.out.println("          ***********************************************");
        System.out.println();


        System.out.println("starting Translator server on 10101");
        process1 = startTestServer("../../src/test/resources/testConfig","DEFConfig_Receiver.json","receiver");

        System.out.println("starting Processor server on 10103");
        process2 = startTestServer("../../src/test/resources/testConfig","DEFConfig_Processor.json","processor");

        System.out.println("starting WCC Simulator server on 10105");
        process3 = startTestServer("../../src/test/resources/testConfig","DEFConfig_Simulator.json","simulator");

    }

    @AfterSuite
    public void teardownServers(){

        System.out.println("stopping Receiver server on 10101");
        stopServer(process1);
        File receiverTarget = new File("./target" + File.separator + "receiver");
        receiverTarget.delete();
        System.out.println("stopping Processor server on 10103");
        stopServer(process2);
        File processorTarget = new File("./target" + File.separator + "processor");
        processorTarget.delete();
        System.out.println("stopping Simulator server on 10105");
        stopServer(process3);
        File simulatorTarget = new File("./target" + File.separator + "simulator");
        simulatorTarget.delete();
    }


    @Test
    //start up the tests
    public void testI1(){

        // first thing - get the WCC simulator to push a doc
        String workDir = System.getProperty("user.dir");

        String response = testServerUrl("https://localhost:10105/api/send/addNewTest1");
        System.out.println("response = " + response);

        assertTrueDoesNotContain(response,"{\"DEFResponse\": {\"version\": \"2.0\",\"requestStatus\": \"OK\", \"authenticationStatus\": \"unsecure\",\"authToken\": \"null\",\"shortMessage\":\"null\",\"payload\":{\"FakeWCCSendCommand\":\"id=addNewTest1\"}}}");

        //wait 15 secs and check the processor has output
        System.out.println("Sleeping for 15 secs");
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Checking for docs");
        String responseDocsPath = workDir + File.separator + "Processor" + File.separator + "df-daily-export" + File.separator + "current";
        System.out.println("Checking for output in " + responseDocsPath);
        File responseDocs[] = new File(responseDocsPath).listFiles();
        if (null != responseDocs) {
            System.out.println(String.format("Found %d output files", responseDocs.length));
            assertFalse(responseDocs.length!=1);
        }

    }

}

