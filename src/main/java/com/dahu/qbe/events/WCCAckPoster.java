package com.dahu.qbe.events;

import com.dahu.core.logging.DEFLogManager;
import com.dahu.core.utils.DahuStringUtils;
import com.dahu.def.config.PluginConfig;
import com.dahu.def.exception.BadArgumentException;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.exception.ContextException;
import com.dahu.def.plugins.EventPluginBase;
import com.dahu.def.types.Event;
import com.dahu.def.types.Properties;
import com.dahu.qbe.DEF_QBE_BAU_CONSTANTS;
import com.dahu.qbe.utils.WCCSynch;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class WCCAckPoster extends EventPluginBase {

    private Logger statusLogger;
    private Boolean stopProcessing = false;
    private String ackDirectoryRoot_ = null;
    private Event event_;
    private int batchSize_;
    private String username_;
    private String password_;
    private String server_;
    private String port_;
    private String protocol_;
    private String certPath_;
    private String urlBase_;
    private String urlServicePath_;


    public WCCAckPoster(Level _level, Event _plugin){
        super(_level,_plugin);
        statusLogger = DEFLogManager.getLogger("DahuQBE-BAU",Level.INFO);
        event_ = _plugin;
    }

    @Override
    public void doStartup(Event _event) throws ContextException, BadArgumentException, BadConfigurationException, UnsupportedEncodingException {

        Properties properties = PluginConfig.getPluginProperties(_event.getName());

        ackDirectoryRoot_ = PluginConfig.getPluginProperties(_event.getName()).getPropertyByName(DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKCACHEROOT);
        batchSize_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKBATCHSIZE,10);
        username_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_USERNAME, null);
        password_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_PASSWORD, null);
        server_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_SERVER, null);
        port_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_PORT, null);
        protocol_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_PROTOCOL, null);
        certPath_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_CERTPATH, null);
        urlBase_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_URLBASE, "cs");
        urlServicePath_ = PluginConfig.getPluginPropertyValue(_event,DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_URLSERVICEPATH, "idcplg");

        if (null == ackDirectoryRoot_  ){
            logger.warn("Unable to start WCCAckPoster event. value for '" + DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKCACHEROOT + "' not found or empty");
        }
    }

    @Override
    public void doShutdown(Event _event) {

    }

    @Override
    public void doRun(Event _event) {
        // get the list of ackCache directories (including remote)
        // get list of files in the ackCache
        // for each file in the list
        // post ack to WCC
        // delete the file

        // we always wait a little bit - just in case the event happened to have fired milliseconds
        // after the item was processed. this tends to cause a few issues at the WCC end.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (null != ackDirectoryRoot_ && ackDirectoryRoot_.length()>0){
            if (stopProcessing){
                logger.error("Failed to delete the files from the pending Cache. process suspended until the issue can be resolved.");
            } else {
                List<String> fileList = new ArrayList<>();

                File file = new File(ackDirectoryRoot_);
                String[] directories = file.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File current, String name) {
                        return new File(current, name).isDirectory();
                    }
                });

                if (null != directories && directories.length >0){

                    for (String directory : directories) {

                        File dirFolder = new File(ackDirectoryRoot_ + File.separator + directory);
                        for (final File f : dirFolder.listFiles()) {
                            if (f.isFile()) {
                                fileList.add(f.getAbsolutePath() + "::" + f.getName());
                            }
                        }
                    }
                }
                // now we have a list of files to post as acknowledgements. we do them in batches

                ArrayList<String> msgList = new ArrayList<>();
                ArrayList<String> deleteList = new ArrayList<>();
                long itemMaxCount = fileList.size();

                if (itemMaxCount>0) {
                    statusLogger.info(String.format("WCC acknowledger: Processing %d items found in ack Caches ", itemMaxCount));

                    int statusCount = 0;
                    int batchCount = 0;

                    for (String msgPath : fileList) {
                        String msg = DahuStringUtils.after(msgPath, "::");
                        String deletePath = DahuStringUtils.before(msgPath, "::");
                        msgList.add(msg);
                        deleteList.add(deletePath);
                        batchCount++;
                        statusCount++;
                        if (batchCount == batchSize_) {
                            statusLogger.info(String.format("WCC acknowledger: Processing batch of %d items", msgList.size()));
                            if (process(msgList)) {
                                // delete the docs we processed
                                deleteDocs(deleteList);
                            }
                            batchCount = 0;
                            msgList.clear();
                        }
                    }
                    statusLogger.info(String.format("WCC acknowledger: Processing batch of %d items", msgList.size()));
                    if (process(msgList)) {
                        deleteDocs(deleteList);
                    }
                    // check that we did process all we thought we'd process
                    if (statusCount == itemMaxCount && itemMaxCount > 0) {
                        statusLogger.info(String.format("WCC acknowledger: Processed all %d items", itemMaxCount));
                    } else {
                        if (itemMaxCount > 0)
                            statusLogger.warn(String.format("WCC acknowledger: Failed to process all %d items. Processed %d", itemMaxCount, statusCount));
                    }
                }
            }

        }
    }
    private void deleteDocs(List<String> _deleteList){
        if (null != _deleteList || _deleteList.size() >0){
            for (String deleteFile : _deleteList){
                logger.debug(String.format("deleting file '%s'",deleteFile));
                File df = new File(deleteFile);
                try {
                    df.delete();
                } catch (Exception ioe){
                    logger.warn(String.format("Failed to delete document '%s'. %s",deleteFile,ioe.getLocalizedMessage()));
                }
            }
        }
    }
    private boolean process(List<String> _msgList){

        logger.debug(String.format("sending synch to %s:\\\\%s:%s\\%s\\",protocol_,server_,port_,urlBase_,urlServicePath_  ));

        WCCSynch synch = new WCCSynch(logger, protocol_, server_, port_, username_, password_, certPath_, urlBase_, urlServicePath_);

        boolean success = synch.notifyWCCSuccessAdds(_msgList);

        if (!success)
            logger.warn("Failed to synchronized documents " + _msgList.toString());
        else
            logger.debug("Synchronized documents " + _msgList.toString());
        return success;
    }


}
