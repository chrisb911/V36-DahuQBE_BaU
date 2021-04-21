package com.dahu.qbe.events;

import com.dahu.core.document.DEFDocument;
import com.dahu.core.interfaces.iDocument;
import com.dahu.core.interfaces.iQueue;
import com.dahu.core.logging.DEFLogManager;
import com.dahu.core.utils.DahuStringUtils;
import com.dahu.def.config.PluginConfig;
import com.dahu.def.config.ServerConfig;
import com.dahu.def.exception.BadArgumentException;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.exception.ContextException;
import com.dahu.def.exception.DEFQueueException;
import com.dahu.def.plugins.EventPluginBase;
import com.dahu.def.types.Event;
import com.dahu.def.types.Metric;
import com.dahu.def.types.Queue2_0;
import com.dahu.qbe.DEF_QBE_BAU_CONSTANTS;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;


public class DEFDocumentTransfer extends EventPluginBase {

    private Logger statusLogger_;
    private String remoteCache_ = null;
    private String localCache_ = null;
    private Queue2_0 queue_;
    private Event event_;

    public DEFDocumentTransfer(Level _level, Event _plugin){
        super(_level,_plugin);
        statusLogger_ = DEFLogManager.getLogger("DahuQBE-BAU",Level.INFO);
        event_ = _plugin;
    }

    @Override
    public void doStartup(Event _event) throws ContextException, BadArgumentException, BadConfigurationException, UnsupportedEncodingException {
        if (outputQueues.size() <1){
            logger.warn("Event should be configured with one output queue. The config has " + outputQueues.size());
        } else {
            queue_ = (Queue2_0) ServerConfig.getQueues().get(PluginConfig.getFirstOutputQueue(_event.getName()));
        }

        remoteCache_ = PluginConfig.getPluginProperties(_event.getName()).getPropertyByName(DEF_QBE_BAU_CONSTANTS.CONFIG_PES_REMOTECACHE);
        localCache_ = PluginConfig.getPluginProperties(_event.getName()).getPropertyByName(DEF_QBE_BAU_CONSTANTS.CONFIG_PES_LOCALCACHE);

        // create the local dir if it doesn't exist already
        File localDirectory = new File(localCache_);
        if (! localDirectory.exists()) {
            localDirectory.mkdir();
        }
    }

    @Override
    public void doShutdown(Event _event) {

    }

    @Override
    public void doRun(Event _event) {
        // get list of docs in remoteDir
        // for each doc in list...
        //    copy doc in list to local cache
        //    if copy successful post on queue
        //    if post successful, delete doc  from remoteDir
        if (null == remoteCache_){
            logger.warn(String.format("Remote cache missing or undefined in configuration. check value of '%s'",DEF_QBE_BAU_CONSTANTS.CONFIG_PES_REMOTECACHE));
            return;
        }
        if (null == localCache_){
            logger.warn(String.format("Local cache missing or undefined in configuration. check value of '%s'",DEF_QBE_BAU_CONSTANTS.CONFIG_PES_LOCALCACHE));
            return;
        }
        // get list of all the files in the remote cache directory.
        List<String> remoteFilesList = new ArrayList<>();
        File dirFolder = new File(remoteCache_ );
        File[] files = dirFolder.listFiles();
        if (null != files && files.length >0) {
            for (final File f : files) {
                if (f.isFile()) {
                    remoteFilesList.add(f.getAbsolutePath());
                }
            }
        }


        if (remoteFilesList.size() > 0) {
            statusLogger_.info(String.format("PES Doc Transfer: Transferring  %s docs",remoteFilesList.size() ));
            int transferCount = 0;
            int deleteCount = 0;
            // now, for this batch of files process each one in turn, copying locally
            for (String fileName : remoteFilesList) {
                // get the local file name
                String localFileName = localCache_ + File.separator + fileName.substring(fileName.lastIndexOf(File.separator));
                FileChannel sourceChannel = null;
                FileChannel destChannel = null;
                boolean copied = false;
                try {
                    sourceChannel = new FileInputStream(new File(fileName)).getChannel();
                    destChannel = new FileOutputStream(new File(localFileName)).getChannel();
                    destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                    copied = true;
                } catch (IOException e) {
                    logger.warn(String.format("Failed to copy file %s to %s",fileName,localFileName));
                } finally {
                    try {
                        sourceChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        destChannel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (copied) {
                    boolean queued = false;
                    transferCount++;
                    // post on queue
                    try {
                        // create an idoc with an id and a source and call toJson and put that on the queue
                        // ... processor stage will throw this away, create a new idoc from on disk file, and add filepatch as metadata
                        iDocument doc = new DEFDocument(localFileName,"file");
                        doc.addField("filepath",localFileName);

                        queue_.postTextMessage(doc.toJson());
                        queued = true;
                    } catch (DEFQueueException e) {
                        logger.warn(String.format("Failed to post message '%s' on queue '%s'", localFileName, queue_.getName()));
                    }
                    if (queued) {
                        deleteCount++;
                        // copied and queued - now we can delete to remote file
                        File deleteFile = new File(fileName);
                        try {
                            deleteFile.delete();
                        } catch (Exception e) {
                            logger.warn(String.format("Failed to delete message '%s'. %s", localFileName,e.getLocalizedMessage() ));
                            statusLogger_.warn(String.format("PES Doc Transfer: Failed to remove %d of %d docs",deleteCount,remoteFilesList.size() ));
                        }
                    }
                }
            }
            if (transferCount == remoteFilesList.size()){
                statusLogger_.info(String.format("PES Doc Transfer: Transferred  %s docs",remoteFilesList.size() ));
            } else {
                statusLogger_.warn(String.format("PES Doc Transfer: Failed to transfer %d of %d docs",transferCount,remoteFilesList.size() ));
            }
            if (deleteCount == remoteFilesList.size()){
                statusLogger_.info(String.format("PES Doc Transfer: Removed  %s remote docs",remoteFilesList.size() ));
            } else {
                statusLogger_.warn(String.format("PES Doc Transfer: Failed to remove %d of %d docs",deleteCount,remoteFilesList.size() ));
            }
        }
    }
}
