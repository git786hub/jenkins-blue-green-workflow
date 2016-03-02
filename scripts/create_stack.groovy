@Grab(group='com.amazonaws', module='aws-java-sdk-cloudformation', version='1.10.57')

#!/usr/bin/env groovy

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient

def cfnClient = new AmazonCloudFormationClient()

print "From create_stack"
