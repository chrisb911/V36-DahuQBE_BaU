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
        import javax.net.ssl.SSLContext;
        import javax.xml.bind.JAXBException;
        import javax.xml.transform.TransformerException;
        import java.io.*;
        import java.security.NoSuchAlgorithmException;
        import java.util.*;
        import static java.lang.String.format;




public class WCCSendSimulator extends CommandPluginBase {

    private static long MAX_FILE_SIZE = 1048576; // 10MB in old money

    public WCCSendSimulator(Level _level, Command _plugin) {

        super(_level, _plugin);
        registerAction("send");
    }

    @Override
    public void doHandleRequest(CommandPluginContext _pluginContext) throws ContextException, MissingArgumentException, BadConfigurationException, IOException, TransformerException, NoSuchAlgorithmException, CommandException, JAXBException {

        // from the request, get the id of the thing we are going to post
        // look up the thing as a resource (file)
        // post it to the configured QBE Router instance (from config


        QueryRequest queryRequest = parseRequest(_pluginContext.getQueryRequest());

        String id = queryRequest.getFullPath();

        ObjectNode retResponse = JsonNodeFactory.instance.objectNode();
        incrementActionRequest("send");

        if (null == id) {
            incrementActionError("send");
            setFail(_pluginContext, "Didn't get an id to post");
        } else {

            //id represents a doc we are going to post. docs will be in the docDir specified in the config

            String docDir = PluginConfig.getPluginProperties(_pluginContext.getCommand().getName()).getPropertyByName("documentDirectory");
            String serverAddress = PluginConfig.getPluginProperties(_pluginContext.getCommand().getName()).getPropertyByName("postServer");


            if (null == docDir || null == serverAddress) {
                setFail(_pluginContext, "Didn't find a setting for either 'documentDirectory' or 'postServer'.");
                incrementActionError("send");
            }


            //get the metadata - same filename but ending in '.meta'. Read each line and it will indicate what to do with it
            String action = null;
            String index = null;
            String date = null;
            String filename = null;
            HashMap<String, String> metasMap = new HashMap<>();
            int metaCount = 1;
            BufferedReader reader;
            try {
                reader = new BufferedReader(new FileReader(docDir + File.separator + id + ".meta"));
                String line = reader.readLine();

                while (null != line){

                    // see what we got
                    if (line.startsWith("id")){ filename = line.substring(line.indexOf("=")+1).trim();}
                    if (line.startsWith("action")){ action = line.substring(line.indexOf("=")+1).trim();}
                    if (line.startsWith("index")){ index = line.substring(line.indexOf("=")+1).trim();}
                    if (line.startsWith("date")){ date = line.substring(line.indexOf("=")+1).trim();}
                    if (line.startsWith("meta")){
                        metasMap.put("meta"+metaCount,line.substring(line.indexOf("=")+1).trim());
                        metaCount++;
                    }
                    line = reader.readLine();
                }
                reader.close();

            } catch (IOException ioe){
                System.out.println("Working Directory = " +
                        System.getProperty("user.dir"));
                incrementActionError("send");
                logger.warn("failed to find the metadata file '" + docDir + File.separator + id + ".meta" + "'" );
            }

            // get the data
            byte[] fileData = null;
            File file = new File(docDir + File.separator + filename);
            try {
                fileData = read(file);
            } catch (IOException e) {
                // file might not be there - but that's ok
            }

// randomise the key and the index
            Random random = new Random();
            int rand = 0;
            while (true){
                rand = random.nextInt(10001);
                if(rand !=0) break;
            }
            rand = rand + 10000;

            filename = String.format("%d",rand);

            metasMap.put("meta"+metaCount,"documentName="+ filename);

            pushDocument(serverAddress , action, filename, date, index, metasMap, fileData);


            retResponse.put("FakeWCCSendCommand", "id=" + id);

            // set the content payload as the response
            incrementActionResponse("send");
            _pluginContext.getQueryResponse().setPayload(retResponse);

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


