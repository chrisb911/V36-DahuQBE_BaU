package com.dahu.qbe.commands;


        import com.dahu.core.exception.BadMetaException;
        import com.dahu.core.interfaces.iDocument;
        import com.dahu.core.interfaces.iQueue;
        import com.dahu.core.logging.DEFLogManager;

        import com.dahu.def.config.PluginConfig;
        import com.dahu.def.exception.*;
        import com.dahu.def.messages.QueryRequest;
        import com.dahu.def.plugins.CommandPluginBase;
        import com.dahu.def.types.Command;
        import com.dahu.def.types.CommandPluginContext;
        import com.dahu.def.types.Payload;
        import com.dahu.qbe.utils.inboundPesWebApiRequest;
        import org.apache.logging.log4j.Level;
        import org.apache.logging.log4j.Logger;
        import org.jetbrains.annotations.NotNull;

        import java.io.*;
        import java.text.SimpleDateFormat;
        import java.time.LocalDate;
        import java.time.ZoneId;
        import java.util.*;
        import java.security.NoSuchAlgorithmException;
        import java.text.ParseException;
        import javax.ws.rs.BadRequestException;
        import javax.xml.bind.JAXBException;
        import javax.xml.transform.TransformerException;
        import com.dahu.qbe.DEF_QBE_BAU_CONSTANTS;


/**
 * Created by :
 * Vince McNamara, Dahu
 * vince@dahu.co.uk
 * on 13/06/2018
 * copyright Dahu Ltd 2018
 * <p>
 * Changed by :
 * Chris Bartlett 03/08/2019
 */


public class WCCReceiver extends CommandPluginBase {


    private String defOldMsgAckQueue = null;
    private String pendingCacheRoot = null;
    private String ackCacheRoot = null;
    private String ackCacheFolder = null;
    private String counterTime = null;
    private int counter = 0;
    iQueue queue = null;

    String uniqueID;
    private Logger statusLogger;

    private static final String archiveFilePath = "D:/pes-export/olddocs";

    private static final int DELAY_AFTER_DELETE = 10000; // how long to wait after sending a delete before responding - adds delay to an UPDATE action in WCC

    protected static final String POST_DOC_PART_NAME = "upfile";

    public WCCReceiver(Level _level, Command _plugin) {

        super(_level, _plugin);
        statusLogger = DEFLogManager.getLogger("DahuQBE-BAU",Level.INFO);
        uniqueID = UUID.randomUUID().toString();
        registerAction("WCCReceiver");
        
    }

    private synchronized String getCounterTime(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmss");
        if (null == counterTime){
            // just starting for the first time - set a time for the counter reset
            counterTime = dateFormat.format(new Date());
            counter = 0;
        } else {
            //has the time changed? if so, reset the counter
            String timeNow = dateFormat.format(new Date());
            if (counterTime.equals(timeNow)){
                counter++;
            } else {
                counter=0;
                counterTime = timeNow;
            }
        }
        return String.format("%s%06d",counterTime,counter);
    }


    @Override
    public void doHandleRequest(CommandPluginContext _context) throws ContextException, MissingArgumentException, BadConfigurationException, IOException, TransformerException, NoSuchAlgorithmException, CommandException, JAXBException {

        logger.trace("WccCommand processing request");

        incrementActionRequest("WCCReceiver");

        QueryRequest request = parseRequest(_context.getQueryRequest());

        Command thisCommand = _context.getCommand();


        pendingCacheRoot = PluginConfig.getPluginPropertyValue(thisCommand,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_CACHEROOT,null);
        ackCacheRoot = PluginConfig.getPluginPropertyValue(thisCommand,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKCACHEROOT,null);
        ackCacheFolder = PluginConfig.getPluginPropertyValue(thisCommand,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKCACHEFOLDER,null);


        if (ackCacheRoot == null  ) {
            throw new BadConfigurationException("No acknowledgement cache directory specified" );
        }
        if (ackCacheFolder == null  ) {
            throw new BadConfigurationException("No acknowledgement cache folder specified" );
        }

        Set<String> headers = _context.getQueryRequest().getHeaders();
        String action = null;
        String filename = null;
        String index = null;
        String lastModified = null;
        String remoteQueue = null;


        String method = request.getRequestMethod();

        if (_context.getQueryRequest().getHeaders() != null && _context.getQueryRequest().getHeaders().size() > 0){
            for (String header : _context.getQueryRequest().getHeaders()) {
                if (header.toLowerCase().startsWith("action=")) {
                    action = header.substring(header.indexOf("=") + 1);
                } else if (header.toLowerCase().startsWith("filename=")) {
                    filename = header.substring(header.indexOf("=") + 1);
                } else if (header.toLowerCase().startsWith("last-modified")) {
                    lastModified = header.substring(header.indexOf("=") + 1);
                } else if (header.toLowerCase().startsWith("index=")) {
                    index = header.substring(header.indexOf("=") + 1);
                }
            }
        } else {

            logger.info("Did not find an action as a Header in this request");
            setResponse(422,_context,"Did not find an action as a Header in this request");
            incrementActionError("WCCReceiver");
            return;
        }


        if (action != null && !(action.equalsIgnoreCase("add") || action.equalsIgnoreCase("delete"))) {
            logger.info("No action specified for this request");
            setResponse(422,_context,"fail : no action defined");
            incrementActionError("WCCReceiver");
            return;
        }

        if (lastModified != null) {
            SimpleDateFormat pes_date_format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm'Z'");
            Date lastModDate = null;
            try {
                lastModDate = pes_date_format.parse(lastModified);
                LocalDate localDate = lastModDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                int year = localDate.getYear();
                if (year < 2016) {
                    logger.debug("last-modified date is before 2016 - drop this document");
                    statusLogger.info(String.format("WCC receiver: doc id %s last mod date  < 2016.",filename));
                    // first write its unique id to a file so we can later send an acknowledgement for it

                    //postAckToQueue(filename);
                    postAckToCache(filename,ackCacheFolder);

                    setResponse(200,_context,"success : doc dropped due to date");
                    incrementActionResponse("WCCReceiver");
                    return;
                }
            } catch (ParseException pe) {
                logger.warn("Failed to parse last-modified date from request : value is " + lastModified);
            }
        }

        logger.info("Request for Doc ID = " + filename + " action = " + action + " index = " + index);

        // index is likely to contain an underbar and date suffix - we need to remove this

        if (index.indexOf("_") > 0){
            logger.debug(String.format("index name reset from '%s' to '%s'",index, index.substring(0,index.indexOf("_"))));
            index = index.substring(0,index.indexOf("_"));
        }


        // we get the queue to post to from the properties
        Set<String> queueMappingKeys  = PluginConfig.getPluginProperties(thisCommand.getName()).getAllSubPropertyNames("queueMapping");
        for (String queueMappingKey : queueMappingKeys ){
            Set<String> queueList = PluginConfig.getPluginProperties(thisCommand.getName()).getPropertiesByName ("queueMapping::"+queueMappingKey);
            for(String queue : queueList){
                if (queue.equalsIgnoreCase(index)){
                    remoteQueue = queueMappingKey;
                }
            }
        }

        if (remoteQueue == null){
            statusLogger.info(String.format("WCC receiver: doc id %s destination %s unknown.",index,filename));
            logger.info("index not recognised - " + index + " for doc id " + filename);

            //postAckToQueue(filename);
            postAckToCache(filename,ackCacheFolder);
            setResponse(200,_context,"success : doc dropped due to unknown destination");

            //setResponse(422,_context,String.format("fail : index %s not recognised for doc id %s",index,filename));
            //logger.info("Fail for " + filename);
            incrementActionResponse("WCCReceiver");
            return;
        }


        inboundPesWebApiRequest inboundRequest = null;
        try {
            inboundRequest = new inboundPesWebApiRequest(headers,logger);
        } catch (BadRequestException e) {
            statusLogger.info(String.format("WCC receiver: doc id %s failed processing.",index,filename));
            logger.warn("Failed to process inboundRequest for doc id." + filename + "." + e.getLocalizedMessage());
        }
        iDocument doc = null;

        if (action.equalsIgnoreCase("add") || action.equalsIgnoreCase("delete")) {

            if (action.equalsIgnoreCase("add")) {
                logger.trace("ADD request for " + filename);
            } else {
                logger.trace("DELETE request for " + filename);
            }

            doc = inboundRequest.getIDoc();

            try {
                if (doc.getFieldValue("isobsolete").equalsIgnoreCase("true")) {
                    statusLogger.info(String.format("WCC receiver: doc id %s date is before 2016. dropping",filename));
                    logger.debug("CreateDate date is before 2016 - drop document. ID = " + doc.getId());
                    logger.info("Success for " + doc.getId());
                    //postAckToQueue(filename);
                    postAckToCache(filename,ackCacheFolder);
                    setResponse(200, _context, "success : doc dropped due to date");
                    incrementActionResponse("WCCReceiver");
                    return;
                }
                // don't care if its not there
            } catch (BadMetaException bme) {}

            doc.setAction(action);


            // set the iDoc content to the byte stream
            if (action.equalsIgnoreCase("add")) {
                InputStream is = _context.getQueryRequest().getRequest().getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                doc.setData(buffer.toByteArray());
            }


            if (doc != null ) {

                try {
                    if (doc != null && doc.getFields() != null){
                        String childDocFileName = doc.getFieldValue("filename");
                        String childDocId = doc.getId();
                        logger.info("Writing Doc to pending cache - filename = " + childDocFileName + " ID = "+ childDocId );


                        if (null != pendingCacheRoot) {


                            File pendingDirectory = new File(pendingCacheRoot);
                            if (! pendingDirectory.exists()) {
                                pendingDirectory.mkdir();
                            }

                            // need a directory for the queue we want to put this on
                            String filePath = pendingCacheRoot + File.separator + remoteQueue;
                            File queueDirectory = new File(filePath);
                            if (! queueDirectory.exists()) {
                                queueDirectory.mkdir();
                            }

                            // create the filename based on time
                            String fileName = filePath + File.separator + getCounterTime() +".idoc";

                            try {
                                // serialize the idoc to file
                                FileOutputStream fileOut = new FileOutputStream(fileName);
                                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                                out.writeObject(doc);
                                statusLogger.info(String.format("WCC receiver: doc id %s received. Action = %s, destination = %s. cache filename = %s",filename,action,index,fileName));

                                out.close();
                                fileOut.close();


                            } catch (IOException ioe) {
                                logger.info("Failed to write to cache - " + filename + "." + ioe.getLocalizedMessage());
                                statusLogger.warn(String.format("WCC receiver: doc id %s Failed to write to cache. (500 response. IO exception) ",doc.getId()));
                                setResponse(500,_context,String.format("Failed to serialize document to pending Cache %s",doc.getId()));
                                logger.info("Fail for " + fileName);
                                incrementActionError("WCCReceiver");
                                return;
                            }
                        } else {
                            // badness - no definition for the pending cache root
                            logger.info("index not recognised - " + index);
                            statusLogger.warn(String.format("WCC receiver: doc id %s Failed to write to cache. (200 response. bad index)",doc.getId()));
                            //setResponse(422,_context,String.format("fail : index %s not recognised",index));
                            //logger.info("Fail for " + filename);
                            logger.info("Fail for " + doc.getId());
                            setResponse(200, _context, "success : doc dropped due to unknown destination");
                            incrementActionResponse("WCCReceiver");
                            return;
                        }

                    } else {
                        logger.warn("Trying to process a child document that has NULL for metadata - failed");
                        statusLogger.warn(String.format("WCC receiver: doc id %s Failed to write to cache. (200 repsonse. null metadata)",doc.getId()));
                        setResponse(200,_context,String.format("fail on ddocname %s",doc.getId()));
                        logger.info("Success for " + doc.getId());
                        incrementActionResponse("WCCReceiver");
                        return;
                    }

                } catch (Exception e) {
                    logger.warn("Failed to post the document: " + e.getLocalizedMessage());
                    statusLogger.warn(String.format("WCC receiver: doc id %s Failed to write to cache. (500 response. misc. catch)",doc.getId()));
                    if (logger.isDebugEnabled()){e.printStackTrace();}
                    _context.setPayload(new Payload("{\"response\":\"fail : " + e.getLocalizedMessage() + "\"}"));
                    setResponse(500, _context, String.format("fail : %s", e.getLocalizedMessage()));
                    logger.warn("Fail for " + doc.getId());
                    incrementActionError("WCCReceiver");
                    return;

                }

                setResponse(200,_context,"success");
                logger.info("Success for " + doc.getId());
                incrementActionResponse("WCCReceiver");
                return;
            }
        } else {
            logger.warn("iDoc is empty for doc id " +filename);
        }

        setResponse(200,_context,"success");
        incrementActionResponse("WCCReceiver");
        try {
            Thread.sleep(DELAY_AFTER_DELETE);
        } catch (InterruptedException ie){
        }

        return;

    }
    private void postAckToCache(@NotNull String _docId,String _folder){

        try {
            if (null != ackCacheRoot) {

                File ackDirectory = new File(ackCacheRoot);
                if (! ackDirectory.exists()) {
                    ackDirectory.mkdir();
                }

                File ackDirectoryFolder = new File(ackCacheRoot+File.separator+_folder);
                if (! ackDirectoryFolder.exists()) {
                    ackDirectoryFolder.mkdir();
                }

                String fileName = ackCacheRoot + File.separator + _folder + File.separator + _docId;
                BufferedWriter wr = new BufferedWriter(new FileWriter(fileName));
                wr.write(_docId);
                wr.close();
                statusLogger.info(String.format("WCC receiver: doc id %s Wrote ack. to cache", _docId));
            } else {
                logger.warn("Failed to write an acknowledgement message to cache - not defined?");
                statusLogger.warn(String.format("WCC receiver: doc id %s Writing ack. to cache failed",_docId));
            }
        } catch (Exception e){
            logger.warn(String.format("Failed to write an acknowledgement message to cache '%s'",ackCacheRoot));
            statusLogger.warn(String.format("WCC receiver: doc id %s Writing ack. to cache failed",_docId));
        }
    }


    private void setResponse( int _code, CommandPluginContext _context, String _msg){
        try {
            _context.getQueryResponse().setStatusCode(_code);
            _context.setPayload(new Payload("{\"response\":\""+ _msg+"\"}"));
            _context.getQueryResponse().setPayload(_context.getPayload().toJson());
        } catch (Exception e) {
            logger.warn("problem trying to set the response for WCCCommand." + e.getLocalizedMessage());
        }
    }

}
