# Usage

```
java8 -jar eureka-0.0.1-SNAPSHOT.jar
java8 -jar -DPORT=8081 storage-0.0.1-SNAPSHOT.jar
java8 -jar -DPORT=8086 api-0.0.1-SNAPSHOT.jar

curl -v localhost:8086/objects/bab -XPUT -H "Content-Type: text/plain" -d "This is content"
```
