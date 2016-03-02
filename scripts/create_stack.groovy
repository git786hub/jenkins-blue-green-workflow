#!/usr/bin/env groovy

@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.10.57')

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient

def cfnClient = new AmazonCloudFormationClient()

print "From create_stack"
