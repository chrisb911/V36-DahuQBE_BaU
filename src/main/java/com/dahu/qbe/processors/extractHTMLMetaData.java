package com.dahu.vector.processors;

import com.dahu.core.abstractcomponent.AbstractProcessor;
import com.dahu.core.document.DEFFileDocument;
import com.dahu.core.exception.BadMetaException;
import com.dahu.core.interfaces.iDocument;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.types.Component;
import com.dahu.fetcher.core.Fetcher;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by :
 * Chris Bartlett, Dahu
 * vince@dahu.co.uk
 * on 01/07/2019
 * copyright Dahu Ltd 2019
 * <p>
 * Changed by :
 */

public class extractHTMLMetaData extends AbstractProcessor {

    public static final String HIERARCHICALFACET_SEPARATOR = "/";
    private static final String CONFIG_HIER_METANAMES = "hier_metanames";
    private static final String CONFIG_VALUES_FIELD = "valuesField";
    private Set<String> metaNames = new HashSet<>();
    private String valuesField = null;

    public extractHTMLMetaData(Level _level, Component _component) throws BadConfigurationException {
        super(_level, _component);

    }
    @Override
    public iDocument process(iDocument _iDocument) {

        // if we think this is an html doc, we need to open the document source, get all the important metas
        // and create idoc fields from them.

        //firstly, do we have a Mime type?
        String mimeType = _iDocument.getMimeType();
        boolean isHtml = false;
        // might be multi-valued so check all values separately
        if (null != mimeType){
            String mimeTypes[] = mimeType.split(";");
            if (mimeTypes.length>0){
                for (String thisMime:mimeTypes){
                    if (thisMime.equalsIgnoreCase("application/xhtml+xml")) isHtml=true;
                }
            }
        } else {
            // didn't get a mime-type- check the extension on the filename
            String filename = _iDocument.getId();
            if (null != filename){
                int pos = filename.lastIndexOf('.');
                if (pos>0){
                    String extension = filename.substring(pos+1);
                    if (extension.equalsIgnoreCase("HTML") || extension.equalsIgnoreCase("HTM"))
                        isHtml = true;
                } else {
                    // at this point we didn't get a mime type, didn't find anything on the ID - but it could still
                    // be HTML - a snippet from a db for example.
                    String htmlContent = _iDocument.getXhtml();

                    // see if we have a metas section



                }
            }
        }
        if (isHtml){
            // we think its html - open the file and take a shufty (we need to open the orig file as tika will have
            // already stripped the html markup.
            String filename = _iDocument.getId();
            _iDocument.getXhtml();
        }



        for (String f : metaNames){
            String path = null;
            try {
                if (f.equalsIgnoreCase("filepath") && _iDocument instanceof DEFFileDocument) {
                    path = ((DEFFileDocument) _iDocument).getPath();
                    // For DEFFile, this path is absolutepath to a file - we want the parent folder. Go up one level...
                    if (path.indexOf("/") > 0) {
                        path = path.substring(0, path.lastIndexOf("/"));
                    }

                } else if (null != _iDocument.getFieldValue(f)) {
                    // this is the folder path - use all of it
                    path = _iDocument.getFieldValue(f);
                }
            } catch (BadMetaException bme){
                logger.debug("Tried to split hierarchical field " + f + " but iDoc has no meta of that name");
            }
            if (null != path) {
                String newFieldName = f;
                if (newFieldName.endsWith("_s")){
                    newFieldName = newFieldName + "s"; // need to indicate we want a multi-value field for all these new values
                }
                for (String fr : this.buildHierarchicalFacet(path, "/")) {
                    // send as _hier_filepath: 0/TrimGroups  , _hier_filepath: 1/TrimGroups/toplevel folder , _hier_filepath: 2/TrimGroups/toplevel folder/another folder
                    _iDocument.addField("_hier_" + newFieldName, fr);
                }
                // add the individual field parts to the given field
                if (null != valuesField) {
                    for (String value : this.buildValuesList(path, "/")) {
                        _iDocument.addField(valuesField,value);
                    }
                }
            }
        }

        return _iDocument;

    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {
        if (getIsPropertiesSet()) {
            for (String k : properties.keySet()) {
                if (k.equalsIgnoreCase(CONFIG_VALUES_FIELD)) {
                    valuesField = properties.get(k);
                }
                if (k.equalsIgnoreCase(CONFIG_HIER_METANAMES)) {
                    String metaNames = properties.get(k);
                    if (metaNames.indexOf(",")>0){
                        String[] metaNamesArray = metaNames.split(",");
                        for (int i = 0; i < metaNamesArray.length; i++){
                            this.metaNames.add(metaNamesArray[i]);
                        }
                    } else {
                        this.metaNames.add(metaNames);
                    }
                }
            }
        }
        if (metaNames.size() == 0){
            throw new BadConfigurationException("HierarchicalFieldSplitter needs at least one meta name to work on - no metas supplied in config as \"hier_metanames\"");
        }
        return true;
    }


    /**
     * Converts a single field that has a hierarchy into a series of fragments that can be passed to Solr and used to create facets
     * eg  D:/folder1/folder2/folder3/file.txt
     * In solr, this should generate facets for D:/, D:/folder1, D:/folder1/folder2 and D:/folder1/folder2/folder3
     * @param _field field to use to create a hierarchical facet in Solr
     * @param _separator character to split the _field input string
     * @return a List of numbered string fragments that can be used in Solr as a hierarchical facet
     */
    public static List<String> buildHierarchicalFacet(String _field, String _separator){

        List<String>retVals = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int counter = 0;

        if (null != _field){
            String input = _field.replaceAll("\"","");
            // special case : field starting file:// or smb://
            if (input.startsWith("file://")){
                input = input.substring(7);
            } else if (input.startsWith("smb:")){
                input = input.substring(6);
            }
            // Now split the string on the separator character(s)
            String[] frags = input.split(_separator);
            for (int i = counter; i < frags.length; i++){
                sb.append(HierarchicalFieldSplitter.HIERARCHICALFACET_SEPARATOR+frags[i].replaceAll(HierarchicalFieldSplitter.HIERARCHICALFACET_SEPARATOR,"%2F"));
                retVals.add(i+sb.toString()+HierarchicalFieldSplitter.HIERARCHICALFACET_SEPARATOR);
            }
        }
        return retVals;
    }

    /**
     * Converts a single field that has a hierarchy into a series of fragments that can be passed to Solr used to create
     * a searchable field - eg aa/bb/cc will create a multi-value field containing 'aaa', 'bbb' and 'ccc'
     * @param _field field representing a hierarchical string
     * @param _separator character to split the _field input string
     * @return a List of string fragments that can be added to a field
     */
    public static List<String> buildValuesList(String _field, String _separator){
        List<String>retVals = new ArrayList<>();
        if (null != _field){
            String input = _field.replaceAll("\"","");
            // special case : field starting file:// or smb://
            if (input.startsWith("file://")){
                input = input.substring(7);
            } else if (input.startsWith("smb:")){
                input = input.substring(6);
            }
            // Now split the string on the separator character(s)
            String[] frags = input.split(_separator);
            for (String frag : frags){
                retVals.add(frag);
            }
        }
        return retVals;
    }



}
