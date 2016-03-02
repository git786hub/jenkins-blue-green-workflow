
import static groovy.io.FileType.FILES

node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'
   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'
   
   def WEB_ASG_STACK_NAME = "web-asg-" + env.BUILD_NUMBER
   def STAGING_ELB_STACK_NAME = "staging-elb-" + env.BUILD_NUMBER
   
   def regions = new ArrayList()
   regions.add("us-east-1")

   regions.each { region->
     
     print "Deploying to region: ${region}"
     
     stage 'CreateStacks'
     createCfnStack(region, "cfn/web-asg.json", WEB_ASG_STACK_NAME)
     createCfnStack(region, "cfn/elb.json", STAGING_ELB_STACK_NAME)
   
     stage 'AssociateWebStackWithStagingELB'
     associateASGWithELB(region, WEB_ASG_STACK_NAME,  STAGING_ELB_STACK_NAME)
   }
   
   
}

def createCfnStack(def awsRegion, def templateFile, def stackName) {
  // Execute create-stack command
  sh "AWS_DEFAULT_REGION=${awsRegion} scripts/create_stack.py ${stackName} ${templateFile} "
}

def associateASGWithELB(def awsRegion, def asgStack, def elbStack) {
  sh "AWS_DEFAULT_REGION=${awsRegion} scripts/assoc_asg_elb.py ${asgStack} ${elbStack} "
}