package com.dahu.qbe.utils;

        import java.util.HashMap;
        import java.util.Map;

/**
 * Created by :
 * Vince McNamara, Dahu
 * vince@dahu.co.uk
 * on 14/06/2018
 * copyright Dahu Ltd 2018
 * <p>
 * Changed by :
 */

public class wccPesFieldMapper {

    private static Map<String,String> WccToPes = null;

    static {

        WccToPes = new HashMap<>();

        WccToPes.put("XQBEADDITIONALREFERENCES","AdditionalReferences");
        WccToPes.put("XQBEARCHIVEBOXREFERENCE","ArchiveBoxReference");
        WccToPes.put("XQBEBUSINESSUNIT","BusinessUnit");
        WccToPes.put("XQBECLAIMDESCRIPTION","ClaimDescription");
        WccToPes.put("XQBECLAIMANT","Claimant");
        WccToPes.put("XQBECLAIMNUMBER","ClaimNumber");
        WccToPes.put("XQBECLAIMSEQUENCE","ClaimSequence");
        WccToPes.put("XQBECLASS","Class");
        WccToPes.put("XQBEDEPARTMENT","Department");
        WccToPes.put("XQBEDIVISION","Division");
        WccToPes.put("XQBEDOCUMENTTYPE","QBEDocumentType");
        WccToPes.put("XQBEEXPIRY","Expiry");
        WccToPes.put("XQBEDATEOFLOSS","DateOfLoss");
        WccToPes.put("XQBEINCEPTIONDATE","InceptionDate");
        WccToPes.put("XQBEOFFICE","Office");
        WccToPes.put("XQBEPOLICYIDENTIFIER","PolicyIdentifier");
        WccToPes.put("XQBECLIENT_REINSURED","Client_Reinsured");
        WccToPes.put("XQBESOURCEDMS","SourceDMS");
        WccToPes.put("XQBESUBTYPE","SubType");
        WccToPes.put("XQBESOURCESYSTEM","SourceSystem");
        WccToPes.put("XQBEUCR","UCR");
        WccToPes.put("XQBEUNDERWRITINGYEAR","UnderwritingYear");
        WccToPes.put("XQBECLAIMHANDLER","ClaimHandler");
        WccToPes.put("XQBEIN_OUTINDICATOR","In_OutIndicator");
        WccToPes.put("XQBEINSUREDNAME","InsuredName");
        WccToPes.put("XQBESUBDIVISION","SubDivision");
        WccToPes.put("XQBECLASSOFBUSINESS","ClassofBusiness");
        WccToPes.put("XQBEOPEN_CLOSEDINDICATOR","Open_CloseIndicator");
        WccToPes.put("XQBECMSDOCUMENTNAME","CMSDocumentName");
        WccToPes.put("XQBEDOCUMENTSUBTYPE","DocumentSubType");
        WccToPes.put("XQBECREATEACTIVITYINCMS","CreateActivityinCMS");
        WccToPes.put("XQBEFNOL","FNOL");
        WccToPes.put("XQBEEVENTCODE","EventCode");
        WccToPes.put("XQBEREINSURED","Reinsured");
        WccToPes.put("XQBESTATUS","Status");
        WccToPes.put("XQBERECIPIENT","Recipient");
        WccToPes.put("XQBEAUTHOR","Author");
        WccToPes.put("XQBEMAILITEMID","MailItemID");
        WccToPes.put("XQBEUSERDEFINED","UserDefined");
        WccToPes.put("XQBELINKEDIDS","LinkedIds");
        WccToPes.put("XQBEEXPOSURENUMBER","ExposureNumber");
        WccToPes.put("XQBEPRIORITY","Priority");
        WccToPes.put("XQBERELATEDTO","RelatedTo");
        WccToPes.put("XQBECMSDOCUMENTDESCRIPTION","CMSDocumentDescription");
        WccToPes.put("XQBECLAIMANTID","ClaimantID");
        WccToPes.put("XQBEHIDDEN","Hidden");
        WccToPes.put("XQBEACTIVITYID","ActivityID");
        WccToPes.put("XQBEBULKINVOICEID","BulkInvoiceId");
        WccToPes.put("XQBENEW_PROCESS_TIME","New_Process_time");
        WccToPes.put("XQBECLAIMHANDLERID","ClaimHandlerID");
        WccToPes.put("XQBERISKREFERENCE","RiskReference");
        WccToPes.put("XQBEALPHABETICALHOLDINGAREA","AlphabeticalHoldingArea");
        WccToPes.put("XQBESCHEME","Scheme");
        WccToPes.put("XQBEINSURED","Insured");
        WccToPes.put("XQBEACSREFERENCE","ACSReference");
        WccToPes.put("XQBESTATE","State");
        WccToPes.put("XQBEDOL","DOL");
        WccToPes.put("XQBEBROKERREF","BrokerRef");
        WccToPes.put("XQBEUNDERWRITINGPOLICYREF","UnderwritingPolicyRef");
        WccToPes.put("XQBEBUREAUREFERENCE","BureauReference");
        WccToPes.put("XQBEECFCLAIM","ECFClaim");
        WccToPes.put("XQBEUNIQUECLAIMREFERENCE","UniqueClaimReference");
        WccToPes.put("XQBECLAIMSHANDLER","ClaimsHandler");
        WccToPes.put("XQBECLAIMANTNAME","ClaimantName");
        WccToPes.put("XCOMMENTS","xComments");
        WccToPes.put("XQBESUBTYPEDESC","SubTypeDesc");
        WccToPes.put("XQBEPOLICYYEAR","PolicyYear");
        WccToPes.put("XQBEPRECLAIMFILENAME","PreClaimFileName");
        WccToPes.put("XQBEQIILCLAIMSMISCDOCUMENTTYPE","QIILClaimsMiscDocumentType");
        WccToPes.put("XQBEBROKER","Broker");
        WccToPes.put("XQBEDOCUMENTSUBTYPEDESCRIPTION","DocumentSubTypeDescription");
        WccToPes.put("XQBEACCOUNTSOURCE","AccountSource");
        WccToPes.put("XQBECLAIMANT_CLAIMNAME","Claimant_ClaimName");
        WccToPes.put("XQBECLOSEDDATE","ClosedDate");
        WccToPes.put("XQBEAUDITNUMBER","AuditNumber");
        WccToPes.put("XQBECLAIMREVIEWEDBY","ClaimReviewedBy");
        WccToPes.put("XQBECLAIMSCOMMENTS","CLaimsComments");
        WccToPes.put("XQBECLAIMSTATUS","ClaimStatus");
        WccToPes.put("XQBEBROKERGROUP","BrokerGroup");
        WccToPes.put("XQBETERRSCOPE","TerrScope");
        WccToPes.put("XQBEDATEAUTHORISED","DateAuthorised");
        WccToPes.put("XQBEDOMICILE","Domicile");
        WccToPes.put("XQBEEXCESS","Excess");
        WccToPes.put("XQBELIMIT","Limit");
        WccToPes.put("XQBEPOLICYDESCRIPTION","PolicyDescription");
        WccToPes.put("XQBEBROKERREF_UMR","BrokerRef_UMR");
        WccToPes.put("XQBEPROGRAMMEREFERENCE","ProgrammeReference");
        WccToPes.put("XQBESIGNINGDATEANDNO","SigningDateandNo");
        WccToPes.put("XQBESUBCLASS","SubClass");
        WccToPes.put("XQBE12STAMPREF","StampRef");
        WccToPes.put("XQBETYPE","QBEType");
        WccToPes.put("XQBETRANSACTIONAL","Transactional");
        WccToPes.put("XSTORAGERULE","StorageRule");
        WccToPes.put("XCLBRAALIASLIST","CLBRAAliasList");
        WccToPes.put("DDOCNAME","DocumentName");
        WccToPes.put("DDOCTYPE","DocumentType");
        WccToPes.put("DDOCTITLE","RevisionTitle");
        WccToPes.put("DSECURITYGROUP","SecurityGroup");
        WccToPes.put("DDOCACCOUNT","DocAccount");
        WccToPes.put("DCREATEDATE","CreateDate");
        WccToPes.put("DFORMAT","DocFormat");
        WccToPes.put("DORIGINALNAME","OriginalName");
        WccToPes.put("DEXTENSION","DocExtension");
        WccToPes.put("DDESCRIPTION","Content_Category");

    }

    public static String getPesField(String _wccField){
        if (_wccField == null){
            return null;
        }

        String fieldName = null;
        if (_wccField.startsWith("edge:jdbc:")){
            fieldName = _wccField.substring("edge:jdbc:".length()+1);
        } else {
            fieldName = _wccField;
        }

        if (WccToPes.containsKey(fieldName.toUpperCase())) {
            return WccToPes.get(fieldName.toUpperCase()).toLowerCase();
        } else {
            return fieldName;
        }
    }

}
