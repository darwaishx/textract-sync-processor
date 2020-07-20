import { expect as expectCDK, matchTemplate, MatchStyle } from '@aws-cdk/assert';
import * as cdk from '@aws-cdk/core';
import * as TextractSyncProcessor from '../lib/textract-sync-processor-stack';

test('Empty Stack', () => {
    const app = new cdk.App();
    // WHEN
    const stack = new TextractSyncProcessor.TextractSyncProcessorStack(app, 'MyTestStack');
    // THEN
    expectCDK(stack).to(matchTemplate({
      "Resources": {}
    }, MatchStyle.EXACT))
});
