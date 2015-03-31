package edu.umd.lib.ole.profile;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.kuali.ole.batch.bo.OLEBatchGloballyProtectedField;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileBibMatchPoint;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileBibStatus;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileBibWorkUnit;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileBo;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileConstantsBo;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileDataMappingOptionsBo;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileDeleteField;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileFilterCriteriaBo;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileInstanceMatchPoint;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileInstanceWorkUnit;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileMappingOptionsBo;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileMatchPoint;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileProtectedField;
import org.kuali.ole.batch.bo.OLEBatchProcessProfileRenameField;
import org.kuali.ole.batch.bo.xstream.OLEBatchProcessProfileRecordProcessor;
import org.kuali.ole.sys.context.SpringContext;
import org.kuali.rice.core.api.resourceloader.GlobalResourceLoader;
import org.kuali.rice.kew.api.exception.WorkflowException;
import org.kuali.rice.krad.UserSession;
import org.kuali.rice.krad.bo.AdHocRouteRecipient;
import org.kuali.rice.krad.bo.DocumentHeader;
import org.kuali.rice.krad.maintenance.MaintenanceDocument;
import org.kuali.rice.krad.service.DocumentService;
import org.kuali.rice.krad.service.KRADServiceLocatorWeb;
import org.kuali.rice.krad.service.LookupService;
import org.kuali.rice.krad.service.MaintenanceDocumentService;
import org.kuali.rice.krad.util.GlobalVariables;
import org.kuali.rice.krad.util.KRADConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;

/**
 * Servlet for importing Batch Process Profiles from XML.
 */
public class ProfileImportServlet extends HttpServlet 
{
  private static final long serialVersionUID = 1L;
  
  private static final Logger LOG = LoggerFactory.getLogger(ProfileImportServlet.class);
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
   * Responds to GET requests. Responds with an HTML form for specifying the
   * profile to upload.
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
    sendImportRequestForm(request, response);
  }
  
  /**
   * Sends an HTML form for specifying the profile to upload.
   * 
   * @param reques the HttpServletRequest containing the request parameters
   * @param response the HttpServletResponse for the server response.
   */
  private void sendImportRequestForm( HttpServletRequest request, 
      HttpServletResponse response) {

      try {
        Writer out = response.getWriter();
        Template temp = freeMarkerCfg.getTemplate("importRequestForm.ftl");
        temp.process(null, out);
      } catch (TemplateException e) {
        LOG.error("Error processing template", e);
      } catch( IOException ioe ) {
        LOG.error("Error writing response", ioe);
      }
  }
  
  /**
   * Imports the profile contained in the given HttpServletRequest, responding
   * with an HTML page describing success or failure.
   * 
   * @param request the HttpServletRequest containing the request parameters
   * @param response the HttpServletResponse for the server response.
   * @throws IOException
   */
  void importProfile( HttpServletRequest request,
    HttpServletResponse response)
    throws IOException
  {  
    // Create a factory for disk-based file items
    DiskFileItemFactory factory = new DiskFileItemFactory();

    // Configure a repository (to ensure a secure temp location is used)
    ServletContext servletContext = this.getServletConfig().getServletContext();
    File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
    factory.setRepository(repository);

    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload(factory);

    List<String> errors = new ArrayList<String>();
    
    List<FileItem> items = null;
    try {
      // Parse the request
      items = upload.parseRequest(request);
    } catch (FileUploadException fue) {
      LOG.error("Error uploading file.", fue);
      errors.add("Error uploading file: " + fue.toString());
    }

    String newProfileName = null;
    String documentDescription = null;
    String userName = null;
    String fileContents = null;
    
    if ( items != null )
    {
      // Process the uploaded items
      Iterator<FileItem> iter = items.iterator();
      while (iter.hasNext()) {
          FileItem item = iter.next();

          if (item.isFormField()) {
            String name = item.getFieldName();
            if ( name == null ) {
              continue;
            }

            if ( name.equals("newProfileName") ) {
              newProfileName = item.getString();
            }
            else if ( name.equals("documentDescription" ) ) {
              documentDescription = item.getString();
            }
            else if ( name.equals("userName" ) ) {
              userName = item.getString();
            }
          } else {
             fileContents = new String(item.get());
          }
      }    
    }
    
    boolean inputOk = true;
    if ( newProfileName == null || (newProfileName.trim().equals(""))) {
      errors.add("Please enter a new profile name.");
      inputOk = false;
    }
    if ( newProfileName.length() > 40 ) {
      errors.add("Profile name is too long. Must be 40 characters or fewer.");
      inputOk = false;
    }
    if ( documentDescription == null || (documentDescription.trim().equals(""))) {
      errors.add("Please enter a document description.");
      inputOk = false;
    }
    if ( documentDescription.length() > 40 ) {
      errors.add("Document description is too long. Must be 40 characters or fewer.");
      inputOk = false;
    }
    if ( userName == null || (userName.trim().equals(""))) {
      errors.add("Please enter a user name.");
      inputOk = false;
    }
    if ( fileContents == null || (fileContents.trim().equals(""))) {
      errors.add("Please select a file for upload.");
      inputOk = false;
    }
    
    boolean profileUploaded = false;
    
    if (inputOk)
    {
      SpringContext.getBean(LookupService.class);
      
      // Convert XML String to OLEBatchProcessProfileBo
      OLEBatchProcessProfileRecordProcessor processor =
          new OLEBatchProcessProfileRecordProcessor();
      OLEBatchProcessProfileBo profile = processor.fromXML(fileContents);
      
      //==================================================================
      // Clear out old ids on profile object and all the objects it holds
      // This is necessary, because otherwise the new profile will "steal"
      // the old object from the old profile, changing the old profile.
      // 
      // If there is a more efficient way to do this, please let me know.
      //==================================================================
      profile.setBatchProcessProfileId(null);
      profile.setObjectId(null);
      profile.setVersionNumber(null);
      
      List<OLEBatchProcessProfileFilterCriteriaBo> ppfcList =
          profile.getOleBatchProcessProfileFilterCriteriaList();
      for(OLEBatchProcessProfileFilterCriteriaBo item: ppfcList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setFilterId(null);
        item.setOleBatchProcessFilterCriteriaBo(null);
      }
      
      List<OLEBatchProcessProfileMappingOptionsBo> ppmoList =
          profile.getOleBatchProcessProfileMappingOptionsList();
      for(OLEBatchProcessProfileMappingOptionsBo item: ppmoList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleBatchProcessDataMapId(null);
        item.setOleBatchProcessDataMapOptionId(null);
        item.setOleBatchProcessProfileBo(null);
        
        List<OLEBatchProcessProfileDataMappingOptionsBo> ppdmobList =
            item.getOleBatchProcessProfileDataMappingOptionsBoList();
        for(OLEBatchProcessProfileDataMappingOptionsBo subitem: ppdmobList) {
          subitem.setObjectId(null);
          subitem.setOleBatchProcessDataMapId(null);
          subitem.setOleBatchProcessProfileDataMappingOptionId(null);
          subitem.setOleBatchProcessProfileMappingOptionsBo(null);
          subitem.setVersionNumber(null);
        }
        
      }
      
      List<OLEBatchProcessProfileDataMappingOptionsBo> ppdmobList =
          profile.getOleBatchProcessProfileDataMappingOptionsBoList();
      for(OLEBatchProcessProfileDataMappingOptionsBo item: ppdmobList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setOleBatchProcessProfileDataMappingOptionId(null);
        item.setOleBatchProcessProfileMappingOptionsBo(null);
        item.setOleBatchProcessDataMapId(null);
      }
      
      List<OLEBatchGloballyProtectedField> gpfList =
          profile.getOleBatchGloballyProtectedFieldList();
      for(OLEBatchGloballyProtectedField item: gpfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setId(null);
      }
      
      List<OLEBatchProcessProfileProtectedField> pppfList =
          profile.getOleBatchProcessProfileProtectedFieldList();
      for(OLEBatchProcessProfileProtectedField item: pppfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleProfileProtectedFieldId(null);
        item.setOleGloballyProtectedFieldId(null);
        item.setOleGloballyProtectedField(null);
      }
      
      List<OLEBatchProcessProfileConstantsBo> ppcList =
          profile.getOleBatchProcessProfileConstantsList();
      for(OLEBatchProcessProfileConstantsBo item: ppcList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleBatchProcessProfileConstantsId(null);
      }
      
      List<OLEBatchProcessProfileInstanceMatchPoint> ppimpList =
          profile.getOleBatchProcessProfileInstanceMatchPointList();
      for(OLEBatchProcessProfileInstanceMatchPoint item: ppimpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleInstanceMatchPointId(null);
      }
      
      List<OLEBatchProcessProfileBibMatchPoint> ppbmpList =
          profile.getOleBatchProcessProfileBibMatchPointList();
      for(OLEBatchProcessProfileBibMatchPoint item: ppbmpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleBibMatchPointId(null);
      }
      
      List<OLEBatchProcessProfileMatchPoint> ppmpList =
          profile.getOleBatchProcessProfileMatchPointList();
      for(OLEBatchProcessProfileMatchPoint item: ppmpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setMatchPointId(null);
      }
      
      List<OLEBatchProcessProfileMatchPoint> ppbibmpList =
          profile.getOleBatchProcessProfileBibliographicMatchPointList();
      for(OLEBatchProcessProfileMatchPoint item: ppbibmpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setMatchPointId(null);
      }      
      
      List<OLEBatchProcessProfileMatchPoint> pphmpList =
          profile.getOleBatchProcessProfileHoldingMatchPointList();
      for(OLEBatchProcessProfileMatchPoint item: pphmpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setMatchPointId(null);
      }
          
      List<OLEBatchProcessProfileMatchPoint> ppItemmpList =
          profile.getOleBatchProcessProfileItemMatchPointList();
      for(OLEBatchProcessProfileMatchPoint item: ppItemmpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setMatchPointId(null);
      }
      
      List<OLEBatchProcessProfileMatchPoint> ppempList =
          profile.getOleBatchProcessProfileEholdingMatchPointList();
      for(OLEBatchProcessProfileMatchPoint item: ppempList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setMatchPointId(null);
      }
     
      List<OLEBatchProcessProfileBibMatchPoint> dbppbmpList =
          profile.getDeletedBatchProcessProfileBibMatchPointList();
      for(OLEBatchProcessProfileBibMatchPoint item: dbppbmpList) {
         item.setObjectId(null);
         item.setVersionNumber(null);
         item.setBatchProcessProfileId(null);
         item.setOleBatchProcessProfileBo(null);
         item.setOleBibMatchPointId(null);
      }
      
      List<OLEBatchProcessProfileBibStatus> dbppbsList =
          profile.getDeleteBatchProcessProfileBibStatusList();
      for(OLEBatchProcessProfileBibStatus item: dbppbsList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setBatchProcessBibStatusId(null);
      }
      
      List<OLEBatchProcessProfileDeleteField> dbppdfList =
          profile.getDeletedBatchProcessProfileDeleteFieldsList();
      for(OLEBatchProcessProfileDeleteField item: dbppdfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setId(null);
      }
      
      List<OLEBatchProcessProfileRenameField> dbpprfList =
          profile.getDeletedBatchProcessProfileRenameFieldsList();
      for(OLEBatchProcessProfileRenameField item: dbpprfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setId(null);
      }
      
      List<OLEBatchProcessProfileFilterCriteriaBo> dbppfcList =
          profile.getDeleteBatchProcessProfileFilterCriteriaList();
      for(OLEBatchProcessProfileFilterCriteriaBo item: dbppfcList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setFilterId(null);
        item.setOleBatchProcessFilterCriteriaBo(null);
      }
      
      List<OLEBatchProcessProfileMappingOptionsBo> dbppmoList =
          profile.getDeletedBatchProcessProfileMappingOptionsList();
      for(OLEBatchProcessProfileMappingOptionsBo item: dbppmoList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleBatchProcessDataMapId(null);
        item.setOleBatchProcessDataMapOptionId(null);
        item.setOleBatchProcessProfileBo(null);
        
        List<OLEBatchProcessProfileDataMappingOptionsBo> optList =
            item.getOleBatchProcessProfileDataMappingOptionsBoList();
        for(OLEBatchProcessProfileDataMappingOptionsBo subitem: optList) {
          subitem.setObjectId(null);
          subitem.setOleBatchProcessDataMapId(null);
          subitem.setOleBatchProcessProfileDataMappingOptionId(null);
          subitem.setOleBatchProcessProfileMappingOptionsBo(null);
          subitem.setVersionNumber(null);
        }
      }
      
      List<OLEBatchProcessProfileDataMappingOptionsBo> dbppdmoList =
          profile.getDeletedBatchProcessProfileDataMappingOptionsList();
      for(OLEBatchProcessProfileDataMappingOptionsBo item: dbppdmoList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setOleBatchProcessProfileDataMappingOptionId(null);
        item.setOleBatchProcessProfileMappingOptionsBo(null);
        item.setOleBatchProcessDataMapId(null);
      }
      
      List<OLEBatchProcessProfileConstantsBo> dbppcList =
          profile.getDeletedBatchProcessProfileConstantsList();
      for(OLEBatchProcessProfileConstantsBo item: dbppcList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleBatchProcessProfileConstantsId(null);
      }
      
      List<OLEBatchProcessProfileProtectedField> dbpppfList =
          profile.getDeletedBatchProcessProfileProtectedFieldList();
      for(OLEBatchProcessProfileProtectedField item: dbpppfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setOleProfileProtectedFieldId(null);
        item.setOleGloballyProtectedFieldId(null);
        item.setOleGloballyProtectedField(null);
      }
      
      List<OLEBatchProcessProfileMatchPoint> dbppmpList =
          profile.getDeletedBatchProcessProfileMatchPointList();
      for(OLEBatchProcessProfileMatchPoint item: dbppmpList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setMatchPointId(null);
      }
      
      List<OLEBatchProcessProfileBibStatus> ppbsList =
          profile.getOleBatchProcessProfileBibStatusList();
      for(OLEBatchProcessProfileBibStatus item: ppbsList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setBatchProcessBibStatusId(null);
      }
      
      List<OLEBatchProcessProfileBibWorkUnit> ppbwuList =
          profile.getOleBatchProcessProfileBibWorkUnitList();
      for(OLEBatchProcessProfileBibWorkUnit item: ppbwuList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setBatchProcessBibWorkUnitId(null);
      }
      
      List<OLEBatchProcessProfileInstanceWorkUnit> ppiwuList =
          profile.getOleBatchProcessProfileInstanceWorkUnitList();
      for(OLEBatchProcessProfileInstanceWorkUnit item: ppiwuList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setBatchProcessInstanceWorkUnitId(null);
      }
      
      List<OLEBatchProcessProfileDeleteField> ppdfList =
          profile.getOleBatchProcessProfileDeleteFieldsList();
      for(OLEBatchProcessProfileDeleteField item: ppdfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setId(null);
      }
      
      List<OLEBatchProcessProfileRenameField> pprfList =
          profile.getOleBatchProcessProfileRenameFieldsList();
      for(OLEBatchProcessProfileRenameField item: pprfList) {
        item.setObjectId(null);
        item.setVersionNumber(null);
        item.setBatchProcessProfileId(null);
        item.setOleBatchProcessProfileBo(null);
        item.setId(null);
      }
      //==================================================================
      // Done clearing ids
      //==================================================================
      
      //=============================================
      // Push OLEBatchProcessProfileBo into database
      //=============================================
      String currentProfileName = profile.getBatchProcessProfileName();
      LOG.info( "importProfile: Importing \"" + currentProfileName + 
          "\" as \"" + newProfileName + "\"");
      
      // Set up a UserSession
      UserSession userSession = new UserSession(userName);
      GlobalVariables.setUserSession(userSession);
      
      // Set up the type of document to populate (OLEBatchProcessProfileBo)
      String dataObjectClassName = "kuali.ole.batch.bo.OLEBatchProcessProfileBo";
      String docTypeName = "OLE_BTCH_PRCS_PRFL";
      String maintenanceAction = KRADConstants.MAINTENANCE_NEW_ACTION;

      // Create a new (empty) Maintenance document
      MaintenanceDocumentService docService = KRADServiceLocatorWeb.getMaintenanceDocumentService();
      MaintenanceDocument doc = docService.setupNewMaintenanceDocument(
          dataObjectClassName, docTypeName, maintenanceAction);

      // Populate the maintenance document header
      DocumentHeader docHeader = doc.getDocumentHeader();
      docHeader.setDocumentDescription(documentDescription);

      // Populate the document object with the profile object
      doc.getNewMaintainableObject().setDataObject(profile);

      // Reset the profile name
      profile.setBatchProcessProfileName(newProfileName);

      // Retrieve the DocumentService for submitting the profile
      final DocumentService docService2 = SpringContext.getBean(DocumentService.class);

      // Retrieve the transaction manager
      PlatformTransactionManager transactionManager =
          GlobalResourceLoader.getService("transactionManager");
      TransactionTemplate template = new TransactionTemplate(transactionManager);

      final MaintenanceDocument transactionDoc = doc;
      final List<AdHocRouteRecipient> adHocRecipients = java.util.Collections.emptyList();
      final String annotations = null;
      
      // Submit the new profile as a maintenance document within a transaction
      profileUploaded = template.execute(new TransactionCallback<Boolean>() {
        public Boolean doInTransaction(TransactionStatus status) {
          try {
            docService2.routeDocument(transactionDoc,annotations, adHocRecipients);
            return Boolean.TRUE;
          } catch( WorkflowException we ) { 
            LOG.error("Error routing document", we);
          }
          return Boolean.FALSE;
        }
      });    
    }
    
    //=============================================
    // Return HTML page indicating success/failure
    //=============================================
    Map<String, Object> templateParams = new HashMap<String, Object>();
    templateParams.put("newProfileName", newProfileName);
    templateParams.put("documentDescription", documentDescription);
    templateParams.put("userName", userName);
    templateParams.put("fileContents", fileContents);
    
    if (profileUploaded) {
      sendImportResponse(response, templateParams, "importSuccess.ftl");
      LOG.info("importProfile: \""+newProfileName+"\" was successfully imported.");
      return;
    } else {
      templateParams.put("errors", errors);
      sendImportResponse(response, templateParams, "importError.ftl");
      
      if ( LOG.isWarnEnabled() ) {
        LOG.warn("importProfile: Could not import profile named \"" + newProfileName + "\".");
        LOG.warn("importProfile: The following errors occurred:");
        for( String error: errors )
        {
          LOG.warn("\t"+error);
        }
      }
    }
  }
  
  /**
   * Sends an HTML response page, using the given HttpServletResponse, FTL
   * template parameters, and FTL template filename,
   * 
   * @param response the HttpServletResponse to write the response to.
   * @param templateParams a Map containing the template parameters
   * @param templateFilename the filename of the FTL template to use in
   * creating the HTML page.
   */
  private void sendImportResponse(HttpServletResponse response,
      Map<String, Object> templateParams, String templateFilename)
  {
    try {
      Template template = freeMarkerCfg.getTemplate(templateFilename);
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
   
      template.process(templateParams, out);
    } catch (TemplateException e) {
      LOG.error("Error processing template", e);
    } catch( IOException ioe ) {
      LOG.error("Error writing response", ioe);
    }
  }

  /**
   * Handles POST requests. If the request contains multipart content, will
   * attempt to import the content as a new profile. Otherwise will delegate
   * to GET to display an HTML form for uploading a profile.
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
    boolean isMultipart = ServletFileUpload.isMultipartContent(request);
    if ( isMultipart )
    {
      importProfile( request, response );
    }
    else
    {
      doGet(request, response);
    }
  }
}
