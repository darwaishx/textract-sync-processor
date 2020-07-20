# Process Image and Pdf documents using Textract Sync API

## Prerequisites

- Node.js (required by CDK)
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html)

## Deployment

- Install [AWS Cloud Development Kit (CDK)](https://docs.aws.amazon.com/cdk/latest/guide/what-is.html): npm install -g aws-cdk
- Download this repo on your local machine
- Go to folder src/TextractSyncWorker and run mvn package
- Go to folder deployment and run: npm install
- Run "cdk bootstrap"
- Run "cdk deploy" to deploy stack

## Test
- Go to S3 bucket created by stack named as "textractsyncprocessorstack-inputbucketxxxxxxxxxx"
- Upload images (png/jpg/jpeg) or pdf documents to S3 bucket. Refresh to see the corresponding output JSON files.

## Development
- If you update Java source code, use mvn package to build jar file
- Run "cdk deploy" in deployment folder to update deployment

## Useful CDK commands

The `cdk.json` file tells the CDK Toolkit how to execute your app.

 * `npm run build`   compile typescript to js
 * `npm run watch`   watch for changes and compile
 * `npm run test`    perform the jest unit tests
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk synth`       emits the synthesized CloudFormation template

## Cost
Solution deploys different AWS components (S3 bucket, SQS queue, Lambda function and IAM roles). You get charged for these components as well usage charges for Textract as you process documents by uploading them to S3 bucket created by the solution.