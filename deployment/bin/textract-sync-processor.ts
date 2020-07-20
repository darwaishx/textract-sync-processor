#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from '@aws-cdk/core';
import { TextractSyncProcessorStack } from '../lib/textract-sync-processor-stack';

const app = new cdk.App();
new TextractSyncProcessorStack(app, 'TextractSyncProcessorStack');
