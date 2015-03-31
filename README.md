# ole-batch-process-profile-import-export

Servlets for exporting and importing OLE Batch Process profiles.

Functionality consists of two servlets, an "export" servlet and an "import" 
servlet.

The export servlet enables an OLE Batch Process profile to be downloaded as an 
XML file.

The import servlet enables an XML file to be uploaded to OLE, and imported as a 
new batch process profile.

These servlets are intended as a stop-gap until OLE-5759 
(https://jira.kuali.org/browse/OLE-5759) is completed.

The script has been tested in the following environment:

* OLE v1.5.6.1
* MySQL v5.6.21

## Important Note on Importing Profiles

These export/import servlets can be used for copying profiles by exporting an 
existing profile, and importing it under a new name. Importing a batch process 
profile is potentially **UNSAFE** and **COULD RESULT IN DATA LOSS**. When a 
batch process profile is exported, unique object identifiers are included in the 
XML file. If these object identifiers are not cleared when imported, any objects 
with those identifiers are moved from the old profile to the new profile, 
changing the old profile.

The servlet automatically finds and clears out these object identifiers so that 
OLE will generate new ones. Attempts have been made to identify and clear out 
the appropriate ids, but this cannot be guaranteed.

One way to verify that the copy did not change the old profile is to re-export 
the original profile, saving it to a different XML file, and comparing the 
originally exported and re-exported XML files for changes. If the files are the 
same, the import did not change the original profile.

Exporting a batch process profile as XML is believed to be safe, as no changes 
are made to the database.

## Requirements

* Java 1.6
* Maven 3

## Build/Setup

1) Install the required software

2) Clone this repository

3) mvn clean package

4) Copy target/profile-servlet-0.1.1.jar to
[Tomcat Directory]/webapps/olefs/WEB-INF/lib/

5) Edit the [Tomcat Directory]/webapps/olefs/WEB-INF/web.xml file, adding the 
following lines:

```
    <!-- Profile Export Servlet -->
    <servlet>
        <servlet-name>umd-profile-export</servlet-name>
        <servlet-class>edu.umd.lib.ole.profile.ProfileExportServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>umd-profile-export</servlet-name>
        <url-pattern>/profile-export</url-pattern>
    </servlet-mapping>
    <!-- Profile Import Servlet -->
    <servlet>
        <servlet-name>umd-profile-import</servlet-name>
        <servlet-class>edu.umd.lib.ole.profile.ProfileImportServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>umd-profile-import</servlet-name>
        <url-pattern>/profile-import</url-pattern>
    </servlet-mapping>
```

## Usage

### To export a profile:

1) Figure out the "Batch Profile Name" of the profile you want to export. In the 
"Batch Process Profile Lookup" page (Admin | Batch Process Profile) this is the 
value in the "Batch Profile Name" column.

2) Go to http://[OLE Server]/olefs/profile-export

3) Put the name from Step 1 in the "Profile Name" field. Left-click the "Submit" 
button. The browser will prompt you to download an XML file containing the 
profile information.

### To import a profile:

1) Go to http://[OLE Server]/olefs/profile-import

2) Fill out the fields:

|Field Name|Expected Value|
|----------|--------------|
|New Profile Name|The name for the new profile. Should NOT be an existing  name.|
|Document Description|A description such as "Adding [new profile name]" where [new profile name] is the name of the new profile. Should be 40 characters or fewer|
|User Name|Typically "ole-quickstart" or any user with permission to create new batch process profiles|
|Profile File|Left-click the "Browse" button and select the XML file to load.|

To copy a profile, do an export of the profile, then an import under a different 
name. SEE "Important Note on Importing Profiles" ABOVE ABOUT THE POSSIBLE 
DANGERS OF DOING THIS!

## License

This software is provided under the CC0 1.0 Universal license
(http://creativecommons.org/publicdomain/zero/1.0/).

The "olefs-webapp-1.5.6.1.jar" file in the lib/ directory is provided under the
Educational Community License v2 (see 
http://site.kuali.org/ole/1.5.6-r21260/reference/html/Index.html#d16073e181).
