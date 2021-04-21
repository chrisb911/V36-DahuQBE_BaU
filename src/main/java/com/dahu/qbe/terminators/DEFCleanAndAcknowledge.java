package com.dahu.qbe.terminators;

import com.dahu.core.abstractcomponent.AbstractTerminator;
import com.dahu.core.interfaces.iDocument;
import com.dahu.core.logging.DEFLogManager;
import com.dahu.def.config.PluginConfig;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.types.Component;
import com.dahu.qbe.DEF_QBE_BAU_CONSTANTS;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class DEFCleanAndAcknowledge extends AbstractTerminator {

    private Logger statusLogger;
    Component _component = null;
    String ackCacheRoot = null;
    String ackCacheFolder = null;

    public DEFCleanAndAcknowledge(Level _level, Component _resource) throws RuntimeException {
        super(_level, _resource);
        _component = _resource;
    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {
        statusLogger = DEFLogManager.getLogger("DahuQBE-BAU",Level.INFO);
        ackCacheRoot = getPropertyValue(DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKCACHEROOT, null);
        ackCacheFolder = getPropertyValue(DEF_QBE_BAU_CONSTANTS.CONFIG_WCC_ACKCACHEFOLDER, null);
        return true;
    }

    @Override
    public boolean terminate(iDocument _iDoc) {
        // post the id of this message on the ackQueue.
        try {
            if (null != _iDoc.getFieldValue("SYNC") && _iDoc.getFieldValue("SYNC").equalsIgnoreCase("TRUE")) {

                if (ackCacheRoot == null) {
                    logger.error("No acknowledgement cache directory specified");
                    return false;
                }
                if (ackCacheFolder == null) {
                    logger.error("No acknowledgement cache folder specified");
                    return false;
                }

                File ackRootDirectory = new File(ackCacheRoot);
                if (!ackRootDirectory.exists()) {
                    ackRootDirectory.mkdir();
                }

                String path = ackCacheRoot + File.separator + ackCacheFolder;
                // check it exists and if it doesnt, create it.

                File ackDirectory = new File(path);
                if (!ackDirectory.exists()) {
                    ackDirectory.mkdir();
                }

                // write the doc id to the ack cache directory
                String id = _iDoc.getId();

                String fileName = ackCacheRoot + File.separator + ackCacheFolder + File.separator + id;
                BufferedWriter wr = new BufferedWriter(new FileWriter(fileName));
                wr.write(id);
                wr.close();
                statusLogger.info(String.format("DEF Clean & Acknowledge: doc id %s Wrote ack. to cache", id));
            } else {
                statusLogger.warn(String.format("DEF Clean & Acknowledge: Failed to post ack for  doc %s",_iDoc.getId()));
                logger.warn("Didn't Synchronized document " + _iDoc.getId() + ". Upstream failure");
            }

            // now we have written the ack file, we can clear out the local cache file
            String filePath = _iDoc.getFieldValue("filePath");
            if (null == filePath){
                logger.warn(String.format("DEF Clean & Acknowledge: Failed to get filePath from idoc - can't clean the pending file %s",_iDoc.getId()));
                return false;
            }
            File delFile = new File(filePath);
            delFile.delete();
            statusLogger.info(String.format("DEF Clean & Acknowledge: doc id %s deleted pending cache file", _iDoc.getId()));

        } catch (Exception e){
            statusLogger.warn(String.format("DEF Clean & Acknowledge: Failed to post ack for  doc %s. %s",_iDoc.getId(),e.getLocalizedMessage()));
            return false;
        }

        return true;
    }
}
