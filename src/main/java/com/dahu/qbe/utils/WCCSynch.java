package com.dahu.qbe.utils;

        import com.dahu.core.logging.DEFLogManager;
        import org.apache.logging.log4j.LogManager;
        import org.apache.logging.log4j.Logger;
        import org.jetbrains.annotations.NotNull;

        import java.io.IOException;
        import java.util.*;
        import java.net.*;
        import java.io.*;
        import java.security.KeyStore;
        import javax.net.ssl.*;

/**
 * Created by :
 * Vince McNamara, Dahu
 * vince@dahu.co.uk
 * on 01/05/2018
 * copyright Dahu Ltd 2018
 * <p>
 * Changed by :
 *
 * Use the WCC Connector Service API to send notifications to WCC on successful indexing by PES
 *
 * For every document that is indexed by PES in the last 24 hours, we send a request with the doc ID to WCC service
 *
 * For every document that is deleted from PES, we need to read all the deletes from a table in the WCC ODM Oracle DB, then
 * confirm that the doc ID no longer exists in PES, and finally, send a notification back to WCC that the doc is deleted
 */

public class WCCSynch {



    private static int BATCH_SIZE = 100;

    private String username;
    private String password;
    private String server;
    private String port;
    private String protocol;
    private String certPath;
    private String urlBase;
    private String urlServicePath;
    private Logger logger;




    public WCCSynch( @NotNull Logger _logger, @NotNull String _protocol, @NotNull String  _server,@NotNull String _port,@NotNull String _username,@NotNull String _password, @NotNull String _certPath, @NotNull String _urlBase, @NotNull String _urlServicePath)  {

        logger = _logger;
        username = _username;
        password = _password;
        server = _server;
        port = _port;
        protocol = _protocol;
        certPath = _certPath;
        urlBase = _urlBase;
        urlServicePath = _urlServicePath;
    }


    /**
     * Send a list of fields that uniquely identify records in WCC (by default, DDocName column is the identifier)
     * The method will create batches of Doc IDs, and send them to WCC Service
     *
     */
    public boolean notifyWCCSuccessAdds(List<String> _ids){

        logger.debug("notifyWCCSuccessfulAdd : received request to process " + _ids.size() + " document identifiers");
        if (_ids == null || _ids.size() == 0){
            logger.trace("notifyWCCSuccessfulAdd : No documents to process.... returning");
            return true;
        }

        Set<String> batches = createDocBatches(_ids);
        boolean successForAllBatches = true;

        for (String ids : batches){
            if (! synchDocBatchToWCC(ids,false)){
                logger.warn("Failed while sending IDs to WCC service");
                successForAllBatches = false;
            }
        }
        return successForAllBatches;
    }


    /**
     *
     * Receieve a string of comma-separated field values (most likely document Ids)
     * Send them on to WCC Service
     * if the doc Ids are for DELETES, then set includeDeletes = true else false
     *
     * All docIds sent will be included in a single request message sent to the WCC service
     * The doc Id list must not be too large or it cannot be processed
     * This method should only be called after the list is broken into batches
     *
     * @param _ids comma-separated list of document ids - not more than BATCH_SIZE ids is permitted
     * @param _includeDeletes boolean - are we sending deletes?
     * @return true if call to WCC service is successful
     */
    private boolean synchDocBatchToWCC(String _ids, boolean _includeDeletes){

        String loginUrl = protocol+ "://" + server + ":" + port + "/" +  urlBase +  "/login/j_security_check";
        String loginParams = "j_username=" + username + "&j_password=" + password + "&j_character_encoding=UTF-8";

        String serviceUrl = protocol+ "://" + server + ":" + port + "/" + urlBase + "/" +  urlServicePath;


        String serviceParams = "IdcService=QBE_CONFIRM_INDEX_SUCCESS&isInclusive=true&IsSoap=1";
        if (_includeDeletes){
            serviceParams = serviceParams + "&isInclusive=true";
        }

        String idToWrite = _ids;
        if (_ids.length() > 512){
            idToWrite = idToWrite.substring(0,512) + "...";
        }

        logger.trace("synchDocBatchToWCC : serviceUrl = " + serviceUrl + " : " + serviceParams + " " + idToWrite);


        // Need to store auth cookies from login request
        CookieHandler.setDefault(new CookieManager());

        // login to WCC first
        try {
            int status = submitWCCServiceRequest(loginUrl, loginParams, false);
            logger.debug("Calling WCC LOGIN : " + loginUrl + " :: " + loginParams);
        } catch (MalformedURLException murle){
            logger.warn("Error in constructing URL to call WCC Service - Url is " + loginUrl + " qstring = " + loginParams);
            return false;
        } catch (IOException ioe){
            logger.warn("IO error when calling WCC Service to login");
            ioe.printStackTrace();
            return false;
        }


        serviceParams = serviceParams + "&ids=" + _ids;
        try {
            int submitStatus = submitWCCServiceRequest(serviceUrl, serviceParams,false);
            logger.debug("Called WCC Service : " + serviceUrl + "?" + serviceParams + ". Service response code = " + submitStatus); } catch (MalformedURLException murle){
            logger.warn("Error in constructing URL to call WCC Service - Url is " + loginUrl + " qstring = " + loginParams);
            return false;
        } catch (IOException ioe){
            logger.warn("IO error when calling WCC Service");
            ioe.printStackTrace();
            return false;
        }
        logger.trace("Finished sending all doc Ids to WCC Service");
        return true;
    }




    /**
     * Helper method that makes HTTP GET request to WCC service. This method handles all authentication in WCC, assuming appropriate data is in params querystring
     * Returns status code
     * @url - the base Url for the WCC service
     * @params - data to send to WCC
     * @printContent - true if raw HTTP response and body should be printed out - false by default
     */
    private int submitWCCServiceRequest(String _url, String _params, boolean _debug) throws IOException {

        logger.debug("Enter submitWCCServiceRequest for " + _url + " and params " + _params);
        URL urlFull = new URL(_url);
        int status = 0;

        if (protocol.startsWith("https")){
            //String pathToCertFile = WCC_SERVICE_CERTPATH;
            try {
                InputStream trustStream = new FileInputStream("./"+certPath);
                char[] trustPassword = "Dahu4ever".toCharArray();
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(trustStream, trustPassword);

                TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustFactory.init(trustStore);
                TrustManager[] trustManagers = trustFactory.getTrustManagers();
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null,trustManagers, null);
                SSLContext.setDefault(sslContext);
//				URLConnection yc = urlFull.openConnection();
                HttpsURLConnection httpsConnection = (HttpsURLConnection) urlFull.openConnection();
                httpsConnection.setHostnameVerifier(
                        new javax.net.ssl.HostnameVerifier(){
                            @Override
                            public boolean verify(String hostname,javax.net.ssl.SSLSession sslSession){
                                return hostname.equalsIgnoreCase("corp.qbe.com");
                            }
                        }
                );


                httpsConnection.setHostnameVerifier(localhostValid);


//				httpsConnection.setAllowUserInteraction(true);
                httpsConnection.setDoOutput(true);
                httpsConnection.setRequestMethod("POST");
                httpsConnection.setRequestProperty("User-Agent","Mozilla/5.0 ( compatible ) ");
                httpsConnection.setRequestProperty("Accept","*/*");
                httpsConnection.setConnectTimeout(10000);
                OutputStreamWriter wr = new OutputStreamWriter(httpsConnection.getOutputStream());
                wr.write(_params);
                wr.flush();
                wr.close();

                status = httpsConnection.getResponseCode();
                logger.debug("callWCC - Response code is " + status);

                if (_debug) {
                    //get all headers
                    Map<String, List<String>> map = httpsConnection.getHeaderFields();
                    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                        logger.trace("WCC response header : " + entry.getKey() + " : " + entry.getValue());
                    }

                    logger.trace("WCC :- Response content :-");
                    BufferedReader in = new BufferedReader(new InputStreamReader(httpsConnection.getInputStream()));
                    String inputLine;
                    while ((inputLine = in.readLine()) != null){
                        logger.trace(inputLine);
                    }
                    in.close();
                }
//				wr.close();


            } catch (Exception e){
                logger.error("Failed to submit request. " + e.getLocalizedMessage());
                e.printStackTrace();
                return 500;
            }

        } else {
            logger.debug("Calling WCC: " + urlFull);
            URLConnection yc = urlFull.openConnection();
            yc.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(yc.getOutputStream());
            wr.write(_params);
            wr.flush();

            status = ((HttpURLConnection)yc).getResponseCode();
            logger.debug("callWCC - Response code is " + status);

            if (_debug) {
                //get all headers
                Map<String, List<String>> map = yc.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    logger.trace("WCC response header : " + entry.getKey() + " : " + entry.getValue());
                }

                logger.trace("WCC :- Response content :-");
                BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null){
                    logger.trace(inputLine);
                }
                in.close();
            }
            wr.close();
        }



        return status;

    }

    public Set<String> createDocBatches(List<String> _ids){

        Set<String> batchesOfDocIds = new HashSet<>();

        // temp containiner to hold the current batch we are processing
        StringBuilder batch = new StringBuilder();

        int docCounter = 0;

        for (String s : _ids){
            docCounter++;
            if (docCounter == BATCH_SIZE){
                batch.setLength(batch.length() - 1);
                batchesOfDocIds.add(batch.toString());
                batch = new StringBuilder();
                docCounter=0;
            }
            batch.append(s + ",");
        }
        batch.setLength(batch.length() - 1);
        batchesOfDocIds.add(batch.toString());

        return batchesOfDocIds;

    }

    public static HostnameVerifier localhostValid = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return "localhost".equals(hostname) || "127.0.0.1".equals(hostname);
        }
    };

}
