package com.dahu.qbe.processors;


import com.dahu.core.abstractcomponent.AbstractProcessor;
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
            logger.warn("failed to get value for 'filepath' from intermediate iDoc. " +e.getLocalizedMessage());
        }
        if (null != filePath) {

            FileInputStream fis = null;
            ObjectInputStream ois = null;
            try {
                fis = new FileInputStream(filePath);
                ois = new ObjectInputStream(fis);

                // Method for deserialization of object
                doc = (iDocument) ois.readObject();
            } catch (Exception e) {
                logger.warn("Failed to deserialize idoc: " + e.getLocalizedMessage());
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
        }
        if (null != doc) {
            // add the filepatch back in so we can delete it from the local cache later
            doc.addField("filePath",filePath);
            return doc;
        }
        else return iDocument;
    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {
        return true;
    }
}
