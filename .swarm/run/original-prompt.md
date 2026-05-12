There is a SFI (security) work item in this file - /src/main/java/com/microsoft/azure/functions/worker/JavaWorkerClient.java 
Multiple language workers use plaintext/insecure gRPC channels, enabling MITM attacks. 
Java worker: Uses plaintext gRPC channel enabling MITM tampering leading to potential code execution 

Can you fix it? It would be great to identify these things - - Is the fix necessary? - What was the historical context of designing the code as is? Was it intentional or a missed gap? - Can you confirm if there will be any regressions if the fix is made? - Will there be a contract or a breaking change for the customer? - How well is it tested?