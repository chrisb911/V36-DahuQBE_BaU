package com.dahu.qbe.processors;


import com.dahu.core.abstractcomponent.AbstractProcessor;
import com.dahu.core.exception.BadDocumentException;
import com.dahu.core.exception.BadMetaException;
import com.dahu.core.interfaces.iDocument;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.types.Component;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class DEFLoadCachedDocument extends AbstractProcessor {
    private Logger statusLogger;
    private String name;

    public DEFLoadCachedDocument(Level _level, Component _component)   {
        super(_level, _component);
    }

    @Override
    public iDocument process(iDocument iDocument) {
        // get the file pointed to by filepath in the idoc, de-serialize the idoc file and
        // create a new idoc from it.
        iDocument doc = null;

        String filePath = null;
        try {
            filePath = iDocument.getFieldValue("filepath");
        } catch (BadMetaException e) {
            logger.warn("DEFLoadCachedDocument: failed to get value for 'filepath' from intermediate iDoc. " +e.getLocalizedMessage());
        }
        if (null != filePath) {

            FileInputStream fis = null;
            ObjectInputStream ois = null;
            try {
                fis = new FileInputStream(filePath);
                ois = new ObjectInputStream(fis);

                // Method for deserialization of object
                doc = (iDocument) ois.readObject();
                logger.debug("DEFLoadCachedDocument: deserialized original idoc: " +doc.getId());
            } catch (Exception e) {
                logger.warn("DEFLoadCachedDocument: Failed to deserialize idoc: " + e.getLocalizedMessage());
            }
            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != ois) {
                try {
                    ois.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            logger.warn("DEFLoadCachedDocument: filepath field field not found in idoc for iDoc" + iDocument.getId());
        }
        if (null != doc) {
            // add the filepath back in so we can delete it from the local cache later
            doc.addField("filePath", filePath);
            logger.debug("DEFLoadCachedDocument: returning new iDoc with bytes for " + doc.getId());
            byte[] testSize = new byte[0];
            try {
                testSize = doc.getData();
                if (null != testSize)
                    logger.debug("DEFLoadCachedDocument: iDoc content size is " + testSize.length);

            } catch (BadDocumentException e) {
                logger.warn("DEFLoadCachedDocument: unable to test size of deserialized content " + iDocument.getId() + ". " + filePath);
            }

            return doc;
        }
        else {
            logger.warn("DEFLoadCachedDocument: failed to add bytes from cached file to iDoc. " + filePath);
            return iDocument;
        }
    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {

        logger.debug("DEFLoadCachedDocument - initializing ");

        return true;
    }
}
