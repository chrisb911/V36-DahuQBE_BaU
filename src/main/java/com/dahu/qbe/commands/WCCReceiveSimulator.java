package com.dahu.qbe.commands;

        import com.dahu.def.exception.BadConfigurationException;
        import com.dahu.def.exception.CommandException;
        import com.dahu.def.exception.ContextException;
        import com.dahu.def.exception.MissingArgumentException;
        import com.dahu.def.messages.QueryRequest;
        import com.dahu.def.plugins.CommandPluginBase;
        import com.dahu.def.types.Command;
        import com.dahu.def.types.CommandPluginContext;
        import com.dahu.def.types.Payload;
        import org.apache.logging.log4j.Level;

        import javax.servlet.http.HttpServletRequest;
        import javax.servlet.http.Part;
        import javax.xml.bind.JAXBException;
        import javax.xml.transform.TransformerException;
        import java.io.BufferedReader;
        import java.io.IOException;
        import java.io.InputStream;
        import java.io.InputStreamReader;
        import java.security.NoSuchAlgorithmException;
        import java.util.Collection;
        import java.util.Set;

public class WCCReceiveSimulator extends CommandPluginBase {

    public WCCReceiveSimulator(Level _level, Command _plugin) {

        super(_level, _plugin);
        registerAction("cs");
        registerAction("security_check");
    }

    @Override
    public void doHandleRequest(CommandPluginContext _context) throws ContextException, MissingArgumentException, BadConfigurationException, IOException, TransformerException, NoSuchAlgorithmException, CommandException, JAXBException {


        QueryRequest request = parseRequest(_context.getQueryRequest());
        String path = request.getFullPath();


        if (path.contains("login/j_security_check")){
            incrementActionRequest("security_check");
            incrementActionResponse("security_check");
            logger.info("got a security request");
            setResponse(200, _context, "success : security request received");


        } else {
            logger.info("got an acknowledgement request " + getBody(request.getRequest()) );
            incrementActionRequest("cs");
            incrementActionResponse("cs");
            setResponse(200, _context, "success : acknowledgement request received");

        }


    }
    private void setResponse( int _code, CommandPluginContext _context, String _msg){
        _context.getQueryResponse().setStatusCode(_code);
        _context.setPayload(new Payload("{\"response\":\""+ _msg+"\"}"));
        _context.getQueryResponse().setPayload(_context.getPayload().toJson());
    }

    public static String getBody(HttpServletRequest request) throws IOException {

        String body = null;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
            }
        } catch (IOException ex) {
            throw ex;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }

        body = stringBuilder.toString();
        return body;
    }
}
