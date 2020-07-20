import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.concurrent.ExecutionException;

public class TextractSyncLambdaHandler implements RequestHandler<SQSEvent, Void> {

    @Override
    public Void handleRequest(SQSEvent event, Context context) {

        System.out.println("Lambda handler starting...");

        for (SQSEvent.SQSMessage msg : event.getRecords()) {

            String msgBody = msg.getBody();
            System.out.println("Message:");
            System.out.println(msgBody);

            JsonObject rootObject = JsonParser.parseString(msgBody).getAsJsonObject();
            JsonArray records = rootObject.get("Records").getAsJsonArray();
            JsonObject record = records.get(0).getAsJsonObject();
            JsonObject s3Object = record.get("s3").getAsJsonObject();
            JsonObject bucketObject = s3Object.getAsJsonObject("bucket");
            JsonObject objectObject = s3Object.getAsJsonObject("object");
            String bucketName = bucketObject.get("name").getAsString();
            String objectName = objectObject.get("key").getAsString();

            try {
                objectName = URLDecoder.decode(objectName, "UTF-8");
                System.out.println("Bucket name: " + bucketName);
                System.out.println("Document name: " + objectName);
                String outputObjectName = objectName.replace('.', '-') + "-output.json";

                TextractSyncProcessor processor = new TextractSyncProcessor();
                processor.run(bucketName, objectName, bucketName, outputObjectName);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
//            if(rootObject == null)
//                System.out.println("Root object is NULL.");
//            else {
//                System.out.print("Got root object");
//                System.out.println(rootObject.toString());
//            }

//            JsonArray records = rootObject.get("Records").getAsJsonArray();
//            if(records == null)
//                System.out.println("Records object is NULL.");
//            else {
//                System.out.print("Got records");
//            }

//            JsonObject record = records.get(0).getAsJsonObject();
//            if(record == null)
//                System.out.println("Record object is NULL.");
//            else {
//                System.out.print("Got record");
//                System.out.println(record.toString());
//            }

//            JsonObject s3Object = record.get("s3").getAsJsonObject();
//            if(s3Object == null)
//                System.out.println("s3 object is NULL.");
//            else {
//                System.out.print("Got s3 object");
//                System.out.println(s3Object.toString());
//            }

//            System.out.println(s3Object.get("s3SchemaVersion").getAsString());

//            JsonObject bucketObject = s3Object.getAsJsonObject("bucket");
//            if(bucketObject == null)
//                System.out.println("Bucket object is NULL.");
//            else {
//                System.out.print("Got bucket object");
//                System.out.println(bucketObject.toString());
//            }

//            JsonObject objectObject = s3Object.getAsJsonObject("object");

//            if(objectObject == null)
//                System.out.println("objetObject is NULL.");
//            else {
//                System.out.print("Got object object");
//                System.out.println(objectObject.toString());
//            }

//            String bucketName = bucketObject.get("name").getAsString();
//            String objectName = objectObject.get("key").getAsString();
//
//            System.out.println(bucketName);
//            System.out.print(objectName);
//
//            System.out.println("End");
        }

        return null;
    }
}