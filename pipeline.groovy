
import static groovy.io.FileType.FILES

node {
   // Mark the code checkout 'stage'....
   stage 'Checkout'
   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'

   // Mark the code build 'stage'....
   stage 'DeployStaging'
   createCfnStack("us-east-1", "cfn/web-asg.json", "web-asg-" + env.BUILD_NUMBER)
   
}

def createCfnStack(def awsRegion, def templateFile, def stackName) {
  
  // Execute create-stack command
  sh "scripts/create_stack.py ${awsRegion} ${stackName} ${templateFile} "
}
