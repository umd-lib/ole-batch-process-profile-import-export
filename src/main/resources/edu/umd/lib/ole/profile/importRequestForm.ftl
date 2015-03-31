<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>Profile Import Request</title>
</head>
<body>
  
  <h1>Batch Process Profile Import</h1>
  <form method="post" enctype="multipart/form-data">
    <table>
      <tr>
        <td>
          <label for="newProfileName">New Profile Name:</label>
        </td>
        <td>
          <input type="text" name="newProfileName"/>
        </td>
      </tr>    
  
      <tr>
        <td>
          <label for="documentDescription">Document Description:</label>
        </td>
        <td>
          <input type="text" name="documentDescription"/>
        </td>
      </tr>    
      
      <tr>
        <td>
          <label for="userName">User Name:</label>
        </td>
        <td>
          <input type="text" name="userName" value="ole-quickstart"/>
        </td>
      </tr>    
  
      <tr>
        <td>
          <label for="xmlProfile">Profile File:</label>
        </td>
        <td>
          <input type="file" name="xmlProfile">
        </td>
      </tr>    
    </table>
    <button type="submit">Upload</button>
  </form>
</body>
</html>