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
</body>
</html>