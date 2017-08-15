package org.n52.wps.execute.input.proxy;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.n52.geoprocessing.oauth2.OAuth2Client;
import org.n52.wps.execute.input.proxy.util.Configuration;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.request.InputReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

import net.opengis.wps.x20.DataInputType;
import net.opengis.wps.x20.ExecuteDocument;

@Controller
@RequestMapping(
        value = "/service", consumes = "*/*", produces = "*/*")
public class Service implements ServletContextAware, ServletConfigAware{

    private static Logger LOGGER = LoggerFactory.getLogger(Service.class);
    
    private ServletContext ctx;

    public void init(){
        Configuration.getInstance(ctx.getResourceAsStream("/WEB-INF/config/config.json"));
    }
    
    @RequestMapping(method = RequestMethod.POST)
    public void post(HttpServletRequest req,
            HttpServletResponse res){
        try {
            XmlObject postRequest = XmlObject.Factory.parse(req.getInputStream());
            
            if(postRequest instanceof ExecuteDocument){
                
                //check inputs
                ExecuteDocument executeDoc = (ExecuteDocument)postRequest;
                
                DataInputType[] dataInputArray = executeDoc.getExecute().getInputArray();
                
                for (DataInputType dataInputType : dataInputArray) {
                    
                    if(dataInputType.isSetReference()){
                        checkDataInput(dataInputType);
                    }
                    
                }
                
                //get token
                
            }
            
            
        } catch (XmlException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    //checks, if data input is an protected OWS service 
    //tries to add access token to authorization header
    private void checkDataInput(DataInputType dataInputType) {
        
        InputReference inputReference = new InputReference(dataInputType); 
     
        InputChecker checker = new InputChecker();
        
        if(checker.isApplicable(inputReference)){
            try {
                checker.fetchData(inputReference);
            } catch (ExceptionReport e) {
                LOGGER.error("Could not fetch input: " + dataInputType.getId(), e);
            }
        }
    }


    @Override
    public void setServletConfig(ServletConfig arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setServletContext(ServletContext arg0) {
       ctx = arg0;        
    }

}
