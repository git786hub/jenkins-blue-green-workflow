import groovy.json.JsonSlurper

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest
import com.amazonaws.services.cloudformation.model.Parameter


node {

   stage 'Checkout'
   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'

   stage "DeployStaging"
   
   def web_asg_template = readFile 'cfn/web-asg.json'
   def elb_template     = readFile 'cfn/elb.json'
   def web_asg_params   = readFile 'cfn/us-east-1-web-asg-param.properties'
   def elb_params       = readFile 'cfn/us-east-1-elb-param.properties'
   
   def green_web_stack_name    = "jenkins-web-asg-stack-" + env.BUILD_NUMBER
   def staging_elb_stack_name  = "jenkins-staging-elb-stack-" + env.BUILD_NUMBER
   
   
   // Create stacks
   create_cfn_stack("us-east-1", green_web_stack_name, web_asg_template, web_asg_params, "production")
   create_cfn_stack("us-east-1", staging_elb_stack_name, elb_template, elb_params, "staging")
   
   // Sleep
   sleep 10
   
   // Wait for stacks to complete creation
   def stackNames = new ArrayList()
   stackNames.add( green_web_stack_name  )
   stackNames.add( staging_elb_stack_name )
   
   while(true) {
     boolean done = true
     for(def stackName : stackNames) {
       if(!isStackCreated(stackName)) {
         done = false
       }
     }
     if(done) {
       break
     } else {
       sleep 10
     }
   }
   
   

}

// ============================================================================

def create_cfn_stack(def region, def stackName, def template, def paramsJson, def environmentType) {
  
  def parameters = new ArrayList()
  
  if(paramsJson != null && !paramsJson.isEmpty()) {
    def slurper = new JsonSlurper()
    def paramArray = slurper.parseText(paramsJson)
    
    paramArray.each { param ->
      parameter = new Parameter()
      parameter.parameterKey   = param.ParameterKey
      parameter.parameterValue = param.ParameterValue
      parameters.add(parameter)
    }
  }
  
  def envTypeParameter = new Parameter()
  envTypeParameter.parameterKey   = 'EnvironmentType'
  envTypeParameter.parameterValue = environmentType
  parameters.add(envTypeParameter)
  
  def createStackRequest = new CreateStackRequest()
  createStackRequest.stackName    = stackName
  createStackRequest.templateBody = template
  createStackRequest.parameters   = parameters
  
  def cfnClient = new AmazonCloudFormationClient()
  def createStackResult = cfnClient.createStack(createStackRequest)
  
  println "Started stack creation ${createStackResult}"
  
  return createStackResult.stackId
}

// ============================================================================

def isStackCreated(def stackName) {
  
  def stackCreatedStatus = true
  
  def cfnClient = new AmazonCloudFormationClient()
  
  def descStackRequest = new DescribeStacksRequest()
  descStackRequest.stackName = stackName
  def descStackResult = cfnClient.describeStacks(descStackRequest)
  def stacks = descStackResult.stacks
  
  if(stacks != null & stacks.size() == 1) {
    def stack = stacks.get(0)
    def stackStatus = stack.stackStatus
    
    print "Stack status (${stackName}) : ${stackStatus}"
    
    if(stackStatus in ['UPDATE_COMPLETE', 'CREATE_COMPLETE']) {
      // Stack is ready
    } else if(stackStatus in ['UPDATE_IN_PROGRESS', 'UPDATE_ROLLBACK_IN_PROGRESS', 
                              'UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS', 'CREATE_IN_PROGRESS', 
                              'ROLLBACK_IN_PROGRESS']) {
      // Stack is not yet ready
      stackCreatedStatus = false
    } else {
      throw new RuntimeException("Stack ${stackName} failed to create.")
    }
    
  }
  
  return stackCreatedStatus

  
}