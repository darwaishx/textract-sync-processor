import * as cdk from '@aws-cdk/core';
import s3 = require('@aws-cdk/aws-s3');
import * as s3n from '@aws-cdk/aws-s3-notifications';
import lambda = require('@aws-cdk/aws-lambda');
import sqs = require('@aws-cdk/aws-sqs');
import { Duration } from '@aws-cdk/core';
import * as lambdaes from '@aws-cdk/aws-lambda-event-sources';
import * as iam from '@aws-cdk/aws-iam'

export class TextractSyncProcessorStack extends cdk.Stack {
  constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

  //S3 bucket for input documents and output
  const inputBucket = new s3.Bucket(this, 'input-bucket', { versioned: false});

  //DLQ
  const dlq = new sqs.Queue(this, 'dlq', {
    visibilityTimeout: cdk.Duration.seconds(30),
    retentionPeriod: cdk.Duration.seconds(1209600)
  });

  //Input Queue for sync jobs
  const syncJobsQueue = new sqs.Queue(this, 'sync-jobs', {
    visibilityTimeout: cdk.Duration.seconds(900),
    retentionPeriod: cdk.Duration.seconds(1209600),
    deadLetterQueue : { queue: dlq, maxReceiveCount: 10 }
  });

  //S3Event to SQS
  inputBucket.addEventNotification(s3.EventType.OBJECT_CREATED,
    new s3n.SqsDestination(syncJobsQueue), { suffix: '.pdf'});
  inputBucket.addEventNotification(s3.EventType.OBJECT_CREATED,
    new s3n.SqsDestination(syncJobsQueue), { suffix: '.jpg'});
  inputBucket.addEventNotification(s3.EventType.OBJECT_CREATED,
    new s3n.SqsDestination(syncJobsQueue), { suffix: '.jpeg'});
  inputBucket.addEventNotification(s3.EventType.OBJECT_CREATED,
    new s3n.SqsDestination(syncJobsQueue), { suffix: '.png'});

  //Event processor - Lambda
  const syncProcessor = new lambda.Function(this, 'sync-processor', {
    runtime: lambda.Runtime.JAVA_11,
    code: lambda.Code.fromAsset('../src/TextractSyncWorker/target/textract-sync-worker-1.0.jar'),
    handler: 'TextractSyncLambdaHandler::handleRequest',
    timeout: Duration.seconds(900),
    reservedConcurrentExecutions: 25,
    memorySize: 3000,
    environment: {
      INPUT_BUCKET: inputBucket.bucketName,
      QUEUE_URL: syncJobsQueue.queueUrl
    }
  });

  //Trigger
  syncProcessor.addEventSource(new lambdaes.SqsEventSource(syncJobsQueue, {
    batchSize: 1
  }));

  syncProcessor.addToRolePolicy(new iam.PolicyStatement({
    effect: iam.Effect.ALLOW,
    resources: ["*"],
    actions: ['textract:AnalyzeDocument']
   }));

  //Permissions
  syncJobsQueue.grantConsumeMessages(syncProcessor)
  inputBucket.grantReadWrite(syncProcessor)
  }
}
