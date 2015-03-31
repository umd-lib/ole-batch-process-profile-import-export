<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>Profile Import Request</title>
</head>
<body>
  
  <h1>Batch Process Profile Import</h1>
  <form method="post" enctype="multipart/form-data">
    <div>
      <label for="newProfileName">New Profile Name:</label>
      <input type="text" name="newProfileName"/>
    </div>
    
    <div>
      <label for="documentDescription">Document Description:</label>
      <input type="text" name="documentDescription"/>
    </div>
    
    <div>
      <label for="userName">User Name:</label>
      <input type="text" name="userName" value="admin"/>
    </div>

    <div>    
      <label for="xmlProfile">Profile File:</label>
      <input type="file" name="xmlProfile">
    </div>

    <button type="submit">Upload</button>
  </form>
</body>
</html>