package edu.umd.lib.ole.profile;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.kuali.ole.batch.bo.OLEBatchProcessProfileBo;
import org.kuali.ole.batch.bo.xstream.OLEBatchProcessProfileRecordProcessor;
import org.kuali.ole.sys.context.SpringContext;
import org.kuali.rice.krad.service.LookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;

/**
 * Servlet for exporting a OLE Batch Process Profile to XML.
 */
public class ProfileExportServlet extends HttpServlet 
{
  private static final long serialVersionUID = 1L;
  
  private static final Logger LOG = LoggerFactory.getLogger(ProfileExportServlet.class);
  private static Configuration freeMarkerCfg;

  /**
   * Sets up FreeMarker configuration.
   */
  @Override
  public void init() throws ServletException {
    super.init();
    
    // Set up FreeMarker configuration
    freeMarkerCfg = new Configuration();
    freeMarkerCfg.setClassForTemplateLoading(this.getClass(), "");
    freeMarkerCfg.setObjectWrapper(new DefaultObjectWrapper());
    freeMarkerCfg.setDefaultEncoding("UTF-8");
    freeMarkerCfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    freeMarkerCfg.setIncompatibleImprovements(new Version(2, 3, 20));  // FreeMarker 2.3.20  
  }

  /**
   * Responds to GET requests. If a request parameter of "profileName" is
   * provided, will attempt to return a profile with that name, otherwise
   * an HTML form will be displayed.
   * 
   * @param request the HttpServletRequest containing the request parameters
   * @param response the HttpServletResponse for the server response.
   * @throws ServletException if the request cannot be handled
   * @throws IOException if an I/O exception occurs.
   */
  public void doGet(
    HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
  {
    String profileName = request.getParameter("profileName");
    if( profileName == null ) 
    {
      sendExportRequestForm(request, response);
    } else {
      sendProfile( profileName, response );  
    }
  }
  
  /**
   * Responds with an HTML form to specify the profile to export.
   * 
   * @param request the HttpServletRequest containing the request parameters
   * @param response the HttpServletResponse for the server response.
   */
  private void sendExportRequestForm( HttpServletRequest request, 
      HttpServletResponse response) {
      
      try {
        Writer out = response.getWriter();
        Template temp = freeMarkerCfg.getTemplate("exportRequestForm.ftl");
        temp.process(null, out);
      } catch (TemplateException e) {
        LOG.error("Error processing template", e);
      } catch( IOException ioe ) {
        LOG.error("Error writing response", ioe);
      }
  }

  /**
   * If a profile with the given name is found, responds with the profile,
   * otherwise an HTML page describing the error is returned.
   * 
   * @param profileName
   * @param response
   * @throws IOException
   */
  void sendProfile( String profileName,
    HttpServletResponse response)
    throws IOException
  {
    LOG.debug("sendProfile: profileName=" + profileName);
    
    List<String> errors = new ArrayList<String>();

    SpringContext.getBean(LookupService.class);
    LookupService lookup = SpringContext.getBean(LookupService.class);
    Collection<OLEBatchProcessProfileBo> c = lookup.findCollectionBySearchHelper(
        OLEBatchProcessProfileBo.class, new HashMap<String, String>(), true);

    boolean profileFound = false;
    for( Iterator<OLEBatchProcessProfileBo> it = c.iterator(); it.hasNext(); ) {
      OLEBatchProcessProfileBo profile = it.next();
      if ( profile.getBatchProcessProfileName().equals(profileName) )
      {
        response.setContentType("text/xml");
        response.setHeader("Content-Disposition",
            "attachment; filename=" + profileName +".xml");
        PrintWriter out = response.getWriter();
        OLEBatchProcessProfileRecordProcessor rp =
            new OLEBatchProcessProfileRecordProcessor();
        out.println(rp.toXml(profile));
        profileFound = true;
        out.flush();
        LOG.info("sendProfile: " + profileName + " successfully exported.");
        return;
      }
    }
    
    // Error handling
    if (!profileFound) {
      errors.add("Could not find a profile named '" + profileName + "'");
    }

    if ( LOG.isWarnEnabled() ) {
      LOG.warn("sendProfile: Could not export profile named \"" + profileName + "\".");
      LOG.warn("sendProfile: The following errors occurred:");
      for( String error: errors )
      {
        LOG.warn("\t"+error);
      }
    }
    
    Map<String, List<String>> templateParams = new HashMap<String,List<String>>();
    templateParams.put("errors", errors);
    
    Template temp = freeMarkerCfg.getTemplate("error.ftl");
    
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
   
    try {
      temp.process(templateParams, out);
    } catch (TemplateException e) {
      LOG.error("Error processing template", e);
    }
  }

  /**
   * Delegates all POST requests to doGet method.
   * 
   * @param request the HttpServletRequest containing the request parameters
   * @param response the HttpServletResponse for the server response.
   * @throws ServletException if the request cannot be handled
   * @throws IOException if an I/O exception occurs.
   */
  public void doPost(
    HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException 
  {  
    doGet( request, response );
  }
}
