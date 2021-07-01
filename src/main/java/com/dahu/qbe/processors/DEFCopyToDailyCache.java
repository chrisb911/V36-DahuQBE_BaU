package com.dahu.qbe.processors;

import com.dahu.core.abstractcomponent.AbstractProcessor;
import com.dahu.core.exception.BadMetaException;
import com.dahu.core.interfaces.iDocument;
import com.dahu.core.logging.DEFLogManager;
import com.dahu.qbe.utils.fileSharder;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.types.Component;
import net.lingala.zip4j.io.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;



public class DEFCopyToDailyCache extends AbstractProcessor {

    private static final String CONFIG_INPUTFOLDER = "inputFolder";
    private static final String CONFIG_OUTPUTFOLDER = "outputFolder";
    private static final String CONFIG_FOLDERDEPTH = "folderDepth";
    private static final String CONFIG_ZIPPREFIX = "zipPrefix";
    private static final String CONFIG_ZIPPASSWORD = "zipPassword";
    private static final String CONFIG_DROPFOLDER = "dropFolder";
    private static final int CONFIG_DEFAULTFOLDERDEPTH = 4;
    private String rootInputFolder = null;
    private String rootOutputFolder = null;
    private String zipPrefix = null;
    private String zipPassword = null;
    private String dropFolder = null;
    private int folderDepth; // default to 4
    private Logger statusLogger;

    public DEFCopyToDailyCache(Level _level, Component _component) {
        super(_level, _component);
    }

    @Override
    public boolean supportsDelete() {
        return true;
    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {
        rootInputFolder = getPropertyValue(CONFIG_INPUTFOLDER, null);
        rootOutputFolder = getPropertyValue(CONFIG_OUTPUTFOLDER, null);
        zipPrefix = getPropertyValue(CONFIG_ZIPPREFIX, null);
        dropFolder = getPropertyValue(CONFIG_DROPFOLDER, null);
        folderDepth = getPropertyValue(CONFIG_FOLDERDEPTH, CONFIG_DEFAULTFOLDERDEPTH);
        zipPassword = getPropertyValue(CONFIG_ZIPPASSWORD, "");
        statusLogger = DEFLogManager.getLogger("DahuQBE-BAU",Level.INFO);

        if (rootOutputFolder == null || rootInputFolder == null || zipPrefix == null || dropFolder == null) {
            logger.warn(Thread.currentThread().getId() + " : Missing or bad config for PESDocFilter - needs at least  '" + CONFIG_INPUTFOLDER + "', '" + CONFIG_OUTPUTFOLDER + "', '" + CONFIG_FOLDERDEPTH + "', '" + CONFIG_ZIPPREFIX + "' or '" + CONFIG_DROPFOLDER + "'");
            throw new BadConfigurationException("Missing or bad config for PESDocFilter - needs at least  '" + CONFIG_INPUTFOLDER + "', '" + CONFIG_OUTPUTFOLDER + "', '" + CONFIG_FOLDERDEPTH + "', '" + CONFIG_ZIPPREFIX + "' or '" + CONFIG_ZIPPREFIX + "'");
        }

        logger.debug("CopyToDailyCache - root output folder: " + rootOutputFolder);
        logger.debug("CopyToDailyCache - root input folder: " + rootInputFolder);

        // run the zipFiles checker - looks to see if we've run over midnight to generate new files
        // and creates the zip file for the cache
        ScheduledExecutorService zipPrevCache = Executors.newScheduledThreadPool(1);
        zipPrevCache.scheduleAtFixedRate(new Runnable() {
            public void run() {
                zipFiles(logger, rootOutputFolder, rootInputFolder, zipPrefix, zipPassword, dropFolder);
            }
        }, 1, 10, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public iDocument process(iDocument _iDoc) {

        // first thing, we expect an idoc to have already been processed by the PES filters before we are invoked - and also
        // that the 'sync' flag will have been set if everything went smoothly. We aren't going to touch the iDoc itself, but
        // we do need to figure out what was written and where

        try {
            if (null != _iDoc.getFieldValue("SYNC") && _iDoc.getFieldValue("SYNC").equalsIgnoreCase("TRUE")) {

                // we also need to know if this is an 'add' or a 'delete'
                String action = _iDoc.getAction();
                if (null == action || !(action.equalsIgnoreCase("insert") || action.equalsIgnoreCase("delete"))) {
                    statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for addition", _iDoc.getId()));
                    logger.warn("no action provided (should be either 'add' or 'delete')");
                }

                // so we are good to go - get the path to the doc
                String indexPath = null;
                try {
                    indexPath = _iDoc.getFieldValue("index");
                } catch (BadMetaException e) {
                    statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for addition", _iDoc.getId()));
                    logger.warn("no index found in the iDoc - can't properly set the index in the output directory.");
                }

                String rootPath = rootInputFolder;
                if (null != indexPath) {
                    rootPath = rootPath.concat(File.separator).concat(indexPath);
                } else {
                    statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for addition", _iDoc.getId()));
                    logger.warn("no index found in the iDoc - can't properly set the index name in the output directory - setting to 'unknown'");
                    rootPath = rootPath.concat("unknown");
                }

                if (action.equalsIgnoreCase("insert")) {
                    // get a list of the HTML files we want to copy

                    Set<String> filesToCopy = fileSharder.getHtmlFilesAtPath(rootPath, _iDoc.getId(), folderDepth);

                    if (filesToCopy.size() > 0) {
                        // good - we have a set of files to copy - work out today's folder name


                        for (String fromFile : filesToCopy) {
                            // remove the input path from the string
                            if (fromFile.toLowerCase().startsWith(rootInputFolder.toLowerCase())) {
                                String subFile = fromFile.substring(rootInputFolder.length());
                                String toFile = getTodayPath(rootOutputFolder) + subFile;

                                Path fromFilePath = Paths.get(fromFile);
                                Path toFilePath = Paths.get(toFile);

                                // first, make sure we have the destination directory structure in place. trim the file off the end of the string
                                String toDirectory = toFile.substring(0, toFile.lastIndexOf(File.separator));
                                new File(toDirectory).mkdirs();
                                try {
                                    // lastly, before we copy, see if its already there and if it is, delete it
                                    File testExists = new File(toFile);
                                    if (testExists.exists()) {
                                        // delete it
                                        if (!testExists.delete()) {
                                            statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for addition", _iDoc.getId()));
                                            logger.warn(String.format("Failed to remove file prior to copy:  '%s'", toFile));
                                            _iDoc.addField("SYNC", "false");
                                        }
                                    }

                                    Files.copy(fromFilePath, toFilePath);
                                    statusLogger.info(String.format("Copy to Daily Cache: Copied  doc %s to %s", _iDoc.getId(), toFile));
                                    logger.info("Copied to daily cache: " + toFile);

                                    // write to the manifest
                                    String updateManifest = getTodayManifest(rootOutputFolder, dropFolder, "update", zipPrefix);
                                    writeToManifest(updateManifest, "<update documentname=\"" + fromFilePath + "\" />");

                                } catch (IOException e) {
                                    statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for addition", _iDoc.getId()));
                                    logger.warn(String.format("Failed to copy file '%s' to '%s'", subFile, toFile));
                                    _iDoc.addField("SYNC", "false");
                                }
                            } else {
                                statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for addition", _iDoc.getId()));
                                logger.warn("unable to get document path from filename (doesn't start with input folder)");
                                _iDoc.addField("SYNC", "false");
                            }
                        }

                    }
                } else {

                    // just need to delete it from the cache if its there (might be if it was added earlier in this cache cycle.)


                    String deleteName = fileSharder.getShardedPath(rootPath, _iDoc.getId(), folderDepth);
                    if (null != deleteName) {


                        if (deleteName.toLowerCase().startsWith(rootInputFolder.toLowerCase())) {
                            String subFile = deleteName.substring(rootInputFolder.length());
                            String delFile = getTodayPath(rootOutputFolder) + subFile;

                            File testExists = new File(delFile);
                            if (testExists.exists()) {
                                try {
                                    deleteDirectory(testExists);
                                    // clean up the path to the directory if necessary
                                    String newPath = testExists.getAbsolutePath().substring(0, testExists.getAbsolutePath().lastIndexOf(File.separator));
                                    if (null != newPath) {
                                        deleteDirectoryTree(newPath);
                                        statusLogger.info(String.format("Copy to Daily Cache: deleted doc %s from ", _iDoc.getId(), newPath));
                                    }
                                } catch (IOException e) {
                                    statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for deletion", _iDoc.getId()));
                                    logger.warn("failed to delete document directory: " + rootPath + File.separator + _iDoc.getId());
                                    if (logger.isTraceEnabled()) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            // write to the manifest
                            String updateManifest = getTodayManifest(rootOutputFolder, dropFolder, "delete", zipPrefix);
                            writeToManifest(updateManifest, "<delete documentname=\"" + rootInputFolder + subFile + "\" />");
                        } else {
                            statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for deletion", _iDoc.getId()));
                            logger.warn("unable to get document path from filename (doesn't start with input folder)");
                            _iDoc.addField("SYNC", "false");
                        }

                    } else {
                        statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s for deletion", _iDoc.getId()));
                        logger.warn("failed to get a sharded path for " + rootPath + File.separator + _iDoc.getId());
                        _iDoc.addField("SYNC", "false");
                    }
                }
            }
        } catch (BadMetaException e) {
            statusLogger.warn(String.format("Copy to Daily Cache: Failed to process doc %s", _iDoc.getId()));
            logger.warn("Unable to get value for 'sync' from the iDoc with key " + _iDoc.getId());
            _iDoc.addField("SYNC", "false");
        }

        return _iDoc;
    }

    private static synchronized void zipFiles(Logger _logger, String _rootDir, String _originDir, String _zipPrefix, String _zipPassword, String _dropFolder) {
        // simply look for yesterday's cache, and if its there but we don't have a zip already, zip it up now

        // is the dropFolder actually there? If not, create it
        File dropDir = new File(_rootDir + File.separator + _dropFolder);
        if (!dropDir.exists()) {
            dropDir.mkdirs();
        }

        // If it doesn't exist, then that means we are creating a new one and might have
        // rolled over. If we've rolled over, we can zip up yesterdays data.

        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        String yesterdayDate = new SimpleDateFormat("yyyyMMdd").format(cal.getTime());
        String yesterdayFolder = _rootDir + File.separator + "cache-" + yesterdayDate;
        File yesterdayDir = new File(yesterdayFolder);
        if (yesterdayDir.exists()) {
            // we have a cache for yesterday - but is it already zipped or have we already processed it?
            // processed means we will have a .delivered file
            String deliveredFile = _rootDir + File.separator + _dropFolder + File.separator + _zipPrefix + "-" + yesterdayDate + ".delivered";

            File testDeliveredFile = new File(deliveredFile);
            if (!testDeliveredFile.exists()) {

                String yesterdayZip = _rootDir + File.separator + _dropFolder + File.separator + _zipPrefix + "-" + yesterdayDate + ".zip";

                File testYesterdayZip = new File(yesterdayZip);
                if (!testYesterdayZip.exists()) {

                    _logger.info("Zipping the daily export cache " + yesterdayFolder);

                    // first build a list of all the html files in the directory
                    Set<String> filesList = new HashSet<>();
                    recurseHtmlFiles(filesList, yesterdayDir);

                    ZipOutputStream outputStream = null;
                    InputStream inputStream = null;

                    try {
                        outputStream = new ZipOutputStream(new FileOutputStream(new File(yesterdayZip)));
                        ZipParameters parameters = new ZipParameters();
                        parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
                        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
                        if (null != _zipPassword && !(_zipPassword.equals(""))) {
                            parameters.setEncryptFiles(true);
                            parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD);
                            parameters.setPassword(_zipPassword);
                        }

                        for (String thisFile : filesList) {

                            String shortPath = _rootDir + File.separator + "cache-" + yesterdayDate;
                            String rootPath = _originDir + thisFile.substring(thisFile.lastIndexOf(shortPath) + shortPath.length());

                            File file = new File(thisFile);

                            parameters.setIncludeRootFolder(false);

                            parameters.setRootFolderInZip(rootPath);
                            outputStream.putNextEntry(file, parameters);
                            

                            inputStream = new FileInputStream(file);
                            byte[] byteBuff = new byte[4096];
                            int readLen = -1;

                            while ((readLen = inputStream.read(byteBuff)) != -1) {
                                outputStream.write(byteBuff, 0, readLen);
                            }

                            outputStream.closeEntry();
                            inputStream.close();

                        }
                        outputStream.finish();


                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (null != outputStream) {
                            try {
                                outputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        if (null != inputStream) {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        // last thing - create a '.comp' file to signal that the data is ready to copy/upload
                        String yesterdayComp = _rootDir + File.separator + _dropFolder + File.separator + _zipPrefix + "-" + yesterdayDate + ".comp";
                        File compFile = new File(yesterdayComp);
                        try {
                            compFile.createNewFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }


    private static void recurseHtmlFiles(Set<String> _filesList, File _dir) {

        File[] files = _dir.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                if (file.getName().toLowerCase().indexOf(".html") > 0) {
                    _filesList.add(file.getAbsolutePath());
                }
            } else {
                recurseHtmlFiles(_filesList, file);
            }
        }
    }


    private static void writeToManifest(String _file, String _data) {

        Charset charSet = Charset.forName("UTF8");

        Path path = Paths.get(_file);

        try {
            BufferedWriter writer = Files.newBufferedWriter(path, charSet, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            writer.append(_data, 0, _data.length());
            writer.newLine();
            writer.flush();
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static String getTodayManifest(String _rootDir, String _dropFolder, String _type, String _prefix) {
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        // get the host name and date stamp for the data files


        return _rootDir + File.separator + _dropFolder + File.separator + _prefix + "-" + date + "-" + _type + ".xml";
    }

    private static String getTodayPath(String _rootDir) {

        // create the name of the cache directory based on today's date
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String folder = _rootDir + File.separator + "cache-" + date;

        // make the new folder if we need to
        new File(folder).mkdirs();

        return folder;
    }

    private void deleteDirectory(File _file) throws IOException {
        if (_file.isDirectory()) {
            File[] entries = _file.listFiles();
            if (null != entries) {
                for (File entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        if (!_file.delete()) {
            throw new IOException("Failed to delete directory " + _file);
        }
    }

    private void deleteDirectoryTree(String _dirPath) throws IOException {
        // walks up the tree deleting the directories if they contain no sub-directories and files (other than the annoying mac DS_Store file)

        File file = new File(_dirPath);
        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            // if no entries or one entry which is a mac DS_Store file then delete

            if (null == entries || entries.length == 0 ||
                    (entries.length == 1 && entries[0].getName().equalsIgnoreCase(".DS_Store"))
            ) {
                // we can delete this one
                for (File entry : entries) {
                    entry.delete();
                }
                file.delete();
                // get the next one
                String newPath = _dirPath.substring(0, _dirPath.lastIndexOf(File.separator));
                if (null != newPath) {
                    deleteDirectoryTree(newPath);
                }
            }
        }
    }
}
