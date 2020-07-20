import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import java.util.ArrayList;
import java.util.List;

public class TextractSyncTest {

    private static void testLambda(){
        SQSEvent.SQSMessage m = new SQSEvent.SQSMessage();
        String msgBody = "{\n" +
                "    \"Records\": [\n" +
                "        {\n" +
                "            \"eventVersion\": \"2.1\",\n" +
                "            \"eventSource\": \"aws:s3\",\n" +
                "            \"awsRegion\": \"us-east-1\",\n" +
                "            \"eventTime\": \"2020-07-18T15:59:40.571Z\",\n" +
                "            \"eventName\": \"ObjectCreated:Copy\",\n" +
                "            \"userIdentity\": {\n" +
                "                \"principalId\": \"AWS:AIDAJT4LV65DTO2IS4LLS\"\n" +
                "            },\n" +
                "            \"requestParameters\": {\n" +
                "                \"sourceIPAddress\": \"73.19.60.189\"\n" +
                "            },\n" +
                "            \"responseElements\": {\n" +
                "                \"x-amz-request-id\": \"18838E1094398293\",\n" +
                "                \"x-amz-id-2\": \"kwy7c7q4nSMehSIgnJpCpfio5BK3xiLCTCb1Fbz5yTiTUsGK3WRA/k7t6Zu4T4Cn56AxDxNrXiOM7wqTO1sdh5+lDNUP5qEPrM8sD5c6/xE=\"\n" +
                "            },\n" +
                "            \"s3\": {\n" +
                "                \"s3SchemaVersion\": \"1.0\",\n" +
                "                \"configurationId\": \"MzMwZDNkODgtZTAyZS00Zjk1LWFkNjEtYjY3YmEwYmFlY2E1\",\n" +
                "                \"bucket\": {\n" +
                "                    \"name\": \"pdfsyncstack-inputbucket3bf8630a-1b80ufhb10j38\",\n" +
                "                    \"ownerIdentity\": {\n" +
                "                        \"principalId\": \"A1MYTGSBJ0QCLZ\"\n" +
                "                    },\n" +
                "                    \"arn\": \"arn:aws:s3:::pdfsyncstack-inputbucket3bf8630a-1b80ufhb10j38\"\n" +
                "                },\n" +
                "                \"object\": {\n" +
                "                    \"key\": \"SampleInput.pdf\",\n" +
                "                    \"size\": 84408,\n" +
                "                    \"eTag\": \"efe3d4bee135b1ee1acce2537fbed35d\",\n" +
                "                    \"sequencer\": \"005F131C72A9BE089C\"\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}";

        m.setBody(msgBody);
        List<SQSEvent.SQSMessage> msgs = new ArrayList<SQSEvent.SQSMessage>();
        msgs.add(m);
        SQSEvent e = new SQSEvent();
        e.setRecords(msgs);
        TextractSyncLambdaHandler lh = new TextractSyncLambdaHandler();
        lh.handleRequest(e, null);
    }

    private static void testLocalDocument(String documentName, String outputDocumentName) throws Exception {
        TextractSyncProcessor processor = new TextractSyncProcessor();
        processor.run(documentName, outputDocumentName);
    }

    private static void testS3Document(String bucketName, String documentName, String outputBucketName, String outputDocumentName) throws Exception {
        TextractSyncProcessor processor = new TextractSyncProcessor();
        processor.run(bucketName, documentName, outputBucketName, outputDocumentName);
    }

    public static void main(String args[]) {
        try {
            //Test local document
            //Test image
            testLocalDocument("documents/SampleInput.png",
                    "documents/SampleInput-png.json");
            //Test pdf
            testLocalDocument("documents/SampleInput.pdf",
                    "documents/SampleInput-pdf.json");

            //Test S3 document
            //Test Image
            testS3Document("pdfsyncstack-inputbucket3bf8630a-1b80ufhb10j38",
                    "SampleInput.png",
                    "pdfsyncstack-inputbucket3bf8630a-1b80ufhb10j38",
                    "SampleInput-png.json");

            //Test Pdf
            testS3Document("pdfsyncstack-inputbucket3bf8630a-1b80ufhb10j38",
                            "SampleInput.pdf",
                            "pdfsyncstack-inputbucket3bf8630a-1b80ufhb10j38",
                            "SampleInput-pdf.json");

            //Test Lambda
            testLambda();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
