package com.dahu.qbe.processors;


import com.dahu.core.abstractcomponent.AbstractProcessor;
import com.dahu.core.exception.BadDocumentException;
import com.dahu.core.exception.BadMetaException;
import com.dahu.core.interfaces.iDocument;
import com.dahu.core.logging.DEFLogManager;
import com.dahu.def.exception.BadArgumentException;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.types.Component;
import com.dahu.qbe.utils.fileSharder;
import com.perceptive.documentfilters.*;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 *
 * HYLAND DOCUMENT FILTERS VECTOR PIPELINE STAGE
 *
 * Takes an iDocument with raw bytes attached to it
 * Converts the document to an HTML rendered format
 * Writes the HTML rendition to the local file system
 * Returns the iDocument UNCHANGED
 *
 *
 *
 * Created by :
 * Vince McNamara, Dahu
 * vince@dahu.co.uk
 * on 04/02/2019
 * copyright Dahu Ltd 2019
 * <p>
 * Changed by :
 */

public class PESFilterDocument extends AbstractProcessor {

    private Logger statusLogger;
    private String name;
    private DocumentFilters isys;
    private int isys_flags;

    private int fileCounterPerFolder=0;

    // Note - this is NOT the maximum number of files that can get written to a single output folder!!!!
    // It is the maximum number of input files to DocFilters that get written to a single output folder
    // if each input file generates 10 output files (parent plus nine images) then total files in output folder is 10*MAX_FILES_PER_FOLDER

    private static final String CONFIG_OUTPUTFORMAT = "outputFormat";
    private static final String CONFIG_OUTPUTFOLDER = "outputFolder";
    private static final String CONFIG_LICENSE = "license";
    private static final String CONFIG_OCR = "ocr";
    private static final String CONFIG_IMAGEURL = "imageurl";
    private static final String CONFIG_NAME = "name";
    private static final String CONFIG_FOLDERDEPTH = "folderDepth";
    private static final int CONFIG_DEFAULTFOLDERDEPTH = 4;

    private String outputformat = null;
    private String rootOutputFolder = null;
    private String license = null;
//    private boolean ocr = true;
    private String imageurl = null;
    private int folderDepth; // default to 4

    private static final String OUTPUTFORMAT_HDHTML = "hd_html";
    private static final String OUTPUTFORMAT_LDHTML = "ld_html";
    private static final String OUTPUTFORMAT_TEXT_ONLY = "text";

    private static String OCR_FLAGS = "OCR=ON;OCR_MIN_WIDTH=400";


    public PESFilterDocument(Level _level, Component _component)   {
        super(_level, _component);
    }

    @Override
    public boolean supportsDelete() {
        return true;
    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {


        folderDepth = getPropertyValue(CONFIG_FOLDERDEPTH, CONFIG_DEFAULTFOLDERDEPTH);
        outputformat = getPropertyValue(CONFIG_OUTPUTFORMAT, null);
        rootOutputFolder = getPropertyValue(CONFIG_OUTPUTFOLDER, null);
        license = getPropertyValue(CONFIG_LICENSE, null);
        //name = getPropertyValue(CONFIG_NAME, null);
        imageurl = getPropertyValue(CONFIG_IMAGEURL, null);

        statusLogger = DEFLogManager.getLogger("DahuQBE-BAU",Level.INFO);

//        if (this.getPropertyValue(CONFIG_OCR,"").equalsIgnoreCase("true") || this.getPropertyValue(CONFIG_OCR,"").equalsIgnoreCase("on")){
//            OCR_FLAGS = "OCR=ON;OCR_MIN_WIDTH=400";
//        } else {
//            OCR_FLAGS = "OCR=OFF";
//        }

        if (rootOutputFolder == null || license == null || outputformat == null ||
                (! (outputformat.equalsIgnoreCase(OUTPUTFORMAT_HDHTML)||
                        outputformat.equalsIgnoreCase(OUTPUTFORMAT_LDHTML) ||
                        outputformat.equalsIgnoreCase(OUTPUTFORMAT_TEXT_ONLY)))
        ){
            logger.warn(Thread.currentThread().getId() + " : Missing or bad config for PESDocFilter - needs at least  '" + CONFIG_OUTPUTFORMAT + "', '" + CONFIG_OUTPUTFOLDER + "', '" + CONFIG_LICENSE +"'");
            throw new BadConfigurationException("Missing or bad config for PESDocFilter - needs at least  '" + CONFIG_OUTPUTFORMAT + "', '" + CONFIG_OUTPUTFOLDER + "', '" + CONFIG_LICENSE +"'");
        }

        // If root folder doesn't exist, create it
        new File(rootOutputFolder).mkdir();

        logger.debug("Initialising DocFilters  ; License => " + license);
        try {
            isys = new DocumentFilters();
            isys.Initialize(license, ".");
        } catch (IGRException igre){
            logger.warn(Thread.currentThread().getId() + " : Error initialising DocumentFilters : " + igre.getLocalizedMessage());
            igre.printStackTrace();
            return false;
        }
        logger.trace(Thread.currentThread().getId() + " : Finished initialising PESDocFilter : Output Folder = " + rootOutputFolder + " Format = " + outputformat + " OCR = " + OCR_FLAGS);


        logger.debug("PESFilterDocument - root output folder: " + rootOutputFolder);

        return true;
    }



    @Override
    public iDocument process(iDocument _iDoc){

        // get the document from local cache that is pointed to by this message

        String action = _iDoc.getAction();
        if (null == action || !(action.equalsIgnoreCase("insert") || action.equalsIgnoreCase("delete"))){
            logger.warn("no action provided (should be either 'add' or 'delete')");
        }

        // rootFolder needs to include the index name. This is in the iDoc (set in the translator service in inboundPesWebApiRequest


        String indexPath = null;
        try {
            indexPath = _iDoc.getFieldValue("index");

        } catch (BadMetaException e) {
            statusLogger.warn(String.format("PES Filter document: Failed to process doc %s for addition",_iDoc.getId()));
            logger.warn("no index found in the iDoc - can't properly set the index in the output directory.");
        }

        String rootPath = rootOutputFolder;
        if (null != indexPath) {
            rootPath = rootPath.concat(File.separator).concat(indexPath);
        } else {
            statusLogger.warn(String.format("PES Filter document: Failed to process doc %s for addition",_iDoc.getId()));
            logger.warn("no index found in the iDoc - can't properly set the index name in the output directory - setting to 'unknown'");
            rootPath = rootPath.concat("unknown");
        }

        // If root folder doesn't exist, create it
        new File(rootPath).mkdir();

        if (action.equalsIgnoreCase("insert")) {

            if (_iDoc == null || _iDoc.getId() == null || _iDoc.getDataSize() == 0) {
                if (_iDoc == null){
                    logger.warn("PES Filter document: iDoc is null. did the get bytes get read?" );
                }
                if (null != _iDoc && _iDoc .getId() == null){
                    logger.warn("PES Filter document: iDoc exists but iDoc ID is null. ");
                }
                if (null != _iDoc && _iDoc.getDataSize() == 0){
                    logger.warn("PES Filter document: iDoc data is zero bytes -  did bytes get read? " + _iDoc.getId());
                }
                statusLogger.warn(String.format("PES Filter document: Failed to process doc %s for addition",_iDoc.getId()));
                logger.debug(Thread.currentThread().getId() + " : Unable to pass this document to doc filters - " + _iDoc.getId() + " data size is " + _iDoc.getDataSize());
                _iDoc.addField("SYNC","false");
                return _iDoc;
            }


            StringBuffer sb = new StringBuffer();
            for (String key : _iDoc.getFieldNames()) {
                try {
                    sb.append("<META name=\"" + key + "\" content=\"" + escapeXMLCharsInString(_iDoc.getFieldValue(key)) + "\" />\n");
                } catch (BadMetaException bme) {
                    statusLogger.warn(String.format("PES Filter document: Failed to process doc %s for addition",_iDoc.getId()));
                    logger.warn("Unable to get a meta value for key = " + key);
                }
            }


            Extractor item = null;

            try {

                // Work out where we are going to write the output
                String outputFolder = null;
                try {
                    // For each unique document, we need a unique folder to store it plus any embedded files in it
                    // NOTE - if output folder already exists (ie we already exported a version of this document) the following call will DELETE THE EXISTING FOLDER

                    // Chris - 01/04/19. Using the front of the docid for our sharding is giving us unbalanced directories which will make
                    // indexing hard. Instead, we are going to reverse the id so the bits we use will be the least significant which should
                    // be random (ish)

                    String revId = "";
                    String tmpId = _iDoc.getId();
                    for (int i = tmpId.length() - 1; i >= 0; i--) {
                        revId = revId.concat(tmpId.substring(i, i + 1));
                    }

                    outputFolder = getDocSubFolder(rootPath, _iDoc.getId());
                } catch (BadArgumentException bae) {
                    statusLogger.warn(String.format("PES Filter document: Failed to process doc %s for addition",_iDoc.getId()));
                    logger.warn(Thread.currentThread().getId() + " : Unable to create subfolders to store the output. " + bae.getLocalizedMessage());
                    _iDoc.addField("SYNC","false");
                    return _iDoc;
                }

                item = isys.GetExtractor(_iDoc.getData());

                logger.debug(Thread.currentThread().getId() + " : Processing " + _iDoc.getId() + " through " + outputformat + " doc filters with " + OCR_FLAGS + " write output to " + outputFolder);

                if (outputformat.equalsIgnoreCase(OUTPUTFORMAT_LDHTML)) {
                    recurseLoDefHtml(outputFolder, _iDoc.getId(), item, sb.toString());
                } else if (outputformat.equalsIgnoreCase(OUTPUTFORMAT_HDHTML)) {
                    recurseHiDefHtml(outputFolder, _iDoc.getId(), item, sb.toString());
                } else if (outputformat.equalsIgnoreCase(OUTPUTFORMAT_TEXT_ONLY)) {
                    recurseTextOnly(outputFolder, _iDoc.getId(), item);
                }

                // Decorate the iDoc with metas showing the location of the exported file, and its file type
                // File format of ld_html or hd_html imply there will be an output file with .html extension under the filepath folder
                // File format of text implies there will be an output file with .txt extension under filepath folder
                _iDoc.addField("df:filepath", outputFolder);
                _iDoc.addField("df:format", outputformat);

                // also, let the acknowledger (if there is one) know we have processed the doc.
                statusLogger.info(String.format("PES Filter document: Processed doc %s for addition",_iDoc.getId()));
                _iDoc.addField("SYNC", "true");

            } catch (IGRException | BadDocumentException igre) {
                igre.printStackTrace();
            } finally {
                try {
                    if (null != item) {
                        item.Close();
                    } else {
                        logger.debug("failed to close item: " + _iDoc.getId());
                    }
                } catch (IGRException igre) {
                    igre.printStackTrace();
                }
            }
        } else if (action.equalsIgnoreCase("delete")){
            logger.debug("Delete the document.");

            String outputFolder = null;

            // For each unique document, we need a unique folder to store it plus any embedded files in it
            // NOTE - if output folder already exists (ie we already exported a version of this document) the following call will DELETE THE EXISTING FOLDER


            String dirToDeleteName = fileSharder.getShardedPath(rootPath,_iDoc.getId(),folderDepth);
            Set<String> filesToNotify = fileSharder.getHtmlFilesAtPath(rootPath,_iDoc.getId(),folderDepth);

            if (deleteSubFolder(rootPath, _iDoc.getId())) {
                _iDoc.addField("SYNC", "true");
                statusLogger.info(String.format("PES Filter document: Processed doc %s for deletion",_iDoc.getId()));

            } else {
                statusLogger.warn(String.format("PES Filter document: Failed to process doc %s for deletion",_iDoc.getId()));
                logger.warn("failed to delete directory '" + dirToDeleteName + "'");
                _iDoc.addField("SYNC", "false");
            }
        }

        return _iDoc;

    }

    /**
     * Generate a text-based output format.
     * Each word is written to a new line, along with data around its location on the page
     * @param _folder folder t0 create this extract into
     * @param _filename base filename for the newly-extracted files - will create new file using this name with "txt" file extension
     * @param _item DocFilters document to extract
     */
    private void recurseTextOnly(String _folder, String _filename, Extractor _item){

        logger.trace(Thread.currentThread().getId() + " : Enter recurseTextOnly : processing " + _filename + " storing output files in " + _folder);

        int flags = isys_docfilters.IGR_BODY_AND_META | isys_docfilters.IGR_FORMAT_HTML;

        try {
            _item.Open(flags, OCR_FLAGS);
        } catch (IGRException igre){
            logger.warn(Thread.currentThread().getId() + " : Unable to open document, " + _filename + " :" + igre.getLocalizedMessage());
            igre.printStackTrace();
            return;
        }


        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            if (_item != null && _item.getSupportsText() && _item.GetPageCount() > 0 ) {
                fw = new FileWriter(_folder + File.separator + _filename + ".txt");
                bw = new BufferedWriter(fw);

                for (int pageIndex = 0; pageIndex < _item.GetPageCount(); pageIndex++) {
                    Page page = _item.GetPage(pageIndex);
                    try {
                        for (Word word = page.getFirstWord(); word != null; word = page.getNextWord()) {
                            bw.write(String.format("%3d. %-15s [x: %4d; y: %4d; width: %3d; height: %3d; character: %3d]",
                                    word.getWordIndex(), word.getText(), word.getX(), word.getY(), word.getWidth(), word.getHeight(), word.getCharacterOffset()));
                        }
                    }
                    finally {
                        page.Close();
                    }
                }

                // Finished writing text for this doc - if its a container ie its an email, look for text in attachments
                if (_item.getSupportsSubFiles()) {
                    SubFile child = _item.GetFirstSubFile();
                    while (child != null) {
                        if (child.getSupportsText() || child.getSupportsSubFiles()) {
                            logger.debug(Thread.currentThread().getId() + " : Processing " + _filename + " : extracted child, " + _filename + " -_ " + child.getName() + " sending to recurseTextOnly");
                            recurseTextOnly(_folder, _filename + " -_ " + child.getName(), child);
                        } else {
                            logger.trace(Thread.currentThread().getId() + " : Processing " + _filename + " : extracted child, " + _filename + " -_ " + child.getName() + " Cannot process it so not sending to recurseTextOnly");
                        }
                        // Move on to the next sub file
                        child = _item.GetNextSubFile();
                    }
                }
            }
        } catch (IGRException igre){
            logger.warn(igre.getLocalizedMessage());
            igre.printStackTrace();
        } catch (IOException ioe){

        } finally {
            try {
                if (bw != null){
                    bw.close();
                }
                if (fw != null){
                    fw.close();
                }
            } catch (IOException ioe){
                ioe.printStackTrace();
            }
        }
    }


    /**
     * HiDef Format
     * It creates an image file per page of input document. In each image, it contains any images plus text
     * Then creates an HTML overlay. all HTML elements are rendered invisible, and sit on top of the image
     * @param _folder folder to create new files inside
     * @param _filename base filename for the newly-extracted files - will create new file using this name with "html" file extension
     * @param _item DocFilters document to extract
     */
    private void recurseHiDefHtml(String _folder, String _filename, Extractor _item, String _metasAsString){

        logger.trace(Thread.currentThread().getId() + " : Enter recurseHiDefHtmlFile : processing " + _filename + " storing output files in " + _folder);

        if (_item != null){


            // Open the file for conversion to image-based Hi-Def HTML
            int flags = isys_docfilters.IGR_BODY_AND_META | isys_docfilters.IGR_FORMAT_IMAGE;

            try {
                // open file - inject all of the custom metadata that comes with the iDoc
                _item.Open(flags, OCR_FLAGS+";HDHTML_OUTPUT_INJECT_HEAD="+_metasAsString);
            } catch (IGRException igre){
                igre.printStackTrace();
                logger.warn(Thread.currentThread().getId() + " : Unable to open document, " + _filename + " :" + igre.getLocalizedMessage());
                return;
            }

            Canvas canvas = null;

            // Create the canvas that we are going to operate on and create pages
            try {
                canvas = isys.MakeOutputCanvas(_folder + File.separator + getFilename(_filename,"html"), isys_docfilters.IGR_DEVICE_HTML, "");
                logger.debug(Thread.currentThread().getId() + " : Processing " + _filename + " : Created Canvas :  copying to " +_folder);
                try
                {
                    for (int pageIndex = 0; pageIndex < _item.GetPageCount(); pageIndex++) {
                        // for each page in the doc, process it and create a new image file
                        Page page = _item.GetPage(pageIndex);
                        try {
                            logger.trace(Thread.currentThread().getId() + " : HiDef Conversion : processing " + _filename + " : created new Canvas for Page " + pageIndex);
                            canvas.RenderPage(page);
                            SubFile image = page.GetFirstImage();
                            while (image != null) {
                                logger.debug(Thread.currentThread().getId() + " : Processing " + _filename + " : extracted image " + image.getName() + " copying to " +_folder);
                                // before we write the file out, we want to be sure its going to work otherwise it can be catastrophic (crash the JVM)
                                File file = new File(_folder + File.separator + image.getName());
                                try {
                                    if (file.createNewFile()){
                                        file.delete();
                                        image.CopyTo(_folder + File.separator + image.getName());
                                    } else {
                                        logger.debug("Failed the pre-copy checks for image file: " + _folder + File.separator + image.getName() );
                                    }
                                } catch (Exception e){
                                    logger.debug(Thread.currentThread().getId() + " : Failed the pre-copy checks for image file with exception : " + _folder + File.separator + image.getName()  );
                                    e.printStackTrace();
                                }

                                image.Close();
                                image = page.GetNextImage();
                            }
                        }
                        finally
                        {
                            page.Close();
                        }
                    }
                }
                finally
                {
                    canvas.Close();
                }

                //  Here comes the recursion
                if (_item.getSupportsSubFiles()) {

                    SubFile child = _item.GetFirstSubFile();
                    while (child != null) {
                        if (! ( child.getFileType() == 73 || child.getFileType() == 72 || child.getFileType() == 118 || child.getFileType() == 121
                                || child.getFileType() == 122 || child.getFileType() == 123 || child.getFileType() == 124 || child.getFileType() == 125
                                || child.getFileType() == 126)){
                            logger.debug("Processing " + _filename + " : extracted child, " + _filename + " -_ " + child.getName() + " sending to recurseHiDefHtml()");
                            recurseHiDefHtml(_folder, _filename + " -_ " + child.getName(), child, _metasAsString);
                        }
                        child = _item.GetNextSubFile();
                    }
                }
            } catch (Exception e){
                logger.warn(Thread.currentThread().getId() + " : Error while converting file to HD HTML : file " + _filename + " : " + e.getLocalizedMessage());
//                e.printStackTrace();
            } finally {
                try {
                    _item.Close();
                } catch (IGRException igre){
                    logger.warn(Thread.currentThread().getId() + " : Error while converting file to HD HTML : file " + _filename + " : " + igre.getLocalizedMessage());
//                    igre.printStackTrace();
                }
            }

        }

    }

    /**
     * LoDef HTML Format
     * It creates an HTML rendition from the original, with inline images
     * Inline images are generated in same folder as original
     * Metadata from the iDoc is inserted into the HTML
     * @param _folder folder to create new files inside
     * @param _filename base filename for the newly-extracted files - will create new file using this name with "html" file extension
     * @param _item DocFilters document to extract
     */
    private void recurseLoDefHtml(String _folder, String _filename, Extractor _item, String _metas){

        logger.debug(Thread.currentThread().getId() + " : Enter recurseLoDefHtmlFile : processing " + _filename + " storing output files in " + _folder);

        if (_item != null){

            boolean isExcel = false; // need to rewrite CSS on the fly if HTML comes from Excel file due to Hyland Doc Filters bug

            // Need to map from absolute path on file-sys to relative URL
            // Starting point is to assume that any path to an export folder will start D:\df-export
            // There may be variability after that - eg D:\df-export-vault7\

            String folderUrlPath = _folder.substring(_folder.indexOf("df-export")+9);
            if (folderUrlPath.indexOf("\\") > 0){
                folderUrlPath = folderUrlPath.substring(folderUrlPath.indexOf("\\")+1);
            }
            // dealt with all variations of D:\df-export-something\path - folderUrPath now just contains the path bit after df-export
            folderUrlPath = folderUrlPath.replaceAll("\\\\","/"); // for Urls, ya know

            String finalImageUrl = imageurl+"/"+folderUrlPath + "/";


            String options = OCR_FLAGS;
            if (null != imageurl){
                options = options + ";IMAGEURL=" + finalImageUrl ;
            }
            logger.debug("processing doc for Lo-Def Conversion with " + options + " for document, " + _filename );

            try {
                int flags = isys_docfilters.IGR_BODY_AND_META | isys_docfilters.IGR_FORMAT_HTML;
                _item.Open(flags, options);
            } catch (IGRException igre){
                logger.warn(Thread.currentThread().getId() + " : Unable to open document, " + _filename + " :" + igre.getLocalizedMessage());
                DEFLogManager.LogStackTrace(logger, "DocFilters", igre);
                return; // nothing further to do with this document - skip it and carry on
            }


            String baseOutputFileName = getFilename(cleanFilename(_filename),"html");

            StringBuilder htmlContent = new StringBuilder();
            String html = null;
            String relativePath = _folder;

            try
            {
                logger.debug("See if this document, " + _filename + " supports text extraction");
                if (_item.getSupportsText()) {
                    logger.debug("Extracting text as HTML for document, " + _filename);
                    // Extract the text and write to exported HTML file
                    while (!_item.getEOF()) {
                        String t = _item.GetText(4096);
                        t = t.replace('\u000E', '\n');
                        t = t.replace('\r', '\n');

                        if (t.toLowerCase().indexOf("meta name=\"appname\" content=\"microsoft excel\"") > 0 ){
                            isExcel = true;
                        }
                        if (isExcel){
                            // Recommendation from Joshua Houle, Hyland, to work around display bug fpr Excel-to-HTML files in Experience GUI
                            if (t.indexOf(".ISYS_TOC")> 0 || t.indexOf(".ISYS_Body_Div") > 0){
                                t = t.replace("position: fixed;","position: relative;");
                            }
                        }

                        logger.trace("Appending text => " + t);
                        htmlContent.append(t);
                    }

                    if (htmlContent.length() > 0) {
                        html = htmlContent.toString();
                        html = html.replaceAll("<title></title>","");
                        html = html.replaceAll("<HEAD>","<head>");

                        // Insert all the metas from iDoc to the HTML file
                        html = html.replaceAll("<head>","<head>\n"+_metas);
                        logger.trace("html metas added: \n" + html );
                        logger.trace("Finished extracting text as HTML for document, " + _filename + " - text size is " + html.length());

                        FileWriter fileWriter = new FileWriter(_folder + File.separator + baseOutputFileName);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.print(html);
                        printWriter.close();


                    } else {
                        logger.trace(Thread.currentThread().getId() + " : Processing " + _filename + " could not extract any text");
                    }
                } else {
                    logger.trace("this document, " + _filename + " with type, " + _item.getFileType() + " apparently doesn't support getting text");
                }

                logger.debug("looking for images in file, " + _filename);
                SubFile image = _item.GetFirstImage();
                int imgCount = 1;
                while (image != null) {
                    logger.debug(Thread.currentThread().getId() + " : Processing images from " + _filename + "image: "+ imgCount +" : extracted image " + image.getName() + " copying to " +_folder );

                    if ((null == image.getName()) || (image.getName().isEmpty())){
                        logger.trace(Thread.currentThread().getId() + " : image file " + imgCount + " for " + _filename + " is blank");
                    } else {
                        logger.trace(Thread.currentThread().getId() + " : image file " + imgCount + " for " + _filename + " is " + image.getName());
                    }

                    imgCount++;
                    // with IMAGEURL, we add a prefix to filenames - need to strip it out, as we want image files to go to the same folder as HTML files
                    String cleanImageName = image.getName();
                    if (cleanImageName.startsWith(finalImageUrl)){
                        cleanImageName = cleanImageName.substring(finalImageUrl.length());
                    }

                    // before we write the file out, we want to be sure its going to work otherwise it can be catastrophic (crash the JVM)
                    String testPath = _folder + File.separator + cleanFilename(cleanImageName);
                    File file = new File(testPath);
                    try {
                        if (file.createNewFile()){
                            logger.debug(Thread.currentThread().getId() + " : Passed the pre-copy checks for image file: " + testPath + " attempting the 'copyTo'" );
                            file.delete();
                            image.CopyTo(testPath);
                        } else {
                            logger.debug(Thread.currentThread().getId() + " : Failed the pre-copy checks for image file: " + testPath );
                        }
                    } catch (Exception e){
                        logger.debug(Thread.currentThread().getId() + " : Failed the pre-copy checks for image file with exception : " + testPath  );
                        e.printStackTrace();
                    }


                    image.Close();
                    image = _item.GetNextImage();
                }

                if (_item.getSupportsSubFiles()) {
                    logger.debug(Thread.currentThread().getId() + " : Looking for child items in file, " + _filename);
                    SubFile child = _item.GetFirstSubFile();
                    int subFileCount = 1;
                    while (child != null) {
                        logger.debug(Thread.currentThread().getId() + " : Found child doc with size of " + child.getFileSize() + " and a type of " + child.getFileType());
                        // if file is larger than 20kb, it should be processed even if its an image because it might contain extractable useful text - logos etc are typically small
                        // if it is zero size, do not recurse it
                        // If it is a common image file format and is very small, it's unlikely to contain useful text so also choose to not recurse
                        if ((  ! ( child.getFileType() == 73 || child.getFileType() == 72 || child.getFileType() == 118 || child.getFileType() == 121
                                || child.getFileType() == 122 || child.getFileType() == 123 || child.getFileType() == 124 || child.getFileType() == 125
                                || child.getFileType() == 126))){
                            logger.debug(Thread.currentThread().getId() + " : Processing " + _filename + " : sending child, " + _filename + " -_ " + child.getName() + " sending to recurseLoDefHtml() as " + _filename + "_" + subFileCount);
                            recurseLoDefHtml(_folder, _filename + "_" + subFileCount , child, _metas);
                            subFileCount++;
                        } else {
                            logger.trace(Thread.currentThread().getId() + " : Processing " + _filename + " : NOT extracting child,  " + _filename + " -_ " + child.getName() + "  - child size = " + child.getFileSize() + " and type = " +child.getFileType()  );
                        }
                        child = _item.GetNextSubFile();
                    }
                }
            } catch (Exception e){
                logger.warn(Thread.currentThread().getId() + " : Unable to extract LD HTML from " + _filename + " : " + e.getLocalizedMessage());
            } finally {
                try {
                    _item.Close();
                } catch (IGRException igre){
                    logger.warn(Thread.currentThread().getId() + " : Unable to close LD HTML from " + _filename + " : " + igre.getLocalizedMessage());
                    igre.printStackTrace();
                }
            }
        }
        logger.trace("Returning from lodef-recurse for " +_filename);
    }


    /**
     * Get sharded folder name - a folder path where each folder is guaranteed to exist and
     * for a given filename, there is guaranteed to be a one-to-one map to a folder path
     * @param _rootFolder parent folder. All sharded folders will be sub-folders beneath _rootFolder
     * @param _filename unique document identifier
     * @return  path to a sub-folder whose name is determined by filename
     */
    private String getShardedFolder(String _rootFolder, String _filename){
        return fileSharder.createShardedDirectories(_rootFolder,_filename,folderDepth);
    }

    /**
     * Get sharded folder path - return the path that a document would exist in. No directories are
     * created.
     * @param _rootFolder parent folder. All sharded folders will be sub-folders beneath _rootFolder
     * @param _filename unique document identifier
     * @return  path to a sub-folder whose name is determined by filename
     */
    private String getShardedPath(String _rootFolder, String _filename){
        return fileSharder.getShardedPath(_rootFolder,_filename,folderDepth);
    }

    private Boolean deleteSubFolder(String _rootfolder, String _basename) {

        String shardedRootFolder = getShardedPath(_rootfolder, _basename);
        if (shardedRootFolder.endsWith(File.separator)){
            shardedRootFolder = shardedRootFolder.substring(0,shardedRootFolder.length()-File.separator.length());
        }

        Path delFolder = Paths.get(shardedRootFolder);

        if (Files.exists(delFolder)){
            try {
                //recursive delete of folders and all subfolders
                FileUtils.deleteDirectory(new File(shardedRootFolder));
            } catch (IOException ioe){
                logger.warn("Unable to delete existing output file, " + _basename  + " from " + shardedRootFolder + ". " +ioe.getLocalizedMessage());
                return false;
            }
        } else {
            // warning - doesn't exist. still send an ack
            logger.warn("output file, " + _basename  + " from " + shardedRootFolder + " doesn't exist");
        }

        return true;
    }

    /**
     * To ensure folder name is valid on all file-systems all characters are converted to lowercase and
     * all reserved characters are converted to their unicode char code (int)
     * @param _basename
     * @return converted basename
     */

    private String cleanFileName(String _basename){

        StringBuffer sb = new StringBuffer();
        for (char c : _basename.toLowerCase().toCharArray()){
            if (Character.isDigit(c)){sb.append(c);}
            else if (c >= 'a' && c <= 'z' ){sb.append(c);}
            else {
                sb.append("_x"+(int)c);
            }
        }
        return sb.toString();
    }

    /**
     * Generate a new folder that can safely be used to write entries relating to a file called _basename
     * If the folder already exists, the old one is deleted and re-created.
     * The folder will be created on the filesystem, and its path is returned
     * @param _rootfolder root folder that all shared folders will sit under
     * @param _basename base name to use to create a safe folder name
     * @return String - all characters are safe to use in a folder name on windows or linux.
     * @throws BadArgumentException exception throw if folderRoot does not exist as a folder, or unable to create new subfolder underneath it
     */
    private String getDocSubFolder(String _rootfolder, String _basename) throws BadArgumentException {

        Path rootFolder = Paths.get(_rootfolder);
        if (!Files.exists(rootFolder)){
            throw new BadArgumentException("Root folder does not exist : " + _rootfolder);
        }
        String shardedRootFolder = getShardedFolder(_rootfolder, _basename);
        if (shardedRootFolder.endsWith(File.separator)){
            shardedRootFolder = shardedRootFolder.substring(0,shardedRootFolder.length()-File.separator.length());
        }

        String newFolderName = shardedRootFolder + File.separator + cleanFileName(_basename);
        Path newFolder = Paths.get(newFolderName);

        if (Files.exists(newFolder)){
            //oh oh collision. We already have an entry for this file. Let's delete the old one before trying to write new output files
            try {
                //recursive delete of folders and all subfolders
                FileUtils.deleteDirectory(new File(newFolderName));
            } catch (IOException ioe){
                logger.warn("Unable to delete existing output file, " + _basename  + " from " + newFolderName);
                ioe.printStackTrace();
                throw new BadArgumentException("Unable to delete folder " + newFolderName + " : " + ioe.getLocalizedMessage());
            }
        }

        // create a new subfolder to write output files
        try {
            Files.createDirectory(newFolder);
        } catch (IOException ioe) {
            throw new BadArgumentException("Unable to create new folder called " + newFolderName + " underneath existing folder, " + rootFolder + " : cause => " + ioe.getLocalizedMessage());
        }


        return newFolderName;
    }

    /**
     * Appends file extention on file name
     * if filename already has an extension, it is replaced with the requested new extension name
     * If filename actually contains parts of a path (eg folders) these are removed leaving only the filename
     * @param baseFileName base filename
     * @param newExtension file extension
     * @return new filename with requested file extension
     */
    protected String getFilename(String baseFileName, String newExtension) {

        // What to do with a filename that is a file path with folder names in it?  Answer - remove all folder path elements
        int pos = baseFileName.lastIndexOf(File.separator);
        if (pos >= 0) {
            baseFileName = baseFileName.substring(pos + 1);
        }

        // If we have a new file extension, throw away the old file extension
        if (newExtension != null && newExtension != "") {
            if (baseFileName.lastIndexOf(".") > 0) {
                baseFileName = baseFileName.replace(".","_") + "." + newExtension;
            } else {
                baseFileName = baseFileName + "." + newExtension;
            }
        }

        return baseFileName;
    }

    protected String cleanFilename(String _filename){

        String returnFilename = _filename.replaceAll("[\\\\/:*?\"<>|]", "-");

        // check for length and shorten it


        return returnFilename;
    }

    private String escapeXMLCharsInString(String XML_TO_ESCAPE){
        StringBuilder escapedXML = new StringBuilder();
        for (int i = 0; i < XML_TO_ESCAPE.length(); i++) {
            char c = XML_TO_ESCAPE.charAt(i);
            switch (c) {
                case '<':
                    escapedXML.append("&lt;");
                    break;
                case '>':
                    escapedXML.append("&gt;");
                    break;
                case '\"':
                    escapedXML.append("&quot;");
                    break;
                case '&':
                    escapedXML.append("&amp;");
                    break;
                case '\'':
                    escapedXML.append("&apos;");
                    break;
                default: if (c > 0x7e) {
                    escapedXML.append("&#" + ((int) c) + ";");
                } else
                    escapedXML.append(c);
            }
        }
        return escapedXML.toString();
    }


}