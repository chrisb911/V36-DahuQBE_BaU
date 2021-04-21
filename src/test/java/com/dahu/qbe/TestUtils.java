package com.dahu.qbe;

import com.dahu.core.document.DEFDocument;
import com.dahu.core.utils.SecurityUtils;
import com.dahu.tools.DahuDigestScheme;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.testng.Assert;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


public class TestUtils {


    // simple wrapper so we can return a custom failure message
    public static void assertTrueEquals(String wanted, String received){
        if (null == received)
            received = "<<NULL>>";
        String failMsg = String.format("Wanted string to equal: '%s', Actual string: '%s'\n", wanted, received);

        assertTrue(received.contains(wanted),failMsg);
    }
    public static void assertTrueIsNotEqual(String wanted, String received){
        if (null == received)
            received = "<<NULL>>";
        String failMsg = String.format("Wanted string to NOT equal: '%s', Actual string: '%s'\n", wanted, received);

        assertFalse(received.contains(wanted),failMsg);
    }

    public static void assertTrueContains(String wanted, String received){
        if (null == received)
            received = "<<NULL>>";
        String failMsg = String.format("Wanted string to contain: '%s', Actual string: '%s'\n", wanted, received);

        int match = received.indexOf(wanted);
        Assert.assertTrue(match > -1);
    }

    public static void assertTrueDoesNotContains(String wanted, String received){
        if (null == received)
            received = "<<NULL>>";
        String failMsg = String.format("Wanted string to contain: '%s', Actual string: '%s'\n", wanted, received);

        int match = received.indexOf(wanted);
        Assert.assertFalse(match > -1);
    }



    public static void stopServer(Process _process){
        if (null != _process){
            _process.destroy();
            // give it a few seconds to actually start..
            System.out.println("test DEFServer stopping...");
            try {
                Thread.sleep(5000);
            } catch(Exception e){
                e.printStackTrace();
            }
            System.out.println("test DEFServer stopped");
        }

    }

    public static Process startTestServer(String _configDir, String _configFile){

        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        // note - relies on the fact we have an ant-run task to create a non-versioned jar file
        System.out.println("server startup command is: 'java -jar -Djava.library.path=./native/mac-x86_64 ./lib/DahuDEFServer.jar -cdir " +  _configDir +  " -c " + _configFile + " -console'");

        ProcessBuilder   ps=new ProcessBuilder("java", "-Djava.library.path=../lib/" +
                "native/mac-x86_64", "-jar", "./lib/DahuDEFServer.jar", "-cdir", _configDir, "-c", _configFile, "-console");
        ps.directory(new File("../target"));

        Process pr;
        try {
            ps.redirectErrorStream(true);
            pr = ps.start();

            // show just a couple of lines of the server startup. Can't show them all as it blocks.
            BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            String line;
            int count = 0;
            while (((line = in.readLine()) != null) && count <4) {
                System.out.println(line);
                count++;
            }

            // give it a few seconds to actually start..
            System.out.println("test DEFServer starting...");
            Thread.sleep(5000);
            System.out.println("test DEFServer started");
            return pr;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static String testServerPostUrl(String _request, Boolean _process,String _filepath){

        CloseableHttpClient client;
        HttpPost httpPost = new HttpPost(_request);
        URI requestURI;
        try {


            requestURI = new URI(_request);
            // right now, we trust all the https certificates. just makes life a lot easier for not much risk given this is a local-host
            // facility
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true).build();
            HttpClientBuilder clientBuilder = HttpClients.custom();
            clientBuilder.setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier());

            clientBuilder.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();

            if(_process) {
                httpPost.addHeader("COMPLETIONMODE","PROCESS");
            }
            client = clientBuilder.build();



            HttpEntity entity = MultipartEntityBuilder
                    .create()
                    .addBinaryBody("upload_file", new File(_filepath), ContentType.create("application/octet-stream"), "filename")
                    .build();

            httpPost.setEntity(entity);
            CloseableHttpResponse response = client.execute(httpPost);


            HttpEntity responseEntity = response.getEntity();
            String responseString = null;
            try {
                responseString = EntityUtils.toString(responseEntity, "UTF-8");
            } catch (Exception e ){
                e.printStackTrace();
            }

            return responseString;

        } catch (Exception e){
            System.out.println("big ole problem posting data. " + e );
            e.printStackTrace();
        }
        return null;
    }

    public static String testServerUrl(String _request) {
        return testServerUrl(_request,-1,null,null);
    }

    public static String testServerUrl(String _request,String _username, String _password){
        return testServerUrl(_request,-1,_username,_password);

    }

    public static String testServerUrl(String _request,int _proxyport){
        return testServerUrl(_request,_proxyport,null,null);
    }

    public static String testServerUrl(String _request,int _proxyport,String _username, String _password) {
        return _testServerUrl(_request,_proxyport,_username,_password);
    }

    public static DEFDocument testServerUrlGetDEFDocument(String _request){

        return _GetRequestReturnDocument(_request, -1, null, null);
    }

    public static DEFDocument testServerUrlGetDEFDocument(String _request,int _proxyport, String _username, String _password){
        return _GetRequestReturnDocument(_request, _proxyport,_username,_password);
    }


    private static DEFDocument _GetRequestReturnDocument(String _request,int _proxyport,String _username, String _password){
        CloseableHttpClient httpClient;
        CloseableHttpResponse serverResponse;



        URI requestURI;
        try {

            requestURI = new URI(_request);
            // right now, we trust all the https certificates. just makes life a lot easier for not much risk given this is a local-host
            // facility
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true).build();
            HttpClientBuilder clientBuilder = HttpClients.custom();
            clientBuilder.setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier());

            clientBuilder.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();

            if (_proxyport >-1){
                HttpHost proxy = new HttpHost("localhost", _proxyport);
                clientBuilder.setProxy(proxy);
            }

            httpClient = clientBuilder.build();
            HttpClientContext context = HttpClientContext.create();
            HttpGet httpGet = new HttpGet(requestURI.toString());

            serverResponse = httpClient.execute(httpGet, context);
            if (serverResponse.getStatusLine().getStatusCode() == 401){
                // we got an auth request, so provide the details
                // get the nonce and the realm and make the request again
                Header header = serverResponse.getFirstHeader("WWW-Authenticate");

                String nonce = null;
                String qop = null;
                String realm = null;
                if (null != header){
                    HeaderElement[] values = header.getElements();
                    for (HeaderElement he : values){
                        if (he.getName().equalsIgnoreCase("nonce")){
                            nonce = he.getValue();
                        }
                        if (he.getName().equalsIgnoreCase("qop")){
                            qop = he.getValue();
                        }
                        if (he.getName().equalsIgnoreCase("DahuDigest realm")){
                            realm = he.getValue();
                        }
                        // if (he.getName().equalsIgnoreCase("Digest realm")){
                        //     realm = he.getValue();
                        //}
                    }
                }

                httpClient.close();

                if (_proxyport >-1){
                    HttpHost proxy = new HttpHost("localhost", _proxyport);
                    clientBuilder.setProxy(proxy);
                }
                HttpClientContext context2 = HttpClientContext.create();
                if (null !=_username && null !=_password) {


                    HttpHost targetHost = new HttpHost(requestURI.toURL().getHost(), requestURI.toURL().getPort(), requestURI.toURL().getProtocol());

                    CredentialsProvider credsProvider = new BasicCredentialsProvider();
                    credsProvider.setCredentials(
                            new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                            new UsernamePasswordCredentials(_username, _password));


                    clientBuilder.setDefaultCredentialsProvider(credsProvider);
                    httpClient = clientBuilder.build();

                    AuthCache authCache = new BasicAuthCache();
                    DahuDigestScheme digestAuth = new DahuDigestScheme();
                    digestAuth.overrideParamter("realm", realm);
                    digestAuth.overrideParamter("nonce", nonce);


                    authCache.put(targetHost, digestAuth);

                    context2.setAuthCache(authCache);
                } else {
                    httpClient = clientBuilder.build();
                }

                HttpGet httpGet2 = new HttpGet(requestURI.toString());

                CloseableHttpResponse serverResponse2 = httpClient.execute(httpGet2, context2);
                DEFDocument newDoc = null;
                try {
                    InputStream input = serverResponse2.getEntity().getContent();
                    ObjectInputStream oi = new ObjectInputStream(input);
                    newDoc = (DEFDocument) oi.readObject();
                    input.close();
                    serverResponse2.close();
                    return newDoc;
                } catch (EOFException eofe){

                }
                serverResponse2.close();
                return newDoc;

            } else {
                DEFDocument newDoc = null;

                try {
                    InputStream input = serverResponse.getEntity().getContent();
                    ObjectInputStream oi = new ObjectInputStream(input);
                    newDoc = (DEFDocument) oi.readObject();
                    input.close();
                    serverResponse.close();
                    return newDoc;

                } catch (EOFException eofe){

                }
                serverResponse.close();
                return newDoc;
            }

        } catch(Exception e){
            e.printStackTrace();

        }
        return null;
    }




    private static String _testServerUrl(String _request,int _proxyport,String _username, String _password){
        CloseableHttpClient httpClient;
        CloseableHttpResponse serverResponse;



        URI requestURI;
        try {

            requestURI = new URI(_request);
            // right now, we trust all the https certificates. just makes life a lot easier for not much risk given this is a local-host
            // facility
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true).build();
            HttpClientBuilder clientBuilder = HttpClients.custom();
            clientBuilder.setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier());

            clientBuilder.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();

            if (_proxyport >-1){
                HttpHost proxy = new HttpHost("localhost", _proxyport);
                clientBuilder.setProxy(proxy);
            }

            httpClient = clientBuilder.build();
            HttpClientContext context = HttpClientContext.create();
            HttpGet httpGet = new HttpGet(requestURI.toString());

            serverResponse = httpClient.execute(httpGet, context);
            if (serverResponse.getStatusLine().getStatusCode() == 401){
                // we got an auth request, so provide the details
                // get the nonce and the realm and make the request again
                Header header = serverResponse.getFirstHeader("WWW-Authenticate");

                String nonce = null;
                String qop = null;
                String realm = null;
                if (null != header){
                    HeaderElement[] values = header.getElements();
                    for (HeaderElement he : values){
                        if (he.getName().equalsIgnoreCase("nonce")){
                            nonce = he.getValue();
                        }
                        if (he.getName().equalsIgnoreCase("qop")){
                            qop = he.getValue();
                        }
                        if (he.getName().equalsIgnoreCase("DahuDigest realm")){
                            realm = he.getValue();
                        }
                        // if (he.getName().equalsIgnoreCase("Digest realm")){
                        //     realm = he.getValue();
                        //}
                    }
                }

                httpClient.close();

                if (_proxyport >-1){
                    HttpHost proxy = new HttpHost("localhost", _proxyport);
                    clientBuilder.setProxy(proxy);
                }
                HttpClientContext context2 = HttpClientContext.create();
                if (null !=_username && null !=_password) {


                    HttpHost targetHost = new HttpHost(requestURI.toURL().getHost(), requestURI.toURL().getPort(), requestURI.toURL().getProtocol());

                    CredentialsProvider credsProvider = new BasicCredentialsProvider();
                    credsProvider.setCredentials(
                            new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                            new UsernamePasswordCredentials(_username, _password));


                    clientBuilder.setDefaultCredentialsProvider(credsProvider);
                    httpClient = clientBuilder.build();

                    AuthCache authCache = new BasicAuthCache();
                    DahuDigestScheme digestAuth = new DahuDigestScheme();
                    digestAuth.overrideParamter("realm", realm);
                    digestAuth.overrideParamter("nonce", nonce);


                    authCache.put(targetHost, digestAuth);

                    context2.setAuthCache(authCache);
                } else {
                    httpClient = clientBuilder.build();
                }


                HttpGet httpGet2 = new HttpGet(requestURI.toString());

                CloseableHttpResponse serverResponse2 = httpClient.execute(httpGet2, context2);

                InputStream input = serverResponse2.getEntity().getContent();
                String retVal = IOUtils.toString(input, "UTF-8");
                serverResponse.close();
                serverResponse2.close();
                return retVal;

            } else {
                InputStream input = serverResponse.getEntity().getContent();
                String retVal = IOUtils.toString(input, "UTF-8");
                serverResponse.close();
                return retVal;
            }

        } catch(Exception e){
            e.printStackTrace();

        }
        return null;
    }


    public static void waitWithMsg(int _ms, String _message){
        try {
            System.out.println(String.format("waiting for %d miliseconds. %s",_ms,_message));
            Thread.sleep(_ms); //  timeout plus one second
        } catch (Exception e){}
    }


    // Creates an event that starts 15 seconds after DEF service starts running
    public static void writeTimeBasedEventConfig(String _filename) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(_filename));

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND,15); // 15 seconds in future

        String dateFormatPattern = "HH:mm:ss";
        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);
        String time = dateFormat.format(calendar.getTime());

        String contents = "";
        contents += "{\n";
        contents += "  \"DEFSettings\":{},\n";
        contents += "\t\"pluginSettings\":{\n";
        contents += "\t\t\"interval\":\"3600\",\n";
        contents += "\t\t\"runAtStartup\":\"false\",\n";
        contents += "\t\t\"startTime\":\"" + time + "\"\n";
        contents += "}\n}\n";

        writer.write(contents);
        writer.close();


    }

    public static void writeEncryptedTestCommandConfig(String _filename) throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter(_filename));

        String encrypt1 =  SecurityUtils.encrypt("password");
        String encrypt2 =  SecurityUtils.encrypt("password123");


        String contents = "";

        contents += "{\n";
        contents += "\t\"DEFSettings\":{\n";
        contents += "\t\t\"logLevel\":\"TRACE\",\n";
        contents += "\t\t\"stores\":[\"testStore\"],\n";
        contents += "\t\t\"inputQueues\":[\"queue1\"]\n";
        contents += "\t},\n";
        contents += "\t\"pluginSettings\":{\n";
        contents += "\t\t\"Property1\":\"property1cvalue\",\n";
        contents += "\t\t\"encryptedsinglevalue_level1\":{\"type\":\"encrypted\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\t\"encryptedarrayvalue_level1\":{\"type\":\"encrypted\",\"values\":[\""+encrypt1+"\",\""+encrypt2+"\"]},\n";
        contents += "\t\t\"non_encryptedsinglevalue_level1\":{\"type\":\"string\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\t\"non_encryptedarrayvalue_level1\":{\"type\":\"string\",\"values\":[\""+encrypt1+"\",\""+encrypt2+"\"]},\n";
        contents += "\t\t\"section1\":{\n";
        contents += "\t\t\t\"simplevalue\":\"value here\",\n";
        contents += "\t\t\t\"simplearray\":[\"value1\",\"value2\"],\n";
        contents += "\t\t\t\"encryptedsinglevalue_level2\":{\"type\":\"encrypted\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\t\t\"encryptedarrayvalue_level2\":{\"type\":\"encrypted\",\"values\":[\""+encrypt1+"\",\""+encrypt2+"\"]},\n";
        contents += "\t\t\t\"non_encryptedsinglevalue_level2\":{\"type\":\"string\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\t\t\"non_encryptedarrayvalue_level2\":{\"type\":\"string\",\"values\":[\""+encrypt1+"\",\""+encrypt2+"\"]},\n";
        contents += "\t\t\t\"subsection1\":{\n";
        contents += "\t\t\t\t\"simplevalue\":\"value here\",\n";
        contents += "\t\t\t\t\"simplearray\":[\"value1\",\"value2\"],\n";
        contents += "\t\t\t\t\"encryptedsinglevalue_level3\":{\"type\":\"encrypted\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\t\t\t\"encryptedarrayvalue_level3\":{\"type\":\"encrypted\",\"values\":[\""+encrypt1+"\",\""+encrypt2+"\"]},\n";
        contents += "\t\t\t\t\"non_encryptedsinglevalue_level3\":{\"type\":\"string\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\t\t\t\"non_encryptedarrayvalue_level3\":{\"type\":\"string\",\"values\":[\""+encrypt1+"\",\""+encrypt2+"\"]}\n";
        contents += "\t\t\t}\n";
        contents += "\t\t}\n";
        contents += "\t}\n";
        contents += "}\n";

        writer.write(contents);

        writer.close();

    }
    public static void writeEncryptedTestSecureConfig() throws IOException {

        BufferedWriter writer = new BufferedWriter(new FileWriter("../src/test/resources/testConfig/testSecureConfig.json"));

        String encrypt1 =  SecurityUtils.encrypt("dahudahu");


        String contents = "";



        contents += "{\n";
        contents += "\t\"logLevel\":\"TRACE\",\n";
        contents += "\t\"name\":\"DEF TEST\",\n";
        contents += "\t\"port\":\"10108\",\n";
        contents += "\t\"threads\":\"40\",\n";
        contents += "\t\"useBroker\":\"false\",\n";
        contents += "\t\"admin\":\"true\",\n";
        contents += "\t\"adminUI\":\"true\",\n";
        contents += "\t\"plugins\":\"./testPlugins\",\n";
        contents += "\t\"crossOrigin\":\"true\",\n";
        contents += "\t\"adminUserName\":\"admin\",\n";
        contents += "\t\"adminUserPassword\":{\"type\":\"encrypted\",\"values\":\""+encrypt1+"\"},\n";
        contents += "\t\"adminTokenTTL\":\"10\",\n";
        contents += "\t\"adminTokenMaxTTL\":\"60\",\n";
        contents += "\t\"responseVersion\":\"2\",\n";
        contents += "\t\"services\":[\n";
        contents += "\t\t{\n";
        contents += "\t\t\t\"name\":\"testInstance1\",\n";
        contents += "\t\t\t\"jar\":\"DahuDEFServer.tests.jar\",\n";
        contents += "\t\t\t\"class\":\"com.dahu.DEFServer.testPlugins.TestService\",\n";
        contents += "\t\t\t\"settingsFile\":\"testInstanceSecure1.json\",\n";
        contents += "\t\t\t\"logLevel\":\"INFO\"\n";
        contents += "\t\t}\n";
        contents += "\t],\n";
        contents += "\t\"stores\":[\n";
        contents += "\t\t{\n";
        contents += "\t\t\t\"name\":\"testStore\",\n";
        contents += "\t\t\t\"jar\":\"DahuDEFServer.tests.jar\",\n";
        contents += "\t\t\t\"class\":\"com.dahu.DEFServer.testPlugins.TestStore\"\n";
        contents += "\t\t}\n";
        contents += "\t],\n";
        contents += "\t\"queues\":[\n";
        contents += "\t\t{\n";
        contents += "\t\t\t\"name\": \"queue1\"\n";
        contents += "\t\t}\n";
        contents += "\t]\n";
        contents += "}\n";

        writer.write(contents);



        writer.close();

    }
}
