
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
  //sh "/bin/aws cloudformation create-stack --stack-name ${stackName} --template-body file://./${templateFile} --region ${awsRegion}"
  
  sh "scripts/create_stack.groovy ${awsRegion} ${templateFile} ${stackName}"
}
