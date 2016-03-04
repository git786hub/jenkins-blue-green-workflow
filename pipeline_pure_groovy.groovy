import groovy.json.JsonSlurper

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.Parameter


node {

   stage 'Checkout'
   // Get some code from a GitHub repository
   git url: 'https://github.com/vinayselvaraj/jenkins-blue-green-workflow.git'

   stage "DeployStaging"
   
   def web_asg_template = readFile 'cfn/web-asg.json'
   def elb_template     = readFile 'cfn/elb.json'
   
   def web_asg_params   = readFile 'cfn/us-east-1-web-asg-param.properties'
   
   create_cfn_stack("us-east-1", "web-asg-stack2", web_asg_template, web_asg_params, "production")
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
  
}

