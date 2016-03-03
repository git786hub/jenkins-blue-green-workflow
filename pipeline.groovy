
assumeRole = "arn:....."

node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'
   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'
   
   def WEB_ASG_STACK_NAME = "web-asg-" + env.BUILD_NUMBER
   def STAGING_ELB_STACK_NAME = "staging-elb-" + env.BUILD_NUMBER
      
   def regions = new ArrayList()
   regions.add("us-east-1")
   regions.add("us-west-1")

   stage "CreateStacks"
   
   // Start stack creations
   regions.each { region->
          
     createCfnStack(region, "cfn/web-asg.json", "cfn/${region}-web-asg-param.properties", WEB_ASG_STACK_NAME, "production")
     createCfnStack(region, "cfn/elb.json",     "cfn/${region}-elb-param.properties", STAGING_ELB_STACK_NAME, "staging")
   }
   
   // Wait for CloudFormation stack creation
   regions.each { region->
     waitForCfnStackCreation(region, WEB_ASG_STACK_NAME)
     waitForCfnStackCreation(region, STAGING_ELB_STACK_NAME)
   }
   
   stage "AssociateWebStackWithStagingELB"
   
   // Associate Staging ELB with AutoScaling group
   regions.each { region->
     associateASGWithELB(region, WEB_ASG_STACK_NAME,  STAGING_ELB_STACK_NAME)
   }
   
   // Sleep for ASG instances to be registered
   sleep(60000) // 60 seconds
   
   // Wait for the ELB to put instances in service
   regions.each { region->
     waitForASGInstancesToGoInService(region, WEB_ASG_STACK_NAME, STAGING_ELB_STACK_NAME)
   }
   
}

def createCfnStack(def awsRegion, def templateFile, def paramFile, def stackName, def environmentType) {
  // Execute create-stack command
  sh "AWS_DEFAULT_REGION=${awsRegion} ASSUME_ROLE=${assumeRole} scripts/create_stack.py ${stackName} ${templateFile} ${paramFile} ${environmentType}"
}

def waitForCfnStackCreation(def awsRegion, def stackName) {
  sh "AWS_DEFAULT_REGION=${awsRegion} ASSUME_ROLE=${assumeRole}  scripts/wait_for_stack_create.py ${stackName} "
}

def associateASGWithELB(def awsRegion, def asgStack, def elbStack) {
  sh "AWS_DEFAULT_REGION=${awsRegion} ASSUME_ROLE=${assumeRole} scripts/assoc_asg_elb.py ${asgStack} ${elbStack} "
}

def disassociateASGWithELB(def awsRegion, def asgStack, def elbStack) {
  sh "AWS_DEFAULT_REGION=${awsRegion} ASSUME_ROLE=${assumeRole} scripts/deassoc_asg_elb.py ${asgStack} ${elbStack} "
}

def waitForASGInstancesToGoInService(def awsRegion, def asgStack, def elbStack) {
  sh "AWS_DEFAULT_REGION=${awsRegion} ASSUME_ROLE=${assumeRole} scripts/wait_for_asg_elb_registration.py ${asgStack} ${elbStack} "
}