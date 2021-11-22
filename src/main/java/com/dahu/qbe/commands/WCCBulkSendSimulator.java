package com.dahu.qbe.commands;

import com.dahu.def.config.PluginConfig;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.exception.CommandException;
import com.dahu.def.exception.ContextException;
import com.dahu.def.exception.MissingArgumentException;
import com.dahu.def.messages.QueryRequest;
import com.dahu.def.messages.ResponseBase;
import com.dahu.def.plugins.CommandPluginBase;
import com.dahu.def.types.Command;
import com.dahu.def.types.CommandPluginContext;
import com.dahu.surface.core.SURFACE_CONSTANTS;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;

import javax.net.ssl.*;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import static java.lang.String.format;


public class WCCBulkSendSimulator extends CommandPluginBase {

    private static long MAX_FILE_SIZE = 1048576; // 10MB in old money

    public WCCBulkSendSimulator(Level _level, Command _plugin) {

        super(_level, _plugin);
        registerAction("send");
    }

    @Override
    public void doHandleRequest(CommandPluginContext _pluginContext) throws ContextException, MissingArgumentException, BadConfigurationException, IOException, TransformerException, NoSuchAlgorithmException, CommandException, JAXBException {

        // from the request, get the name of the csv we are going to process
        // look up the thing as a resource (file)
        // post it to the configured QBE Router instance (from config


        QueryRequest queryRequest = parseRequest(_pluginContext.getQueryRequest());

        String bulkFileName = queryRequest.getFullPath();

        ObjectNode retResponse = JsonNodeFactory.instance.objectNode();
        incrementActionRequest("send");

        if (null == bulkFileName) {
            incrementActionError("send");
            setFail(_pluginContext, "Didn't get a filename to process");
        } else {

            //bulkFileName represents a csv file of docs we are going to post

            String docDir = PluginConfig.getPluginProperties(_pluginContext.getCommand().getName()).getPropertyByName("documentDirectory");
            String serverAddress = PluginConfig.getPluginProperties(_pluginContext.getCommand().getName()).getPropertyByName("postServer");


            if (null == docDir || null == serverAddress) {
                setFail(_pluginContext, "Didn't find a setting for either 'documentDirectory' or 'postServer'.");
                incrementActionError("send");
            }


            //get the metadata - same filename but ending in '.meta'. Read each line and it will indicate what to do with it

            BufferedReader reader;
            try {
                reader = new BufferedReader(new FileReader(docDir + File.separator + bulkFileName));
                String line = reader.readLine();

                while (null != line){
                    // each line is a document - get the individual elements/ (lines that start // are comments
                    if (!line.trim().startsWith("//")){
                        String[] docElements=line.split(",");
                        if (null != docElements && docElements.length>=5) {

                            HashMap<String, String> metasMap = new HashMap<>();
                            int metaCount = 1;

                            String filename = docElements[0];
                            String id = docElements[1];
                            String action = docElements[2];
                            String date = docElements[3];
                            String index = docElements[4];
                            for (int i = 5; i < docElements.length; i++) {
                                metasMap.put("meta"+metaCount,docElements[i].trim());
                                metaCount++;
                            }
                            // get the data
                            byte[] fileData = null;
                            File file = new File(docDir + File.separator + filename);
                            try {
                                fileData = read(file);
                            } catch (IOException e) {
                                // file might not be there - but that's ok
                            }
                            metasMap.put("meta"+metaCount,"documentName="+ filename);

                            pushDocument2(serverAddress , action, filename, date, index, metasMap, fileData);

                        }
                    }
                    line = reader.readLine();
                }
                reader.close();
                retResponse.put("FakeWCCSendCommand", "id=" + bulkFileName);
                // set the content payload as the response
                incrementActionResponse("send");
                _pluginContext.getQueryResponse().setPayload(retResponse);

            } catch (IOException ioe){
                System.out.println("Working Directory = " +
                        System.getProperty("user.dir"));
                incrementActionError("send");
                logger.warn("failed to find the metadata file '" + docDir + File.separator + bulkFileName + ".meta" + "'" );
            }
        }
    }

    void setFail(CommandPluginContext _pluginContext, String _message) {
        _pluginContext.getQueryResponse().setStatusMessage(ResponseBase.RESPONSESTATUSFAIL);
        _pluginContext.getQueryResponse().setMessage(_message);
        logger.warn(_message);

    }

    public byte[] read(File file) throws IOException {
        if (file.length() > MAX_FILE_SIZE) {
            System.out.println("file too big");
        }

        byte[] buffer = new byte[(int) file.length()];
        InputStream ios = null;
        try {
            ios = new FileInputStream(file);
            if (ios.read(buffer) == -1) {
                throw new IOException(
                        "EOF reached while trying to read the whole file");
            }
        } finally {
            try {
                if (ios != null)
                    ios.close();
            } catch (IOException e) {
            }
        }
        return buffer;
    }

    private HttpURLConnection getConnection(String _server) {
        HttpURLConnection con = null;
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                            throws CertificateException {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                            throws CertificateException {}

                }
        };

        SSLContext sc=null;
        try {
            sc = SSLContext.getInstance("SSL");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String string, SSLSession sslSession) {
                return true;
            }
        });
        try {
            URL url = new URL(_server);
            con = (HttpsURLConnection) url.openConnection();
            con.setAllowUserInteraction(false);
            return con;
        } catch (Exception e){
            logger.warn("problem getting connection: " + e.getLocalizedMessage());
            logger.warn(e);
        }   finally {
            if (con != null){
                con.disconnect();
            }
        }

        return null;
    }

    private int pushDocument2(String _server, String _action, String _filename, String _date, String _indexName, Map<String, String> _metas, byte[] _data) {

        HttpURLConnection con = null;
        int length = 0;
        try {
            con = getConnection(_server);
            con.addRequestProperty("action",_action);
            con.addRequestProperty("index",_indexName);
            con.addRequestProperty("filename",_filename);
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            df.setTimeZone(tz);

            // SHOULD THIS BE A LAST-MODIFIED DATE FROM THE DOC? We are sending the Index date instead
            con.addRequestProperty("last-modified", df.format(new Date()));
            con.addRequestProperty("password", "???");

            con.addRequestProperty("meta1", "indexname="+_filename);

            // add some  metadata
            int count = 1;

            for (String DEFMetaName : _metas.keySet()) {
                String DEFMetaValue = _metas.get(DEFMetaName);
                con.addRequestProperty(DEFMetaName,DEFMetaValue);
                count++;
            }
            con.setDoInput(true);
            con.setDoOutput(true);

            if (null == _data || _data.length >102400000){
                logger.warn("data missing or too large");
                String keyName = "meta"+String.valueOf(count);
                String value = "indexedContent=false";
                con.addRequestProperty(keyName,value);

                //set the size
                String payload = "filename:"+_filename + "\n";
                length = payload.length();
                con.setRequestProperty("Content-Length",""+length);
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(payload);
                wr.flush();
                wr.close();
            } else {
                logger.debug("adding data...");
                String keyName = "meta" + String.valueOf(count);
                String value = "indexedContent=true";
                con.addRequestProperty(keyName,value);
                length = _data.length;
                logger.debug("Key : "+keyName+"  file data length: "+ length);
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());

                wr.write(_data,0,length);
                wr.flush ();
                wr.close ();

            }
        } catch(Exception e){
            logger.warn("failed to post message: " + e.getLocalizedMessage());
            logger.warn(e);
        } finally {
            if (null != con){
                con.disconnect();
            }
        }
        try {
            int code = 0;
            try {
                code = con.getResponseCode();
            } catch(IOException ioe){
                logger.debug("failed while getting response code: " + ioe.getLocalizedMessage());
                logger.debug(ioe);
            }
            if (code != 200){

                logger.debug("push failed - " + code);

            } else {
                logger.debug("push worked  - " + code);
            }
            return code;
        } catch (Exception e){
            logger.warn("something bad: " + e.getLocalizedMessage());
            logger.warn(e);
        }
        return 500;
    }

    private int pushDocument(String _server, String _action, String _filename, String _date, String _indexName, Map<String, String> _metas, byte[] _data) {

        String messageIndexName = _indexName; // If the incoming message defines a target index, use it, otherwise use the one given in config
        logger.debug("posting to " + _server);

        CloseableHttpClient client;
        HttpPost httpPost = new HttpPost(_server);
        try {
            // right now, we trust all the https certificates. just makes life a lot easier for not much risk given this is a local-host
            // facility
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (certificate, authType) -> true).build();
            HttpClientBuilder clientBuilder = HttpClients.custom();
            clientBuilder.setSSLContext(sslContext).setSSLHostnameVerifier(new NoopHostnameVerifier());
            clientBuilder.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()).build();


            httpPost.addHeader("action", _action);
            httpPost.addHeader("last-modified", _date);
            httpPost.addHeader("filename", _filename);
            httpPost.addHeader("index", _indexName);


            // add some  metadata
            int count = 1;

            for (String DEFMetaName : _metas.keySet()) {

                String DEFMetaValue = _metas.get(DEFMetaName);
                httpPost.addHeader(DEFMetaName, DEFMetaValue);
            }

            client = clientBuilder.build();


            ByteArrayEntity entity = new ByteArrayEntity(_data);

            // multipart form data version
            //HttpEntity entity = MultipartEntityBuilder
            //        .create()
            //        .addBinaryBody("upload_file", _data, ContentType.DEFAULT_BINARY, filename)
            //        .build();
            httpPost.setEntity(entity);


            // now push the thing
            CloseableHttpResponse response = client.execute(httpPost);
            if (null != response && null != response.getStatusLine()) {
                int code = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseString = null;
                try {
                    responseString = EntityUtils.toString(responseEntity, "UTF-8");
                } catch (Exception e) {
                    logger.warn("Failed to read response content from PES when response code was not 200 : " + e.getLocalizedMessage());
                }
                if (code != 200) {

                    logger.warn(format("DEFPusher failed (%d not 200) returned %s", code, responseString));

                } else {

                    logger.info(format(" pushed document successfully: %s. code returned: %d, msg returned: '%s' ", _filename,code,responseString));
                }
                return code;
            } else {
                //really bad - return server error
                logger.warn("failed to get a response from push request.");
                return 500;
            }
        } catch (Exception e) {
            logger.warn("failed to post request to Push API.");
            if (logger.isDebugEnabled()){
                e.printStackTrace();
            }
            return 500;
        }
    }
}


