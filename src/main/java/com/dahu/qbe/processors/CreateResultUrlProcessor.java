package com.dahu.qbe.processors;

import com.dahu.core.abstractcomponent.AbstractProcessor;
import com.dahu.core.abstractcomponent.AbstractQueryPostProcessor;
import com.dahu.core.abstractcomponent.AbstractQueryPreProcessor;
import com.dahu.core.interfaces.iDocument;
import com.dahu.core.search.DEFSurfaceQueryRequest;
import com.dahu.core.search.DEFSurfaceQueryResponse;
import com.dahu.def.config.PluginConfig;
import com.dahu.def.exception.BadConfigurationException;
import com.dahu.def.types.Component;
import com.dahu.def.types.Properties;
import com.dahu.surface.core.exception.SurfaceException;
import com.dahu.warehouse.core.Warehouse;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;


public class CreateResultUrlProcessor extends AbstractQueryPostProcessor {

    private final static String RESULTURLPROCESSOR_INPUT_PREFIX = "inputPrefix";
    private final static String RESULTURLPROCESSOR_REPLACE_VALUE = "replaceValue";
    private final static String RESULTURLPROCESSOR_INPUT_FIELD = "inputField";
    private final static String RESULTURLPROCESSOR_OUTPUT_FIELD = "outputField";

    String inputField_;
    String outputField_;
    String inputPrefix_;
    String replaceValue_;



    public CreateResultUrlProcessor(Level _level, Component _component) throws BadConfigurationException {
        super(_level, _component);
    }


    @Override
    public DEFSurfaceQueryResponse process(DEFSurfaceQueryResponse _response) throws SurfaceException {

        if ((null == inputField_) || (null == outputField_)){
            throw new SurfaceException("PrefixReplacer needs an input and output field to be defined in the Surface config");
        }
        if (_response.getDocuments().size()>0){
            for (DEFSurfaceQueryResponse.DEFSurfaceQueryResponseDocument doc : _response.getDocuments()) {
                String inputField = null;
                List<DEFSurfaceQueryResponse.DEFSurfaceQueryResponseDocumentField> fields = doc.getPrimaryFields();
                for (DEFSurfaceQueryResponse.DEFSurfaceQueryResponseDocumentField field : fields) {
                    if (field.getName().equalsIgnoreCase(inputField_)) {
                        List<String> values = field.getValues();
                        if (null != values && values.size()>=1) {
                            inputField = values.get(0);
                        }
                    }
                }

                if (null != inputField && null !=inputPrefix_  && null != replaceValue_&& inputField.startsWith(inputPrefix_)) {
                    String outputField = replaceValue_.concat(inputField.substring(inputField.indexOf(inputPrefix_) + 1));
                    DEFSurfaceQueryResponse.DEFSurfaceQueryResponseDocumentField newField = new DEFSurfaceQueryResponse.DEFSurfaceQueryResponseDocumentField();
                    newField.setFieldName(outputField_);
                    List<String> newValues = new ArrayList<>();
                    newValues.add(outputField);
                    newField.setValues(newValues);
                    fields.add(newField);
                } else {
                    // not necessarily an error - flag it in debug though
                    logger.debug("Failed to replace the field value for input field  '"+inputField +"'");
                }
            }
        }
        return _response;
    }

    @Override
    public boolean initialiseMe() throws BadConfigurationException {
        Properties surface = PluginConfig.getPluginProperties(getParent());
        inputPrefix_ = surface.getPropertyByName("postQueryComponents::"+resourceName+"::" + RESULTURLPROCESSOR_INPUT_PREFIX);
        replaceValue_ = surface.getPropertyByName("postQueryComponents::"+resourceName+"::" + RESULTURLPROCESSOR_REPLACE_VALUE);
        inputField_ = surface.getPropertyByName("postQueryComponents::"+resourceName+"::" + RESULTURLPROCESSOR_INPUT_FIELD);
        outputField_ = surface.getPropertyByName("postQueryComponents::"+resourceName+"::" + RESULTURLPROCESSOR_OUTPUT_FIELD);
        if (null == inputPrefix_ || null == replaceValue_ || null == inputField_ || null == outputField_) throw new BadConfigurationException("CreateResultUrlProcessor requires an inputPrefix, a replaceValue , an inputField and an outputField to be set in the config.");
        return true;
    }
}