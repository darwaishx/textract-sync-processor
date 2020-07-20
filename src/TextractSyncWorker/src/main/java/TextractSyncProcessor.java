import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.Document;
import com.amazonaws.util.IOUtils;
import com.google.gson.Gson;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class TextractSyncProcessor {

    private enum DocumentType {
        JPEG, PNG, PDF, NOTSUPPORTED;
    }

    private class PageData{
        int pageNumber = -1;
        List<Block> pageBlocks = null;

        PageData(int pageNumber, List<Block> blocks){
            this.pageNumber = pageNumber;
            this.pageBlocks = blocks;
        }

        public int getPageNumber(){
            return this.pageNumber;
        }

        public List<Block> getPageBlocks(){
            return this.pageBlocks;
        }
    }

    private class Processor implements Callable<TextractSyncProcessor.PageData> {
        ByteBuffer imageBytes = null;
        int pageNumber = 0;
        AmazonTextract client = null;

        Processor(int pageNumber, ByteBuffer imageBytes, AmazonTextract client){
            this.imageBytes = imageBytes;
            this.pageNumber = pageNumber;
            this.client = client;
        }

        @Override
        public TextractSyncProcessor.PageData call() throws Exception {
//            AmazonTextract client = AmazonTextractClientBuilder.defaultClient();


            AnalyzeDocumentRequest request = new AnalyzeDocumentRequest()
                    .withFeatureTypes("TABLES", "FORMS")
                    .withDocument(new Document().withBytes(imageBytes));

            AnalyzeDocumentResult result = client.analyzeDocument(request);

            TextractSyncProcessor.PageData pageData = new TextractSyncProcessor.PageData(this.pageNumber, result.getBlocks());
            return pageData;
        }
    }

    private AmazonTextract createAwsClient(){
        AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration(
                "https://textract.us-east-1.amazonaws.com", "us-east-1");

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setMaxErrorRetry(20);
//        clientConfiguration.setMaxConnections(200);
//        RetryPolicy retryPolicy = new RetryPolicy(PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION,
//                PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
//                5,
//                false);
//        clientConfiguration.setRetryPolicy(retryPolicy);

        AmazonTextract client = AmazonTextractClientBuilder.standard()
                .withEndpointConfiguration(endpoint)
                .withClientConfiguration(clientConfiguration)
                .build();

        return client;
    }

    private void getResults(List<Future> threads, List<Block> documentBlocks) throws ExecutionException, InterruptedException, IOException {

        List<TextractSyncProcessor.PageData> documentData = new ArrayList<TextractSyncProcessor.PageData>();

        for(Future<TextractSyncProcessor.PageData> thread : threads){
            documentData.add(thread.get());
        }

        //Sort by page number
        documentData.sort(Comparator.comparing(TextractSyncProcessor.PageData::getPageNumber));

        for(TextractSyncProcessor.PageData pageData: documentData){
            documentBlocks.addAll(pageData.getPageBlocks());
        }
    }

    private InputStream getDocumentFromS3(String bucketName, String documentName) throws IOException {
        AmazonS3 s3client = AmazonS3ClientBuilder.defaultClient();
        com.amazonaws.services.s3.model.S3Object fullObject = s3client.getObject(new GetObjectRequest(bucketName, documentName));
        InputStream in = fullObject.getObjectContent();
        return in;
    }

    private void saveResultsToS3(String bucketName, String documentName, List<Block> documentBlocks) throws IOException {

        Gson gson = new Gson();
        String jsonString = gson.toJson(documentBlocks);
        byte[] bytes = jsonString.getBytes();
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(bytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("application/json");
        PutObjectRequest putRequest = new PutObjectRequest(bucketName, documentName, baInputStream, metadata);
        AmazonS3 s3client = AmazonS3ClientBuilder.defaultClient();
        s3client.putObject(putRequest);
    }

    private void saveResults(String outputDocumentName, List<Block> documentBlocks) throws IOException {
        //Save JSON to local disk
        Gson gson = new Gson();
        String jsonString = gson.toJson(documentBlocks);
        try (FileWriter fileWriter = new FileWriter(outputDocumentName)) {
            fileWriter.write(jsonString);
        }
    }

    private int calculateDpi(float mediaBoxSize){
        int maxDpi = 300;
        int targetResizePoints = 6250;

        return Math.min(maxDpi, (int)((targetResizePoints/mediaBoxSize)*72));
    }

    private List<Block> processPdfDocument(InputStream inputPdf) throws IOException, ExecutionException, InterruptedException {

        //Number of documents to render
        // and schedule for processing at a time
        int batchSize = 5;

        //Number of threads to process concurrent jobs
        int threadPoolSize = 5;

        //Output blocks
        List<Block> documentBlocks = new ArrayList<Block>();

        ExecutorService service = Executors.newFixedThreadPool(threadPoolSize);

        List<Future> threads = new ArrayList<Future>();

        BufferedImage image = null;
        ByteArrayOutputStream byteArrayOutputStream = null;
        ByteBuffer imageBytes = null;

        AmazonTextract client = createAwsClient();

        PDDocument inputDocument = PDDocument.load(inputPdf);
        PDFRenderer pdfRenderer = new PDFRenderer(inputDocument);

        int pageNumber = 1;

        for (int pageIndex = 0; pageIndex < inputDocument.getNumberOfPages(); ++pageIndex) {

           int dpi = this.calculateDpi(Math.max(inputDocument.getPage(pageIndex).getMediaBox().getWidth(),
                   inputDocument.getPage(pageIndex).getMediaBox().getHeight()));

           System.out.println("Rendering page " + pageNumber + " with dpi: " + dpi);

            //Render image
            image = pdfRenderer.renderImageWithDPI(pageIndex, dpi, org.apache.pdfbox.rendering.ImageType.RGB);

            //Get image bytes
            byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIOUtil.writeImage(image, "jpeg", byteArrayOutputStream);
            byteArrayOutputStream.flush();
            imageBytes = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());

            //Schedule to extract text and data
            Future<TextractSyncProcessor.PageData> thread = service.submit(new TextractSyncProcessor.Processor(pageNumber, imageBytes, client));
            threads.add(thread);

            if(pageNumber % batchSize == 0){
                int minPage = pageNumber - batchSize + 1;
                if(minPage < 0) {minPage = 1;}
                System.out.println("Processing page: " + minPage + " - " + pageNumber);

                getResults(threads, documentBlocks);
                threads.clear();
            }

            pageNumber++;
        }

        inputDocument.close();

        if(threads.size() > 0){
            pageNumber--;
            int minPage = pageNumber - batchSize + 1;
            if(minPage < 0) {minPage = 1;}
            System.out.println("Processing page: " + minPage + " - " + pageNumber);
            getResults(threads, documentBlocks);
            threads.clear();
        }

        service.shutdown();

        return documentBlocks;
    }

    private List<Block> processImageDocument(ByteBuffer imageBytes) throws IOException {

        AmazonTextract client = this.createAwsClient();

        AnalyzeDocumentRequest request = new AnalyzeDocumentRequest()
                .withFeatureTypes("TABLES", "FORMS")
                .withDocument(new Document().withBytes(imageBytes));

        AnalyzeDocumentResult result = client.analyzeDocument(request);

        return result.getBlocks();
    }

    private DocumentType getDocumentType(String documentName){
        String ldname = documentName.toLowerCase();
        if(ldname.endsWith(".png")){
            return DocumentType.PNG;
        }
        if(ldname.endsWith(".jpg") || ldname.endsWith(".jpeg")){
            return DocumentType.JPEG;
        }
        else if(ldname.endsWith(".pdf")){
            return DocumentType.PDF;
        }
        else{
            return DocumentType.NOTSUPPORTED;
        }
    }

    public void run(String documentName, String outputDocumentName) throws Exception {

        System.out.println("Processing " + documentName + " using sync API...");

        List<Block> documentBlocks = null;

        //Load document
        InputStream inputDocument = new FileInputStream(new File(documentName));

        DocumentType docType = this.getDocumentType(documentName);

        if(docType == DocumentType.JPEG || docType == DocumentType.PNG) {
            ByteBuffer imageBytes = null;
            imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputDocument));
            documentBlocks = this.processImageDocument(imageBytes);
        }
        else if (docType == DocumentType.PDF){
            documentBlocks = this.processPdfDocument(inputDocument);
        }
        else{
            throw new Exception("Document type is not supported.");
        }

        this.saveResults(outputDocumentName, documentBlocks);

        System.out.println("Processed document "
                    + documentName +
                    " and generated JSON " + outputDocumentName);
    }

    public void run(String bucketName, String documentName, String outputBucketName, String outputDocumentName) throws Exception {

        System.out.println("Processing " + bucketName + "/" + documentName + " using sync API...");

        List<Block> documentBlocks = null;

        //Get input document from Amazon S3
        InputStream inputDocument = this.getDocumentFromS3(bucketName, documentName);

        DocumentType docType = this.getDocumentType(documentName);

        if(docType == DocumentType.JPEG || docType == DocumentType.PNG) {
            ByteBuffer imageBytes = null;
            imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputDocument));
            documentBlocks = this.processImageDocument(imageBytes);
        }
        else if (docType == DocumentType.PDF){
            documentBlocks = this.processPdfDocument(inputDocument);
        }
        else {
            throw new Exception("Document type is not supported.");
        }

        this.saveResultsToS3(outputBucketName, outputDocumentName, documentBlocks);

        System.out.println("Processed document " +
                bucketName + "/" + documentName +
                " and generated JSON " + outputBucketName + "/" + outputDocumentName);
    }
}
