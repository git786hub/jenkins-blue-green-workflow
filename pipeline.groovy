
node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'
   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'
   
   def WEB_ASG_STACK_NAME = "web-asg-" + env.BUILD_NUMBER
   def STAGING_ELB_STACK_NAME = "staging-elb-" + env.BUILD_NUMBER
   
   def PRODUCTION_ELB_STACK_NAME = "prod-web-elb"
      
   def region = "us-east-1"
   
   // Initialzie Blue/Green deploy script
   sh "JOB_NAME=${env.JOB_NAME} BUILD_NUMBER=${env.BUILD_NUMBER} scripts/bgdeploy.groovy --region ${region} --ami-id ${AMIID} --elb-stack ${PRODUCTION_ELB_STACK_NAME} --init"

   stage "CreateStacks"
   sh "scripts/bgdeploy.groovy --create-stacks"
   
   stage "Flip Green to Blue"
   sh "scripts/bgdeploy.groovy --flip-green-to-blue"
  
   stage "Cleanup"
   def userInput = input (id: 'userInput',  message: 'Proceed with deployment?', parameters: [[$class: 'ChoiceParameterDefinition', choices: "Yes\nNo", description: 'Proceed with deployment?', name: 'proceed']])
   if(userInput.equals("No")) {
     // Abort deployment and rollback
     
     print "Rolling back deployment"
     sh "scripts/bgdeploy.groovy --rollback"
     print "Rollback completed" 
  } else {
    // Cleanup.  Remove test ELB stack and old production stack
    sh "scripts/bgdeploy.groovy --cleanup"
  }
}
