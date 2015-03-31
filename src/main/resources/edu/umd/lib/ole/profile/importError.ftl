<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>Error</title>
</head>
<body>
  <h1>Error</h1>
  <p>The following errors occurred:</p>
  <ul>
  <#list errors as error>
    <li>${error}</li>
  </#list>
  </ul>
  <p>newProfileName: ${newProfileName!"(Missing)"}</p>
  <p>documentDescription: ${documentDescription!"(Missing)"}</p>
  <p>userName: ${userName!"(Missing)"}</p>
  <p>fileContents: ${fileContents!"(Missing)"}</p>
</body>
</html>