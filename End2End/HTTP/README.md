### Steps
1. Run `mvn clean package` under `HTTP` folder to build the project.
2. Run `func start` under `HTTP\src\main\functions` folder to start the function
3. Use whatever tools you want to send an Http request to the function. for example: 
- Open a browser, copy the url from log to browser and add a request string `name=world`, press enter. The url should be like: 
```
http://localhost:7071/api/HttpTriggerJava?name=world