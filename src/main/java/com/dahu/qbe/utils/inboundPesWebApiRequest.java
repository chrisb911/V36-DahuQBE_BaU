package com.dahu.qbe.utils;

        import com.dahu.core.interfaces.iDocument;
        import com.dahu.core.document.DEFDocument;


        import java.io.UnsupportedEncodingException;
        import java.nio.charset.StandardCharsets;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Date;

        import com.dahu.core.logging.DEFLogManager;
        import org.apache.logging.log4j.Logger;

        import javax.ws.rs.BadRequestException;
        import java.text.ParseException;
        import java.time.LocalDate;
        import java.text.SimpleDateFormat;
        import java.time.ZoneId;
        import java.util.Set;


/**
 * Created by :
 * Vince McNamara, Dahu
 * vince@dahu.co.uk
 * on 13/06/2018
 * copyright Dahu Ltd 2018
 * <p>
 * Changed by :
 *
 * Encapsulates an INBOUND PES WEB API Request
 *
 * This is a request that has been received from a client that contains an action
 *
 * Action will either be ADD or DELETE
 * From this, we generate an outbound PES API request, after processing of the data in this request
 *
 *
 */

public class inboundPesWebApiRequest {

    Logger logger = null;

    private static final String ACTION = "action";
    private static final String INDEX = "index";
    private static final String FILENAME = "filename";
    private static final String LASTMODIFIED = "last-modified";
    private static final String PASSWORD = "password";

    private String pes_action = null;
    private String pes_index = null;
    private String pes_filename = null;
    private String pes_last_modified = null;
    private String pes_password = null;
    private String pes_index_folder = null;

    private List<String> metas = new ArrayList<>();
    private byte[] data = null;

    private int folder_depth = 4; // default to 4


    /**
     *
     * @param _headers
     */
    public inboundPesWebApiRequest(Set<String> _headers, Logger _l) throws BadRequestException {

        if (null != _l){
            this.logger = _l;
        } else {
            this.logger= DEFLogManager.getDEFSysLog();
        }

        for (String h : _headers){
            String value = h.substring(h.indexOf("=")+1).trim();

            // its quite possible (likely, even) that the values have been URL-encoded.
            // decode it before use.
            try {
                value = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                logger.warn("unsupported encoding exception while trying to decode meta value:" +e.getLocalizedMessage());
                DEFLogManager.LogStackTrace(logger, "Exception in InboundPesWebApiRequest:constructor", e);
            } catch (IllegalArgumentException ie){
                // looks like there was a problem URL-decoding the values- possibly because they are not URL encoded..
                // log it and add the value as we received it
                logger.warn(String.format("failed to decode the value for %s. adding it anyway",h));
            }

            if (h.toLowerCase().startsWith(ACTION+"=")){
                pes_action = value;
            } else if (h.toLowerCase().startsWith(INDEX)){
                //pes_index = formatIndexName(value);
                if (value.indexOf("_") > 0){ //strip off the year component if its there
                    pes_index = value.substring(0,value.indexOf("_"));
                } else {
                    pes_index = value;
                }

            } else if (h.toLowerCase().startsWith(FILENAME+"=")){
                pes_filename = value;
            } else if (h.toLowerCase().startsWith(LASTMODIFIED+"=")){
                pes_last_modified = value;
            } else if (h.toLowerCase().startsWith(PASSWORD+"=")){
                pes_password = value;
            } else if (h.toLowerCase().startsWith("meta")){
                metas.add(value);
                this.logger.debug("adding meta: " + value);
            }
        }

        // we need to create the folder name. This is the first path element of the sharding path (SHA, SHB,SHC etc).
        // we use this downstream when we do indexing
        if (null!= pes_index && null != pes_filename){
            // get the shardPath for the filename
            List<String> paths = fileSharder.getSubDirectories(pes_filename,folder_depth);

            if (paths.size()>0){
                pes_index_folder = paths.get(0);
            } else {
                throw  new BadRequestException("Failed when mapping request to iDoc - Failed to get sharding paths for " + pes_filename);
            }

        } else {
            throw  new BadRequestException("Failed when mapping request to iDoc - no index or filename specified in request header");
        }
    }

    public void setLogger(Logger _l){this.logger = _l;}

    public String getAction(){return this.pes_action;}

    public String getIndex(){return this.pes_index;}

    public String getFilename(){return this.pes_filename;}

    public String getLastmodified(){return this.pes_last_modified;}

    public String getPassword(){return this.pes_password;}

    public List<String> getMetas(){return this.metas;}

    public void setData(byte[] _data){
        this.data = _data;
    }

    public iDocument getIDoc(){

        iDocument doc = new DEFDocument(pes_filename,"PushAPI");
        doc.setUrl("def://PushAPI/WCCConnector/pk/" + pes_filename);
        doc.addField("filename",pes_filename);
        doc.setAction(pes_action);
        doc.addField("indexname",pes_index + "_" + pes_index_folder);
        doc.addField("index",pes_index );

        for (String inboundMeta : metas){

            if (inboundMeta.indexOf("=")>0){
                String metaName = inboundMeta.substring(0,inboundMeta.indexOf("="));
                String metaValue = inboundMeta.substring(inboundMeta.indexOf("=")+1);
                if (metaValue != null && metaValue.length() > 0){
                    metaName = wccPesFieldMapper.getPesField(metaName);
                    if (metaName.equalsIgnoreCase("indexname")){
                        // index is likely to contain an underbar and date suffix - we need to remove this

                        if (metaValue.indexOf("_") > 0){
                            metaValue = metaValue.substring(0,metaValue.indexOf("_"));
                        }
                    } else if (metaName.equalsIgnoreCase("createdate")){

                        SimpleDateFormat pes_date_format = null;
                        // two possible date formats can be present
                        //	21/08/2008 17:13
                        // {ts '2015-08-28 14:33:00.270'}
                        if (metaValue.startsWith("{ts")){
                            metaValue = metaValue.substring(5,metaValue.length()-6);
                            pes_date_format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                        } else {
                            pes_date_format = new SimpleDateFormat("dd/MM/yyyy hh:mm");
                        }

                        Date lastModDate = null;
                        try {
                            lastModDate = pes_date_format.parse(metaValue);
                            LocalDate localDate = lastModDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                            int year = localDate.getYear();
                            if (year < 2016){
                                doc.addField("isobsolete","true");
                            }
                        } catch (ParseException pe){
                            logger.warn("Failed to parse createdate date from request : value is " + metaValue);
                        }

                    }
                    doc.addField(metaName,metaValue);
                }
            }
        }
        return doc;
    }

    private String formatIndexName(String _indexName){
        if (_indexName.indexOf("_") > 0){
            String indexBase = _indexName.substring(0,_indexName.indexOf("_"));
            return insertIndexPrefix(indexBase) + "!" + _indexName;
        } else {
            return _indexName;
        }
    }

    private String insertIndexPrefix(String _indexBase){


        if (_indexBase.equalsIgnoreCase("broker")){
            return "01.Broker";
        } else if (_indexBase.equalsIgnoreCase("cru")){
            return "02.CRU";
        } else if (_indexBase.equalsIgnoreCase("history")){
            return "03.History";
        } else if (_indexBase.equalsIgnoreCase("financial")){
            return "04.Financial";
        } else if (_indexBase.equalsIgnoreCase("medical")){
            return "05.Medical";
        } else if (_indexBase.equalsIgnoreCase("others")){
            return "06.Others";
        } else if (_indexBase.equalsIgnoreCase("proceedings")){
            return "07.Proceedings";
        } else if (_indexBase.equalsIgnoreCase("qbe")){
            return "08.QBE";
        } else if (_indexBase.equalsIgnoreCase("reports")){
            return "09.Reports";
        } else if (_indexBase.equalsIgnoreCase("thirdparty")){
            return "10.ThirdParty";
        } else {
            return _indexBase;
        }
    }

}

